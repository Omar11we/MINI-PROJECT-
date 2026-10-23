// Master-data pages: vehicles, customers and chauffeurs.
import {
  api, esc, money, fmtDate, fmtDateTime, pill, icon, setHeader, listPage, openForm, openModal,
  confirmDialog, toast, lookups, invalidateLookups, daysFromToday,
} from './core.js';

// ---------- Vehicles ----------

export async function renderVehicles(container) {
  setHeader('Vehicles', 'The fleet across every branch. Select a row for details.',
    `<button class="btn primary" id="add-vehicle">${icon('plus')} Add vehicle</button>`);
  const lk = await lookups();
  const toOptions = (list) => list.map((x) => ({ value: x.id, label: x.name }));

  const page = listPage(container, {
    endpoint: '/vehicles',
    searchPlaceholder: 'Search by registration, model or brand',
    defaultSort: 'id',
    tabs: { param: 'status', values: ['Available', 'Reserved', 'Rented', 'Leased', 'Maintenance', 'Inactive'] },
    filters: [
      { name: 'brandId', label: 'Brands', options: toOptions(lk.brands) },
      { name: 'categoryId', label: 'Categories', options: toOptions(lk.categories) },
      { name: 'branchId', label: 'Branches', options: toOptions(lk.branches) },
    ],
    columns: [
      { label: 'Registration', sort: 'registration', render: (r) => `<span class="mono cell-main">${esc(r.registration_no)}</span>` },
      { label: 'Vehicle', sort: 'brand', render: (r) => `<div class="cell-main">${esc(r.brand_name)} ${esc(r.model)}</div><div class="cell-sub">${esc(r.category_name)} · ${r.model_year}</div>` },
      { label: 'Branch', sort: 'branch', render: (r) => `${esc(r.branch_name)}<div class="cell-sub">${esc(r.branch_city)}</div>` },
      { label: 'Per day', sort: 'rentalRate', num: true, render: (r) => money(r.rental_rate) },
      { label: 'Per month', sort: 'leaseRate', num: true, render: (r) => money(r.lease_rate) },
      { label: 'Status', sort: 'status', render: (r) => pill(r.display_status) },
    ],
    actions: (r) => [
      { label: 'Edit', act: 'edit' },
      ...(r.stored_status === 'Inactive' ? [{ label: 'Reactivate', act: 'reactivate', tone: 'accent' }] : []),
      ...(['Available', 'Maintenance'].includes(r.stored_status) ? [{ label: 'Deactivate', act: 'deactivate' }] : []),
      { label: 'Delete', act: 'delete', tone: 'danger' },
    ],
    onRowClick: (row, reload) => vehicleDetails(row, reload),
    onAction: async (act, row, reload) => {
      if (act === 'edit') return vehicleForm(row, reload);
      if (act === 'deactivate' && await confirmDialog(`${row.registration_no} will stop appearing for new rentals and leases. You can reactivate it later.`, { confirmLabel: 'Deactivate', title: 'Take this vehicle out of service?' })) {
        await api.post(`/vehicles/${row.vehicle_id}/deactivate`);
        toast(`${row.registration_no} deactivated.`);
        invalidateLookups();
        reload();
      }
      if (act === 'reactivate') {
        await api.post(`/vehicles/${row.vehicle_id}/reactivate`);
        toast(`${row.registration_no} is back in service.`);
        invalidateLookups();
        reload();
      }
      if (act === 'delete' && await confirmDialog(`This permanently removes ${row.registration_no}. A vehicle with any rental, lease or service history cannot be deleted. Deactivate it instead.`, { confirmLabel: 'Delete vehicle', danger: true, title: 'Delete this vehicle?' })) {
        await api.del(`/vehicles/${row.vehicle_id}`);
        toast(`${row.registration_no} deleted.`);
        invalidateLookups();
        reload();
      }
    },
  });
  document.getElementById('add-vehicle').onclick = () => vehicleForm(null, page.reload);
}

