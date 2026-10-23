// Lookups, dashboard, vehicles and chauffeurs.
const express = require('express');
const {
  col, aggregate, aggregateOne, nextId, AppError,
} = require('../lib/db');
const {
  handle, paging, sorting, Filter, countBy, listPage,
} = require('../lib/http');
const { lookupOne, fullName } = require('../lib/pipeline');
const v = require('../lib/validate');

const router = express.Router();

/** Refuses to delete a document that other documents still point to, like a foreign key. */
async function ensureUnreferenced(checks) {
  for (const [collection, filter] of checks) {
    if (await col(collection).countDocuments(filter, { limit: 1 })) {
      throw new AppError('This record is used by other records and cannot be deleted.', 409);
    }
  }
}

/** Refuses an id that points at nothing, like a foreign key. */
async function ensureExists(checks) {
  for (const [collection, id] of checks) {
    if (!await col(collection).countDocuments({ _id: id }, { limit: 1 })) {
      throw new AppError('A selected related record does not exist.');
    }
  }
}

// ---------- Lookups used by drop-downs ----------

router.get('/lookups', handle(async () => {
  const [brands, categories, branches, drivers, customers, vehicles] = await Promise.all([
    col('brand').find({}, { projection: { _id: 0, id: '$_id', name: '$brand_name' }, sort: { brand_name: 1 } }).toArray(),
    col('vehicle_category').find({}, { projection: { _id: 0, id: '$_id', name: '$category_name' }, sort: { category_name: 1 } }).toArray(),
    col('branch').find({}, { projection: { _id: 0, id: '$_id', name: '$branch_name', city: 1 }, sort: { branch_name: 1 } }).toArray(),
    col('driver').find({}, {
      projection: { _id: 0, id: '$_id', name: fullName(), status: 1 }, sort: { first_name: 1, last_name: 1 },
    }).toArray(),
    col('customer').find({}, {
      projection: {
        _id: 0, id: '$_id', type: '$customer_type', name: fullName(),
        company_name: { $ifNull: ['$corporate.company_name', null] },
        license_expiry: { $ifNull: ['$individual.license_expiry', null] },
      },
      sort: { first_name: 1, last_name: 1 },
    }).toArray(),
    aggregate('vehicle', [
      ...lookupOne('brand', 'brand_id', 'b'),
      { $sort: { 'b.brand_name': 1, model: 1 } },
      {
        $project: {
          _id: 0, id: '$_id', registration_no: 1, name: { $concat: ['$b.brand_name', ' ', '$model'] },
          rental_rate: 1, lease_rate: 1, status: 1,
        },
      },
    ]),
  ]);
  return { brands, categories, branches, drivers, customers, vehicles };
}));

// ---------- Dashboard ----------

/** First day of the month `offset` months from now, as 'YYYY-MM-01'. */
function monthStart(offset = 0) {
  const d = new Date();
  const first = new Date(d.getFullYear(), d.getMonth() + offset, 1);
  return `${first.getFullYear()}-${String(first.getMonth() + 1).padStart(2, '0')}-01`;
}

async function successfulPayments(from, to) {
  const range = to ? { $gte: from, $lt: to } : { $gte: from };
  const row = await aggregateOne('payment', [
    { $match: { status: 'Success', payment_date: range } },
    { $group: { _id: null, total: { $sum: '$amount' } } },
  ]);
  return row ? row.total : 0;
}

async function positiveBalances(view) {
  const row = await aggregateOne(view, [
    { $match: { balance_due: { $gt: 0 } } },
    { $group: { _id: null, total: { $sum: '$balance_due' } } },
  ]);
  return row ? row.total : 0;
}

