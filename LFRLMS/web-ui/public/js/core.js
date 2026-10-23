// Shared front-end helpers: API calls, formatting, icons, modals, forms and list pages.

// ---------- API ----------

async function request(method, url, body) {
  const options = { method, headers: {} };
  if (body !== undefined) {
    options.headers['Content-Type'] = 'application/json';
    options.body = JSON.stringify(body);
  }
  let response;
  try {
    response = await fetch(`/api${url}`, options);
  } catch {
    throw new Error('Cannot reach the web server. Is "npm start" still running?');
  }
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data.error || `Request failed (${response.status})`);
  return data;
}

function clean(params) {
  return Object.fromEntries(Object.entries(params)
    .filter(([, v]) => v !== '' && v !== null && v !== undefined && v !== false));
}

export const api = {
  get: (url, params) => request('GET', params ? `${url}?${new URLSearchParams(clean(params))}` : url),
  post: (url, body = {}) => request('POST', url, body),
  put: (url, body) => request('PUT', url, body),
  del: (url) => request('DELETE', url),
};

// ---------- Formatting ----------

const ESCAPES = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
export const esc = (value) => String(value ?? '').replace(/[&<>"']/g, (c) => ESCAPES[c]);

const rupees = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });
const rupeesExact = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2 });
export const money = (n, exact = false) => (n === null || n === undefined || n === ''
  ? '—' : (exact ? rupeesExact : rupees).format(Number(n)));
export function compactMoney(n) {
  const value = Number(n);
  const trim = (x) => String(Number(x.toFixed(1)));
  if (value >= 1e7) return `₹${trim(value / 1e7)}Cr`;
  if (value >= 1e5) return `₹${trim(value / 1e5)}L`;
  if (value >= 1e3) return `₹${trim(value / 1e3)}K`;
  return `₹${value}`;
}

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
/** '2026-10-23' -> '23 Oct 2026' */
export function fmtDate(value) {
  if (!value) return '—';
  const [y, m, d] = value.slice(0, 10).split('-');
  return `${Number(d)}\u00a0${MONTHS[Number(m) - 1]}\u00a0${y}`;
}
/** '2026-10-23 14:05:00' -> '23 Oct 2026, 2:05 pm' */
export function fmtDateTime(value) {
  if (!value) return '—';
  return `${fmtDate(value)}, ${fmtTime(value)}`;
}
export function fmtTime(value) {
  const [h, m] = value.slice(11, 16).split(':').map(Number);
  return `${h % 12 || 12}:${String(m).padStart(2, '0')}\u00a0${h < 12 ? 'am' : 'pm'}`;
}
export const monthLabel = (ym) => `${MONTHS[Number(ym.slice(5, 7)) - 1]} ${ym.slice(0, 4)}`;
export const monthShort = (ym) => MONTHS[Number(ym.slice(5, 7)) - 1];

/** Days from today to a 'YYYY-MM-DD' date (negative = past). */
export function daysFromToday(value) {
  const [y, m, d] = value.slice(0, 10).split('-').map(Number);
  const now = new Date();
  return Math.round((new Date(y, m - 1, d) - new Date(now.getFullYear(), now.getMonth(), now.getDate())) / 86400000);
}
export function relativeDay(value) {
  const days = daysFromToday(value);
  if (days === 0) return 'today';
  if (days === 1) return 'tomorrow';
  if (days === -1) return 'yesterday';
  return days > 0 ? `in ${days} days` : `${-days} days ago`;
}

const pad = (n) => String(n).padStart(2, '0');
/** Local time for datetime-local inputs, e.g. '2026-10-23T14:05'. */
export function localInput(offsetHours = 0) {
  const d = new Date(Date.now() + offsetHours * 3600000);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}
export const today = (offsetDays = 0) => localInput(offsetDays * 24).slice(0, 10);

const TONES = {
  Available: 'green', Reserved: 'violet', Rented: 'blue', Leased: 'violet', Maintenance: 'amber', Inactive: 'gray',
  Confirmed: 'violet', Active: 'blue', Completed: 'green', Cancelled: 'gray',
  Scheduled: 'violet', Terminated: 'red',
  Success: 'green', Pending: 'amber', Failed: 'red', Refunded: 'gray',
  'In Progress': 'amber', Complete: 'green', Overdue: 'red', Late: 'red',
};
/** Status badge: a dot plus the label, so colour is never the only cue. */
export const pill = (status, tone) => `<span class="status ${tone || TONES[status] || 'gray'}">${esc(status)}</span>`;

