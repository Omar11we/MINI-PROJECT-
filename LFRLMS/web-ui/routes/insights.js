// DBMS Insights: a read-only MongoDB shell, the query log with explain plans, and the schema browser.
// Mirrors the Swing app's DbmsInsightsPanel / InsightsDao.
const express = require('express');
const { BSON: { EJSON } } = require('mongodb');
const {
  db, col, config, queryLog, AppError,
} = require('../lib/db');
const { handle } = require('../lib/http');
const { parseCommand } = require('../lib/shell');
const billing = require('../lib/billing');
const availability = require('../lib/availability');
const procedures = require('../lib/procedures');
const { VIEWS } = require('../lib/schema');

const router = express.Router();

const MAX_ROWS = 500;
const TIME_LIMIT_MS = 5000;
// Stages and operators that write data or run server-side JavaScript.
const FORBIDDEN = new Set(['$out', '$merge', '$where', '$function', '$accumulator']);
const EXPLAINABLE = new Set(['find', 'aggregate', 'count', 'distinct']);

function refuseWrites(value) {
  if (Array.isArray(value)) {
    value.forEach(refuseWrites);
  } else if (value && typeof value === 'object' && !(value instanceof RegExp)) {
    for (const [key, inner] of Object.entries(value)) {
      if (FORBIDDEN.has(key)) throw new AppError(`${key} is not allowed: the console is read-only.`);
      refuseWrites(inner);
    }
  }
}

const isObject = (value) => value && typeof value === 'object' && !Array.isArray(value) && !(value instanceof RegExp);

/** Checks a parsed command and turns it into the database command it stands for. */
async function toDatabaseCommand(parsed) {
  const { collection, method, args, chain } = parsed;
  const names = (await db.listCollections({}, { nameOnly: true }).toArray()).map((c) => c.name);
  if (!names.includes(collection)) throw new AppError(`There is no collection or view called "${collection}".`);
  refuseWrites(args);
  chain.forEach((c) => refuseWrites(c.args));

  const explain = chain.some((c) => c.name === 'explain');
  const modifiers = chain.filter((c) => c.name !== 'explain');
  const objectArg = (i, label) => {
    const value = args[i] ?? {};
    if (!isObject(value)) throw new AppError(`The ${label} must be an object, like { status: "Active" }.`);
    return value;
  };

  let command;
  switch (method) {
    case 'find':
    case 'findOne': {
      command = { find: collection, filter: objectArg(0, 'filter') };
      if (args[1] !== undefined) command.projection = objectArg(1, 'projection');
      if (method === 'findOne') command.limit = 1;
      for (const { name, args: [value] } of modifiers) {
        if (name === 'sort' && isObject(value)) command.sort = value;
        else if ((name === 'limit' || name === 'skip') && Number.isInteger(value) && value >= 0) command[name] = value;
        else throw new AppError(`.${name}() is not supported here; use .sort({...}), .skip(n) or .limit(n).`);
      }
      break;
    }
    case 'aggregate':
      if (!Array.isArray(args[0])) throw new AppError('aggregate() takes an array of stages, like [{ $match: {...} }].');
      command = { aggregate: collection, pipeline: args[0], cursor: {} };
      break;
    case 'countDocuments':
      command = { count: collection, query: objectArg(0, 'filter') };
      break;
    case 'distinct':
      if (typeof args[0] !== 'string') throw new AppError('distinct() takes a field name, like distinct("status").');
      command = { distinct: collection, key: args[0], query: objectArg(1, 'filter') };
      break;
    case 'getIndexes':
      command = { listIndexes: collection };
      break;
    default:
      throw new AppError(`${method}() is not allowed. The console is read-only: use find, findOne, aggregate, countDocuments, distinct or getIndexes.`);
  }
  if (method !== 'find' && method !== 'findOne' && modifiers.length) {
    throw new AppError(`.${modifiers[0].name}() can only follow find().`);
  }
  return { command, explain, kind: Object.keys(command)[0] };
}

// ---------- Explain plans ----------

const brief = (value) => {
  const text = JSON.stringify(value);
  return text.length > 140 ? `${text.slice(0, 140)}…` : text;
};

