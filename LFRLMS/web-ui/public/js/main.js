// Hash router, top navigation, theme toggle, clock and database status.
import { api, esc, icon } from './core.js';
import { renderDashboard } from './dashboard.js';
import { renderVehicles, renderCustomers, renderDrivers } from './entities.js';
import {
  renderRentals, renderLeases, renderPayments, renderMaintenance,
} from './contracts.js';
import { renderReports } from './reports.js';
import { renderInsights } from './insights.js';

const ROUTES = [
  { path: 'dashboard', label: 'Dashboard', render: renderDashboard },
  { path: 'rentals', label: 'Rentals', render: renderRentals },
  { path: 'leases', label: 'Leases', render: renderLeases },
  { path: 'vehicles', label: 'Vehicles', render: renderVehicles },
  { path: 'customers', label: 'Customers', render: renderCustomers },
  { path: 'payments', label: 'Payments', render: renderPayments },
  { path: 'maintenance', label: 'Maintenance', render: renderMaintenance },
  { path: 'drivers', label: 'Chauffeurs', render: renderDrivers },
  { path: 'reports', label: 'Reports', render: renderReports },
  { path: 'insights', label: 'Insights', render: renderInsights },
];

const nav = document.getElementById('nav');
nav.innerHTML = ROUTES.map((r) => `<a href="#/${r.path}" data-path="${r.path}">${esc(r.label)}</a>`).join('');

let routeNo = 0;
async function route() {
  const mine = ++routeNo;
  const path = location.hash.replace(/^#\/?/, '').split('?')[0] || 'dashboard';
  const target = ROUTES.find((r) => r.path === path) || ROUTES[0];
  nav.querySelectorAll('a').forEach((a) => {
    const on = a.dataset.path === target.path;
    a.classList.toggle('active', on);
    if (on) { a.setAttribute('aria-current', 'page'); a.scrollIntoView({ block: 'nearest', inline: 'nearest' }); } else a.removeAttribute('aria-current');
  });
  // Close any dialog left open on the previous page.
  document.querySelectorAll('.modal-backdrop [data-close]').forEach((button) => button.click());
  // Each visit renders into a fresh element, so a slow earlier page can never paint over this one.
  const view = document.createElement('div');
  document.getElementById('content').replaceChildren(view);
  window.scrollTo(0, 0);
  try {
    await target.render(view);
  } catch (error) {
    if (mine !== routeNo) return;
    view.innerHTML = `<div class="card"><div class="empty"><b>This page could not load</b>${esc(error.message)}</div></div>`;
  }
}
window.addEventListener('hashchange', route);

// Theme: follows the OS until the user picks one (saved in localStorage).
const themeBtn = document.getElementById('theme-btn');
function currentTheme() {
  return document.documentElement.dataset.theme
    || (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
}
function paintThemeButton() {
  const dark = currentTheme() === 'dark';
  themeBtn.innerHTML = icon(dark ? 'sun' : 'moon');
  themeBtn.title = dark ? 'Switch to light mode' : 'Switch to dark mode';
}
themeBtn.onclick = () => {
  const next = currentTheme() === 'dark' ? 'light' : 'dark';
  document.documentElement.dataset.theme = next;
  try { localStorage.setItem('lfrlms-theme', next); } catch { /* storage unavailable */ }
  paintThemeButton();
};
paintThemeButton();

// Clock, like the Swing app's ClockThread.
const clock = document.getElementById('clock');
const tick = () => {
  clock.textContent = new Date().toLocaleString('en-IN', { weekday: 'short', day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' });
};
tick();
setInterval(tick, 15000);

// Database status, checked every 30 seconds.
async function checkDb() {
  const dot = document.getElementById('db-dot');
  const text = document.getElementById('db-text');
  try {
    await api.get('/health');
    dot.className = 'dot ok';
    text.textContent = 'MongoDB live';
  } catch {
    dot.className = 'dot bad';
    text.textContent = 'Database offline';
  }
}
checkDb();
setInterval(checkDb, 30000);

route();