async function vehicleForm(row, reload) {
  const lk = await lookups();
  const opts = (list) => list.map((x) => ({ value: x.id, label: x.name }));
  openForm({
    title: row ? `Edit ${row.registration_no}` : 'Add a vehicle',
    subtitle: row ? `${row.brand_name} ${row.model}` : 'New vehicles start as Available.',
    values: row || { model_year: new Date().getFullYear() },
    fields: [
      { name: 'registration_no', label: 'Registration number', required: true, placeholder: 'MH12AB1234', hint: 'e.g. MH12AB1234, no spaces needed' },
      { name: 'model', label: 'Model', required: true, placeholder: 'S-Class' },
      { name: 'brand_id', label: 'Brand', type: 'select', options: opts(lk.brands), required: true },
      { name: 'category_id', label: 'Category', type: 'select', options: opts(lk.categories), required: true },
      { name: 'branch_id', label: 'Branch', type: 'select', options: opts(lk.branches), required: true },
      { name: 'model_year', label: 'Model year', type: 'number', min: 2000, max: 2100, required: true },
      { name: 'rental_rate', label: 'Rental rate (₹ per day)', type: 'number', min: 0, step: '0.01', hint: 'Leave empty if not offered for rent' },
      { name: 'lease_rate', label: 'Lease rate (₹ per month)', type: 'number', min: 0, step: '0.01', hint: 'Leave empty if not offered for lease' },
    ],
    onSubmit: async (values) => {
      if (!values.rental_rate && !values.lease_rate) throw new Error('Enter a rental rate, a lease rate, or both.');
      if (row) await api.put(`/vehicles/${row.vehicle_id}`, values);
      else await api.post('/vehicles', values);
      toast(row ? 'Vehicle updated.' : `${values.registration_no.toUpperCase()} added to the fleet.`);
      invalidateLookups();
      reload();
    },
  });
}

async function vehicleDetails(row, reload) {
  const { vehicle: v, upcoming, maintenance } = await api.get(`/vehicles/${row.vehicle_id}`);
  const modal = openModal({
    title: `${v.brand_name} ${v.model}`,
    subtitle: `${v.registration_no} · ${v.category_name} · ${v.model_year}`,
    wide: true,
    body: `<dl class="detail-grid">
        <div><dt>Status</dt><dd>${pill(v.display_status)}</dd></div>
        <div><dt>Branch</dt><dd>${esc(v.branch_name)}, ${esc(v.branch_city)}</dd></div>
        <div><dt>Rates</dt><dd>${money(v.rental_rate)} / day · ${money(v.lease_rate)} / month</dd></div>
      </dl>
      <div class="section-title">Upcoming reservations</div>
      ${upcoming.length ? `<div class="inner-table"><table class="data"><thead><tr><th>Rental</th><th>Customer</th><th>From</th><th>To</th></tr></thead><tbody>
        ${upcoming.map((r) => `<tr><td class="id">#${r.rental_id}</td><td>${esc(r.customer)}</td><td>${fmtDateTime(r.scheduled_start_datetime)}</td><td>${fmtDateTime(r.expected_return_datetime)}</td></tr>`).join('')}
      </tbody></table></div>` : '<div class="muted">No upcoming reservations.</div>'}
      <div class="section-title">Service history</div>
      ${maintenance.length ? `<div class="inner-table"><table class="data"><thead><tr><th>Date</th><th>Service</th><th class="num">Cost</th><th>Status</th></tr></thead><tbody>
        ${maintenance.map((m) => `<tr><td>${fmtDate(m.service_date)}</td><td>${esc(m.service_type)}</td><td class="num">${money(m.cost)}</td><td>${pill(m.status)}</td></tr>`).join('')}
      </tbody></table></div>` : '<div class="muted">No service records yet.</div>'}`,
    footer: `<button class="btn" data-edit>Edit vehicle</button>`,
  });
  modal.el.querySelector('[data-edit]').onclick = () => { modal.close(); vehicleForm(row, reload); };
}

// ---------- Customers ----------

export function renderCustomers(container) {
  setHeader('Customers', 'Individuals and corporate accounts. Select a row for history.',
    `<button class="btn primary" id="add-customer">${icon('plus')} Add customer</button>`);
  const page = listPage(container, {
    endpoint: '/customers',
    searchPlaceholder: 'Search by name, email, phone or company',
    defaultSort: 'id',
    tabs: { param: 'type', values: ['Individual', 'Corporate'] },
    filters: [{ name: 'expiring', type: 'checkbox', label: 'Licence expiring in 30 days' }],
    columns: [
      { label: 'Customer', sort: 'name', render: (r) => `<div class="cell-main">${esc(r.first_name)} ${esc(r.last_name)}</div><div class="cell-sub">${esc(r.company_name || r.email)}</div>` },
      { label: 'Type', sort: 'type', render: (r) => `<span class="tag">${esc(r.customer_type)}</span>` },
      { label: 'Phone', render: (r) => `<span class="mono">${esc(r.phone)}</span>` },
      { label: 'City', sort: 'city', key: 'city' },
      { label: 'Licence or GST', sort: 'licenseExpiry', render: licenceCell },
      { label: 'Owes', num: true, render: (r) => (Number(r.outstanding) > 0 ? `<span class="owed">${money(r.outstanding)}</span>` : '<span class="muted">—</span>') },
    ],
    actions: () => [{ label: 'Edit', act: 'edit' }, { label: 'Delete', act: 'delete', tone: 'danger' }],
    onRowClick: (row, reload) => customerDetails(row, reload),
    onAction: async (act, row, reload) => {
      if (act === 'edit') return customerForm(row, reload);
      if (act === 'delete' && await confirmDialog(`This permanently removes ${row.first_name} ${row.last_name}. Customers with any rental or lease history cannot be deleted.`, { confirmLabel: 'Delete customer', danger: true, title: 'Delete this customer?' })) {
        await api.del(`/customers/${row.customer_id}`);
        toast('Customer deleted.');
        invalidateLookups();
        reload();
      }
    },
  });
  document.getElementById('add-customer').onclick = () => customerForm(null, page.reload);
}

