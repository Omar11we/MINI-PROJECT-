// Rentals, leases, payments and maintenance.
// Each write follows the Java services: lock the vehicle, recheck the rules, then write.
const express = require('express');
const {
  col, aggregate, nextId, AppError,
} = require('../lib/db');
const {
  handle, paging, sorting, Filter, listPage,
} = require('../lib/http');
const { isVehicleAvailable, isDriverAvailable } = require('../lib/availability');
const { rentalCharge, leaseTotal } = require('../lib/billing');
const { afterRentalChange, completeRental } = require('../lib/procedures');
const { withLock } = require('../lib/locks');
const { lookupOne, fullName } = require('../lib/pipeline');
const v = require('../lib/validate');

const router = express.Router();

/** Individuals need a licence valid through the end date; corporate rentals need a chauffeur. */
async function checkCustomer(customerId, endDate, { driverId = null, isRental = false } = {}) {
  const customer = await col('customer').findOne({ _id: customerId });
  if (!customer) throw new AppError('Select an existing customer.');
  if (customer.customer_type === 'Individual') {
    const expiry = customer.individual?.license_expiry;
    if (!expiry || expiry < endDate.slice(0, 10)) {
      throw new AppError(`The individual's licence must remain valid through the ${isRental ? 'rental' : 'lease'} end date.`);
    }
  } else if (isRental && !driverId) {
    throw new AppError('Every corporate rental must have an active chauffeur.');
  }
  if (driverId) {
    const driver = await col('driver').findOne({ _id: driverId });
    if (!driver || driver.status !== 'Active') throw new AppError('The assigned chauffeur is not active.');
  }
}

/** Joins a contract's balance from a balance view; contracts not in the view owe nothing. */
function balanceStages(view, fields) {
  return [
    ...lookupOne(view, '_id', 'b'),
    { $set: Object.fromEntries(fields.map((f) => [f, { $ifNull: [`$b.${f}`, null] }])) },
  ];
}

// ---------- Quotes (fn_rental_charge and fn_lease_total) ----------

router.get('/quote', handle(async (req) => {
  const vehicleId = v.id(req.query.vehicleId);
  if (!vehicleId) return { amount: null };
  const vehicle = await col('vehicle').findOne({ _id: vehicleId });
  if (req.query.kind === 'lease') {
    const start = v.date(req.query.from);
    const end = v.date(req.query.to);
    if (!start || !end || end <= start || vehicle?.lease_rate == null) return { amount: null };
    return { lease_rate: vehicle.lease_rate, amount: leaseTotal(vehicle.lease_rate, start, end) };
  }
  const start = v.dateTime(req.query.from) || v.nowString();
  const end = v.dateTime(req.query.to);
  if (!end || end <= start || vehicle?.rental_rate == null) return { amount: null };
  return { rental_rate: vehicle.rental_rate, amount: rentalCharge(start, end, vehicle.rental_rate) };
}));

// ---------- Rentals ----------

const RENTAL_SORT = {
  id: '_id', customer: 'customer_name', vehicle: 'registration_no',
  start: 'scheduled_start_datetime', expected: 'expected_return_datetime',
  status: 'status', balance: 'balance_due',
};

router.get('/rentals', handle(async (req) => {
  const now = v.nowString();
  const filter = new Filter().search(req.query.q,
    ['c.first_name', 'c.last_name', 'v.registration_no', 'v.model', '#_id']);
  if (req.query.outstanding === '1') filter.add({ balance_due: { $gt: 0 } });
  return listPage('rental', {
    stages: [
      ...lookupOne('customer', 'customer_id', 'c'),
      ...lookupOne('vehicle', 'vehicle_id', 'v'),
      ...balanceStages('vw_rental_balance', ['charge_total', 'paid_amount', 'balance_due']),
      { $set: { customer_name: fullName('c'), registration_no: '$v.registration_no' } },
    ],
    filter,
    statusField: 'status',
    status: req.query.status,
    sort: sorting(req, RENTAL_SORT, 'id', 'desc'),
    page: paging(req),
    output: [
      ...lookupOne('driver', 'driver_id', 'd'),
      {
        $set: {
          rental_id: '$_id',
          customer_type: '$c.customer_type',
          model: '$v.model',
          driver_name: { $ifNull: [fullName('d'), null] },
          overdue: { $and: [{ $eq: ['$status', 'Active'] }, { $lt: ['$expected_return_datetime', now] }] },
          expired: { $and: [{ $eq: ['$status', 'Confirmed'] }, { $lte: ['$expected_return_datetime', now] }] },
        },
      },
      { $project: { _id: 0, c: 0, v: 0, d: 0, b: 0 } },
    ],
  });
}));

