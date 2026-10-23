// What the MySQL stored procedure sp_complete_rental and the rental triggers did, as application
// code. MongoDB has no triggers on a standalone server, so every route that changes a rental's
// status calls afterRentalChange() straight after the write.
const { col, AppError } = require('./db');
const { rentalCharge } = require('./billing');
const { nowString } = require('./validate');

/** trg_rental_after_insert and trg_rental_after_update: keep the vehicle status in step. */
async function afterRentalChange(oldStatus, newStatus, vehicleId) {
  let status = null;
  if (newStatus === 'Active' && (oldStatus === null || oldStatus === 'Confirmed')) status = 'Rented';
  if (oldStatus === 'Active' && newStatus === 'Completed') status = 'Available';
  if (status) {
    await col('vehicle').updateOne({ _id: vehicleId }, { $set: { status, updated_at: nowString() } });
  }
}

/** sp_complete_rental: checks the return, sets the final charge and completes the rental. */
async function completeRental(rentalId, returnAt, odometerIn, conditionIn) {
  const rental = await col('rental').findOne({ _id: rentalId });
  if (!rental) throw new AppError('Rental was not found');
  if (rental.status !== 'Active') throw new AppError('Only an Active rental can be completed');
  if (!returnAt || returnAt <= rental.actual_pickup_datetime) {
    throw new AppError('Return time must be later than pickup');
  }
  if (odometerIn === null || odometerIn < rental.odometer_out) {
    throw new AppError('Return odometer cannot be below the pickup odometer');
  }
  const charges = rentalCharge(rental.actual_pickup_datetime, returnAt, rental.daily_rate_applied);
  // Matching on status as well means a second, simultaneous completion changes nothing.
  const result = await col('rental').updateOne({ _id: rentalId, status: 'Active' }, {
    $set: {
      actual_return_datetime: returnAt,
      odometer_in: odometerIn,
      condition_in: conditionIn,
      charges,
      status: 'Completed',
      updated_at: nowString(),
    },
  });
  if (!result.modifiedCount) throw new AppError('Only an Active rental can be completed');
  await afterRentalChange('Active', 'Completed', rental.vehicle_id);
  return charges;
}

module.exports = { afterRentalChange, completeRental };