function licenceCell(r) {
  if (r.customer_type !== 'Individual') return `<span class="mono">${esc(r.gst_no)}</span><div class="cell-sub">GSTIN</div>`;
  if (!r.license_expiry) return `<span class="mono">${esc(r.license_no)}</span>`;
  const days = daysFromToday(r.license_expiry);
  const note = days < 0 ? '<span class="owed">expired</span>' : days <= 30 ? `<span class="owed">expires in ${days} days</span>` : `expires ${fmtDate(r.license_expiry)}`;
  return `<span class="mono">${esc(r.license_no)}</span><div class="cell-sub">${note}</div>`;
}

function customerForm(row, reload) {
  const isIndividual = (v) => v.customer_type !== 'Corporate';
  openForm({
    title: row ? `Edit ${row.first_name} ${row.last_name}` : 'Add a customer',
    values: row || { customer_type: 'Individual' },
    fields: [
      { name: 'customer_type', label: 'Customer type', type: 'segmented', span: 2,
        options: [{ value: 'Individual', label: 'Individual' }, { value: 'Corporate', label: 'Corporate' }] },
      { name: 'first_name', label: 'First name', required: true, autocomplete: 'given-name' },
      { name: 'last_name', label: 'Last name', required: true, autocomplete: 'family-name' },
      { name: 'company_name', label: 'Company name', required: true, showIf: (v) => !isIndividual(v) },
      { name: 'gst_no', label: 'GST number', required: true, maxlength: 15, placeholder: '27ABCDE1234F1Z5', showIf: (v) => !isIndividual(v) },
      { name: 'email', label: 'Email', type: 'email', required: true },
      { name: 'phone', label: 'Mobile number', required: true, maxlength: 10, placeholder: '10 digits' },
      { name: 'street', label: 'Street address', required: true, span: 2 },
      { name: 'city', label: 'City', required: true },
      { name: 'pincode', label: 'Pincode', required: true, maxlength: 6, placeholder: '6 digits' },
      { name: 'date_of_birth', label: 'Date of birth', type: 'date', required: true, showIf: isIndividual },
      { name: 'license_no', label: 'Driving licence number', required: true, showIf: isIndividual, placeholder: 'MH12 20200001234' },
      { name: 'license_expiry', label: 'Licence valid until', type: 'date', required: true, showIf: isIndividual, hint: 'Must cover the end of any rental or lease' },
    ],
    onSubmit: async (values) => {
      if (row) await api.put(`/customers/${row.customer_id}`, values);
      else await api.post('/customers', values);
      toast(row ? 'Customer updated.' : `${values.first_name} ${values.last_name} added.`);
      invalidateLookups();
      reload();
    },
  });
}