function planLines(node, depth, lines) {
  if (!node || typeof node !== 'object') return;
  const details = [];
  if (node.indexName) details.push(`index ${node.indexName} ${brief(node.keyPattern)}`);
  if (node.direction && node.indexName) details.push(node.direction);
  if (node.foreignCollection) details.push(`from ${node.foreignCollection.split('.').pop()} on ${node.localField} = ${node.foreignField}`);
  if (node.filter && Object.keys(node.filter).length) details.push(`filter ${brief(node.filter)}`);
  if (node.sortPattern) details.push(`by ${brief(node.sortPattern)}`);
  if (node.limitAmount) details.push(`${node.limitAmount}`);
  lines.push(`${'   '.repeat(depth)}${depth ? '└─ ' : ''}${node.stage}${details.length ? `  ${details.join(' · ')}` : ''}`);
  for (const child of [node.inputStage, ...(node.inputStages || [])]) planLines(child, depth + 1, lines);
}

/** Turns explain output into a readable tree, from the outermost stage down to the scan. */
function formatPlan(explain) {
  const cursor = explain.stages?.[0]?.$cursor;
  const planner = explain.queryPlanner || cursor?.queryPlanner;
  const stats = explain.executionStats || cursor?.executionStats;
  if (!planner) return JSON.stringify(explain, null, 2);

  const lines = [`Collection: ${planner.namespace.split('.').pop()}`, '', 'Winning plan:'];
  const winning = planner.winningPlan.queryPlan || planner.winningPlan;
  planLines(winning, 0, lines);
  const rejected = planner.rejectedPlans?.length || 0;
  if (rejected) lines.push('', `${rejected} other plan${rejected === 1 ? ' was' : 's were'} considered and rejected.`);
  if (stats) {
    lines.push('', `Returned ${stats.nReturned} · keys examined ${stats.totalKeysExamined} · documents examined ${stats.totalDocsExamined} · ${stats.executionTimeMillis} ms`);
  }
  const later = (explain.stages || []).slice(1).map((stage) => Object.keys(stage)[0]);
  if (later.length) lines.push('', `Then pipeline stages: ${later.join(' → ')}`);
  return lines.join('\n');
}

async function explainCommand(command) {
  const result = await db.command({ explain: command, verbosity: 'executionStats' });
  return formatPlan(result);
}

// ---------- Console ----------

/** Lays documents out as a table: one column per top-level field, nested values as JSON. */
function toTable(docs) {
  const columns = [];
  for (const doc of docs) {
    for (const key of Object.keys(doc)) if (!columns.includes(key)) columns.push(key);
  }
  const cell = (value) => {
    if (value === undefined || value === null) return null;
    if (typeof value === 'object') return EJSON.stringify(value, { relaxed: true });
    return value;
  };
  return { columns, rows: docs.map((doc) => columns.map((c) => cell(doc[c]))) };
}

router.post('/insights/sql', handle(async (req) => {
  const { command, explain, kind } = await toDatabaseCommand(parseCommand(req.body.sql));
  if (req.body.explain || explain) {
    if (!EXPLAINABLE.has(kind)) throw new AppError('Explain works on find, aggregate, countDocuments and distinct.');
    const started = performance.now();
    const plan = await explainCommand(command);
    return { plan, ms: Number((performance.now() - started).toFixed(2)) };
  }

  const collection = col(command[kind]);
  const options = { maxTimeMS: TIME_LIMIT_MS };
  const started = performance.now();
  let docs;
  if (kind === 'find') {
    docs = await collection.find(command.filter, {
      ...options, projection: command.projection, sort: command.sort, skip: command.skip,
      limit: Math.min(command.limit || MAX_ROWS + 1, MAX_ROWS + 1),
    }).toArray();
  } else if (kind === 'aggregate') {
    docs = await collection.aggregate([...command.pipeline, { $limit: MAX_ROWS + 1 }], options).toArray();
  } else if (kind === 'count') {
    docs = [{ count: await collection.countDocuments(command.query, options) }];
  } else if (kind === 'distinct') {
    docs = (await collection.distinct(command.key, command.query, options)).map((value) => ({ [command.key]: value }));
  } else {
    // Every index shares the collection's collation, so it is left out to keep the table readable.
    docs = (await collection.listIndexes().toArray()).map(({ v: version, collation, ...index }) => index);
  }
  const ms = Number((performance.now() - started).toFixed(2));
  const { columns, rows } = toTable(docs.slice(0, MAX_ROWS));
  if (!columns.length) return { columns: ['result'], rows: [], rowCount: 0, ms };
  return {
    columns, rows, rowCount: Math.min(docs.length, MAX_ROWS), truncated: docs.length > MAX_ROWS, ms,
  };
}));