router.post('/rentals', handle(async (req) => {
  const body = req.body;
  const walkIn = body.mode === 'walkin';
  const customerId = v.id(body.customer_id);
  const vehicleId = v.id(body.vehicle_id);
  const driverId = v.id(body.driver_id);
  const now = v.nowString();
  const start = walkIn ? now : v.dateTime(body.scheduled_start);
  const expected = v.dateTime(body.expected_return);
  const odometerOut = walkIn ? v.number(body.odometer_out) : null;
  const conditionOut = walkIn ? v.text(body.condition_out) : null;

  v.check(customerId && vehicleId, 'Choose a customer and a vehicle.');
  v.check(start && expected && expected > start, 'Expected return must be after the start.');
  v.check(start >= v.nowString(-5), 'A new rental cannot be scheduled in the past.');
  if (walkIn) {
    v.check(Number.isInteger(odometerOut) && odometerOut >= 0, 'Enter the pickup odometer reading.');
    v.check(conditionOut, 'Describe the vehicle condition at pickup.');
  }

  const id = await withLock([`vehicle:${vehicleId}`, driverId && `driver:${driverId}`], async () => {
    const vehicle = await col('vehicle').findOne({ _id: vehicleId });
    if (!vehicle || vehicle.rental_rate === null || ['Maintenance', 'Inactive'].includes(vehicle.status)) {
      throw new AppError('The selected vehicle cannot be rented.');
    }
    await checkCustomer(customerId, expected, { driverId, isRental: true });
    if (!await isVehicleAvailable(vehicleId, start, expected)) {
      throw new AppError('The vehicle is not available for the full requested period.', 409);
    }
    if (driverId && !await isDriverAvailable(driverId, start, expected)) {
      throw new AppError('The assigned chauffeur is not available for this period.', 409);
    }
    const status = walkIn ? 'Active' : 'Confirmed';
    const rentalId = await nextId('rental');
    await col('rental').insertOne({
      _id: rentalId,
      customer_id: customerId,
      vehicle_id: vehicleId,
      driver_id: driverId,
      scheduled_start_datetime: start,
      expected_return_datetime: expected,
      actual_pickup_datetime: walkIn ? start : null,
      actual_return_datetime: null,
      odometer_out: odometerOut,
      odometer_in: null,
      condition_out: conditionOut,
      condition_in: null,
      daily_rate_applied: vehicle.rental_rate,
      charges: null,
      status,
      created_at: now,
      updated_at: now,
    });
    // A walk-in starts Active, so the vehicle is marked Rented (trg_rental_after_insert).
    await afterRentalChange(null, status, vehicleId);
    return rentalId;
  });
  return { id };
}));