// ---------- Icons (inline SVG, stroke style) ----------

const ICON_PATHS = {
  plus: '<path d="M12 5v14M5 12h14"/>',
  search: '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/>',
  download: '<path d="M12 4v11M7 10l5 5 5-5M5 20h14"/>',
  refresh: '<path d="M20 12a8 8 0 1 1-2.3-5.7L20 8.5M20 3.5v5h-5"/>',
  sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>',
  moon: '<path d="M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z"/>',
  x: '<path d="M6 6l12 12M18 6 6 18"/>',
  check: '<path d="m5 12.5 4.5 4.5L19 7.5"/>',
  checkCircle: '<circle cx="12" cy="12" r="9"/><path d="m8 12.5 2.8 2.8L16.5 9.5"/>',
  alert: '<path d="M12 3.5 2.5 20h19z"/><path d="M12 10v4.5M12 17.2v.1"/>',
  info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v5.5M12 7.8v.1"/>',
  wrench: '<path d="M14.5 6.5a4 4 0 0 0 5 5l1 1-7.5 7.5a2.5 2.5 0 0 1-3.5-3.5L17 9l-1-1a4 4 0 0 0-1.5-1.5z"/>',
  calendar: '<rect x="3.5" y="5" width="17" height="15.5" rx="2"/><path d="M3.5 10h17M8 3v4M16 3v4"/>',
  idcard: '<rect x="3" y="5" width="18" height="14" rx="2"/><circle cx="9" cy="11" r="2.2"/><path d="M5.8 16a3.4 3.4 0 0 1 6.4 0M14.5 10h4M14.5 13.5h3"/>',
  play: '<path d="M7 5v14l11-7z"/>',
  card: '<rect x="2.5" y="5" width="19" height="14" rx="2"/><path d="M2.5 10h19M6.5 15h4"/>',
  key: '<circle cx="8" cy="15" r="4.5"/><path d="m11.2 11.8 8.3-8.3M16 7l3 3"/>',
  doc: '<path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5M9 13h6M9 17h6"/>',
  trash: '<path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"/>',
};
export const icon = (name) => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${ICON_PATHS[name] || ''}</svg>`;

// ---------- Toasts ----------

export function toast(message, type = 'ok') {
  const el = document.createElement('div');
  el.className = `toast ${type === 'error' ? 'error' : ''}`;
  el.innerHTML = `${icon(type === 'error' ? 'alert' : 'checkCircle')}<span></span>`;
  el.querySelector('span').textContent = message;
  document.getElementById('toasts').append(el);
  setTimeout(() => el.remove(), type === 'error' ? 6500 : 3500);
}

// ---------- Modals ----------

/** Opens a modal and returns { el, body, close }. */
export function openModal({ title, subtitle = '', body = '', footer = '', wide = false, onClose }) {
  const previousFocus = document.activeElement;
  const backdrop = document.createElement('div');
  backdrop.className = 'modal-backdrop';
  backdrop.innerHTML = `<div class="modal ${wide ? 'wide' : ''}" role="dialog" aria-modal="true" aria-label="${esc(title)}">
    <div class="modal-head"><div><h2>${esc(title)}</h2>${subtitle ? `<p>${esc(subtitle)}</p>` : ''}</div>
      <button class="icon-btn close" data-close aria-label="Close">${icon('x')}</button></div>
    <div class="modal-body">${body}</div>
    ${footer ? `<div class="modal-foot">${footer}</div>` : ''}
  </div>`;
  let closed = false;
  const close = () => {
    if (closed) return;
    closed = true;
    backdrop.remove();
    document.removeEventListener('keydown', onKey);
    previousFocus?.focus?.();
    onClose?.();
  };
  const onKey = (e) => {
    // Only the top-most dialog reacts to Escape.
    if (e.key === 'Escape' && document.querySelectorAll('.modal-backdrop').length
      && [...document.querySelectorAll('.modal-backdrop')].pop() === backdrop) close();
  };
  backdrop.addEventListener('mousedown', (e) => { if (e.target === backdrop) close(); });
  backdrop.querySelector('[data-close]').addEventListener('click', close);
  document.addEventListener('keydown', onKey);
  document.body.append(backdrop);
  return { el: backdrop, body: backdrop.querySelector('.modal-body'), close };
}

/** Asks a yes/no question; resolves true when confirmed. */
export function confirmDialog(message, { confirmLabel = 'Confirm', danger = false, title = 'Are you sure?' } = {}) {
  return new Promise((resolve) => {
    let answered = false;
    const modal = openModal({
      title,
      body: `<p style="margin:2px 0 4px;color:var(--ink-2)">${esc(message)}</p>`,
      footer: `<button class="btn" data-no>Keep as is</button>
               <button class="btn ${danger ? 'danger-solid' : 'primary'}" data-yes>${esc(confirmLabel)}</button>`,
      onClose: () => { if (!answered) resolve(false); },
    });
    modal.el.querySelector('[data-no]').onclick = () => modal.close();
    modal.el.querySelector('[data-yes]').onclick = () => { answered = true; modal.close(); resolve(true); };
    modal.el.querySelector('[data-yes]').focus();
  });
}

// ---------- Forms ----------

const optionOf = (o) => (typeof o === 'object' ? o : { value: o, label: o });

function fieldHtml(f, value) {
  const id = `f-${f.name}`;
  const req = f.required ? 'required' : '';
  let control;
  if (f.type === 'select') {
    const options = (typeof f.options === 'function' ? f.options() : f.options) || [];
    control = `<select id="${id}" name="${f.name}" ${req}>
      ${f.placeholder !== false ? `<option value="">${esc(f.placeholder || 'Select…')}</option>` : ''}
      ${options.map(optionOf).map((o) => `<option value="${esc(o.value)}" ${String(o.value) === String(value ?? '') ? 'selected' : ''}>${esc(o.label)}</option>`).join('')}</select>`;
  } else if (f.type === 'textarea') {
    control = `<textarea id="${id}" name="${f.name}" ${req} placeholder="${esc(f.placeholder || '')}">${esc(value)}</textarea>`;
  } else if (f.type === 'segmented') {
    control = `<div class="segmented" data-segment="${f.name}" role="radiogroup">${f.options.map((o) => `<button type="button" role="radio" aria-checked="${o.value === value}" data-value="${esc(o.value)}" class="${o.value === value ? 'on' : ''}">${esc(o.label)}</button>`).join('')}</div>
      <input type="hidden" name="${f.name}" value="${esc(value)}">`;
  } else if (f.type === 'html') {
    control = `<div data-html="${f.name}"></div>`;
  } else {
    const extra = [f.step && `step="${f.step}"`, f.min !== undefined && `min="${f.min}"`, f.max !== undefined && `max="${f.max}"`,
      f.placeholder && `placeholder="${esc(f.placeholder)}"`, f.maxlength && `maxlength="${f.maxlength}"`,
      f.autocomplete && `autocomplete="${f.autocomplete}"`].filter(Boolean).join(' ');
    control = `<input id="${id}" name="${f.name}" type="${f.type || 'text'}" value="${esc(value)}" ${req} ${extra}>`;
  }
  return `<div class="field ${f.span === 2 ? 'span-2' : ''}" data-field="${f.name}">
    ${f.label ? `<label for="${id}">${esc(f.label)}${f.required || f.type === 'segmented' ? '' : ' <span class="opt">· optional</span>'}</label>` : ''}
    ${control}
    ${f.hint ? `<div class="hint">${esc(f.hint)}</div>` : ''}
  </div>`;
}

/**
 * Opens a form in a modal. onSubmit(values, form) may throw; its message is shown in the form.
 * Fields: { name, label, type, options, required, hint, span, showIf(values) }.
 */
export function openForm({ title, subtitle, fields, values = {}, submitLabel = 'Save', onSubmit, onChange, wide }) {
  const modal = openModal({
    title,
    subtitle,
    wide,
    body: `<form class="form-grid" novalidate autocomplete="off">
      <div class="form-error" hidden>${icon('alert')}<span></span></div>
      ${fields.map((f) => fieldHtml(f, values[f.name] ?? f.default ?? '')).join('')}
      <button type="submit" hidden></button></form>`,
    footer: `<button class="btn" data-cancel>Cancel</button><button class="btn primary" data-submit>${esc(submitLabel)}</button>`,
  });
  const form = modal.el.querySelector('form');
  const errorBox = form.querySelector('.form-error');
  const submitBtn = modal.el.querySelector('[data-submit]');

  const read = () => {
    const data = Object.fromEntries(new FormData(form).entries());
    for (const [k, v] of Object.entries(data)) if (typeof v === 'string') data[k] = v.trim();
    return data;
  };
  const showError = (message, fieldName) => {
    errorBox.querySelector('span').textContent = message;
    errorBox.hidden = false;
    form.querySelectorAll('.field.invalid').forEach((el) => el.classList.remove('invalid'));
    if (fieldName) form.querySelector(`[data-field="${fieldName}"]`)?.classList.add('invalid');
    errorBox.scrollIntoView({ block: 'nearest' });
  };
  const handle = {
    form,
    read,
    html: (name, html) => { form.querySelector(`[data-html="${name}"]`).innerHTML = html; },
    setOptions: (name, options, placeholder = 'Select…') => {
      const select = form.elements[name];
      const current = select.value;
      select.innerHTML = `<option value="">${esc(placeholder)}</option>${options.map(optionOf).map((o) => `<option value="${esc(o.value)}">${esc(o.label)}</option>`).join('')}`;
      if (options.some((o) => String(optionOf(o).value) === current)) select.value = current;
    },
  };
  const refresh = () => {
    const data = read();
    for (const f of fields) {
      if (f.showIf) form.querySelector(`[data-field="${f.name}"]`).classList.toggle('hidden', !f.showIf(data));
    }
    onChange?.(data, handle);
  };

  form.querySelectorAll('[data-segment]').forEach((seg) => {
    seg.addEventListener('click', (e) => {
      const btn = e.target.closest('button');
      if (!btn) return;
      seg.querySelectorAll('button').forEach((b) => {
        b.classList.toggle('on', b === btn);
        b.setAttribute('aria-checked', String(b === btn));
      });
      form.elements[seg.dataset.segment].value = btn.dataset.value;
      refresh();
    });
  });
  form.addEventListener('input', (e) => {
    e.target.closest('.field')?.classList.remove('invalid');
    refresh();
  });
  form.addEventListener('change', refresh);

  const submit = async (e) => {
    e?.preventDefault();
    errorBox.hidden = true;
    const data = read();
    // Required check only for fields that are currently visible.
    const missing = fields.find((f) => f.required && (!f.showIf || f.showIf(data)) && !data[f.name]);
    if (missing) {
      showError(`${missing.label} is required.`, missing.name);
      form.elements[missing.name]?.focus?.();
      return;
    }
    submitBtn.disabled = true;
    submitBtn.textContent = 'Saving…';
    try {
      await onSubmit(data, handle);
      modal.close();
    } catch (error) {
      showError(error.message);
    } finally {
      submitBtn.disabled = false;
      submitBtn.textContent = submitLabel;
    }
  };
  form.addEventListener('submit', submit);
  submitBtn.addEventListener('click', submit);
  modal.el.querySelector('[data-cancel]').onclick = () => modal.close();
  refresh();
  form.querySelector('input:not([type=hidden]), select, textarea')?.focus();
  return handle;
}

// ---------- Lookups (brands, branches, customers…) ----------

let lookupCache = null;
export async function lookups(force = false) {
  if (!lookupCache || force) lookupCache = await api.get('/lookups');
  return lookupCache;
}
export const invalidateLookups = () => { lookupCache = null; };

// ---------- CSV ----------

export function downloadCsv(filename, columns, rows) {
  const cell = (v) => {
    const s = v === null || v === undefined ? '' : String(v);
    return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  };
  const get = (r, c, i) => (Array.isArray(r) ? r[i] : r[c]);
  const csv = [columns.map(cell).join(','), ...rows.map((r) => columns.map((c, i) => cell(get(r, c, i))).join(','))].join('\n');
  const link = document.createElement('a');
  link.href = URL.createObjectURL(new Blob([`﻿${csv}`], { type: 'text/csv;charset=utf-8' }));
  link.download = filename;
  link.click();
  setTimeout(() => URL.revokeObjectURL(link.href), 1000);
}

// ---------- Page chrome ----------

export function setHeader(title, sub = '', actionsHtml = '') {
  document.getElementById('page-title').textContent = title;
  document.getElementById('page-sub').textContent = sub;
  document.getElementById('page-actions').innerHTML = actionsHtml;
  document.title = `${title} · LFRLMS`;
}

// ---------- Generic list page ----------

/**
 * A searchable, filterable, sortable, paged table backed by a list endpoint.
 * config: {
 *   endpoint, columns: [{ label, sort, num, render(row), key }],
 *   tabs: { param, values: [...] }  -- status tabs with counts from the server,
 *   filters: [{ name, label, options } | { name, type: 'checkbox', label }],
 *   defaultSort, defaultDir, searchPlaceholder, emptyText,
 *   actions(row) -> [{ label, act, tone }], onAction(act, row, reload), onRowClick(row)
 * }
 */
export function listPage(container, config) {
  const state = {
    q: '', page: 1, pageSize: 15, sort: config.defaultSort, dir: config.defaultDir || 'asc', filters: {}, tab: '',
  };
  let rows = [];
  let searchTimer;
  let requestNo = 0;

  container.innerHTML = `<div class="card">
    ${config.tabs ? '<div class="tabs" role="tablist"></div>' : ''}
    <div class="toolbar">
      <div class="search">${icon('search')}<input class="control" type="search" placeholder="${esc(config.searchPlaceholder || 'Search…')}" aria-label="Search"><kbd>/</kbd></div>
      ${(config.filters || []).map((f) => (f.type === 'checkbox'
    ? `<label class="check"><input type="checkbox" data-filter="${f.name}"> ${esc(f.label)}</label>`
    : `<select class="control" data-filter="${f.name}" aria-label="${esc(f.label)}"><option value="">All ${esc(f.label.toLowerCase())}</option>
            ${(f.options || []).map(optionOf).map((o) => `<option value="${esc(o.value)}">${esc(o.label)}</option>`).join('')}</select>`)).join('')}
      <span class="count"></span>
    </div>
    <div class="table-wrap"><table class="data"><thead></thead><tbody></tbody></table></div>
    <div class="pager"></div>
  </div>`;

  const tabsEl = container.querySelector('.tabs');
  const thead = container.querySelector('thead');
  const tbody = container.querySelector('tbody');
  const pager = container.querySelector('.pager');
  const count = container.querySelector('.count');
  const searchInput = container.querySelector('.search input');
  const span = config.columns.length + (config.actions ? 1 : 0);

  searchInput.addEventListener('input', () => {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => { state.q = searchInput.value; state.page = 1; load(); }, 250);
  });
  container.querySelectorAll('[data-filter]').forEach((el) => {
    el.addEventListener('change', () => {
      state.filters[el.dataset.filter] = el.type === 'checkbox' ? (el.checked ? '1' : '') : el.value;
      state.page = 1;
      load();
    });
  });
  tabsEl?.addEventListener('click', (e) => {
    const btn = e.target.closest('button[data-tab]');
    if (!btn) return;
    state.tab = btn.dataset.tab;
    state.page = 1;
    load();
  });
  thead.addEventListener('click', (e) => {
    const th = e.target.closest('th[data-sort]');
    if (!th) return;
    if (state.sort === th.dataset.sort) state.dir = state.dir === 'asc' ? 'desc' : 'asc';
    else { state.sort = th.dataset.sort; state.dir = 'asc'; }
    load();
  });
  tbody.addEventListener('click', async (e) => {
    const tr = e.target.closest('tr[data-index]');
    if (!tr) return;
    const row = rows[Number(tr.dataset.index)];
    const btn = e.target.closest('[data-act]');
    try {
      if (btn) await config.onAction(btn.dataset.act, row, load);
      else if (config.onRowClick && !e.target.closest('a, button')) await config.onRowClick(row, load);
    } catch (error) {
      toast(error.message, 'error');
    }
  });
  pager.addEventListener('click', (e) => {
    const btn = e.target.closest('[data-page]');
    if (btn && !btn.disabled) { state.page = Number(btn.dataset.page); load(); }
  });

  function renderTabs(counts = {}) {
    if (!tabsEl) return;
    const all = Object.values(counts).reduce((a, b) => a + Number(b), 0);
    const tab = (value, label, n) => `<button role="tab" data-tab="${esc(value)}" class="${state.tab === value ? 'on' : ''}" aria-selected="${state.tab === value}">${esc(label)}<span class="n">${n}</span></button>`;
    tabsEl.innerHTML = tab('', 'All', all) + config.tabs.values.map((v) => tab(v, v, counts[v] || 0)).join('');
  }

  function renderHead() {
    thead.innerHTML = `<tr>${config.columns.map((c) => {
      const arrow = state.sort === c.sort ? `<span class="arrow">${state.dir === 'asc' ? '↑' : '↓'}</span>` : '';
      const aria = state.sort === c.sort ? `aria-sort="${state.dir === 'asc' ? 'ascending' : 'descending'}"` : '';
      return `<th class="${c.sort ? 'sortable' : ''} ${c.num ? 'num' : ''}" ${c.sort ? `data-sort="${c.sort}"` : ''} ${aria}>${esc(c.label)}${arrow}</th>`;
    }).join('')}${config.actions ? '<th><span class="muted" style="font-weight:500"></span></th>' : ''}</tr>`;
  }

  async function load() {
    const mine = ++requestNo;
    renderHead();
    if (!rows.length) tbody.innerHTML = Array(6).fill(`<tr class="skeleton">${'<td></td>'.repeat(span)}</tr>`).join('');
    else tbody.style.opacity = '.55';
    try {
      const params = {
        q: state.q, page: state.page, pageSize: state.pageSize, sort: state.sort, dir: state.dir, ...state.filters,
      };
      if (config.tabs) params[config.tabs.param] = state.tab;
      const data = await api.get(config.endpoint, params);
      if (mine !== requestNo) return; // a newer request has been sent; ignore this answer
      rows = data.rows;
      renderTabs(data.counts);
      const filtered = state.q || state.tab || Object.values(state.filters).some(Boolean);
      tbody.innerHTML = rows.length ? rows.map((row, i) => `<tr data-index="${i}" class="${config.onRowClick ? 'clickable' : ''}">
        ${config.columns.map((c) => `<td class="${c.num ? 'num' : ''}">${c.render ? c.render(row) : esc(row[c.key])}</td>`).join('')}
        ${config.actions ? `<td class="actions">${config.actions(row).map((a) => `<button class="btn sm ${a.tone || ''}" data-act="${a.act}">${esc(a.label)}</button>`).join('')}</td>` : ''}
      </tr>`).join('') : `<tr><td colspan="${span}" class="empty"><b>${filtered ? 'Nothing matches' : 'Nothing here yet'}</b>${filtered ? 'Try a different search or filter.' : esc(config.emptyText || '')}</td></tr>`;
      const pages = Math.max(1, Math.ceil(data.total / data.pageSize));
      const first = data.total ? (data.page - 1) * data.pageSize + 1 : 0;
      count.textContent = `${data.total} ${data.total === 1 ? 'record' : 'records'}`;
      pager.innerHTML = `<span>${data.total ? `${first}–${Math.min(data.total, data.page * data.pageSize)} of ${data.total}` : 'No records'}</span>
        <div class="btns">
          <button class="btn sm" data-page="${data.page - 1}" ${data.page <= 1 ? 'disabled' : ''}>← Previous</button>
          <span>Page ${data.page} of ${pages}</span>
          <button class="btn sm" data-page="${data.page + 1}" ${data.page >= pages ? 'disabled' : ''}>Next →</button>
        </div>`;
      // Deleting the last row on a page leaves it empty: step back one page.
      if (!rows.length && data.total && state.page > 1) { state.page = pages; load(); }
    } catch (error) {
      if (mine !== requestNo) return;
      tbody.innerHTML = `<tr><td colspan="${span}" class="empty"><b>Could not load</b>${esc(error.message)}</td></tr>`;
    } finally {
      if (mine === requestNo) tbody.style.opacity = '';
    }
  }

  load();
  return { reload: load, focusSearch: () => searchInput.focus() };
}

/** "/" focuses the search box on list pages. */
document.addEventListener('keydown', (e) => {
  if (e.key !== '/' || e.metaKey || e.ctrlKey || /INPUT|TEXTAREA|SELECT/.test(document.activeElement?.tagName)) return;
  if (document.querySelector('.modal-backdrop')) return;
  const input = document.querySelector('#content .search input');
  if (input) { e.preventDefault(); input.focus(); }
});