// ---------- Query log ----------

router.get('/insights/log', handle(async () => ({
  entries: [...queryLog].reverse().map(({ raw, ...entry }) => ({ ...entry, explainable: EXPLAINABLE.has(entry.command) })),
})));

router.post('/insights/explain', handle(async (req) => {
  const entry = queryLog.find((e) => e.id === Number(req.body.id));
  if (!entry) throw new AppError('That command is no longer in the log.');
  if (!EXPLAINABLE.has(entry.command)) throw new AppError('Explain is available for logged read commands.');
  return { plan: await explainCommand(entry.raw) };
}));

router.post('/insights/log/clear', handle(async () => {
  queryLog.length = 0;
}));

// ---------- Schema objects ----------

const pretty = (value) => EJSON.stringify(value, null, 2, { relaxed: true });

// Where each MySQL function, procedure and trigger lives now.
const LOGIC = [
  { name: 'fn_rental_charge', kind: 'Function', where: 'lib/billing.js', code: [billing.rentalCharge, billing.rentalChargeExpr] },
  { name: 'fn_lease_total', kind: 'Function', where: 'lib/billing.js', code: [billing.leaseTotal] },
  { name: 'fn_customer_outstanding', kind: 'Function', where: 'lib/billing.js (OUTSTANDING_STAGES)', code: [billing.OUTSTANDING_STAGES] },
  { name: 'sp_available_vehicles', kind: 'Procedure', where: 'lib/availability.js', code: [availability.availableVehicles] },
  { name: 'sp_complete_rental', kind: 'Procedure', where: 'lib/procedures.js', code: [procedures.completeRental] },
  { name: 'trg_rental_after_insert', kind: 'Trigger', where: 'lib/procedures.js', code: [procedures.afterRentalChange] },
  { name: 'trg_rental_after_update', kind: 'Trigger', where: 'lib/procedures.js', code: [procedures.afterRentalChange] },
].map((item) => ({
  ...item,
  definition: `// Now in ${item.where}\n\n${item.code.map((c) => (typeof c === 'function' ? c.toString() : pretty(c))).join('\n\n')}`,
  code: undefined,
}));

router.get('/insights/schema', handle(async () => {
  const all = await db.listCollections().toArray();
  const collections = all.filter((c) => c.type === 'collection' && !c.name.startsWith('system.'))
    .sort((a, b) => a.name.localeCompare(b.name));
  const tables = await Promise.all(collections.map(async (c) => ({
    name: c.name,
    rows: await col(c.name).countDocuments(),
    validated: Boolean(c.options?.validator),
  })));
  const views = all.filter((c) => c.type === 'view').sort((a, b) => a.name.localeCompare(b.name))
    .map((c) => ({
      name: c.name,
      on: c.options.viewOn,
      definition: `db.createView("${c.name}", "${c.options.viewOn}", ${pretty(VIEWS[c.name]?.pipeline ?? c.options.pipeline)})`,
    }));
  const indexes = (await Promise.all(collections.map(async (c) => (await col(c.name).listIndexes().toArray())
    .map((index) => ({
      on_table: c.name,
      name: index.name,
      columns: Object.entries(index.key).map(([field, dir]) => `${field}${dir === -1 ? ' desc' : ''}`).join(', '),
      unique: Boolean(index.unique) || index.name === '_id_',
      partial: Boolean(index.partialFilterExpression),
    }))))).flat();
  return {
    database: config.database, tables, views, logic: LOGIC, indexes,
  };
}));

router.get('/insights/table/:name', handle(async (req) => {
  const info = (await db.listCollections({ name: String(req.params.name), type: 'collection' }).toArray())[0];
  if (!info) throw new AppError('Unknown collection.', 404);
  const sample = await col(info.name).findOne({}, { sort: { _id: 1 } });
  const validator = info.options?.validator;
  return {
    name: info.name,
    ddl: `// Validator (the NOT NULL, ENUM and CHECK rules)\n${validator ? pretty(validator) : 'none'}\n\n`
      + `// A sample document\n${sample ? pretty(sample) : 'The collection is empty.'}`,
  };
}));

module.exports = router;
