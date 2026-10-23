// In-process locks that stand in for SELECT ... FOR UPDATE.
// A standalone MongoDB server has no multi-document transactions, and this server is the only
// program writing to the lfrlms MongoDB database, so serialising writes per vehicle, chauffeur or
// contract here gives the same protection: two requests can never both pass the same check.

const tails = new Map();

function acquire(key) {
  const previous = tails.get(key) || Promise.resolve();
  let release;
  const mine = new Promise((resolve) => { release = resolve; });
  const tail = previous.then(() => mine);
  tails.set(key, tail);
  return previous.then(() => () => {
    release();
    if (tails.get(key) === tail) tails.delete(key);
  });
}

/** Runs work while holding every named lock. Keys are taken in sorted order, so no deadlocks. */
async function withLock(keys, work) {
  const unique = [...new Set(keys.filter(Boolean))].sort();
  const releases = [];
  try {
    for (const key of unique) releases.push(await acquire(key));
    return await work();
  } finally {
    releases.reverse().forEach((release) => release());
  }
}

module.exports = { withLock };
