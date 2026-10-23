# LFRLMS Web UI

A browser interface for LFRLMS, built with Node.js (Express), HTML, CSS and plain JavaScript, with **MongoDB** as its database.
The Java Swing app is unchanged and keeps using MySQL. The two apps have separate data: the web UI starts from a copy of the MySQL data and changes it independently from then on.

## Run it

Requires Node.js 18 or newer, MongoDB 6 or newer running locally, and (once, for the first copy of the data) the `lfrlms` MySQL database.

```bash
cd web-ui
npm install
npm run migrate      # first time only: copies the MySQL data into MongoDB
npm start
```

Then open http://localhost:3000. Opening `public/index.html` directly, or through a static server such as VS Code's Live Server, will not work: the pages need the API that `npm start` runs.

- `npm run dev` restarts the server automatically whenever a file changes.
- `npm run migrate` refuses to overwrite a MongoDB database that already has data. `npm run migrate -- --force` replaces it with a fresh copy of the MySQL data.
- MongoDB defaults to `mongodb://127.0.0.1:27017`, database `lfrlms`. Override with `MONGO_URL`, `MONGO_DB` or `PORT`.
- The migration reads the MySQL credentials from `../config/db.properties`, the file the Java app uses (or `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`).

## Pages

| Page | What you can do |
|---|---|
| Dashboard | Key figures (utilisation, active contracts, revenue, receivables); today's pickups and returns with Hand over and Check in buttons (expired bookings show as no-shows); fleet breakdown; things needing attention; 12-month revenue chart |
| Vehicles | Search and filter the fleet, add or edit vehicles, deactivate or delete them, and view upcoming bookings |
| Customers | Individual and corporate customers, outstanding balances and full transaction history |
| Chauffeurs | Add, edit, activate or deactivate drivers |
| Rentals | Create reservations or walk-ins (only free vehicles are offered, with a charge quote), start, return, cancel and take payments |
| Leases | Schedule (with a lease total quote), start, complete or terminate leases |
| Payments | Record payments against a rental or lease (overpayment is blocked), and refund them |
| Maintenance | Open and complete service records; the vehicle status follows |
| Reports | R-01 to R-09, with CSV export |
| Insights | A read-only Mongo shell with examples ($lookup, $group, anti-join, view, regex, $setWindowFields, $unionWith, embedded subtype, indexes), explain plans, the live log of every command the app sends, and a schema browser (collections with validators, views, indexes, and where each SQL routine now lives) |

The Mongo shell accepts one command in mongosh syntax, for example `db.vehicle.find({ status: "Available" }).sort({ model: 1 })`.
Only `find`, `findOne`, `aggregate`, `countDocuments`, `distinct` and `getIndexes` are allowed; `$out`, `$merge`, `$where`, `$function` and `$accumulator` are refused, and each command has a 5-second limit. Commands are parsed as data, never run as JavaScript.

## How the relational design maps to MongoDB

| MySQL | MongoDB (this app) |
|---|---|
| Tables | Collections with the same names (`vehicle`, `rental`, `payment`, …) |
| `AUTO_INCREMENT` primary keys | Integer `_id`s handed out by a `counters` collection |
| `NOT NULL`, `ENUM`, `CHECK` constraints | `$jsonSchema` validators plus `$expr` rules for cross-field checks (`lib/schema.js`) |
| `UNIQUE` constraints | Unique indexes (partial where the column could be NULL) |
| `utf8mb4_0900_ai_ci` collation | Every collection and view uses a case- and accent-insensitive collation |
| `customer` + `individual_customer` / `corporate_customer` | One `customer` document with an embedded `individual` or `corporate` object; the validator allows exactly one |
| Foreign keys | Integer references joined with `$lookup`; the API checks references exist and refuses to delete referenced records |
| Views (`vw_vehicle_overview`, `vw_active_leases`, `vw_rental_balance`, `vw_lease_balance`) | MongoDB views with the same names, defined by aggregation pipelines |
| `fn_rental_charge`, `fn_lease_total`, `fn_customer_outstanding` | `lib/billing.js`, as JavaScript and as aggregation expressions |
| `sp_available_vehicles`, `sp_complete_rental` | `lib/availability.js`, `lib/procedures.js` |
| Rental triggers | `afterRentalChange()` in `lib/procedures.js`, called after every rental status change |
| `SELECT … FOR UPDATE` in a transaction | Per-vehicle / per-contract locks in `lib/locks.js` (a standalone MongoDB server has no multi-document transactions, and this server is the only writer) |
| `EXPLAIN FORMAT=TREE` | `explain("executionStats")`, drawn as a tree |

Dates are stored as `'YYYY-MM-DD'` and date-times as `'YYYY-MM-DD HH:MM:SS'` strings in local time, the same values the MySQL app uses. In that fixed format, comparing the strings compares the times.

The API in `routes/` applies the same business rules as the Java service layer: the availability overlap rule from `AvailabilityService`, licence validity through the contract end date, a chauffeur for every corporate rental, and the validation patterns from `Validator.java` (`lib/validate.js`).

```
web-ui/
├── server.js            Express entry point; creates the collections, indexes and views at start-up
├── lib/                 MongoDB client, schema, billing rules, availability, locks, validation, helpers
├── routes/              REST API (fleet, customers, contracts, reports, insights)
├── scripts/             migrate-from-mysql.js
└── public/              index.html, styles.css, js/ (one module per area)
```
