// Small helpers shared by the route modules.
const { friendlyError, aggregate } = require('./db');

/** Wraps an async route so errors become JSON responses with a readable message. */
function handle(fn) {
  return async (req, res) => {
    try {
      const result = await fn(req, res);
      if (!res.headersSent) {
        res.json(result === undefined ? { ok: true } : result);
      }
    } catch (error) {
      const friendly = friendlyError(error);
      res.status(friendly.status || 400).json({ error: friendly.message });
    }
  };
}

/** Reads page and pageSize from the query string. */
function paging(req, defaultSize = 15) {
  const page = Math.max(1, parseInt(req.query.page, 10) || 1);
  const pageSize = Math.min(100, Math.max(1, parseInt(req.query.pageSize, 10) || defaultSize));
  return { page, pageSize, limit: pageSize, offset: (page - 1) * pageSize };
}

/** Builds a $sort stage from a whitelist so user input never names a field directly. */
function sorting(req, fields, fallbackKey, fallbackDir = 'asc') {
  const key = Object.prototype.hasOwnProperty.call(fields, req.query.sort) ? req.query.sort : fallbackKey;
  const dir = (req.query.dir || fallbackDir).toLowerCase() === 'desc' ? -1 : 1;
  // _id breaks ties so paging is stable.
  return { $sort: { [fields[key]]: dir, _id: dir } };
}

const escapeRegex = (text) => text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

/** Collects $match conditions. */
class Filter {
  constructor() {
    this.conditions = [];
  }

  add(condition) {
    this.conditions.push(condition);
    return this;
  }

  /** Case-insensitive "contains" search, like LIKE '%term%'. A field written '#field' is a number. */
  search(value, fields) {
    const term = typeof value === 'string' ? value.trim() : '';
    if (term) {
      const regex = escapeRegex(term);
      this.add({
        $or: fields.map((field) => (field.startsWith('#')
          ? { $expr: { $regexMatch: { input: { $toString: `$${field.slice(1)}` }, regex, options: 'i' } } }
          : { [field]: { $regex: regex, $options: 'i' } })),
      });
    }
    return this;
  }

  stages() {
    return this.conditions.length ? [{ $match: { $and: this.conditions } }] : [];
  }
}

/** Counts documents per value of a field, for the status tabs above each list. */
async function countBy(collection, stages, field) {
  const rows = await aggregate(collection, [...stages, { $group: { _id: `$${field}`, n: { $sum: 1 } } }]);
  return Object.fromEntries(rows.map((r) => [r._id, r.n]));
}

/**
 * One page of a list screen: the status counts (ignoring the status tab), the rows and the total.
 * `stages` join and compute fields, `filter` holds the search, `output` shapes each returned row.
 */
async function listPage(collection, {
  stages = [], filter, statusField, status, sort, page, output = [],
}) {
  const base = [...stages, ...filter.stages()];
  const counts = await countBy(collection, base, statusField);
  const byStatus = typeof status === 'string' && status ? [{ $match: { [statusField]: status } }] : [];
  const [result] = await aggregate(collection, [
    ...base,
    ...byStatus,
    {
      $facet: {
        rows: [sort, { $skip: page.offset }, { $limit: page.limit }, ...output],
        total: [{ $count: 'n' }],
      },
    },
  ]);
  return {
    rows: result.rows, total: result.total[0]?.n ?? 0, page: page.page, pageSize: page.pageSize, counts,
  };
}

module.exports = {
  handle, paging, sorting, Filter, countBy, listPage,
};
