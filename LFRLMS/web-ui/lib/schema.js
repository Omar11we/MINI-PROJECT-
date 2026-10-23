// The MongoDB schema: collections with $jsonSchema validators (the SQL NOT NULL, ENUM and CHECK
// constraints), indexes (PRIMARY KEY, UNIQUE and the foreign-key indexes) and views.
// ensureSchema() is safe to run any number of times; the server runs it at start-up.
const { db } = require('./db');
const { RENTAL_BALANCE_VIEW, LEASE_BALANCE_VIEW } = require('./billing');
const { nowExpr, lookupOne, fullName } = require('./pipeline');

// Case- and accent-insensitive comparisons, sorting and unique indexes, like MySQL's
// utf8mb4_0900_ai_ci. Every collection and view shares it, which $lookup between them requires.
const COLLATION = { locale: 'en', strength: 1 };

const DATE = '^\\d{4}-\\d{2}-\\d{2}$';
const DATETIME = '^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$';
const NUMBER = ['int', 'long', 'double', 'decimal'];

const t = {
  id: { bsonType: ['int', 'long'], minimum: 1 },
  optionalId: { bsonType: ['int', 'long', 'null'], minimum: 1 },
  text: (maxLength) => ({ bsonType: 'string', minLength: 1, maxLength }),
  optionalText: (maxLength) => ({ bsonType: ['string', 'null'], maxLength }),
  pattern: (pattern, nullable = false) => ({ bsonType: nullable ? ['string', 'null'] : 'string', pattern }),
  money: { bsonType: NUMBER, minimum: 0 },
  optionalMoney: { bsonType: [...NUMBER, 'null'], minimum: 0 },
  int: { bsonType: ['int', 'long'] },
  optionalInt: { bsonType: ['int', 'long', 'null'], minimum: 0 },
  enum: (values) => ({ enum: values }),
  date: { bsonType: 'string', pattern: DATE },
  optionalDate: { bsonType: ['string', 'null'], pattern: DATE },
  dateTime: { bsonType: 'string', pattern: DATETIME },
  optionalDateTime: { bsonType: ['string', 'null'], pattern: DATETIME },
};

const timestamps = { created_at: t.dateTime, updated_at: t.dateTime };

/** Builds a validator from required fields, field rules and optional cross-field $expr rules. */
function schema(required, properties, expr) {
  const validator = { $jsonSchema: { bsonType: 'object', required: ['_id', ...required], properties: { _id: t.id, ...properties } } };
  if (expr) validator.$expr = { $and: expr };
  return validator;
}

const isNull = (field) => ({ $eq: [{ $ifNull: [`$${field}`, null] }, null] });
const notNull = (field) => ({ $not: [isNull(field)] });