router.post('/rentals/:id/start', handle(async (req) => {
  const rentalId = v.id(req.params.id);
  const pickup = v.dateTime(req.body.pickup) || v.nowString();
  const odometerOut = v.number(req.body.odometer_out);
  const conditionOut = v.text(req.body.condition_out);
  v.check(Number.isInteger(odometerOut) && odometerOut >= 0 && conditionOut,
    'Enter the pickup time, odometer and condition.');

  const initial = await col('rental').findOne({ _id: rentalId });
  if (!initial) throw new AppError('The rental record no longer exists.', 404);

  await withLock([`vehicle:${initial.vehicle_id}`, initial.driver_id && `driver:${initial.driver_id}`], async () => {
    const vehicle = await col('vehicle').findOne({ _id: initial.vehicle_id });
    const rental = await col('rental').findOne({ _id: rentalId });
    if (!vehicle || !rental || rental.status !== 'Confirmed') {
      throw new AppError('Only a confirmed rental can be started.');
    }
    if (pickup >= rental.expected_return_datetime) throw new AppError('Pickup must be before expected return.');
    await checkCustomer(rental.customer_id, rental.expected_return_datetime,
      { driverId: rental.driver_id, isRental: true });
    if (!await isVehicleAvailable(rental.vehicle_id, pickup, rental.expected_return_datetime,
      { ignoreRental: rentalId })) {
      throw new AppError('The vehicle has another commitment during this rental.', 409);
    }
    if (rental.driver_id && !await isDriverAvailable(rental.driver_id, pickup,
      rental.expected_return_datetime, rentalId)) {
      throw new AppError('The assigned chauffeur is not available for this period.', 409);
    }
    await col('rental').updateOne({ _id: rentalId }, {
      $set: {
        actual_pickup_datetime: pickup, odometer_out: odometerOut, condition_out: conditionOut,
        status: 'Active', updated_at: v.nowString(),
      },
    });
    // trg_rental_after_update: the vehicle is now Rented.
    await afterRentalChange('Confirmed', 'Active', rental.vehicle_id);
  });
}));

router.post('/rentals/:id/complete', handle(async (req) => {
  const rentalId = v.id(req.params.id);
  const returnAt = v.dateTime(req.body.return_at) || v.nowString();
  const odometerIn = v.number(req.body.odometer_in);
  const conditionIn = v.text(req.body.condition_in);
  v.check(Number.isInteger(odometerIn) && odometerIn >= 0 && conditionIn,
    'Enter the return time, odometer and condition.');

  const rental = await col('rental').findOne({ _id: rentalId });
  if (!rental || rental.status !== 'Active') throw new AppError('Only an active rental can be completed.');
  if (odometerIn < rental.odometer_out || returnAt <= rental.actual_pickup_datetime) {
    throw new AppError('Return details must follow the pickup details.');
  }
  // sp_complete_rental sets the charge and completes it; the vehicle is then freed.
  const charges = await withLock([`vehicle:${rental.vehicle_id}`],
    () => completeRental(rentalId, returnAt, odometerIn, conditionIn));
  return { charges };
}));

router.post('/rentals/:id/cancel', handle(async (req) => {
  const result = await col('rental').updateOne(
    { _id: v.id(req.params.id), status: 'Confirmed' },
    { $set: { status: 'Cancelled', updated_at: v.nowString() } },
  );
  if (!result.matchedCount) throw new AppError('Only a confirmed rental can be cancelled.');
}));

// ---------- Leases ----------

const LEASE_SORT = {
  id: '_id', customer: 'customer_name', vehicle: 'registration_no',
  start: 'start_date', end: 'end_date', monthly: 'monthly_charge',
  status: 'status', balance: 'balance_due',
};

router.get('/leases', handle(async (req) => {
  const filter = new Filter().search(req.query.q,
    ['c.first_name', 'c.last_name', 'v.registration_no', 'v.model', '#_id']);
  if (req.query.outstanding === '1') filter.add({ balance_due: { $gt: 0 } });
  return listPage('lease', {
    stages: [
      ...lookupOne('customer', 'customer_id', 'c'),
      ...lookupOne('vehicle', 'vehicle_id', 'v'),
      ...balanceStages('vw_lease_balance', ['lease_total', 'paid_amount', 'balance_due']),
      { $set: { customer_name: fullName('c'), registration_no: '$v.registration_no' } },
    ],
    filter,
    statusField: 'status',
    status: req.query.status,
    sort: sorting(req, LEASE_SORT, 'id', 'desc'),
    page: paging(req),
    output: [
      { $set: { lease_id: '$_id', model: '$v.model' } },
      { $project: { _id: 0, c: 0, v: 0, b: 0 } },
    ],
  });
}));