router.get('/dashboard', handle(async () => {
  const now = v.nowString();
  const today = v.todayString();
  const tomorrow = v.todayString(1);

  const [fleet, activeRentals, activeLeases, upcomingRentals, monthly, lastMonth, rentalOwed, leaseOwed] = await Promise.all([
    countBy('vehicle', [], 'status'),
    col('rental').countDocuments({ status: 'Active' }),
    col('lease').countDocuments({ status: 'Active' }),
    col('rental').countDocuments({ status: 'Confirmed', scheduled_start_datetime: { $gte: now, $lt: v.nowString(24 * 60) } }),
    successfulPayments(monthStart()),
    successfulPayments(monthStart(-1), monthStart()),
    positiveBalances('vw_rental_balance'),
    positiveBalances('vw_lease_balance'),
  ]);
  const summary = {
    total_vehicles: Object.values(fleet).reduce((sum, n) => sum + n, 0),
    available_vehicles: fleet.Available || 0,
    rented_vehicles: fleet.Rented || 0,
    leased_vehicles: fleet.Leased || 0,
    maintenance_vehicles: fleet.Maintenance || 0,
    inactive_vehicles: fleet.Inactive || 0,
    active_rentals: activeRentals,
    active_leases: activeLeases,
    upcoming_rentals: upcomingRentals,
    monthly_revenue: monthly,
    last_month_revenue: lastMonth,
    receivables: Math.round((rentalOwed + leaseOwed) * 100) / 100,
  };

  // The front desk: confirmed pickups up to the end of today (including late ones),
  // and active rentals due back by the end of today (including overdue ones).
  const desk = (match, sortField, flags) => aggregate('rental', [
    { $match: match },
    { $sort: { [sortField]: 1 } },
    ...lookupOne('customer', 'customer_id', 'c'),
    ...lookupOne('vehicle', 'vehicle_id', 'v'),
    ...lookupOne('brand', 'v.brand_id', 'b'),
    {
      $project: {
        _id: 0, rental_id: '$_id', status: 1, scheduled_start_datetime: 1, expected_return_datetime: 1,
        actual_pickup_datetime: 1, odometer_out: 1, customer_name: fullName('c'),
        registration_no: '$v.registration_no', model: '$v.model', brand_name: '$b.brand_name', ...flags,
      },
    },
  ]);

  // Same alert rules as AlertService.java (overdue and upcoming rentals are on the desk above).
  const [pickups, returns, leasesEnding, maintenanceDue, licences, revenue] = await Promise.all([
    desk({ status: 'Confirmed', scheduled_start_datetime: { $lt: tomorrow } }, 'scheduled_start_datetime', {
      late: { $lt: ['$scheduled_start_datetime', now] },
      expired: { $lte: ['$expected_return_datetime', now] },
    }),
    desk({ status: 'Active', expected_return_datetime: { $lt: tomorrow } }, 'expected_return_datetime', {
      late: { $lt: ['$expected_return_datetime', now] },
    }),
    aggregate('lease', [
      { $match: { status: 'Active', end_date: { $gte: today, $lte: v.todayString(30) } } },
      { $sort: { end_date: 1 } },
      ...lookupOne('vehicle', 'vehicle_id', 'v'),
      ...lookupOne('customer', 'customer_id', 'c'),
      {
        $project: {
          _id: 0, lease_id: '$_id', registration_no: '$v.registration_no', model: '$v.model', end_date: 1,
          customer_name: fullName('c'),
        },
      },
    ]),
    aggregate('maintenance', [
      { $match: { status: 'In Progress', next_service_date: { $ne: null, $lte: v.todayString(7) } } },
      { $sort: { next_service_date: 1 } },
      ...lookupOne('vehicle', 'vehicle_id', 'v'),
      {
        $project: {
          _id: 0, maintenance_id: '$_id', registration_no: '$v.registration_no', model: '$v.model',
          service_type: 1, next_service_date: 1,
        },
      },
    ]),
    col('customer').find(
      { 'individual.license_expiry': { $gte: today, $lte: v.todayString(30) } },
      {
        projection: { _id: 0, customer_id: '$_id', customer_name: fullName(), license_expiry: '$individual.license_expiry' },
        sort: { 'individual.license_expiry': 1 },
      },
    ).toArray(),
    aggregate('payment', [
      { $match: { status: 'Success', payment_date: { $gte: monthStart(-11) } } },
      { $group: { _id: { $substrBytes: ['$payment_date', 0, 7] }, revenue: { $sum: '$amount' } } },
      { $sort: { _id: 1 } },
      { $project: { _id: 0, month: '$_id', revenue: 1 } },
    ]),
  ]);

  return { summary, pickups, returns, leasesEnding, maintenanceDue, licences, revenue };
}));

