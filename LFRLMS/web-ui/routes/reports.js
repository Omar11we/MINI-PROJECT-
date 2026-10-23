// Reports R-01 to R-09: the same reports as ReportDao.java, written as MongoDB queries.
const express = require('express');
const { col, aggregate, AppError } = require('../lib/db');
const { handle } = require('../lib/http');
const { availableVehicles } = require('../lib/availability');
const { OUTSTANDING_STAGES } = require('../lib/billing');
const { lookupOne, customerHistory } = require('../lib/pipeline');
const v = require('../lib/validate');

const router = express.Router();

/** Shapes documents into named columns, so empty results still show headers. */
function table(columns, docs) {
  return { columns, rows: docs.map((doc) => Object.fromEntries(columns.map((c) => [c, doc[c] ?? null]))) };
}

const REPORTS = {
  // R-01
  available: {
    title: 'Available vehicles',
    run: async (q) => {
      const from = v.dateTime(q.from);
      const to = v.dateTime(q.to);
      if (!from || !to || to <= from) throw new AppError('The end of the requested period must be later.');
      return table(
        ['vehicle_id', 'registration_no', 'brand_name', 'category_name', 'branch_name', 'branch_city', 'model', 'model_year', 'rental_rate', 'lease_rate', 'status'],
        await availableVehicles(from, to, v.id(q.branchId), v.id(q.categoryId)),
      );
    },
  },
  // R-02
  'active-rentals': {
    title: 'Active rentals',
    run: async () => table(
      ['rental_id', 'customer_id', 'first_name', 'last_name', 'vehicle_id', 'registration_no', 'model', 'scheduled_start_datetime', 'expected_return_datetime', 'actual_pickup_datetime', 'status'],
      await aggregate('rental', [
        { $match: { status: 'Active' } },
        { $sort: { scheduled_start_datetime: -1 } },
        ...lookupOne('customer', 'customer_id', 'c'),
        ...lookupOne('vehicle', 'vehicle_id', 'v'),
        {
          $set: {
            rental_id: '$_id', first_name: '$c.first_name', last_name: '$c.last_name',
            registration_no: '$v.registration_no', model: '$v.model',
          },
        },
      ]),
    ),
  },
  // R-03
  'active-leases': {
    title: 'Active leases',
    run: async () => table(
      ['lease_id', 'customer_id', 'customer_name', 'vehicle_id', 'registration_no', 'model', 'start_date', 'end_date', 'monthly_charge', 'deposit', 'status'],
      await col('vw_active_leases').find({}, { sort: { end_date: 1, lease_id: 1 } }).toArray(),
    ),
  },
  // R-04
  revenue: {
    title: 'Revenue by month',
    run: async (q) => {
      const from = v.date(q.from) || `${new Date().getFullYear()}-01-01`;
      const to = v.date(q.to) || v.todayString();
      return table(['revenue_month', 'revenue'], await aggregate('payment', [
        { $match: { status: 'Success', payment_date: { $gte: from, $lt: v.addDays(to, 1) } } },
        { $group: { _id: { $substrBytes: ['$payment_date', 0, 7] }, revenue: { $sum: '$amount' } } },
        { $sort: { _id: 1 } },
        { $project: { revenue_month: '$_id', revenue: 1 } },
      ]));
    },
  },
  // R-05
  outstanding: {
    title: 'Outstanding balances',
    run: async () => table(
      ['customer_id', 'customer_type', 'first_name', 'last_name', 'outstanding'],
      await aggregate('customer', [
        ...OUTSTANDING_STAGES,
        { $set: { customer_id: '$_id' } },
        { $sort: { outstanding: -1, last_name: 1, first_name: 1 } },
      ]),
    ),
  },
  // R-06
  'most-rented': {
    title: 'Most rented vehicles',
    run: async (q) => table(
      ['vehicle_id', 'registration_no', 'brand_name', 'model', 'rental_count'],
      await aggregate('vehicle', [
        {
          $lookup: {
            from: 'rental',
            localField: '_id',
            foreignField: 'vehicle_id',
            pipeline: [{ $match: { status: { $in: ['Active', 'Completed'] } } }, { $count: 'n' }],
            as: 'rentals',
          },
        },
        { $set: { vehicle_id: '$_id', rental_count: { $ifNull: [{ $first: '$rentals.n' }, 0] } } },
        { $sort: { rental_count: -1, _id: 1 } },
        { $limit: Math.min(100, v.id(q.limit) || 10) },
        ...lookupOne('brand', 'brand_id', 'b'),
        { $set: { brand_name: '$b.brand_name' } },
      ]),
    ),
  },
  // R-07
  'maintenance-due': {
    title: 'Maintenance due',
    run: async (q) => table(
      ['maintenance_id', 'vehicle_id', 'registration_no', 'model', 'service_type', 'next_service_date', 'status'],
      await aggregate('maintenance', [
        { $match: { next_service_date: { $ne: null, $lte: v.date(q.through) || v.todayString(30) } } },
        { $sort: { next_service_date: 1, _id: 1 } },
        ...lookupOne('vehicle', 'vehicle_id', 'v'),
        { $set: { maintenance_id: '$_id', registration_no: '$v.registration_no', model: '$v.model' } },
      ]),
    ),
  },
  // R-08
  'customer-history': {
    title: 'Customer transaction history',
    run: async (q) => {
      const id = v.id(q.customerId);
      if (!id) throw new AppError('Choose a customer.');
      const history = await aggregate('rental', customerHistory(id));
      return table(
        ['transaction_type', 'reference_id', 'transaction_date', 'registration_no', 'status', 'amount'],
        history.map((h) => ({
          transaction_type: h.type, reference_id: h.ref, transaction_date: h.date,
          registration_no: h.registration_no, status: h.status, amount: h.amount,
        })),
      );
    },
  },
  // R-09
  'rental-status': {
    title: 'Rental status summary',
    run: async () => table(['status', 'rental_count'], await aggregate('rental', [
      { $group: { _id: '$status', rental_count: { $sum: 1 } } },
      { $sort: { _id: 1 } },
      { $project: { status: '$_id', rental_count: 1 } },
    ])),
  },
};

router.get('/reports/:key', handle(async (req) => {
  const report = Object.prototype.hasOwnProperty.call(REPORTS, req.params.key) ? REPORTS[req.params.key] : null;
  if (!report) throw new AppError('Unknown report.', 404);
  return { title: report.title, ...await report.run(req.query) };
}));

module.exports = router;
