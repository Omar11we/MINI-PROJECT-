// Dashboard: KPI band, today's desk (pickups and returns), fleet, attention list and revenue chart.
import {
  api, esc, money, compactMoney, fmtDate, fmtTime, monthLabel, monthShort, relativeDay, daysFromToday,
  icon, setHeader, toast,
} from './core.js';
import {
  rentalForm, paymentForm, startRental, completeRental, cancelRental,
} from './contracts.js';

function greeting() {
  const h = new Date().getHours();
  return h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening';
}

export async function renderDashboard(container) {
  const dateLine = new Date().toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
  setHeader(greeting(), `${dateLine} · here is how the fleet stands`,
    `<button class="btn" id="dash-pay">${icon('card')} Record payment</button>
     <button class="btn primary" id="dash-rent">${icon('plus')} New rental</button>`);
  const reload = () => renderDashboard(container);
  document.getElementById('dash-rent').onclick = () => rentalForm(reload);
  document.getElementById('dash-pay').onclick = () => paymentForm(null, reload);

  if (!container.querySelector('.kpi-band')) container.innerHTML = '<div class="loading">Loading the dashboard…</div>';
  const d = await api.get('/dashboard');
  if (!Array.isArray(d.returns)) {
    throw new Error('The web server is running an older version. Stop it with Ctrl+C in its terminal, run "npm start" again, then refresh.');
  }
  const s = d.summary;
  const inService = s.total_vehicles - s.inactive_vehicles;
  const inUse = s.rented_vehicles + s.leased_vehicles;
  const utilisation = inService ? Math.round((inUse / inService) * 100) : 0;
  const overdue = d.returns.filter((r) => r.late).length;
  const change = s.last_month_revenue > 0
    ? Math.round(((s.monthly_revenue - s.last_month_revenue) / s.last_month_revenue) * 100) : null;

  container.innerHTML = `
    <section class="card kpi-band" aria-label="Key figures">
      ${kpi('Fleet utilisation', `${utilisation}<small>%</small>`, `${inUse} of ${inService} vehicles in use`, '#/vehicles')}
      ${kpi('Active rentals', s.active_rentals, overdue ? `${overdue} overdue` : 'None overdue', '#/rentals', overdue ? 'bad' : '')}
      ${kpi('Active leases', s.active_leases, d.leasesEnding.length ? `${d.leasesEnding.length} ending within 30 days` : 'None ending soon', '#/leases')}
      ${kpi('Revenue this month', compactMoney(s.monthly_revenue), change === null ? money(s.monthly_revenue) : `${change >= 0 ? '▲' : '▼'} ${Math.abs(change)}% vs last month`, '#/payments', change === null ? '' : change >= 0 ? 'good' : 'bad')}
      ${kpi('Receivables', compactMoney(s.receivables), 'Still owed on rentals & leases', '#/rentals', '')}
    </section>

    <div class="dash-grid">
      <section class="card">
        <div class="card-head"><h2 class="card-title">At the desk today</h2>
          <span class="card-sub">Pickups and returns due by end of day</span>
          <a class="more" href="#/rentals">All rentals →</a></div>
        <div class="desk">
          ${deskColumn('Pickups', d.pickups, 'pickup')}
          ${deskColumn('Returns', d.returns, 'return')}
        </div>
      </section>

      <section class="card">
        <div class="card-head"><h2 class="card-title">Fleet</h2><span class="card-sub">${s.total_vehicles} vehicles</span>
          <a class="more" href="#/vehicles">Vehicles →</a></div>
        <div class="card-body">${fleet(s, utilisation)}</div>
      </section>
    </div>

    <div class="dash-grid">
      <section class="card">
        <div class="card-head"><h2 class="card-title">Revenue</h2><span class="card-sub">Successful payments, last 12 months</span>
          <a class="more" href="#/reports">Reports →</a></div>
        <div class="card-body"><div class="chart" id="revenue-chart"></div></div>
      </section>
      <section class="card">
        <div class="card-head"><h2 class="card-title">Needs attention</h2></div>
        <div class="card-body">${attention(d)}</div>
      </section>
    </div>`;

  container.querySelectorAll('[data-desk]').forEach((btn) => {
    btn.onclick = async () => {
      const list = btn.dataset.desk === 'return' ? d.returns : d.pickups;
      const row = list.find((r) => String(r.rental_id) === btn.dataset.id);
      try {
        if (btn.dataset.desk === 'noshow') await cancelRental(row, reload);
        else (btn.dataset.desk === 'pickup' ? startRental : completeRental)(row, reload);
      } catch (error) {
        toast(error.message, 'error');
      }
    };
  });
  revenueChart(container.querySelector('#revenue-chart'), d.revenue);
}