router.post('/leases', handle(async (req) => {
  const customerId = v.id(req.body.customer_id);
  const vehicleId = v.id(req.body.vehicle_id);
  const startDate = v.date(req.body.start_date);
  const endDate = v.date(req.body.end_date);
  const deposit = v.number(req.body.deposit) ?? 0;
  v.check(customerId && vehicleId, 'Choose a customer and a vehicle.');
  v.check(startDate && endDate && endDate > startDate, 'The lease end date must be after its start date.');
  v.check(deposit >= 0, 'Lease deposit must be zero or greater.');

  const id = await withLock([`vehicle:${vehicleId}`], async () => {
    const vehicle = await col('vehicle').findOne({ _id: vehicleId });
    if (!vehicle || vehicle.lease_rate === null || ['Maintenance', 'Inactive'].includes(vehicle.status)) {
      throw new AppError('The selected vehicle cannot be leased.');
    }
    await checkCustomer(customerId, endDate);
    // Whole days: from the start date through the end date.
    if (!await isVehicleAvailable(vehicleId, `${startDate} 00:00:00`, `${v.addDays(endDate, 1)} 00:00:00`)) {
      throw new AppError('The vehicle is not available for the full lease period.', 409);
    }
    const leaseId = await nextId('lease');
    const now = v.nowString();
    await col('lease').insertOne({
      _id: leaseId,
      customer_id: customerId,
      vehicle_id: vehicleId,
      start_date: startDate,
      end_date: endDate,
      monthly_charge: vehicle.lease_rate,
      deposit,
      lease_total: leaseTotal(vehicle.lease_rate, startDate, endDate),
      status: 'Scheduled',
      created_at: now,
      updated_at: now,
    });
    return leaseId;
  });
  return { id };
}));

router.post('/leases/:id/start', handle(async (req) => {
  const leaseId = v.id(req.params.id);
  const initial = await col('lease').findOne({ _id: leaseId });
  if (!initial) throw new AppError('The lease record no longer exists.', 404);

  await withLock([`vehicle:${initial.vehicle_id}`], async () => {
    const vehicle = await col('vehicle').findOne({ _id: initial.vehicle_id });
    const lease = await col('lease').findOne({ _id: leaseId });
    if (!vehicle || !lease || lease.status !== 'Scheduled') {
      throw new AppError('Only a scheduled lease can be started.');
    }
    await checkCustomer(lease.customer_id, lease.end_date);
    const now = v.nowString();
    const scheduledStart = `${lease.start_date} 00:00:00`;
    const from = now < scheduledStart ? now : scheduledStart;
    if (!await isVehicleAvailable(lease.vehicle_id, from,
      `${v.addDays(lease.end_date, 1)} 00:00:00`, { ignoreLease: leaseId })) {
      throw new AppError('The vehicle has another commitment during this lease.', 409);
    }
    await col('lease').updateOne({ _id: leaseId }, { $set: { status: 'Active', updated_at: now } });
    await col('vehicle').updateOne({ _id: lease.vehicle_id }, { $set: { status: 'Leased', updated_at: now } });
  });
}));

router.post('/leases/:id/finish', handle(async (req) => {
  const leaseId = v.id(req.params.id);
  const finalStatus = req.body.status;
  v.check(['Completed', 'Terminated'].includes(finalStatus), 'Choose Completed or Terminated as the final status.');
  const initial = await col('lease').findOne({ _id: leaseId });
  if (!initial || !['Active', 'Scheduled'].includes(initial.status)) {
    throw new AppError('Only an active or scheduled lease can be ended.');
  }
  await withLock([`vehicle:${initial.vehicle_id}`], async () => {
    const lease = await col('lease').findOne({ _id: leaseId });
    if (!lease || !['Active', 'Scheduled'].includes(lease.status)) {
      throw new AppError('Only an active or scheduled lease can be ended.');
    }
    const now = v.nowString();
    await col('lease').updateOne({ _id: leaseId }, { $set: { status: finalStatus, updated_at: now } });
    // Free the vehicle only if the lease had started.
    if (lease.status === 'Active') {
      await col('vehicle').updateOne({ _id: initial.vehicle_id }, { $set: { status: 'Available', updated_at: now } });
    }
  });
}));

// ---------- Payments ----------

const PAYMENT_SORT = {
  id: '_id', date: 'payment_date', amount: 'amount', method: 'method',
  status: 'status', customer: 'customer_name',
};

