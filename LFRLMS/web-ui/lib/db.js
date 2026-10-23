// Database access shared by every route.
// The web UI keeps its data in MongoDB; the Java Swing app keeps using MySQL.
const { MongoClient } = require('mongodb');

const QUERY_LOG_SIZE = 150;

const config = {
  url: process.env.MONGO_URL || 'mongodb://127.0.0.1:27017',
  database: process.env.MONGO_DB || 'lfrlms',
};

/** Error with a message that is safe to show to staff. */
class AppError extends Error {
  constructor(message, status = 400) {
    super(message);
    this.status = status;
  }
}

// ---------- Query log (like QueryLog.java), shown on the Insights page ----------

const queryLog = [];
let nextLogId = 1;
const pending = new Map();

// Only data commands are logged; handshakes, index builds and cursor housekeeping are not.
const LOGGED_COMMANDS = new Set(['find', 'aggregate', 'insert', 'update', 'delete', 'findAndModify', 'count', 'distinct']);
// Driver bookkeeping fields that do not belong to the query itself.
const DRIVER_FIELDS = new Set(['lsid', 'txnNumber', 'autocommit', 'startTransaction', 'readConcern', 'writeConcern', 'apiVersion']);

function cleanCommand(command) {
  return Object.fromEntries(Object.entries(command)
    .filter(([key]) => !key.startsWith('$') && !DRIVER_FIELDS.has(key)));
}

const json = (value) => JSON.stringify(value ?? {});

/** Writes a command the way you would type it in mongosh. */
function describe(name, c) {
  switch (name) {
    case 'find': {
      let text = `db.${c.find}.find(${json(c.filter)}${c.projection ? `, ${json(c.projection)}` : ''})`;
      if (c.sort) text += `.sort(${json(c.sort)})`;
      if (c.skip) text += `.skip(${c.skip})`;
      if (c.limit) text += `.limit(${c.limit})`;
      return text;
    }
    case 'aggregate':
      return `db.${c.aggregate}.aggregate(${json(c.pipeline)})`;
    case 'count':
      return `db.${c.count}.countDocuments(${json(c.query)})`;
    case 'distinct':
      return `db.${c.distinct}.distinct(${json(c.key)}, ${json(c.query)})`;
    case 'insert':
      return c.documents.length === 1
        ? `db.${c.insert}.insertOne(${json(c.documents[0])})`
        : `db.${c.insert}.insertMany(${json(c.documents)})`;
    case 'update':
      return c.updates.map((u) => `db.${c.update}.${u.multi ? 'updateMany' : 'updateOne'}(${json(u.q)}, ${json(u.u)})`).join('; ');
    case 'delete':
      return c.deletes.map((d) => `db.${c.delete}.${d.limit ? 'deleteOne' : 'deleteMany'}(${json(d.q)})`).join('; ');
    case 'findAndModify':
      return `db.${c.findAndModify}.findOneAndUpdate(${json(c.query)}, ${json(c.update)})`;
    default:
      return json(c);
  }
}

function countResult(name, reply) {
  if (reply?.cursor) return reply.cursor.firstBatch.length;
  if (name === 'count' || name === 'insert' || name === 'delete') return reply?.n ?? null;
  if (name === 'update') return reply?.nModified ?? reply?.n ?? null;
  if (name === 'distinct') return reply?.values?.length ?? null;
  if (name === 'findAndModify') return reply?.value ? 1 : 0;
  return null;
}

function record(requestId, reply, error, duration) {
  const started = pending.get(requestId);
  if (!started) return;
  pending.delete(requestId);
  queryLog.push({
    id: nextLogId++,
    at: started.at,
    command: started.name,
    collection: started.collection,
    text: describe(started.name, started.command),
    // Kept so the Insights page can explain the same command later.
    raw: started.command,
    ms: Number(Number(duration).toFixed(2)),
    rows: error ? null : countResult(started.name, reply),
    error: error ? error.message : null,
  });
  if (queryLog.length > QUERY_LOG_SIZE) queryLog.shift();
}

// ---------- Connection ----------

const client = new MongoClient(config.url, {
  monitorCommands: true,
  serverSelectionTimeoutMS: 10000,
});

client.on('commandStarted', (event) => {
  if (event.databaseName !== config.database || !LOGGED_COMMANDS.has(event.commandName)) return;
  const command = cleanCommand(event.command);
  if (command[event.commandName] === 'counters') return;
  pending.set(event.requestId, {
    at: new Date().toISOString(), name: event.commandName, collection: command[event.commandName], command,
  });
});
client.on('commandSucceeded', (event) => record(event.requestId, event.reply, null, event.duration));
client.on('commandFailed', (event) => record(event.requestId, null, event.failure, event.duration));

const db = client.db(config.database);

/** Shorthand for a collection or view. */
const col = (name) => db.collection(name);

/** Hands out the next integer id for a collection, like AUTO_INCREMENT. */
async function nextId(name) {
  const counter = await col('counters').findOneAndUpdate(
    { _id: name }, { $inc: { seq: 1 } }, { upsert: true, returnDocument: 'after' },
  );
  return counter.seq;
}

/** Runs an aggregation and returns its documents. */
function aggregate(name, pipeline) {
  return col(name).aggregate(pipeline).toArray();
}

/** Runs an aggregation and returns the first document, or null. */
async function aggregateOne(name, pipeline) {
  const [first] = await aggregate(name, [...pipeline, { $limit: 1 }]);
  return first ?? null;
}

/** Turns MongoDB errors into messages staff can act on. */
function friendlyError(error) {
  if (error instanceof AppError) {
    return error;
  }
  switch (error.code) {
    case 11000:
      return new AppError('A record with the same unique value already exists.', 409);
    case 121:
      return new AppError('The database rejected the values (document failed validation).');
    case 50:
      return new AppError('The query took longer than 5 seconds and was stopped.');
    default:
      if (error.name === 'MongoServerSelectionError' || error.name === 'MongoNetworkError') {
        return new AppError('Cannot connect to MongoDB. Is the server running?', 503);
      }
      console.error(error);
      return new AppError('An unexpected database error occurred.', 500);
  }
}

module.exports = {
  client, db, col, config, nextId, aggregate, aggregateOne, AppError, friendlyError, queryLog,
};
