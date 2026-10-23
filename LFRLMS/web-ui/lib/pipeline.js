// Reusable aggregation pipeline pieces: joins, names, and "now" worked out by MongoDB.
//
// Dates are stored as 'YYYY-MM-DD' and date-times as 'YYYY-MM-DD HH:MM:SS' strings, in local time,
// exactly as the MySQL app shows them. In that fixed format, comparing strings compares times.

const DATETIME_FORMAT = '%Y-%m-%d %H:%M:%S';
const TIMEZONE = Intl.DateTimeFormat().resolvedOptions().timeZone;

/** The current local time as a date-time string, worked out by the server from $$NOW. */
function nowExpr(hoursAhead = 0) {
  const date = hoursAhead ? { $dateAdd: { startDate: '$$NOW', unit: 'hour', amount: hoursAhead } } : '$$NOW';
  return { $dateToString: { date, format: DATETIME_FORMAT, timezone: TIMEZONE } };
}

/** Parses a stored date-time string into a BSON date, for arithmetic. */
function toDate(expr) {
  return { $dateFromString: { dateString: expr, format: DATETIME_FORMAT, timezone: 'UTC' } };
}

/** LEFT JOIN on an id: adds the matching document under `as`, or nothing when there is none. */
function lookupOne(from, localField, as) {
  return [
    { $lookup: { from, localField, foreignField: '_id', as } },
    { $unwind: { path: `$${as}`, preserveNullAndEmptyArrays: true } },
  ];
}

/** CONCAT(first_name, ' ', last_name) for the document at `path` ('' for the top level). */
function fullName(path = '') {
  const prefix = path ? `$${path}.` : '$';
  return { $concat: [`${prefix}first_name`, ' ', `${prefix}last_name`] };
}

/** Shows the embedded individual / corporate details as flat fields, like the SQL LEFT JOINs did. */
const flattenCustomer = [
  {
    $set: {
      customer_id: '$_id',
      date_of_birth: { $ifNull: ['$individual.date_of_birth', null] },
      license_no: { $ifNull: ['$individual.license_no', null] },
      license_expiry: { $ifNull: ['$individual.license_expiry', null] },
      company_name: { $ifNull: ['$corporate.company_name', null] },
      gst_no: { $ifNull: ['$corporate.gst_no', null] },
    },
  },
  { $unset: ['individual', 'corporate'] },
];

/** Rentals, leases and payments of one customer, newest first ($unionWith is UNION ALL). */
function customerHistory(customerId) {
  return [
    { $match: { customer_id: customerId } },
    ...lookupOne('vehicle', 'vehicle_id', 'v'),
    {
      $project: {
        _id: 0, type: { $literal: 'Rental' }, ref: '$_id', date: '$scheduled_start_datetime',
        registration_no: '$v.registration_no', status: 1, amount: '$charges',
      },
    },
    {
      $unionWith: {
        coll: 'lease',
        pipeline: [
          { $match: { customer_id: customerId } },
          ...lookupOne('vehicle', 'vehicle_id', 'v'),
          {
            $project: {
              _id: 0, type: { $literal: 'Lease' }, ref: '$_id', date: { $concat: ['$start_date', ' 00:00:00'] },
              registration_no: '$v.registration_no', status: 1, amount: '$monthly_charge',
            },
          },
        ],
      },
    },
    {
      $unionWith: {
        coll: 'payment',
        pipeline: [
          ...lookupOne('rental', 'rental_id', 'r'),
          ...lookupOne('lease', 'lease_id', 'l'),
          { $match: { $expr: { $eq: [{ $ifNull: ['$r.customer_id', '$l.customer_id'] }, customerId] } } },
          { $set: { contract_vehicle: { $ifNull: ['$r.vehicle_id', '$l.vehicle_id'] } } },
          ...lookupOne('vehicle', 'contract_vehicle', 'v'),
          {
            $project: {
              _id: 0, type: { $literal: 'Payment' }, ref: '$_id', date: '$payment_date',
              registration_no: { $ifNull: ['$v.registration_no', ''] }, status: 1, amount: 1,
            },
          },
        ],
      },
    },
    { $sort: { date: -1, type: 1 } },
  ];
}

module.exports = {
  DATETIME_FORMAT, TIMEZONE, nowExpr, toDate, lookupOne, fullName, flattenCustomer, customerHistory,
};