async function customerDetails(row, reload) {
  const { customer: c, history } = await api.get(`/customers/${row.customer_id}`);
  const modal = openModal({
    title: `${c.first_name} ${c.last_name}`,
    subtitle: `${c.customer_type}${c.company_name ? ` · ${c.company_name}` : ''} · customer #${c.customer_id}`,
    wide: true,
    body: `<dl class="detail-grid">
        <div><dt>Email</dt><dd>${esc(c.email)}</dd></div>
        <div><dt>Mobile</dt><dd class="mono">${esc(c.phone)}</dd></div>
        <div><dt>Address</dt><dd>${esc(c.street)}, ${esc(c.city)} ${esc(c.pincode)}</dd></div>
        ${c.customer_type === 'Individual'
    ? `<div><dt>Licence</dt><dd class="mono">${esc(c.license_no)}</dd></div><div><dt>Licence valid until</dt><dd>${fmtDate(c.license_expiry)}</dd></div>`
    : `<div><dt>GST number</dt><dd class="mono">${esc(c.gst_no)}</dd></div><div></div>`}
        <div><dt>Outstanding balance</dt><dd class="${Number(c.outstanding) > 0 ? 'owed' : 'paid'}">${money(c.outstanding, true)}</dd></div>
      </dl>
      <div class="section-title">History</div>
      ${history.length ? `<div class="inner-table table-wrap"><table class="data"><thead><tr><th>Type</th><th>Ref</th><th>Date</th><th>Vehicle</th><th>Status</th><th class="num">Amount</th></tr></thead><tbody>
        ${history.map((h) => `<tr><td>${esc(h.type)}</td><td class="id">#${h.ref}</td><td>${fmtDateTime(h.date)}</td><td class="mono">${esc(h.registration_no)}</td><td>${pill(h.status)}</td><td class="num">${money(h.amount)}</td></tr>`).join('')}
      </tbody></table></div>` : '<div class="muted">No rentals, leases or payments yet.</div>'}`,
    footer: '<button class="btn" data-edit>Edit customer</button>',
  });
  modal.el.querySelector('[data-edit]').onclick = () => { modal.close(); customerForm(row, reload); };
}

// ---------- Chauffeurs ----------

export function renderDrivers(container) {
  setHeader('Chauffeurs', 'Drivers for chauffeured bookings. Every corporate rental needs one.',
    `<button class="btn primary" id="add-driver">${icon('plus')} Add chauffeur</button>`);
  const page = listPage(container, {
    endpoint: '/drivers',
    searchPlaceholder: 'Search by name, phone or licence',
    defaultSort: 'id',
    tabs: { param: 'status', values: ['Active', 'Inactive'] },
    columns: [
      { label: 'Name', sort: 'name', render: (r) => `<span class="cell-main">${esc(r.first_name)} ${esc(r.last_name)}</span>` },
      { label: 'Mobile', sort: 'phone', render: (r) => `<span class="mono">${esc(r.phone)}</span>` },
      { label: 'Licence', sort: 'license', render: (r) => `<span class="mono">${esc(r.license_no)}</span>` },
      { label: 'Right now', render: (r) => (r.active_rentals ? pill('On a rental', 'blue') : '<span class="muted">Free</span>') },
      { label: 'Status', sort: 'status', render: (r) => pill(r.status, r.status === 'Active' ? 'green' : 'gray') },
    ],
    actions: (r) => [
      { label: 'Edit', act: 'edit' },
      { label: r.status === 'Active' ? 'Deactivate' : 'Activate', act: 'toggle', tone: r.status === 'Active' ? '' : 'accent' },
      { label: 'Delete', act: 'delete', tone: 'danger' },
    ],
    onAction: async (act, row, reload) => {
      if (act === 'edit') return driverForm(row, reload);
      if (act === 'toggle') {
        await api.put(`/drivers/${row.driver_id}`, { ...row, status: row.status === 'Active' ? 'Inactive' : 'Active' });
        toast(`${row.first_name} ${row.last_name} is now ${row.status === 'Active' ? 'inactive' : 'active'}.`);
        invalidateLookups();
        reload();
      }
      if (act === 'delete' && await confirmDialog(`This permanently removes ${row.first_name} ${row.last_name}. Chauffeurs who have driven any rental cannot be deleted. Deactivate them instead.`, { confirmLabel: 'Delete chauffeur', danger: true, title: 'Delete this chauffeur?' })) {
        await api.del(`/drivers/${row.driver_id}`);
        toast('Chauffeur deleted.');
        invalidateLookups();
        reload();
      }
    },
  });
  document.getElementById('add-driver').onclick = () => driverForm(null, page.reload);
}

function driverForm(row, reload) {
  openForm({
    title: row ? `Edit ${row.first_name} ${row.last_name}` : 'Add a chauffeur',
    values: row || { status: 'Active' },
    fields: [
      { name: 'first_name', label: 'First name', required: true },
      { name: 'last_name', label: 'Last name', required: true },
      { name: 'phone', label: 'Mobile number', required: true, maxlength: 10, placeholder: '10 digits' },
      { name: 'license_no', label: 'Licence number', required: true },
      { name: 'status', label: 'Status', type: 'select', options: ['Active', 'Inactive'], placeholder: false, required: true },
    ],
    onSubmit: async (values) => {
      if (row) await api.put(`/drivers/${row.driver_id}`, values);
      else await api.post('/drivers', values);
      toast(row ? 'Chauffeur updated.' : `${values.first_name} ${values.last_name} added.`);
      invalidateLookups();
      reload();
    },
  });
}