const COLLECTIONS = {
  brand: {
    validator: schema(['brand_name'], { brand_name: t.text(100) }),
    indexes: [{ key: { brand_name: 1 }, name: 'uq_brand_name', unique: true }],
  },
  vehicle_category: {
    validator: schema(['category_name'], { category_name: t.text(100), description: t.optionalText(255) }),
    indexes: [{ key: { category_name: 1 }, name: 'uq_vehicle_category_name', unique: true }],
  },
  branch: {
    validator: schema(['branch_name'], {
      branch_name: t.text(100),
      street: t.optionalText(180),
      city: t.optionalText(80),
      pincode: t.pattern('^[0-9]{6}$', true),
      phone: t.pattern('^[0-9]{10}$', true),
    }),
    indexes: [{ key: { branch_name: 1 }, name: 'uq_branch_name', unique: true }],
  },
  // The individual / corporate specialisation is embedded: each customer holds exactly one of them.
  customer: {
    validator: {
      ...schema(['customer_type', 'first_name', 'last_name', 'created_at', 'updated_at'], {
        customer_type: t.enum(['Individual', 'Corporate']),
        first_name: t.text(80),
        last_name: t.text(80),
        email: t.optionalText(150),
        phone: t.pattern('^[0-9]{10}$', true),
        street: t.optionalText(180),
        city: t.optionalText(80),
        pincode: t.pattern('^[0-9]{6}$', true),
        individual: {
          bsonType: 'object',
          required: ['date_of_birth', 'license_no', 'license_expiry'],
          properties: { date_of_birth: t.date, license_no: t.text(30), license_expiry: t.date },
        },
        corporate: {
          bsonType: 'object',
          required: ['company_name', 'gst_no'],
          properties: { company_name: t.text(150), gst_no: { bsonType: 'string', minLength: 15, maxLength: 15 } },
        },
        ...timestamps,
      }),
      $or: [
        { customer_type: 'Individual', individual: { $exists: true }, corporate: { $exists: false } },
        { customer_type: 'Corporate', corporate: { $exists: true }, individual: { $exists: false } },
      ],
    },
    indexes: [
      { key: { email: 1 }, name: 'uq_customer_email', unique: true, partialFilterExpression: { email: { $type: 'string' } } },
      { key: { 'individual.license_no': 1 }, name: 'uq_individual_license', unique: true, partialFilterExpression: { 'individual.license_no': { $exists: true } } },
      { key: { 'corporate.gst_no': 1 }, name: 'uq_corporate_customer_gst', unique: true, partialFilterExpression: { 'corporate.gst_no': { $exists: true } } },
      { key: { last_name: 1, first_name: 1 }, name: 'ix_customer_name' },
    ],
  },
  driver: {
    validator: schema(['first_name', 'last_name', 'phone', 'license_no', 'status'], {
      first_name: t.text(80),
      last_name: t.text(80),
      phone: t.pattern('^[0-9]{10}$'),
      license_no: t.text(30),
      status: t.enum(['Active', 'Inactive']),
    }),
    indexes: [{ key: { license_no: 1 }, name: 'uq_driver_license', unique: true }],
  },
  vehicle: {
    validator: schema(
      ['registration_no', 'brand_id', 'category_id', 'branch_id', 'model', 'model_year', 'status', 'created_at', 'updated_at'],
      {
        registration_no: t.pattern('^[A-Z]{2}[0-9]{2}[A-Z]{1,3}[0-9]{4}$'),
        brand_id: t.id,
        category_id: t.id,
        branch_id: t.id,
        model: t.text(100),
        model_year: { bsonType: ['int', 'long'], minimum: 2000, maximum: 2100 },
        rental_rate: t.optionalMoney,
        lease_rate: t.optionalMoney,
        status: t.enum(['Available', 'Reserved', 'Rented', 'Leased', 'Maintenance', 'Inactive']),
        ...timestamps,
      },
      [{ $or: [notNull('rental_rate'), notNull('lease_rate')] }],
    ),
    indexes: [
      { key: { registration_no: 1 }, name: 'uq_vehicle_registration', unique: true },
      { key: { status: 1 }, name: 'ix_vehicle_status' },
      { key: { brand_id: 1 }, name: 'fk_vehicle_brand' },
      { key: { category_id: 1 }, name: 'fk_vehicle_category' },
      { key: { branch_id: 1 }, name: 'fk_vehicle_branch' },
    ],
  },
  rental: {
    validator: schema(
      ['customer_id', 'vehicle_id', 'scheduled_start_datetime', 'expected_return_datetime', 'daily_rate_applied', 'status', 'created_at', 'updated_at'],
      {
        customer_id: t.id,
        vehicle_id: t.id,
        driver_id: t.optionalId,
        scheduled_start_datetime: t.dateTime,
        expected_return_datetime: t.dateTime,
        actual_pickup_datetime: t.optionalDateTime,
        actual_return_datetime: t.optionalDateTime,
        odometer_out: t.optionalInt,
        odometer_in: t.optionalInt,
        condition_out: t.optionalText(255),
        condition_in: t.optionalText(255),
        daily_rate_applied: t.money,
        charges: t.optionalMoney,
        status: t.enum(['Confirmed', 'Active', 'Completed', 'Cancelled']),
        ...timestamps,
      },
      [
        { $gt: ['$expected_return_datetime', '$scheduled_start_datetime'] },
        { $or: [{ $not: [{ $in: ['$status', ['Active', 'Completed']] }] }, { $and: [notNull('actual_pickup_datetime'), notNull('odometer_out')] }] },
        { $or: [{ $ne: ['$status', 'Completed'] }, { $and: [notNull('actual_return_datetime'), notNull('odometer_in'), notNull('charges')] }] },
        { $or: [isNull('odometer_in'), { $gte: ['$odometer_in', '$odometer_out'] }] },
        { $or: [isNull('actual_return_datetime'), { $gt: ['$actual_return_datetime', '$actual_pickup_datetime'] }] },
      ],
    ),
    indexes: [
      { key: { vehicle_id: 1, scheduled_start_datetime: 1, expected_return_datetime: 1 }, name: 'ix_rental_vehicle_dates' },
      { key: { customer_id: 1 }, name: 'fk_rental_customer' },
      { key: { driver_id: 1 }, name: 'fk_rental_driver' },
    ],
  },
  lease: {
    validator: schema(
      ['customer_id', 'vehicle_id', 'start_date', 'end_date', 'monthly_charge', 'deposit', 'lease_total', 'status', 'created_at', 'updated_at'],
      {
        customer_id: t.id,
        vehicle_id: t.id,
        start_date: t.date,
        end_date: t.date,
        monthly_charge: t.money,
        deposit: t.money,
        lease_total: t.money,
        status: t.enum(['Scheduled', 'Active', 'Completed', 'Terminated']),
        ...timestamps,
      },
      [{ $gt: ['$end_date', '$start_date'] }],
    ),
    indexes: [
      { key: { customer_id: 1 }, name: 'fk_lease_customer' },
      { key: { vehicle_id: 1 }, name: 'fk_lease_vehicle' },
    ],
  },
  payment: {
    validator: schema(['amount', 'method', 'payment_date', 'status', 'created_at', 'updated_at'], {
      rental_id: t.optionalId,
      lease_id: t.optionalId,
      amount: { bsonType: NUMBER, minimum: 0, exclusiveMinimum: true },
      method: t.enum(['Cash', 'Card', 'Bank Transfer', 'UPI']),
      payment_date: t.dateTime,
      status: t.enum(['Success', 'Pending', 'Failed', 'Refunded']),
      ...timestamps,
    }, [
      // Exactly one parent: a rental or a lease.
      { $ne: [isNull('rental_id'), isNull('lease_id')] },
    ]),
    indexes: [
      { key: { payment_date: 1 }, name: 'ix_payment_date' },
      { key: { rental_id: 1 }, name: 'fk_payment_rental' },
      { key: { lease_id: 1 }, name: 'fk_payment_lease' },
    ],
  },
  maintenance: {
    validator: schema(['vehicle_id', 'service_date', 'service_type', 'cost', 'status', 'created_at', 'updated_at'], {
      vehicle_id: t.id,
      service_date: t.date,
      service_type: t.text(100),
      description: t.optionalText(500),
      cost: t.money,
      next_service_date: t.optionalDate,
      completed_date: t.optionalDate,
      status: t.enum(['In Progress', 'Complete']),
      ...timestamps,
    }),
    indexes: [{ key: { vehicle_id: 1 }, name: 'fk_maintenance_vehicle' }],
  },
};