function kpi(label, value, note, href, tone = '') {
  return `<a class="kpi" href="${href}">
    <div class="kpi-label">${esc(label)}</div>
    <div class="kpi-value">${value}</div>
    <div class="kpi-note ${tone}">${esc(note)}</div></a>`;
}

function deskColumn(title, rows, kind) {
  const when = (r) => (kind === 'pickup' ? r.scheduled_start_datetime : r.expected_return_datetime);
  const items = rows.map((r) => {
    const at = when(r);
    const late = Boolean(r.late);
    const noShow = kind === 'pickup' && Boolean(r.expired);
    const note = noShow ? 'no-show' : late ? 'late' : daysFromToday(at) === 0 ? 'today' : relativeDay(at);
    const timeHtml = daysFromToday(at) === 0
      ? `${fmtTime(at)}<small>${note}</small>`
      : `${fmtDate(at).slice(0, -5)}<small>${note}</small>`;
    const button = noShow
      ? `<button class="btn sm danger" data-desk="noshow" data-id="${r.rental_id}">Cancel</button>`
      : `<button class="btn sm ${kind === 'pickup' ? 'accent' : ''}" data-desk="${kind}" data-id="${r.rental_id}">${kind === 'pickup' ? 'Hand over' : 'Check in'}</button>`;
    return `<div class="desk-item">
      <div class="desk-time ${late ? 'late' : ''}">${timeHtml}</div>
      <div style="min-width:0">
        <div class="desk-who">${esc(r.customer_name)}</div>
        <div class="desk-what">${esc(r.brand_name)} ${esc(r.model)} · <span class="mono">${esc(r.registration_no)}</span> · #${r.rental_id}</div>
      </div>
      ${button}
    </div>`;
  }).join('');
  return `<div class="desk-col">
    <h3>${title}<span class="count">${rows.length}</span></h3>
    ${items || `<div class="desk-empty">No ${title.toLowerCase()} due today.</div>`}
  </div>`;
}

function fleet(s, utilisation) {
  // Categorical slots 1–4 in fixed order; each row is labelled, so colour is never the only cue.
  const parts = [
    ['Available', s.available_vehicles, 'var(--series-1)'],
    ['Rented', s.rented_vehicles, 'var(--series-2)'],
    ['Leased', s.leased_vehicles, 'var(--series-3)'],
    ['In maintenance', s.maintenance_vehicles, 'var(--series-4)'],
  ];
  if (s.inactive_vehicles) parts.push(['Inactive', s.inactive_vehicles, 'var(--line-strong)']);
  const total = Math.max(1, s.total_vehicles);
  return `<div class="fleet-util"><b>${utilisation}%</b><span>of in-service vehicles are earning</span></div>
    <div class="fleet-bar" role="img" aria-label="${parts.map((p) => `${p[0]} ${p[1]}`).join(', ')}">
      ${parts.filter((p) => p[1] > 0).map((p) => `<span title="${p[0]}: ${p[1]}" style="flex:${p[1] / total};background:${p[2]}"></span>`).join('')}
    </div>
    <ul class="fleet-list">${parts.map((p) => `<li><i style="background:${p[2]}"></i>${p[0]}<b>${p[1]}</b><span class="pct">${Math.round((p[1] / total) * 100)}%</span></li>`).join('')}</ul>`;
}

function attention(d) {
  const items = [
    ...d.maintenanceDue.map((m) => ({ tone: 'warning', ico: 'wrench',
      title: `${m.service_type} due ${relativeDay(m.next_service_date)}`,
      meta: `${m.model} · ${m.registration_no} · ${fmtDate(m.next_service_date)}`, href: '#/maintenance' })),
    ...d.leasesEnding.map((l) => ({ tone: 'info', ico: 'calendar',
      title: `Lease #${l.lease_id} ends ${relativeDay(l.end_date)}`,
      meta: `${l.customer_name} · ${l.model} · ${fmtDate(l.end_date)}`, href: '#/leases' })),
    ...d.licences.map((c) => ({ tone: 'warning', ico: 'idcard',
      title: `${c.customer_name}'s licence expires ${relativeDay(c.license_expiry)}`,
      meta: `Customer #${c.customer_id} · ${fmtDate(c.license_expiry)}`, href: '#/customers' })),
  ];
  if (!items.length) return `<div class="all-clear">${icon('checkCircle')} Nothing needs attention. Services, leases and licences are all in order.</div>`;
  return `<ul class="attention">${items.map((i) => `<li>
    <span class="ico ${i.tone}">${icon(i.ico)}</span>
    <div><a class="title" href="${i.href}" style="color:inherit">${esc(i.title)}</a><div class="meta">${esc(i.meta)}</div></div></li>`).join('')}</ul>`;
}

