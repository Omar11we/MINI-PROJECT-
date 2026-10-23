// DBMS Insights: a read-only MongoDB shell, the live query log with explain plans, and the schema browser.
import { api, esc, icon, setHeader, openModal, toast, downloadCsv } from './core.js';

const EXAMPLES = [
  { label: '$lookup (JOIN)', sql: `db.vehicle.aggregate([
  { $lookup: { from: "brand", localField: "brand_id", foreignField: "_id", as: "brand" } },
  { $unwind: "$brand" },
  { $lookup: { from: "vehicle_category", localField: "category_id", foreignField: "_id", as: "category" } },
  { $unwind: "$category" },
  { $lookup: { from: "branch", localField: "branch_id", foreignField: "_id", as: "branch" } },
  { $unwind: "$branch" },
  { $project: { _id: 0, registration_no: 1, brand: "$brand.brand_name", model: 1,
                category: "$category.category_name", city: "$branch.city" } },
  { $sort: { brand: 1, model: 1 } }
])` },
  { label: '$group + $match (HAVING)', sql: `db.payment.aggregate([
  { $match: { status: "Success", rental_id: { $ne: null } } },
  { $lookup: { from: "rental", localField: "rental_id", foreignField: "_id", as: "rental" } },
  { $unwind: "$rental" },
  { $group: { _id: "$rental.customer_id", payments: { $sum: 1 }, total_paid: { $sum: "$amount" } } },
  { $match: { total_paid: { $gt: 20000 } } },  // the HAVING clause
  { $lookup: { from: "customer", localField: "_id", foreignField: "_id", as: "c" } },
  { $unwind: "$c" },
  { $project: { customer: { $concat: ["$c.first_name", " ", "$c.last_name"] }, payments: 1, total_paid: 1 } },
  { $sort: { total_paid: -1 } }
])` },
  { label: 'Anti-join', sql: `// Vehicles that have never been rented (NOT IN / NOT EXISTS)
db.vehicle.aggregate([
  { $lookup: { from: "rental", localField: "_id", foreignField: "vehicle_id", as: "rentals" } },
  { $match: { rentals: { $size: 0 } } },
  { $project: { _id: 0, registration_no: 1, model: 1, status: 1 } }
])` },
  { label: 'View', sql: 'db.vw_rental_balance.find({ balance_due: { $gt: 0 } }).sort({ balance_due: -1 })' },
  { label: 'find + regex', sql: `db.vehicle.find(
  { model: /class/i, rental_rate: { $lte: 20000 } },
  { _id: 0, registration_no: 1, model: 1, rental_rate: 1, status: 1 }
).sort({ rental_rate: -1 })` },
  { label: 'Window function', sql: `db.vehicle.aggregate([
  { $lookup: { from: "rental", localField: "_id", foreignField: "vehicle_id", as: "rentals" } },
  { $lookup: { from: "vehicle_category", localField: "category_id", foreignField: "_id", as: "category" } },
  { $project: { category: { $first: "$category.category_name" }, registration_no: 1, model: 1,
                rentals: { $size: "$rentals" } } },
  { $setWindowFields: { partitionBy: "$category", sortBy: { rentals: -1 },
                        output: { rank_in_category: { $rank: {} } } } },
  { $sort: { category: 1, rank_in_category: 1 } }
])` },
  { label: 'UNION ALL', sql: `db.rental.aggregate([
  { $project: { _id: 0, contract: "Rental", status: 1 } },
  { $unionWith: { coll: "lease", pipeline: [ { $project: { _id: 0, contract: "Lease", status: 1 } } ] } },
  { $group: { _id: { contract: "$contract", status: "$status" }, count: { $sum: 1 } } },
  { $sort: { "_id.contract": 1, "_id.status": 1 } }
])` },
  { label: 'Embedded subtype (ISA)', sql: `db.customer.find(
  { customer_type: "Corporate" },
  { first_name: 1, last_name: 1, "corporate.company_name": 1, "corporate.gst_no": 1 }
)` },
  { label: 'Indexes', sql: 'db.rental.getIndexes()' },
];

let activeTab = 'console';
let consoleSql = EXAMPLES[0].sql;

export function renderInsights(container) {
  setHeader('DBMS Insights', 'Run your own read-only MongoDB queries, watch the commands this app sends, and browse the schema.');
  container.innerHTML = `<div class="card">
    <div class="tabs" role="tablist">
      <button data-tab="console">Mongo shell</button>
      <button data-tab="log">Query log</button>
      <button data-tab="schema">Schema</button>
    </div>
    <div id="insights-body"></div>
  </div>`;
  const tabs = container.querySelector('.tabs');
  const body = container.querySelector('#insights-body');
  const show = (tab) => {
    activeTab = tab;
    tabs.querySelectorAll('button').forEach((b) => b.classList.toggle('on', b.dataset.tab === tab));
    ({ console: renderConsole, log: renderLog, schema: renderSchema })[tab](body);
  };
  tabs.addEventListener('click', (e) => { const b = e.target.closest('button[data-tab]'); if (b) show(b.dataset.tab); });
  show(activeTab);
}

