// Reports R-01 to R-09 with parameters, a results table and CSV export.
import {
  api, esc, money, fmtDate, fmtDateTime, pill, icon, setHeader, lookups, downloadCsv, localInput, today, toast,
} from './core.js';

const REPORTS = [
  { key: 'available', code: 'R-01', title: 'Available vehicles', note: 'Vehicles free for the whole period: busy vehicles are found with distinct() and excluded with $nin.',
    params: [
      { name: 'from', label: 'From', type: 'datetime-local', value: () => localInput(1) },
      { name: 'to', label: 'To', type: 'datetime-local', value: () => localInput(49) },
      { name: 'branchId', label: 'Branch', type: 'branch' },
      { name: 'categoryId', label: 'Category', type: 'category' },
    ] },
  { key: 'active-rentals', code: 'R-02', title: 'Active rentals', note: 'Rentals currently out with customers.' },
  { key: 'active-leases', code: 'R-03', title: 'Active leases', note: 'Read from the vw_active_leases MongoDB view, ending soonest first.' },
  { key: 'revenue', code: 'R-04', title: 'Revenue by month', note: 'Successful payments grouped by month ($group on the year-month part of the date).',
    params: [
      { name: 'from', label: 'From', type: 'date', value: () => `${new Date().getFullYear()}-01-01` },
      { name: 'to', label: 'To', type: 'date', value: () => today() },
    ] },
  { key: 'outstanding', code: 'R-05', title: 'Outstanding balances', note: 'Every customer with the balances from vw_rental_balance and vw_lease_balance, highest first.' },
  { key: 'most-rented', code: 'R-06', title: 'Most rented vehicles', note: 'Active and completed rentals per vehicle ($lookup + $count).',
    params: [{ name: 'limit', label: 'Show top', type: 'number', value: () => 10 }] },
  { key: 'maintenance-due', code: 'R-07', title: 'Maintenance due', note: 'Jobs whose next service falls on or before the date.',
    params: [{ name: 'through', label: 'Due by', type: 'date', value: () => today(30) }] },
  { key: 'customer-history', code: 'R-08', title: 'Customer history', note: 'Rentals, leases and payments for one customer ($unionWith, like UNION ALL).',
    params: [{ name: 'customerId', label: 'Customer', type: 'customer' }] },
  { key: 'rental-status', code: 'R-09', title: 'Rental status summary', note: 'Number of rentals in each status.' },
];

const MONEY_COLUMNS = /(rate|charge|amount|revenue|outstanding|deposit|cost|total)$/;
const COUNT_COLUMNS = /(count|_id)$/;
const label = (column) => column.replace(/_/g, ' ').replace(/\bid\b/i, 'ID').replace(/^\w/, (c) => c.toUpperCase());

function formatCell(column, value) {
  if (value === null || value === undefined) return '<span class="muted">—</span>';
  if (column === 'status') return pill(value);
  if (MONEY_COLUMNS.test(column)) return money(value, column === 'outstanding');
  if (/_datetime$|transaction_date$/.test(column)) return fmtDateTime(value);
  if (/_date$/.test(column)) return fmtDate(value);
  if (column === 'registration_no') return `<span class="mono">${esc(value)}</span>`;
  return esc(value);
}

export async function renderReports(container) {
  setHeader('Reports', 'The project reports R-01 to R-09. Run any report and export it as CSV.');
  const lk = await lookups();
  let current = REPORTS[0];
  let lastResult = null;

  container.innerHTML = `<div class="split">
    <nav class="card side-list" aria-label="Reports">${REPORTS.map((r) => `<button data-key="${r.key}"><span class="code">${r.code}</span>${esc(r.title)}</button>`).join('')}</nav>
    <section class="card" id="report-panel"></section>
  </div>`;
  const panel = container.querySelector('#report-panel');

  container.querySelector('.side-list').addEventListener('click', (e) => {
    const btn = e.target.closest('button[data-key]');
    if (btn) select(REPORTS.find((r) => r.key === btn.dataset.key));
  });

  function paramControl(p) {
    const opts = (list, all) => `<option value="">${all}</option>${list.map((x) => `<option value="${x.id}">${esc(x.name)}</option>`).join('')}`;
    if (p.type === 'branch') return `<select name="${p.name}">${opts(lk.branches, 'All branches')}</select>`;
    if (p.type === 'category') return `<select name="${p.name}">${opts(lk.categories, 'All categories')}</select>`;
    if (p.type === 'customer') return `<select name="${p.name}">${opts(lk.customers.map((c) => ({ id: c.id, name: `${c.name} · #${c.id}` })), 'Choose a customer…')}</select>`;
    return `<input name="${p.name}" type="${p.type}" value="${esc(p.value())}" ${p.type === 'number' ? 'min="1" max="100"' : ''}>`;
  }

  function select(report) {
    current = report;
    lastResult = null;
    container.querySelectorAll('.side-list button').forEach((b) => b.classList.toggle('on', b.dataset.key === report.key));
    panel.innerHTML = `
      <div class="card-head" style="padding-bottom:14px"><div><h2 class="card-title">${report.code} · ${esc(report.title)}</h2><div class="card-sub">${esc(report.note)}</div></div>
        <button class="btn sm" id="csv" style="margin-left:auto" disabled>${icon('download')} CSV</button></div>
      <form class="params" ${(report.params || []).length ? '' : 'hidden'}>
        ${(report.params || []).map((p) => `<div class="field"><label>${esc(p.label)}</label>${paramControl(p)}</div>`).join('')}
        <button class="btn primary" type="submit">Run report</button>
      </form>
      <div id="report-out"></div>`;
    panel.querySelector('form').addEventListener('submit', (e) => { e.preventDefault(); run(); });
    panel.querySelector('#csv').onclick = () => lastResult
      && downloadCsv(`${report.code}-${report.key}.csv`, lastResult.columns, lastResult.rows);
    if ((report.params || []).some((p) => p.type === 'customer')) {
      panel.querySelector('#report-out').innerHTML = '<div class="empty"><b>Choose a customer</b>then run the report.</div>';
      panel.querySelector('select[name=customerId]').addEventListener('change', run);
    } else {
      run();
    }
  }

  async function run() {
    const out = panel.querySelector('#report-out');
    const params = Object.fromEntries(new FormData(panel.querySelector('form')).entries());
    out.innerHTML = '<div class="loading">Running…</div>';
    try {
      const result = await api.get(`/reports/${current.key}`, params);
      lastResult = result;
      panel.querySelector('#csv').disabled = !result.rows.length;
      const align = (c) => (MONEY_COLUMNS.test(c) || COUNT_COLUMNS.test(c) ? 'num' : '');
      out.innerHTML = `<div class="table-wrap"><table class="data">
        <thead><tr>${result.columns.map((c) => `<th class="${align(c)}">${esc(label(c))}</th>`).join('')}</tr></thead>
        <tbody>${result.rows.length ? result.rows.map((row) => `<tr>${result.columns.map((c) => `<td class="${align(c)}">${formatCell(c, row[c])}</td>`).join('')}</tr>`).join('')
          : `<tr><td colspan="${result.columns.length}" class="empty"><b>No rows</b>Nothing matches these parameters.</td></tr>`}</tbody>
      </table></div>
      <div class="pager">${result.rows.length} row${result.rows.length === 1 ? '' : 's'}</div>`;
    } catch (error) {
      out.innerHTML = `<div class="empty"><b>Could not run the report</b>${esc(error.message)}</div>`;
      toast(error.message, 'error');
    }
  }

  select(current);
}
