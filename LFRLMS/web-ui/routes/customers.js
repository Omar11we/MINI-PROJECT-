// Customers: the individual or corporate details are embedded in the customer document,
// so a customer and its subtype are always written together in one atomic operation.
const express = require('express');
const {
  col, aggregate, aggregateOne, nextId, AppError,
} = require('../lib/db');
const {
  handle, paging, sorting, Filter, listPage,
} = require('../lib/http');
const { flattenCustomer, customerHistory } = require('../lib/pipeline');
const { OUTSTANDING_STAGES } = require('../lib/billing');
const v = require('../lib/validate');

const router = express.Router();

const CUSTOMER_SORT = {
  id: '_id', name: 'first_name', type: 'customer_type', email: 'email',
  city: 'city', licenseExpiry: 'individual.license_expiry',
};

const CUSTOMER_OUTPUT = [...OUTSTANDING_STAGES, ...flattenCustomer, { $project: { _id: 0 } }];

router.get('/customers', handle(async (req) => {
  const filter = new Filter().search(req.query.q,
    ['first_name', 'last_name', 'email', 'phone', 'city', 'corporate.company_name']);
  if (req.query.expiring === '1') {
    filter.add({ 'individual.license_expiry': { $gte: v.todayString(), $lte: v.todayString(30) } });
  }
  return listPage('customer', {
    filter,
    statusField: 'customer_type',
    status: ['Individual', 'Corporate'].includes(req.query.type) ? req.query.type : null,
    sort: sorting(req, CUSTOMER_SORT, 'id'),
    page: paging(req),
    output: CUSTOMER_OUTPUT,
  });
}));

router.get('/customers/:id', handle(async (req) => {
  const id = v.id(req.params.id);
  const customer = await aggregateOne('customer', [{ $match: { _id: id } }, ...CUSTOMER_OUTPUT]);
  if (!customer) throw new AppError('The customer record no longer exists.', 404);
  // Same UNION ALL as ReportDao.customerTransactionHistory, written with $unionWith.
  const history = await aggregate('rental', customerHistory(id));
  return { customer, history };
}));

function readCustomer(body) {
  const customer = {
    customer_type: body.customer_type === 'Corporate' ? 'Corporate' : 'Individual',
    first_name: v.text(body.first_name),
    last_name: v.text(body.last_name),
    email: v.text(body.email),
    phone: v.text(body.phone),
    street: v.text(body.street),
    city: v.text(body.city),
    pincode: v.text(body.pincode),
  };
  v.check(customer.first_name && customer.last_name, 'Enter the first and last name.');
  v.check(v.PATTERNS.email.test(customer.email), 'Enter a valid email address.');
  v.check(v.PATTERNS.phone.test(customer.phone), 'Phone must be 10 digits.');
  v.check(customer.street && customer.city, 'Enter the street and city.');
  v.check(v.PATTERNS.pincode.test(customer.pincode), 'Pincode must be 6 digits.');

  if (customer.customer_type === 'Individual') {
    customer.individual = {
      date_of_birth: v.date(body.date_of_birth),
      license_no: v.normalizeLicense(body.license_no),
      license_expiry: v.date(body.license_expiry),
    };
    v.check(customer.individual.date_of_birth, 'Enter the date of birth.');
    v.check(v.PATTERNS.license.test(customer.individual.license_no), 'Licence number must be 5–30 letters, digits, / or -.');
    v.check(customer.individual.license_expiry, 'Enter the licence expiry date.');
  } else {
    customer.corporate = {
      company_name: v.text(body.company_name),
      gst_no: v.text(body.gst_no).toUpperCase(),
    };
    v.check(customer.corporate.company_name, 'Enter the company name.');
    v.check(v.PATTERNS.gst.test(customer.corporate.gst_no), 'GST number must be a valid 15-character GSTIN.');
  }
  return customer;
}

router.post('/customers', handle(async (req) => {
  const customer = readCustomer(req.body);
  const id = await nextId('customer');
  const now = v.nowString();
  await col('customer').insertOne({ _id: id, ...customer, created_at: now, updated_at: now });
  return { id };
}));

router.put('/customers/:id', handle(async (req) => {
  const customer = readCustomer(req.body);
  // Drop the other subtype in case the type changed.
  const other = customer.customer_type === 'Individual' ? 'corporate' : 'individual';
  const result = await col('customer').updateOne(
    { _id: v.id(req.params.id) },
    { $set: { ...customer, updated_at: v.nowString() }, $unset: { [other]: '' } },
  );
  if (!result.matchedCount) throw new AppError('The customer record no longer exists.', 404);
}));

router.delete('/customers/:id', handle(async (req) => {
  const id = v.id(req.params.id);
  const history = await col('rental').countDocuments({ customer_id: id })
    + await col('lease').countDocuments({ customer_id: id });
  if (history > 0) {
    throw new AppError('This customer has transaction history and cannot be deleted.', 409);
  }
  const result = await col('customer').deleteOne({ _id: id });
  if (!result.deletedCount) throw new AppError('The customer record no longer exists.', 404);
}));

module.exports = router;
