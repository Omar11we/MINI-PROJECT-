// The shared date-overlap rule, matching AvailabilityService.java and sp_available_vehicles.
const { col, aggregate } = require('./db');
const { lookupOne } = require('./pipeline');
const { nowString, addDays } = require('./validate');

/**
 * The first date whose midnight is at or after a date-time, so that a stored date d satisfies
 * `d < ceilDate(t)` exactly when CAST(d AS DATETIME) < t.
 */
function ceilDate(dateTime) {
  const day = dateTime.slice(0, 10);
  return dateTime.slice(11) > '00:00:00' ? addDays(day, 1) : day;
}

/** Filters for the commitments that block a vehicle (or chauffeur) during [from, to). */
function clashes(from, to) {
  return {
    // An Active rental blocks from pickup until its expected return, or indefinitely once overdue.
    rental: {
      $or: [
        { status: 'Confirmed', scheduled_start_datetime: { $lt: to }, expected_return_datetime: { $gt: from } },
        {
          status: 'Active',
          actual_pickup_datetime: { $lt: to },
          $or: [{ expected_return_datetime: { $gt: from } }, { expected_return_datetime: { $lte: nowString() } }],
        },
      ],
    },
    // A lease covers whole days, from its start date through its end date.
    lease: {
      status: { $in: ['Scheduled', 'Active'] },
      start_date: { $lt: ceilDate(to) },
      end_date: { $gte: from.slice(0, 10) },
    },
    maintenance: { status: 'In Progress', service_date: { $lt: ceilDate(to) } },
  };
}

/** Checks a vehicle is in service and free for [from, to), skipping the given contracts. */
async function isVehicleAvailable(vehicleId, from, to, { ignoreRental = 0, ignoreLease = 0 } = {}) {
  const vehicle = await col('vehicle').findOne({ _id: vehicleId }, { projection: { status: 1 } });
  if (!vehicle || ['Maintenance', 'Inactive'].includes(vehicle.status)) return false;
  const c = clashes(from, to);
  const projection = { projection: { _id: 1 } };
  const [rental, lease, maintenance] = await Promise.all([
    col('rental').findOne({ vehicle_id: vehicleId, _id: { $ne: ignoreRental }, ...c.rental }, projection),
    col('lease').findOne({ vehicle_id: vehicleId, _id: { $ne: ignoreLease }, ...c.lease }, projection),
    col('maintenance').findOne({ vehicle_id: vehicleId, ...c.maintenance }, projection),
  ]);
  return !rental && !lease && !maintenance;
}

/** Checks a chauffeur is active and has no overlapping rental. */
async function isDriverAvailable(driverId, from, to, ignoreRental = 0) {
  const driver = await col('driver').findOne({ _id: driverId }, { projection: { status: 1 } });
  if (!driver || driver.status !== 'Active') return false;
  const clash = await col('rental').findOne(
    { driver_id: driverId, _id: { $ne: ignoreRental }, ...clashes(from, to).rental },
    { projection: { _id: 1 } },
  );
  return !clash;
}

/** sp_available_vehicles: vehicles free for the whole of [from, to), optionally by branch and category. */
async function availableVehicles(from, to, branchId, categoryId) {
  const c = clashes(from, to);
  // The NOT EXISTS subqueries become lists of busy vehicle ids.
  const busy = (await Promise.all([
    col('rental').distinct('vehicle_id', c.rental),
    col('lease').distinct('vehicle_id', c.lease),
    col('maintenance').distinct('vehicle_id', c.maintenance),
  ])).flat();
  const match = { status: { $nin: ['Maintenance', 'Inactive'] }, _id: { $nin: busy } };
  if (branchId) match.branch_id = branchId;
  if (categoryId) match.category_id = categoryId;
  return aggregate('vehicle', [
    { $match: match },
    ...lookupOne('brand', 'brand_id', 'b'),
    ...lookupOne('vehicle_category', 'category_id', 'vc'),
    ...lookupOne('branch', 'branch_id', 'br'),
    {
      $project: {
        _id: 0, vehicle_id: '$_id', registration_no: 1, brand_name: '$b.brand_name',
        category_name: '$vc.category_name', branch_name: '$br.branch_name', branch_city: '$br.city',
        model: 1, model_year: 1, rental_rate: 1, lease_rate: 1, status: 1,
      },
    },
    { $sort: { branch_name: 1, category_name: 1, brand_name: 1, model: 1, vehicle_id: 1 } },
  ]);
}

module.exports = { isVehicleAvailable, isDriverAvailable, availableVehicles };
