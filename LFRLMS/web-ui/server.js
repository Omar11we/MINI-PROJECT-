// LFRLMS web interface: an Express API over the lfrlms MongoDB database plus a static front end.
// Runs separately from the Java Swing app, which keeps its data in MySQL.
const path = require('path');
const express = require('express');
const { db, col, config } = require('./lib/db');
const { ensureSchema } = require('./lib/schema');

const app = express();
const PORT = Number(process.env.PORT) || 3000;

app.use(express.json());
// Always revalidate front-end files so a browser never runs an outdated script after an update.
app.use(express.static(path.join(__dirname, 'public'), {
  setHeaders: (res) => res.setHeader('Cache-Control', 'no-cache'),
}));

app.get('/api/health', async (req, res) => {
  try {
    await db.command({ ping: 1 });
    res.json({ ok: true });
  } catch (error) {
    res.status(503).json({ ok: false, error: 'Cannot reach the lfrlms database.' });
  }
});

app.use('/api', require('./routes/fleet'));
app.use('/api', require('./routes/customers'));
app.use('/api', require('./routes/contracts'));
app.use('/api', require('./routes/reports'));
app.use('/api', require('./routes/insights'));

app.use('/api', (req, res) => res.status(404).json({ error: 'Not found.' }));

// Malformed JSON bodies and other unexpected errors still answer in JSON.
// eslint-disable-next-line no-unused-vars
app.use((error, req, res, next) => {
  res.status(error.status || 500).json({ error: error.status === 400 ? 'The request body is not valid JSON.' : 'Unexpected server error.' });
});

async function start() {
  try {
    await ensureSchema();
    if (!await col('vehicle').estimatedDocumentCount()) {
      console.warn(`The "${config.database}" MongoDB database is empty. Run "npm run migrate" to copy the data from MySQL.`);
    }
  } catch (error) {
    console.error(`Cannot prepare MongoDB at ${config.url}: ${error.message}`);
    if (error.name === 'MongoServerSelectionError') {
      console.error('Start MongoDB (for example "brew services start mongodb-community") and try again.');
    }
    process.exit(1);
  }

  const server = app.listen(PORT, () => {
    console.log(`LFRLMS web UI running at http://localhost:${PORT} (MongoDB database "${config.database}")`);
  });
  server.on('error', (error) => {
    if (error.code === 'EADDRINUSE') {
      console.error(`Port ${PORT} is already in use — the web UI may already be running. `
        + `Open http://localhost:${PORT}, or stop the other copy (Ctrl+C in its terminal) and try again.`);
      process.exit(1);
    }
    throw error;
  });
}

start();
