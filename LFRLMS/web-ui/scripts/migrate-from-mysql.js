// Copies the lfrlms MySQL database into the lfrlms MongoDB database used by the web UI.
//   npm run migrate            refuses to overwrite a MongoDB database that already has data
//   npm run migrate -- --force replaces whatever is in MongoDB with the current MySQL data
// MySQL credentials come from ../config/db.properties, the file the Java app uses.
const fs = require('fs');
const path = require('path');
const mysql = require('mysql2/promise');
const {
  client, db, col, config,
} = require('../lib/db');
const { ensureSchema } = require('../lib/schema');
const { leaseTotal } = require('../lib/billing');

const PROPERTIES_FILE = path.join(__dirname, '..', '..', 'config', 'db.properties');

function readProperties(file) {
  const values = {};
  if (!fs.existsSync(file)) return values;
  for (const line of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const index = trimmed.indexOf('=');
    if (index > 0) values[trimmed.slice(0, index).trim()] = trimmed.slice(index + 1).trim();
  }
  return values;
}

function mysqlConfig() {
  const props = readProperties(PROPERTIES_FILE);
  // jdbc:mysql://host:port/database?options
  const match = /^jdbc:mysql:\/\/([^:/?]+)(?::(\d+))?\/([^?]+)/.exec(props['db.url'] || '');
  return {
    host: process.env.DB_HOST || (match ? match[1] : 'localhost'),
    port: Number(process.env.DB_PORT || (match && match[2]) || 3306),
    database: process.env.DB_NAME || (match ? match[3] : 'lfrlms'),
    user: process.env.DB_USER || props['db.username'] || 'root',
    password: process.env.DB_PASSWORD ?? props['db.password'] ?? '',
    // Keep DATE and DATETIME values as the same strings the web UI stores.
    dateStrings: true,
    decimalNumbers: true,
  };
}

/** Renames the primary key column to _id and keeps every other column as a field. */
const withId = (idColumn) => ({ [idColumn]: id, ...rest }) => ({ _id: id, ...rest });

async function main() {
  const force = process.argv.includes('--force');
  await client.connect();
  if (!force && await db.listCollections({ name: 'vehicle' }).hasNext() && await col('vehicle').estimatedDocumentCount()) {
    console.error(`MongoDB database "${config.database}" already has data. Run "npm run migrate -- --force" to replace it.`);
    process.exitCode = 1;
    return;
  }

  const source = await mysql.createConnection(mysqlConfig());
  const rows = async (sql) => (await source.query(sql))[0];
  try {
    const [brands, categories, branches, customers, individuals, corporates, drivers, vehicles, rentals, leases, payments, maintenance] = await Promise.all([
      rows('SELECT * FROM brand'),
      rows('SELECT * FROM vehicle_category'),
      rows('SELECT * FROM branch'),
      rows('SELECT * FROM customer'),
      rows('SELECT * FROM individual_customer'),
      rows('SELECT * FROM corporate_customer'),
      rows('SELECT * FROM driver'),
      rows('SELECT * FROM vehicle'),
      rows('SELECT * FROM rental'),
      rows('SELECT * FROM lease'),
      rows('SELECT * FROM payment'),
      rows('SELECT * FROM maintenance'),
    ]);

    // The subtype rows are embedded into their customer documents.
    const individualById = new Map(individuals.map(({ customer_id: id, ...rest }) => [id, rest]));
    const corporateById = new Map(corporates.map(({ customer_id: id, ...rest }) => [id, rest]));
    const customerDocs = customers.map((c) => {
      const doc = withId('customer_id')(c);
      if (individualById.has(c.customer_id)) doc.individual = individualById.get(c.customer_id);
      if (corporateById.has(c.customer_id)) doc.corporate = corporateById.get(c.customer_id);
      return doc;
    });

    const data = {
      brand: brands.map(withId('brand_id')),
      vehicle_category: categories.map(withId('category_id')),
      branch: branches.map(withId('branch_id')),
      customer: customerDocs,
      driver: drivers.map(withId('driver_id')),
      vehicle: vehicles.map(withId('vehicle_id')),
      rental: rentals.map(withId('rental_id')),
      lease: leases.map((l) => ({ ...withId('lease_id')(l), lease_total: leaseTotal(l.monthly_charge, l.start_date, l.end_date) })),
      payment: payments.map(withId('payment_id')),
      maintenance: maintenance.map(withId('maintenance_id')),
    };

    await db.dropDatabase();
    await ensureSchema();
    for (const [name, docs] of Object.entries(data)) {
      if (docs.length) await col(name).insertMany(docs);
      // Ids carry on from the highest one copied, like AUTO_INCREMENT.
      const highest = docs.reduce((max, doc) => Math.max(max, doc._id), 0);
      await col('counters').updateOne({ _id: name }, { $set: { seq: highest } }, { upsert: true });
      console.log(`${name.padEnd(17)} ${docs.length} document${docs.length === 1 ? '' : 's'}`);
    }
    console.log(`\nCopied MySQL "${source.config.database}" into MongoDB "${config.database}".`);
  } finally {
    await source.end();
  }
}

main()
  .catch((error) => {
    console.error(error.errInfo ? `${error.message}\n${JSON.stringify(error.errInfo, null, 2)}` : error.message);
    process.exitCode = 1;
  })
  .finally(() => client.close());