const VIEWS = {
  vw_vehicle_overview: {
    viewOn: 'vehicle',
    pipeline: [
      ...lookupOne('brand', 'brand_id', 'b'),
      ...lookupOne('vehicle_category', 'category_id', 'vc'),
      ...lookupOne('branch', 'branch_id', 'br'),
      // An available vehicle with a confirmed pickup in the next 24 hours shows as Reserved.
      {
        $lookup: {
          from: 'rental',
          localField: '_id',
          foreignField: 'vehicle_id',
          pipeline: [
            {
              $match: {
                status: 'Confirmed',
                $expr: { $and: [{ $gt: ['$scheduled_start_datetime', nowExpr()] }, { $lte: ['$scheduled_start_datetime', nowExpr(24)] }] },
              },
            },
            { $limit: 1 },
          ],
          as: 'pickup_soon',
        },
      },
      {
        $project: {
          vehicle_id: '$_id',
          registration_no: 1,
          brand_name: '$b.brand_name',
          category_name: '$vc.category_name',
          branch_name: '$br.branch_name',
          branch_city: '$br.city',
          model: 1,
          model_year: 1,
          rental_rate: 1,
          lease_rate: 1,
          stored_status: '$status',
          display_status: {
            $cond: [{ $and: [{ $eq: ['$status', 'Available'] }, { $gt: [{ $size: '$pickup_soon' }, 0] }] }, 'Reserved', '$status'],
          },
          brand_id: 1,
          category_id: 1,
          branch_id: 1,
        },
      },
    ],
  },
  vw_active_leases: {
    viewOn: 'lease',
    pipeline: [
      { $match: { status: 'Active' } },
      ...lookupOne('customer', 'customer_id', 'c'),
      ...lookupOne('vehicle', 'vehicle_id', 'v'),
      {
        $project: {
          lease_id: '$_id', customer_id: 1, customer_name: fullName('c'), vehicle_id: 1,
          registration_no: '$v.registration_no', model: '$v.model', start_date: 1, end_date: 1,
          monthly_charge: 1, deposit: 1, status: 1,
        },
      },
    ],
  },
  vw_rental_balance: { viewOn: 'rental', pipeline: RENTAL_BALANCE_VIEW },
  vw_lease_balance: { viewOn: 'lease', pipeline: LEASE_BALANCE_VIEW },
};

async function ensureSchema() {
  const existing = new Map((await db.listCollections({}, { nameOnly: false }).toArray())
    .map((c) => [c.name, c]));

  for (const [name, { validator, indexes }] of Object.entries(COLLECTIONS)) {
    const current = existing.get(name);
    if (current?.type === 'collection') {
      // A collection's collation is fixed when it is created.
      if (current.options?.collation?.locale !== COLLATION.locale) {
        throw new Error(`The "${name}" collection was created without the shared collation. Run "npm run migrate -- --force" to rebuild the database.`);
      }
      await db.command({ collMod: name, validator, validationLevel: 'strict', validationAction: 'error' });
    } else {
      await db.createCollection(name, {
        validator, validationLevel: 'strict', validationAction: 'error', collation: COLLATION,
      });
    }
    await db.collection(name).createIndexes(indexes);
  }

  // Views are recreated every time so they always match the code.
  for (const [name, { viewOn, pipeline }] of Object.entries(VIEWS)) {
    if (existing.has(name)) await db.collection(name).drop();
    await db.createCollection(name, { viewOn, pipeline, collation: COLLATION });
  }
}

module.exports = { ensureSchema, COLLECTIONS, VIEWS };
