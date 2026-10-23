// Charges and balances: what fn_rental_charge, fn_lease_total, fn_customer_outstanding and the
// vw_rental_balance / vw_lease_balance views did in MySQL. Each rule exists twice: as plain
// JavaScript for one contract, and as an aggregation expression for many documents at once.
const { nowExpr, toDate } = require('./pipeline');
const { addDays } = require('./validate');

const round2 = (n) => Math.round(n * 100) / 100;

/** Milliseconds since the epoch for a stored 'YYYY-MM-DD[ HH:MM:SS]' string, read as UTC. */
function epoch(value) {
  const [y, mo, d, h = 0, mi = 0, s = 0] = value.split(/[- :]/).map(Number);
  return Date.UTC(y, mo - 1, d, h, mi, s);
}

/** fn_rental_charge: the daily rate for every started 24 hours, at least one day. */
function rentalCharge(start, end, dailyRate) {
  const hours = Math.trunc((epoch(end) - epoch(start)) / 3600000);
  return round2(Math.max(1, Math.ceil(hours / 24)) * dailyRate);
}

/** TIMESTAMPDIFF(MONTH, a, b) for two dates: whole months from a to b. */
function wholeMonths(a, b) {
  const [y1, m1, d1] = a.split('-').map(Number);
  const [y2, m2, d2] = b.split('-').map(Number);
  let months = (y2 - y1) * 12 + (m2 - m1);
  if (months > 0 && d2 < d1) months -= 1;
  if (months < 0 && d2 > d1) months += 1;
  return months;
}

/** fn_lease_total: the monthly charge for every whole month from start through end, at least one. */
function leaseTotal(monthly, startDate, endDate) {
  return round2(monthly * Math.max(1, wholeMonths(startDate, addDays(endDate, 1))));
}

/** fn_rental_charge as an aggregation expression. */
function rentalChargeExpr(start, end, dailyRate) {
  const hours = { $trunc: [{ $divide: [{ $subtract: [toDate(end), toDate(start)] }, 3600000] }, 0] };
  return { $round: [{ $multiply: [{ $max: [1, { $ceil: { $divide: [hours, 24] } }] }, dailyRate] }, 2] };
}

/**
 * What a rental is worth so far: the final charge once completed, otherwise the charge from
 * pickup (or the scheduled start) to the later of the expected return and now.
 */
const RENTAL_CHARGE_TOTAL = {
  $cond: [
    { $eq: ['$status', 'Completed'] },
    '$charges',
    rentalChargeExpr(
      { $ifNull: ['$actual_pickup_datetime', '$scheduled_start_datetime'] },
      { $max: ['$expected_return_datetime', nowExpr()] },
      '$daily_rate_applied',
    ),
  ],
};

/** Adds paid_amount: the successful payments made against this rental or lease. */
function paidAmountStages(paymentField) {
  return [
    {
      $lookup: {
        from: 'payment',
        localField: '_id',
        foreignField: paymentField,
        pipeline: [{ $match: { status: 'Success' } }, { $group: { _id: null, total: { $sum: '$amount' } } }],
        as: 'payments',
      },
    },
    { $set: { paid_amount: { $round: [{ $ifNull: [{ $first: '$payments.total' }, 0] }, 2] } } },
  ];
}

// ---------- The balance views (created as MongoDB views by lib/schema.js) ----------

const RENTAL_BALANCE_VIEW = [
  { $match: { status: { $in: ['Active', 'Completed'] } } },
  { $set: { charge_total: RENTAL_CHARGE_TOTAL } },
  ...paidAmountStages('rental_id'),
  { $set: { balance_due: { $round: [{ $subtract: ['$charge_total', '$paid_amount'] }, 2] } } },
  {
    $project: {
      rental_id: '$_id', customer_id: 1, vehicle_id: 1, status: 1, scheduled_start_datetime: 1,
      expected_return_datetime: 1, actual_pickup_datetime: 1, actual_return_datetime: 1,
      daily_rate_applied: 1, charge_total: 1, paid_amount: 1, balance_due: 1,
    },
  },
];

// lease_total is worked out with fn_lease_total's rule when the lease is created; a lease's
// rate and dates never change afterwards.
const LEASE_BALANCE_VIEW = [
  { $match: { status: { $in: ['Active', 'Completed'] } } },
  ...paidAmountStages('lease_id'),
  { $set: { balance_due: { $round: [{ $subtract: ['$lease_total', '$paid_amount'] }, 2] } } },
  {
    $project: {
      lease_id: '$_id', customer_id: 1, vehicle_id: 1, status: 1, start_date: 1, end_date: 1,
      monthly_charge: 1, deposit: 1, lease_total: 1, paid_amount: 1, balance_due: 1,
    },
  },
];

/** fn_customer_outstanding: adds `outstanding` to customer documents. */
const OUTSTANDING_STAGES = [
  ...['vw_rental_balance', 'vw_lease_balance'].map((view, i) => ({
    $lookup: {
      from: view,
      localField: '_id',
      foreignField: 'customer_id',
      pipeline: [{ $group: { _id: null, total: { $sum: '$balance_due' } } }],
      as: `owed${i}`,
    },
  })),
  {
    $set: {
      outstanding: {
        $round: [{ $add: [{ $ifNull: [{ $first: '$owed0.total' }, 0] }, { $ifNull: [{ $first: '$owed1.total' }, 0] }] }, 2],
      },
    },
  },
  { $unset: ['owed0', 'owed1'] },
];

module.exports = {
  rentalCharge, leaseTotal, rentalChargeExpr, RENTAL_CHARGE_TOTAL,
  RENTAL_BALANCE_VIEW, LEASE_BALANCE_VIEW, OUTSTANDING_STAGES,
};