router.get('/payments', handle(async (req) => {
  const filter = new Filter().search(req.query.q,
    ['c.first_name', 'c.last_name', 'method', '#_id', '#rental_id', '#lease_id']);
  if (req.query.method) filter.add({ method: String(req.query.method) });
  return listPage('payment', {
    stages: [
      ...lookupOne('rental', 'rental_id', 'r'),
      ...lookupOne('lease', 'lease_id', 'l'),
      { $set: { contract_customer: { $ifNull: ['$r.customer_id', '$l.customer_id'] } } },
      ...lookupOne('customer', 'contract_customer', 'c'),
      { $set: { customer_name: fullName('c') } },
    ],
    filter,
    statusField: 'status',
    status: req.query.status,
    sort: sorting(req, PAYMENT_SORT, 'date', 'desc'),
    page: paging(req),
    output: [
      { $set: { payment_id: '$_id' } },
      { $project: { _id: 0, r: 0, l: 0, c: 0, contract_customer: 0 } },
    ],
  });
}));

// Active and completed contracts with what is still owed.
router.get('/payments/billable', handle(async () => {
  const billable = (view, idField, totalField) => aggregate(view, [
    ...lookupOne('customer', 'customer_id', 'c'),
    ...lookupOne('vehicle', 'vehicle_id', 'v'),
    { $set: { owes: { $gt: ['$balance_due', 0] } } },
    { $sort: { owes: -1, _id: -1 } },
    {
      $project: {
        _id: 0, id: `$${idField}`, status: 1, total: `$${totalField}`, paid_amount: 1, balance_due: 1,
        customer_name: fullName('c'), registration_no: '$v.registration_no',
      },
    },
  ]);
  const [rentals, leases] = await Promise.all([
    billable('vw_rental_balance', 'rental_id', 'charge_total'),
    billable('vw_lease_balance', 'lease_id', 'lease_total'),
  ]);
  return { rentals, leases };
}));

router.post('/payments', handle(async (req) => {
  const rentalId = v.id(req.body.rental_id);
  const leaseId = v.id(req.body.lease_id);
  const amount = v.number(req.body.amount);
  const method = req.body.method;
  const status = req.body.status || 'Success';
  const paymentDate = v.dateTime(req.body.payment_date) || v.nowString();
  v.check(amount > 0, 'Payment amount must be greater than zero.');
  v.check(Boolean(rentalId) !== Boolean(leaseId), 'Choose exactly one rental or lease reference.');
  v.check(['Cash', 'Card', 'Bank Transfer', 'UPI'].includes(method)
    && ['Success', 'Pending', 'Failed', 'Refunded'].includes(status),
  'Choose a valid payment method and status.');

  // Lock the contract so two payments cannot both pass the balance check.
  const id = await withLock([rentalId ? `rental:${rentalId}` : `lease:${leaseId}`], async () => {
    const parent = rentalId
      ? await col('rental').findOne({ _id: rentalId })
      : await col('lease').findOne({ _id: leaseId });
    if (!parent) throw new AppError(`Select an existing ${rentalId ? 'rental' : 'lease'}.`);
    // Only Active and Completed contracts appear in the balance views; others owe nothing.
    const balance = rentalId
      ? await col('vw_rental_balance').findOne({ _id: rentalId })
      : await col('vw_lease_balance').findOne({ _id: leaseId });
    const due = balance ? Number(balance.balance_due) : 0;
    if (amount > due + 0.0001) {
      throw new AppError(`The payment exceeds the outstanding balance (₹${due.toFixed(2)}).`);
    }
    const paymentId = await nextId('payment');
    const now = v.nowString();
    await col('payment').insertOne({
      _id: paymentId,
      rental_id: rentalId,
      lease_id: leaseId,
      amount,
      method,
      payment_date: paymentDate,
      status,
      created_at: now,
      updated_at: now,
    });
    return paymentId;
  });
  return { id };
}));