// ---------- Vehicles ----------

const VEHICLE_SORT = {
  id: '_id', registration: 'registration_no', brand: 'brand_name', model: 'model', year: 'model_year',
  rentalRate: 'rental_rate', leaseRate: 'lease_rate', status: 'display_status', branch: 'branch_name',
};

router.get('/vehicles', handle(async (req) => {
  const filter = new Filter().search(req.query.q, ['registration_no', 'model', 'brand_name', 'category_name']);
  if (v.id(req.query.brandId)) filter.add({ brand_id: v.id(req.query.brandId) });
  if (v.id(req.query.categoryId)) filter.add({ category_id: v.id(req.query.categoryId) });
  if (v.id(req.query.branchId)) filter.add({ branch_id: v.id(req.query.branchId) });
  return listPage('vw_vehicle_overview', {
    filter,
    statusField: 'display_status',
    status: req.query.status,
    sort: sorting(req, VEHICLE_SORT, 'id'),
    page: paging(req),
    output: [{ $project: { _id: 0 } }],
  });
}));

router.get('/vehicles/:id', handle(async (req) => {
  const id = v.id(req.params.id);
  const vehicle = await col('vw_vehicle_overview').findOne({ _id: id }, { projection: { _id: 0 } });
  if (!vehicle) throw new AppError('The vehicle record no longer exists.', 404);
  const [upcoming, maintenance] = await Promise.all([
    aggregate('rental', [
      { $match: { vehicle_id: id, status: 'Confirmed', scheduled_start_datetime: { $gte: v.nowString() } } },
      { $sort: { scheduled_start_datetime: 1 } },
      { $limit: 5 },
      ...lookupOne('customer', 'customer_id', 'c'),
      {
        $project: {
          _id: 0, rental_id: '$_id', scheduled_start_datetime: 1, expected_return_datetime: 1, customer: fullName('c'),
        },
      },
    ]),
    col('maintenance').find({ vehicle_id: id }, {
      projection: { _id: 0, maintenance_id: '$_id', service_date: 1, service_type: 1, cost: 1, status: 1 },
      sort: { service_date: -1 },
      limit: 5,
    }).toArray(),
  ]);
  return { vehicle, upcoming, maintenance };
}));

function readVehicle(body) {
  const vehicle = {
    registration_no: v.text(body.registration_no).toUpperCase().replace(/\s+/g, ''),
    brand_id: v.id(body.brand_id),
    category_id: v.id(body.category_id),
    branch_id: v.id(body.branch_id),
    model: v.text(body.model),
    model_year: Number(body.model_year),
    rental_rate: v.number(body.rental_rate),
    lease_rate: v.number(body.lease_rate),
  };
  v.check(v.PATTERNS.registration.test(vehicle.registration_no),
    'Registration must look like MH01AB1234.');
  v.check(vehicle.brand_id && vehicle.category_id && vehicle.branch_id,
    'Choose a brand, category and branch.');
  v.check(vehicle.model, 'Enter the model name.');
  v.check(Number.isInteger(vehicle.model_year) && vehicle.model_year >= 2000
    && vehicle.model_year <= 2100, 'Model year must be between 2000 and 2100.');
  v.check(vehicle.rental_rate !== null || vehicle.lease_rate !== null,
    'Enter a rental rate, a lease rate, or both.');
  for (const rate of [vehicle.rental_rate, vehicle.lease_rate]) {
    v.check(rate === null || rate >= 0, 'Rates must be zero or greater.');
  }
  return vehicle;
}

const vehicleReferences = (vehicle) => [
  ['brand', vehicle.brand_id], ['vehicle_category', vehicle.category_id], ['branch', vehicle.branch_id],
];

router.post('/vehicles', handle(async (req) => {
  const vehicle = readVehicle(req.body);
  await ensureExists(vehicleReferences(vehicle));
  const id = await nextId('vehicle');
  const now = v.nowString();
  await col('vehicle').insertOne({
    _id: id, ...vehicle, status: 'Available', created_at: now, updated_at: now,
  });
  return { id };
}));