// ---------- Mongo shell ----------

function renderConsole(body) {
  let last = null;
  body.innerHTML = `
    <div class="chips"><span class="muted" style="font-size:12.5px;align-self:center;margin-right:4px">Examples:</span>
      ${EXAMPLES.map((x, i) => `<button class="chip" data-example="${i}">${esc(x.label)}</button>`).join('')}</div>
    <textarea class="sql-editor" spellcheck="false" aria-label="MongoDB command">${esc(consoleSql)}</textarea>
    <div class="sql-bar">
      <button class="btn primary" data-run>Run <kbd style="margin-left:4px;background:transparent;color:inherit;border-color:rgba(255,255,255,.4)">${/Mac|iPhone|iPad/.test(navigator.platform) ? '⌘' : 'Ctrl'} ↵</kbd></button>
      <button class="btn" data-explain>Explain</button>
      <button class="btn" data-csv disabled>${icon('download')} CSV</button>
      <span class="meta">Read-only: find, findOne, aggregate, countDocuments, distinct, getIndexes. $out and $merge are blocked.</span>
    </div>
    <div data-out><div class="empty"><b>Ready</b>Pick an example or write a command such as db.vehicle.find({ status: "Available" }), then press Run.</div></div>`;
  const editor = body.querySelector('.sql-editor');
  const out = body.querySelector('[data-out]');
  const csv = body.querySelector('[data-csv]');

  editor.addEventListener('input', () => { consoleSql = editor.value; });
  editor.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) { e.preventDefault(); run(false); }
    if (e.key === 'Tab') { e.preventDefault(); editor.setRangeText('  ', editor.selectionStart, editor.selectionEnd, 'end'); }
  });
  body.querySelector('.chips').addEventListener('click', (e) => {
    const chip = e.target.closest('[data-example]');
    if (!chip) return;
    editor.value = consoleSql = EXAMPLES[Number(chip.dataset.example)].sql;
    run(false);
  });
  body.querySelector('[data-run]').onclick = () => run(false);
  body.querySelector('[data-explain]').onclick = () => run(true);
  csv.onclick = () => last && downloadCsv('query-result.csv', last.columns, last.rows);

  async function run(explain) {
    out.innerHTML = '<div class="loading">Running…</div>';
    csv.disabled = true;
    try {
      const result = await api.post('/insights/sql', { sql: editor.value, explain });
      if (result.plan) {
        out.innerHTML = `<div style="padding:16px"><div class="section-title" style="margin-top:0">Query plan (explain, executionStats)</div><pre class="code">${esc(result.plan)}</pre></div>`;
        return;
      }
      last = result;
      csv.disabled = !result.rows.length;
      out.innerHTML = `<div class="table-wrap" style="max-height:520px;overflow:auto"><table class="data">
          <thead><tr>${result.columns.map((c) => `<th>${esc(c)}</th>`).join('')}</tr></thead>
          <tbody>${result.rows.map((r) => `<tr>${r.map((v) => `<td class="${typeof v === 'number' ? 'num' : ''}">${v === null ? '<span class="muted">null</span>' : esc(v)}</td>`).join('')}</tr>`).join('')
            || `<tr><td colspan="${result.columns.length}" class="empty"><b>No documents</b>The command ran but returned nothing.</td></tr>`}</tbody>
        </table></div>
        <div class="pager">${result.rowCount} document${result.rowCount === 1 ? '' : 's'}${result.truncated ? ' (showing the first 500)' : ''} · ${result.ms} ms</div>`;
    } catch (error) {
      out.innerHTML = `<div style="padding:16px"><div class="form-error">${icon('alert')}<span>${esc(error.message)}</span></div></div>`;
    }
  }
}

// ---------- Query log ----------