router.post('/payments/:id/refund', handle(async (req) => {
  const id = v.id(req.params.id);
  const payment = await col('payment').findOne({ _id: id });
  if (!payment || payment.status === 'Refunded') throw new AppError('Choose an existing, unrefunded payment.');
  if (payment.status !== 'Success') throw new AppError('Only successful payments can be refunded.');
  await col('payment').updateOne({ _id: id }, { $set: { status: 'Refunded', updated_at: v.nowString() } });
}));

// ---------- Maintenance ----------

const MAINTENANCE_SORT = {
  id: '_id', vehicle: 'v.registration_no', serviceDate: 'service_date',
  type: 'service_type', cost: 'cost', next: 'next_service_date', status: 'status',
};

router.get('/maintenance', handle(async (req) => listPage('maintenance', {
  stages: lookupOne('vehicle', 'vehicle_id', 'v'),
  filter: new Filter().search(req.query.q, ['v.registration_no', 'v.model', 'service_type', 'description']),
  statusField: 'status',
  status: req.query.status,
  sort: sorting(req, MAINTENANCE_SORT, 'serviceDate', 'desc'),
  page: paging(req),
  output: [
    { $set: { maintenance_id: '$_id', registration_no: '$v.registration_no', model: '$v.model' } },
    { $project: { _id: 0, v: 0 } },
  ],
})));

router.post('/maintenance', handle(async (req) => {
  const vehicleId = v.id(req.body.vehicle_id);
  const serviceDate = v.date(req.body.service_date);
  const serviceType = v.text(req.body.service_type);
  const description = v.text(req.body.description) || null;
  const cost = v.number(req.body.cost);
  const nextServiceDate = v.date(req.body.next_service_date);
  v.check(vehicleId && serviceDate && serviceType, 'Choose a vehicle and enter the service date and type.');
  v.check(cost !== null && cost >= 0, 'Cost must be zero or greater.');
  v.check(!nextServiceDate || nextServiceDate > serviceDate, 'Next service date must be after the service date.');

  // Confirmed bookings from the service date on, so staff can rebook them.
  const warnings = await col('rental').find(
    { vehicle_id: vehicleId, status: 'Confirmed', scheduled_start_datetime: { $gte: `${serviceDate} 00:00:00` } },
    { projection: { _id: 0, rental_id: '$_id', scheduled_start_datetime: 1 }, sort: { scheduled_start_datetime: 1 } },
  ).toArray();

  const id = await withLock([`vehicle:${vehicleId}`], async () => {
    const vehicle = await col('vehicle').findOne({ _id: vehicleId });
    if (!vehicle || ['Rented', 'Leased', 'Inactive'].includes(vehicle.status)) {
      throw new AppError('Maintenance cannot be opened on a rented, leased or inactive vehicle.');
    }
    const maintenanceId = await nextId('maintenance');
    const now = v.nowString();
    await col('maintenance').insertOne({
      _id: maintenanceId,
      vehicle_id: vehicleId,
      service_date: serviceDate,
      service_type: serviceType,
      description,
      cost,
      next_service_date: nextServiceDate,
      completed_date: null,
      status: 'In Progress',
      created_at: now,
      updated_at: now,
    });
    await col('vehicle').updateOne({ _id: vehicleId }, { $set: { status: 'Maintenance', updated_at: now } });
    return maintenanceId;
  });
  return { id, warnings };
}));

router.post('/maintenance/:id/complete', handle(async (req) => {
  const maintenanceId = v.id(req.params.id);
  const completedDate = v.date(req.body.completed_date) || v.todayString();
  const initial = await col('maintenance').findOne({ _id: maintenanceId });
  if (!initial || initial.status !== 'In Progress') {
    throw new AppError('Only in-progress maintenance can be completed.');
  }
  await withLock([`vehicle:${initial.vehicle_id}`], async () => {
    const now = v.nowString();
    const result = await col('maintenance').updateOne(
      { _id: maintenanceId, status: 'In Progress' },
      { $set: { completed_date: completedDate, status: 'Complete', updated_at: now } },
    );
    if (!result.modifiedCount) throw new AppError('The maintenance record is no longer active.');
    await col('vehicle').updateOne({ _id: initial.vehicle_id }, { $set: { status: 'Available', updated_at: now } });
  });
}));

module.exports = router;