// Edits keep the current status; rentals, leases and maintenance own status changes.
router.put('/vehicles/:id', handle(async (req) => {
  const vehicle = readVehicle(req.body);
  await ensureExists(vehicleReferences(vehicle));
  const result = await col('vehicle').updateOne(
    { _id: v.id(req.params.id) }, { $set: { ...vehicle, updated_at: v.nowString() } },
  );
  if (!result.matchedCount) throw new AppError('The vehicle record no longer exists.', 404);
}));

router.post('/vehicles/:id/deactivate', handle(async (req) => {
  const id = v.id(req.params.id);
  const vehicle = await col('vehicle').findOne({ _id: id }, { projection: { status: 1 } });
  if (!vehicle) throw new AppError('The vehicle record no longer exists.', 404);
  // A vehicle that is out with a customer must come back first.
  if (['Rented', 'Leased'].includes(vehicle.status)) {
    throw new AppError(`This vehicle is currently ${vehicle.status.toLowerCase()}. End the contract before deactivating it.`);
  }
  await col('vehicle').updateOne({ _id: id }, { $set: { status: 'Inactive', updated_at: v.nowString() } });
}));

// Brings an inactive vehicle back into service.
router.post('/vehicles/:id/reactivate', handle(async (req) => {
  const result = await col('vehicle').updateOne(
    { _id: v.id(req.params.id), status: 'Inactive' },
    { $set: { status: 'Available', updated_at: v.nowString() } },
  );
  if (!result.matchedCount) throw new AppError('Only an inactive vehicle can be reactivated.');
}));

router.delete('/vehicles/:id', handle(async (req) => {
  const id = v.id(req.params.id);
  await ensureUnreferenced([['rental', { vehicle_id: id }], ['lease', { vehicle_id: id }], ['maintenance', { vehicle_id: id }]]);
  const result = await col('vehicle').deleteOne({ _id: id });
  if (!result.deletedCount) throw new AppError('The vehicle record no longer exists.', 404);
}));

// ---------- Chauffeurs ----------

const DRIVER_SORT = {
  id: '_id', name: 'first_name', phone: 'phone', license: 'license_no', status: 'status',
};

router.get('/drivers', handle(async (req) => listPage('driver', {
  filter: new Filter().search(req.query.q, ['first_name', 'last_name', 'phone', 'license_no']),
  statusField: 'status',
  status: req.query.status,
  sort: sorting(req, DRIVER_SORT, 'id'),
  page: paging(req),
  output: [
    {
      $lookup: {
        from: 'rental', localField: '_id', foreignField: 'driver_id', pipeline: [{ $match: { status: 'Active' } }, { $count: 'n' }], as: 'active',
      },
    },
    { $set: { driver_id: '$_id', active_rentals: { $ifNull: [{ $first: '$active.n' }, 0] } } },
    { $project: { _id: 0, active: 0 } },
  ],
})));

function readDriver(body) {
  const driver = {
    first_name: v.text(body.first_name),
    last_name: v.text(body.last_name),
    phone: v.text(body.phone),
    license_no: v.normalizeLicense(body.license_no),
    status: body.status === 'Inactive' ? 'Inactive' : 'Active',
  };
  v.check(driver.first_name && driver.last_name, 'Enter the chauffeur\'s first and last name.');
  v.check(v.PATTERNS.phone.test(driver.phone), 'Phone must be 10 digits.');
  v.check(v.PATTERNS.license.test(driver.license_no), 'Licence number must be 5–30 letters, digits, / or -.');
  return driver;
}

router.post('/drivers', handle(async (req) => {
  const driver = readDriver(req.body);
  const id = await nextId('driver');
  await col('driver').insertOne({ _id: id, ...driver });
  return { id };
}));

router.put('/drivers/:id', handle(async (req) => {
  const driver = readDriver(req.body);
  const result = await col('driver').updateOne({ _id: v.id(req.params.id) }, { $set: driver });
  if (!result.matchedCount) throw new AppError('The chauffeur record no longer exists.', 404);
}));

router.delete('/drivers/:id', handle(async (req) => {
  const id = v.id(req.params.id);
  await ensureUnreferenced([['rental', { driver_id: id }]]);
  const result = await col('driver').deleteOne({ _id: id });
  if (!result.deletedCount) throw new AppError('The chauffeur record no longer exists.', 404);
}));

module.exports = router;