/** Single-series bar chart with per-bar hover tooltips and a label on the current month. */
function revenueChart(el, rows) {
  const byMonth = Object.fromEntries(rows.map((r) => [r.month, Number(r.revenue)]));
  const months = [];
  const now = new Date();
  for (let i = 11; i >= 0; i--) {
    const dt = new Date(now.getFullYear(), now.getMonth() - i, 1);
    const key = `${dt.getFullYear()}-${String(dt.getMonth() + 1).padStart(2, '0')}`;
    months.push({ key, value: byMonth[key] || 0 });
  }
  const W = 680; const H = 240; const left = 50; const right = 6; const top = 22; const bottom = 26;
  const plotW = W - left - right; const plotH = H - top - bottom;
  const max = niceMax(Math.max(...months.map((m) => m.value), 1));
  const step = plotW / months.length;
  const barW = Math.min(28, step - 12);
  const y = (v) => top + plotH - (v / max) * plotH;
  const base = top + plotH;

  const ticks = [0.25, 0.5, 0.75, 1].map((t) => t * max);
  const grid = ticks.map((t) => `<line class="gridline" x1="${left}" x2="${W - right}" y1="${y(t)}" y2="${y(t)}"/>
    <text class="axis-label" x="${left - 10}" y="${y(t) + 4}" text-anchor="end">${compactMoney(t)}</text>`).join('');

  const bars = months.map((m, i) => {
    const x = left + i * step + (step - barW) / 2;
    const h = (m.value / max) * plotH;
    const r = Math.min(4, h);
    // Rounded at the data end, square on the baseline.
    const path = h > 0 ? `M${x},${base} V${base - h + r} Q${x},${base - h} ${x + r},${base - h}
      H${x + barW - r} Q${x + barW},${base - h} ${x + barW},${base - h + r} V${base} Z` : '';
    const last = i === months.length - 1;
    return `<g data-i="${i}">
      <rect class="hit" x="${left + i * step}" y="${top}" width="${step}" height="${plotH}"/>
      ${path ? `<path class="bar" d="${path}"/>` : ''}
      ${last && m.value ? `<text class="value-label" x="${x + barW / 2}" y="${y(m.value) - 7}" text-anchor="middle">${compactMoney(m.value)}</text>` : ''}
      <text class="axis-label" x="${x + barW / 2}" y="${H - 6}" text-anchor="middle">${monthShort(m.key)}</text></g>`;
  }).join('');

  const empty = months.every((m) => !m.value);
  el.innerHTML = `<svg viewBox="0 0 ${W} ${H}" role="img" aria-label="Revenue by month for the last 12 months">
      ${grid}<line class="baseline" x1="${left}" x2="${W - right}" y1="${base}" y2="${base}"/>
      <text class="axis-label" x="${left - 10}" y="${base + 4}" text-anchor="end">₹0</text>${bars}
      ${empty ? `<text class="axis-label" x="${left + plotW / 2}" y="${top + plotH / 2}" text-anchor="middle">No payments recorded in the last 12 months</text>` : ''}
    </svg><div class="tooltip" hidden></div>`;

  const tip = el.querySelector('.tooltip');
  const svg = el.querySelector('svg');
  const clear = () => {
    tip.hidden = true;
    svg.querySelectorAll('.bar.dim').forEach((b) => b.classList.remove('dim'));
  };
  svg.addEventListener('mousemove', (e) => {
    const g = e.target.closest('g[data-i]');
    if (!g) { clear(); return; }
    const i = Number(g.dataset.i);
    const m = months[i];
    svg.querySelectorAll('g[data-i] .bar').forEach((b) => b.classList.toggle('dim', b.parentNode !== g));
    const scale = svg.getBoundingClientRect().width / W;
    tip.innerHTML = `${monthLabel(m.key)}<b>${money(m.value)}</b>`;
    tip.style.left = `${(left + i * step + step / 2) * scale}px`;
    tip.style.top = `${y(m.value) * scale}px`;
    tip.hidden = false;
  });
  svg.addEventListener('mouseleave', clear);
}

function niceMax(value) {
  const magnitude = 10 ** Math.floor(Math.log10(value));
  const n = value / magnitude;
  return (n <= 1 ? 1 : n <= 2 ? 2 : n <= 2.5 ? 2.5 : n <= 5 ? 5 : 10) * magnitude;
}