async function renderLog(body) {
  body.innerHTML = `<div class="toolbar"><span class="muted" style="font-size:13px">Every command this web app has sent to MongoDB, newest first (last 150).</span>
    <span style="margin-left:auto;display:flex;gap:8px"><button class="btn sm" data-refresh>${icon('refresh')} Refresh</button><button class="btn sm" data-clear>Clear</button></span></div>
    <div data-out><div class="loading">Loading…</div></div>`;
  body.querySelector('[data-refresh]').onclick = () => renderLog(body);
  body.querySelector('[data-clear]').onclick = async () => { await api.post('/insights/log/clear'); renderLog(body); };
  const { entries } = await api.get('/insights/log');
  const out = body.querySelector('[data-out]');
  if (!entries.length) {
    out.innerHTML = '<div class="empty"><b>No commands yet</b>Use the app, then refresh this tab.</div>';
    return;
  }
  out.innerHTML = `<div class="table-wrap"><table class="data">
    <thead><tr><th>Time</th><th>Command</th><th>Collection</th><th class="num">Docs</th><th class="num">ms</th><th></th></tr></thead>
    <tbody>${entries.map((e) => `<tr>
      <td class="muted" style="white-space:nowrap">${new Date(e.at).toLocaleTimeString('en-IN')}</td>
      <td><div class="sql-cell">${esc(e.text.length > 260 ? `${e.text.slice(0, 260)}…` : e.text)}</div>${e.error ? `<div class="err-text">${esc(e.error)}</div>` : ''}</td>
      <td class="mono" style="font-size:11.5px">${esc(e.collection)}</td>
      <td class="num">${e.rows ?? ''}</td><td class="num">${e.ms}</td>
      <td class="actions">${e.explainable ? `<button class="btn sm" data-explain="${e.id}">Explain</button>
        <button class="btn sm ghost" data-copy="${e.id}">Open in shell</button>` : ''}</td>
    </tr>`).join('')}</tbody></table></div>`;
  out.addEventListener('click', async (ev) => {
    const explain = ev.target.closest('[data-explain]');
    const copy = ev.target.closest('[data-copy]');
    if (explain) {
      try {
        const { plan } = await api.post('/insights/explain', { id: Number(explain.dataset.explain) });
        openModal({ title: 'Query plan', subtitle: 'explain("executionStats") of the logged command', wide: true, body: `<pre class="code">${esc(plan)}</pre>` });
      } catch (error) { toast(error.message, 'error'); }
    }
    if (copy) {
      consoleSql = entries.find((x) => String(x.id) === copy.dataset.copy).text;
      document.querySelector('.tabs button[data-tab="console"]').click();
    }
  });
}

// ---------- Schema ----------

async function renderSchema(body) {
  body.innerHTML = '<div class="loading">Reading the database catalogue…</div>';
  const s = await api.get('/insights/schema');
  const objects = (list, kind, meta) => (list.length ? `<div class="object-grid">${list.map((o, i) => `<button class="object" data-kind="${kind}" data-i="${i}"><b>${esc(o.name)}</b><span>${esc(meta(o))}</span></button>`).join('')}</div>` : '<div class="muted">None.</div>');
  body.innerHTML = `<div class="card-body" data-schema>
    <div class="section-title" style="margin-top:4px">Collections in <span class="mono">${esc(s.database)}</span></div>
    ${objects(s.tables, 'table', (t) => `${t.rows} documents${t.validated ? ' · validator' : ''}`)}
    <div class="section-title">Views</div>${objects(s.views, 'view', (v) => `view on ${v.on}`)}
    <div class="section-title">SQL functions, procedures and triggers, now in the app</div>
    ${objects(s.logic, 'logic', (l) => `${l.kind.toLowerCase()} · ${l.where}`)}
    <div class="section-title">Indexes</div>
    <div class="inner-table table-wrap"><table class="data"><thead><tr><th>Collection</th><th>Index</th><th>Fields</th><th>Unique</th></tr></thead>
      <tbody>${s.indexes.map((x) => `<tr><td class="mono">${esc(x.on_table)}</td><td class="mono">${esc(x.name)}</td><td class="mono">${esc(x.columns)}</td><td>${x.unique ? `yes${x.partial ? ' <span class="muted">(partial)</span>' : ''}` : '<span class="muted">no</span>'}</td></tr>`).join('')}</tbody></table></div>
  </div>`;
  body.querySelector('[data-schema]').addEventListener('click', async (e) => {
    const btn = e.target.closest('.object');
    if (!btn) return;
    const i = Number(btn.dataset.i);
    try {
      if (btn.dataset.kind === 'table') {
        const t = await api.get(`/insights/table/${encodeURIComponent(s.tables[i].name)}`);
        openModal({ title: t.name, subtitle: 'Validator and a sample document', wide: true, body: `<pre class="code">${esc(t.ddl)}</pre>` });
      } else {
        const o = { view: s.views, logic: s.logic }[btn.dataset.kind][i];
        openModal({ title: o.name, subtitle: btn.querySelector('span').textContent, wide: true, body: `<pre class="code">${esc(o.definition)}</pre>` });
      }
    } catch (error) { toast(error.message, 'error'); }
  });
}
