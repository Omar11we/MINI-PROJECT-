// Contract pages: rentals, leases, payments and maintenance.
import {
  api, esc, money, fmtDate, fmtDateTime, pill, icon, setHeader, listPage, openForm,
  confirmDialog, toast, lookups, invalidateLookups, localInput, today,
} from './core.js';

const customerLabel = (c) => `${c.name}${c.company_name ? ` — ${c.company_name}` : ''} · ${c.type}`;
const balanceCell = (due) => (due === null || due === undefined ? '<span class="muted">—</span>'
  : Number(due) > 0 ? `<span class="owed">${money(due)}</span>` : '<span class="paid">Paid</span>');
const vehicleCell = (r) => `<div class="cell-main">${esc(r.model)}</div><div class="cell-sub mono">${esc(r.registration_no)}</div>`;

function debounce(fn, ms = 300) {
  let timer;
  return (...args) => { clearTimeout(timer); timer = setTimeout(() => fn(...args), ms); };
}

// ---------- Rentals ----------

export function renderRentals(container) {
  setHeader('Rentals', 'Reservations, walk-ins, hand-overs and returns',
    `<button class="btn primary" id="new-rental">${icon('plus')} New rental</button>`);
  const page = listPage(container, {
    endpoint: '/rentals',
    searchPlaceholder: 'Search by #, customer or registration',
    emptyText: 'Create a reservation or walk-in to get started.',
    defaultSort: 'id',
    defaultDir: 'desc',
    tabs: { param: 'status', values: ['Confirmed', 'Active', 'Completed', 'Cancelled'] },
    filters: [{ name: 'outstanding', type: 'checkbox', label: 'Balance due only' }],
    columns: [
      { label: 'Rental', sort: 'id', render: (r) => `<span class="id">#${r.rental_id}</span>` },
      { label: 'Customer', sort: 'customer', render: (r) => `<div class="cell-main">${esc(r.customer_name)}</div><div class="cell-sub">${esc(r.customer_type)}${r.driver_name ? ` · chauffeur ${esc(r.driver_name)}` : ''}</div>` },
      { label: 'Vehicle', sort: 'vehicle', render: vehicleCell },
      { label: 'Pickup', sort: 'start', render: (r) => `${fmtDateTime(r.actual_pickup_datetime || r.scheduled_start_datetime)}<div class="cell-sub">${r.actual_pickup_datetime ? 'picked up' : 'scheduled'}</div>` },
      { label: 'Return', sort: 'expected', render: (r) => `${fmtDateTime(r.actual_return_datetime || r.expected_return_datetime)}<div class="cell-sub">${r.actual_return_datetime ? 'returned' : 'expected'}</div>` },
      { label: 'Status', sort: 'status', render: (r) => (r.overdue ? pill('Overdue') : r.expired ? pill('No-show', 'red') : pill(r.status)) },
      { label: 'Charges', num: true, render: (r) => money(r.charge_total) },
      { label: 'Balance', sort: 'balance', num: true, render: (r) => balanceCell(r.balance_due) },
    ],
    actions: (r) => [
      ...(r.status === 'Confirmed' && !r.expired ? [{ label: 'Hand over', act: 'start', tone: 'accent' }] : []),
      ...(r.status === 'Confirmed' ? [{ label: 'Cancel', act: 'cancel', tone: 'danger' }] : []),
      ...(r.status === 'Active' ? [{ label: 'Check in', act: 'complete', tone: 'accent' }] : []),
      ...(Number(r.balance_due) > 0 ? [{ label: 'Take payment', act: 'pay' }] : []),
    ],
    onAction: async (act, row, reload) => {
      if (act === 'start') startRental(row, reload);
      if (act === 'complete') completeRental(row, reload);
      if (act === 'pay') paymentForm({ kind: 'rental', id: row.rental_id }, reload);
      if (act === 'cancel') await cancelRental(row, reload);
    },
  });
  document.getElementById('new-rental').onclick = () => rentalForm(page.reload);
}

export async function rentalForm(reload) {
  const lk = await lookups(true);
  const customers = lk.customers.map((c) => ({ value: c.id, label: customerLabel(c) }));
  const corporate = new Set(lk.customers.filter((c) => c.type === 'Corporate').map((c) => String(c.id)));
  const drivers = lk.drivers.filter((d) => d.status === 'Active').map((d) => ({ value: d.id, label: d.name }));
  const isWalkIn = (v) => v.mode === 'walkin';
  let lastRange = '';
  let requestNo = 0;

  // Re-list free vehicles whenever the period changes, then refresh the price estimate.
  const update = debounce(async (values, form) => {
    const mine = ++requestNo;
    const from = isWalkIn(values) ? localInput() : values.scheduled_start;
    const to = values.expected_return;
    if (!from || !to || to <= from) {
      form.html('available', '<div class="hint">Choose a return time after the start to see free vehicles.</div>');
      form.html('quote', '');
      return;
    }
    const range = `${from}|${to}`;
    if (range !== lastRange) {
      try {
        const res = await api.get('/reports/available', { from, to });
        if (mine !== requestNo) return;
        lastRange = range;
        const free = res.rows.filter((v) => v.rental_rate !== null).map((v) => ({
          value: v.vehicle_id, label: `${v.brand_name} ${v.model} · ${v.registration_no} · ${money(v.rental_rate)}/day`,
        }));
        form.setOptions('vehicle_id', free, free.length ? 'Choose a vehicle…' : 'No vehicles free for this period');
        form.html('available', `<div class="hint">${free.length} vehicle${free.length === 1 ? '' : 's'} free for the whole period.</div>`);
      } catch (error) {
        form.html('available', `<div class="hint">${esc(error.message)}</div>`);
      }
    }
    const vehicleId = form.form.elements.vehicle_id.value;
    if (!vehicleId) { form.html('quote', ''); return; }
    const q = await api.get('/quote', { vehicleId, from, to }).catch(() => ({}));
    if (mine !== requestNo) return;
    form.html('quote', q.amount !== null && q.amount !== undefined
      ? `<div class="quote"><span>Estimated charge</span><b>${money(q.amount)}</b><span>${money(q.rental_rate)} per started 24 h</span></div>` : '');
  });

  openForm({
    title: 'New rental',
    subtitle: 'Only vehicles free for the whole period are listed.',
    submitLabel: 'Create rental',
    values: { mode: 'reservation', scheduled_start: localInput(24), expected_return: localInput(72) },
    fields: [
      { name: 'mode', type: 'segmented', span: 2, options: [{ value: 'reservation', label: 'Reservation' }, { value: 'walkin', label: 'Walk-in · starts now' }] },
      { name: 'customer_id', label: 'Customer', type: 'select', options: customers, required: true, span: 2 },
      { name: 'scheduled_start', label: 'Pickup', type: 'datetime-local', required: true, showIf: (v) => !isWalkIn(v) },
      { name: 'expected_return', label: 'Expected return', type: 'datetime-local', required: true },
      { name: 'vehicle_id', label: 'Vehicle', type: 'select', options: [], required: true, span: 2, placeholder: 'Loading free vehicles…' },
      { name: 'available', type: 'html', span: 2 },
      { name: 'driver_id', label: 'Chauffeur', type: 'select', options: drivers, span: 2, placeholder: 'Self-drive (no chauffeur)', hint: 'Corporate bookings must have a chauffeur.' },
      { name: 'odometer_out', label: 'Odometer at pickup (km)', type: 'number', min: 0, required: true, showIf: isWalkIn },
      { name: 'condition_out', label: 'Condition at pickup', required: true, showIf: isWalkIn, placeholder: 'e.g. Spotless, full tank' },
      { name: 'quote', type: 'html', span: 2 },
    ],
    onChange: (values, form) => update(values, form),
    onSubmit: async (values) => {
      if (corporate.has(values.customer_id) && !values.driver_id) {
        throw new Error('This is a corporate customer, so choose a chauffeur.');
      }
      const { id } = await api.post('/rentals', values);
      toast(values.mode === 'walkin' ? `Walk-in rental #${id} is out on the road.` : `Reservation #${id} confirmed.`);
      invalidateLookups();
      reload();
    },
  });
}

export async function cancelRental(row, reload) {
  const message = row.expired
    ? `The booking window for rental #${row.rental_id} has passed and ${row.customer_name} never collected the vehicle. Cancel it to release the booking?`
    : `Cancel rental #${row.rental_id} for ${row.customer_name}? The reservation will be released.`;
  if (await confirmDialog(message, { confirmLabel: 'Cancel rental', danger: true, title: row.expired ? 'Cancel this no-show?' : 'Cancel this rental?' })) {
    await api.post(`/rentals/${row.rental_id}/cancel`);
    toast(`Rental #${row.rental_id} cancelled.`);
    reload();
  }
}

export function startRental(row, reload) {
  openForm({
    title: `Hand over rental #${row.rental_id}`,
    subtitle: `${row.customer_name} · ${row.model} ${row.registration_no}`,
    submitLabel: 'Hand over vehicle',
    values: { pickup: localInput() },
    fields: [
      { name: 'pickup', label: 'Pickup time', type: 'datetime-local', required: true },
      { name: 'odometer_out', label: 'Odometer (km)', type: 'number', min: 0, required: true },
      { name: 'condition_out', label: 'Condition at pickup', required: true, span: 2, placeholder: 'e.g. Spotless, full tank' },
    ],
    onSubmit: async (values) => {
      await api.post(`/rentals/${row.rental_id}/start`, values);
      toast(`Rental #${row.rental_id} handed over.`);
      reload();
    },
  });
}

export function completeRental(row, reload) {
  openForm({
    title: `Check in rental #${row.rental_id}`,
    subtitle: `${row.customer_name} · ${row.model} ${row.registration_no} · out at ${row.odometer_out} km`,
    submitLabel: 'Complete return',
    values: { return_at: localInput() },
    fields: [
      { name: 'return_at', label: 'Return time', type: 'datetime-local', required: true },
      { name: 'odometer_in', label: 'Odometer (km)', type: 'number', min: row.odometer_out, required: true, hint: `At least ${row.odometer_out} km` },
      { name: 'condition_in', label: 'Condition at return', required: true, span: 2 },
    ],
    onSubmit: async (values) => {
      const result = await api.post(`/rentals/${row.rental_id}/complete`, values);
      toast(`Rental #${row.rental_id} returned. Final charge ${money(result.charges)}.`);
      invalidateLookups();
      reload();
    },
  });
}

// ---------- Leases ----------

export function renderLeases(container) {
  setHeader('Leases', 'Long-term leases, from scheduling to hand-back',
    `<button class="btn primary" id="new-lease">${icon('plus')} New lease</button>`);
  const page = listPage(container, {
    endpoint: '/leases',
    searchPlaceholder: 'Search by #, customer or registration',
    emptyText: 'Schedule a lease to get started.',
    defaultSort: 'id',
    defaultDir: 'desc',
    tabs: { param: 'status', values: ['Scheduled', 'Active', 'Completed', 'Terminated'] },
    filters: [{ name: 'outstanding', type: 'checkbox', label: 'Balance due only' }],
    columns: [
      { label: 'Lease', sort: 'id', render: (r) => `<span class="id">#${r.lease_id}</span>` },
      { label: 'Customer', sort: 'customer', render: (r) => `<span class="cell-main">${esc(r.customer_name)}</span>` },
      { label: 'Vehicle', sort: 'vehicle', render: vehicleCell },
      { label: 'Term', sort: 'start', render: (r) => `${fmtDate(r.start_date)}<div class="cell-sub">to ${fmtDate(r.end_date)}</div>` },
      { label: 'Monthly', sort: 'monthly', num: true, render: (r) => money(r.monthly_charge) },
      { label: 'Deposit', num: true, render: (r) => money(r.deposit) },
      { label: 'Status', sort: 'status', render: (r) => pill(r.status) },
      { label: 'Balance', sort: 'balance', num: true, render: (r) => balanceCell(r.balance_due) },
    ],
    actions: (r) => [
      ...(r.status === 'Scheduled' ? [{ label: 'Start', act: 'start', tone: 'accent' }] : []),
      ...(r.status === 'Active' ? [{ label: 'Complete', act: 'Completed', tone: 'accent' }] : []),
      ...(['Scheduled', 'Active'].includes(r.status) ? [{ label: 'Terminate', act: 'Terminated', tone: 'danger' }] : []),
      ...(Number(r.balance_due) > 0 ? [{ label: 'Take payment', act: 'pay' }] : []),
    ],
    onAction: async (act, row, reload) => {
      if (act === 'pay') return paymentForm({ kind: 'lease', id: row.lease_id }, reload);
      if (act === 'start' && await confirmDialog(`Start lease #${row.lease_id} for ${row.customer_name}? ${row.registration_no} will be marked Leased.`, { confirmLabel: 'Start lease', title: 'Start this lease?' })) {
        await api.post(`/leases/${row.lease_id}/start`);
        toast(`Lease #${row.lease_id} is now active.`);
        invalidateLookups();
        reload();
      }
      if ((act === 'Completed' || act === 'Terminated')
        && await confirmDialog(`Mark lease #${row.lease_id} as ${act.toLowerCase()}?${row.status === 'Active' ? ` ${row.registration_no} will become available again.` : ''}`,
          { confirmLabel: act === 'Completed' ? 'Complete lease' : 'Terminate lease', danger: act === 'Terminated', title: act === 'Completed' ? 'Complete this lease?' : 'Terminate this lease?' })) {
        await api.post(`/leases/${row.lease_id}/finish`, { status: act });
        toast(`Lease #${row.lease_id} ${act.toLowerCase()}.`);
        invalidateLookups();
        reload();
      }
    },
  });
  document.getElementById('new-lease').onclick = () => leaseForm(page.reload);
}

async function leaseForm(reload) {
  const lk = await lookups(true);
  const customers = lk.customers.map((c) => ({ value: c.id, label: customerLabel(c) }));
  const vehicles = lk.vehicles.filter((v) => v.lease_rate !== null && !['Maintenance', 'Inactive'].includes(v.status))
    .map((v) => ({ value: v.id, label: `${v.name} · ${v.registration_no} · ${money(v.lease_rate)}/month` }));

  const updateQuote = debounce(async (values, form) => {
    if (!values.vehicle_id || !values.start_date || !values.end_date || values.end_date <= values.start_date) {
      form.html('quote', '');
      return;
    }
    const q = await api.get('/quote', { kind: 'lease', vehicleId: values.vehicle_id, from: values.start_date, to: values.end_date }).catch(() => ({}));
    form.html('quote', q.amount !== null && q.amount !== undefined
      ? `<div class="quote"><span>Lease total</span><b>${money(q.amount)}</b><span>${money(q.lease_rate)} per whole month</span></div>` : '');
  });

  openForm({
    title: 'New lease',
    subtitle: 'The vehicle is checked for clashes across the whole term.',
    submitLabel: 'Schedule lease',
    values: { start_date: today(1), end_date: today(366), deposit: 0 },
    fields: [
      { name: 'customer_id', label: 'Customer', type: 'select', options: customers, required: true, span: 2 },
      { name: 'vehicle_id', label: 'Vehicle', type: 'select', options: vehicles, required: true, span: 2 },
      { name: 'start_date', label: 'Start date', type: 'date', required: true },
      { name: 'end_date', label: 'End date', type: 'date', required: true },
      { name: 'deposit', label: 'Deposit (₹)', type: 'number', min: 0, step: '0.01', required: true },
      { name: 'quote', type: 'html', span: 2 },
    ],
    onChange: (values, form) => updateQuote(values, form),
    onSubmit: async (values) => {
      const { id } = await api.post('/leases', values);
      toast(`Lease #${id} scheduled.`);
      reload();
    },
  });
}

// ---------- Payments ----------

export function renderPayments(container) {
  setHeader('Payments', 'Money received against rentals and leases',
    `<button class="btn primary" id="new-payment">${icon('plus')} Record payment</button>`);
  const page = listPage(container, {
    endpoint: '/payments',
    searchPlaceholder: 'Search by payment #, customer, rental or lease #',
    defaultSort: 'date',
    defaultDir: 'desc',
    tabs: { param: 'status', values: ['Success', 'Pending', 'Failed', 'Refunded'] },
    filters: [{ name: 'method', label: 'Methods', options: ['Cash', 'Card', 'Bank Transfer', 'UPI'] }],
    columns: [
      { label: 'Payment', sort: 'id', render: (r) => `<span class="id">#${r.payment_id}</span>` },
      { label: 'Date', sort: 'date', render: (r) => fmtDateTime(r.payment_date) },
      { label: 'Customer', sort: 'customer', render: (r) => `<span class="cell-main">${esc(r.customer_name)}</span>` },
      { label: 'Against', render: (r) => (r.rental_id ? `Rental <span class="id">#${r.rental_id}</span>` : `Lease <span class="id">#${r.lease_id}</span>`) },
      { label: 'Method', sort: 'method', key: 'method' },
      { label: 'Amount', sort: 'amount', num: true, render: (r) => `<span class="cell-main">${money(r.amount, true)}</span>` },
      { label: 'Status', sort: 'status', render: (r) => pill(r.status) },
    ],
    actions: (r) => (r.status === 'Success' ? [{ label: 'Refund', act: 'refund', tone: 'danger' }] : []),
    onAction: async (act, row, reload) => {
      if (act === 'refund' && await confirmDialog(`Refund ${money(row.amount, true)} to ${row.customer_name} (payment #${row.payment_id})? The amount becomes owed again.`, { confirmLabel: 'Refund', danger: true, title: 'Refund this payment?' })) {
        await api.post(`/payments/${row.payment_id}/refund`);
        toast(`Payment #${row.payment_id} refunded.`);
        reload();
      }
    },
  });
  document.getElementById('new-payment').onclick = () => paymentForm(null, page.reload);
}

export async function paymentForm(preset, reload) {
  const billable = await api.get('/payments/billable');
  const option = (kind) => (b) => ({
    value: b.id,
    label: `${kind} #${b.id} · ${b.customer_name} · ${b.registration_no} · ${Number(b.balance_due) > 0 ? `${money(b.balance_due, true)} due` : 'fully paid'}`,
  });
  const dueFor = (kind, id) => (kind === 'lease' ? billable.leases : billable.rentals).find((b) => String(b.id) === String(id));
  let lastRef = '';

  const values = { kind: preset?.kind || 'rental', method: 'UPI', status: 'Success' };
  if (preset) {
    values[`${preset.kind}_id`] = preset.id;
    values.amount = Math.max(0, Number(dueFor(preset.kind, preset.id)?.balance_due || 0)).toFixed(2);
    lastRef = `${preset.kind}:${preset.id}`;
  }

  openForm({
    title: 'Record payment',
    subtitle: 'Payments cannot exceed what is still owed.',
    submitLabel: 'Record payment',
    values,
    fields: [
      { name: 'kind', type: 'segmented', span: 2, options: [{ value: 'rental', label: 'Rental' }, { value: 'lease', label: 'Lease' }] },
      { name: 'rental_id', label: 'Rental', type: 'select', options: billable.rentals.map(option('Rental')), required: true, span: 2, showIf: (v) => v.kind === 'rental', hint: 'Active and completed rentals, those with a balance first' },
      { name: 'lease_id', label: 'Lease', type: 'select', options: billable.leases.map(option('Lease')), required: true, span: 2, showIf: (v) => v.kind === 'lease', hint: 'Active and completed leases, those with a balance first' },
      { name: 'amount', label: 'Amount (₹)', type: 'number', min: 0.01, step: '0.01', required: true },
      { name: 'method', label: 'Method', type: 'select', options: ['UPI', 'Card', 'Cash', 'Bank Transfer'], placeholder: false, required: true },
      { name: 'status', label: 'Status', type: 'select', options: ['Success', 'Pending', 'Failed'], placeholder: false, required: true },
      { name: 'payment_date', label: 'Received at', type: 'datetime-local', hint: 'Leave empty for now' },
    ],
    onChange: (v, form) => {
      // Prefill the amount with the balance due whenever the reference changes.
      const id = v.kind === 'lease' ? v.lease_id : v.rental_id;
      const ref = `${v.kind}:${id}`;
      if (id && ref !== lastRef) {
        lastRef = ref;
        const due = dueFor(v.kind, id);
        if (due) form.form.elements.amount.value = Math.max(0, Number(due.balance_due)).toFixed(2);
      }
    },
    onSubmit: async (v) => {
      const body = { amount: v.amount, method: v.method, status: v.status, payment_date: v.payment_date };
      body[`${v.kind}_id`] = v.kind === 'lease' ? v.lease_id : v.rental_id;
      const { id } = await api.post('/payments', body);
      toast(`Payment #${id} of ${money(v.amount, true)} recorded.`);
      reload();
    },
  });
}

// ---------- Maintenance ----------

export function renderMaintenance(container) {
  setHeader('Maintenance', 'Servicing and repairs. A vehicle in service cannot be booked.',
    `<button class="btn primary" id="new-maintenance">${icon('plus')} Send for service</button>`);
  const page = listPage(container, {
    endpoint: '/maintenance',
    searchPlaceholder: 'Search by registration or service type',
    defaultSort: 'serviceDate',
    defaultDir: 'desc',
    tabs: { param: 'status', values: ['In Progress', 'Complete'] },
    columns: [
      { label: 'Job', sort: 'id', render: (r) => `<span class="id">#${r.maintenance_id}</span>` },
      { label: 'Vehicle', sort: 'vehicle', render: vehicleCell },
      { label: 'Service', sort: 'type', render: (r) => `<div class="cell-main">${esc(r.service_type)}</div><div class="cell-sub">${esc(r.description || '')}</div>` },
      { label: 'Service date', sort: 'serviceDate', render: (r) => fmtDate(r.service_date) },
      { label: 'Next service', sort: 'next', render: (r) => fmtDate(r.next_service_date) },
      { label: 'Cost', sort: 'cost', num: true, render: (r) => money(r.cost) },
      { label: 'Status', sort: 'status', render: (r) => `${pill(r.status)}${r.completed_date ? `<div class="cell-sub">done ${fmtDate(r.completed_date)}</div>` : ''}` },
    ],
    actions: (r) => (r.status === 'In Progress' ? [{ label: 'Mark complete', act: 'complete', tone: 'accent' }] : []),
    onAction: (act, row, reload) => {
      if (act !== 'complete') return;
      openForm({
        title: `Complete job #${row.maintenance_id}`,
        subtitle: `${row.model} ${row.registration_no} will become available again.`,
        submitLabel: 'Mark complete',
        values: { completed_date: today() },
        fields: [{ name: 'completed_date', label: 'Completed on', type: 'date', required: true }],
        onSubmit: async (v) => {
          await api.post(`/maintenance/${row.maintenance_id}/complete`, v);
          toast(`${row.registration_no} is back in service.`);
          invalidateLookups();
          reload();
        },
      });
    },
  });
  document.getElementById('new-maintenance').onclick = () => maintenanceForm(page.reload);
}

async function maintenanceForm(reload) {
  const lk = await lookups(true);
  const vehicles = lk.vehicles.filter((v) => ['Available', 'Reserved'].includes(v.status))
    .map((v) => ({ value: v.id, label: `${v.name} · ${v.registration_no}` }));
  openForm({
    title: 'Send a vehicle for service',
    subtitle: 'Rented, leased and inactive vehicles are not listed.',
    submitLabel: 'Open job',
    values: { service_date: today() },
    fields: [
      { name: 'vehicle_id', label: 'Vehicle', type: 'select', options: vehicles, required: true, span: 2 },
      { name: 'service_type', label: 'Service type', required: true, placeholder: 'e.g. Brake inspection' },
      { name: 'service_date', label: 'Service date', type: 'date', required: true },
      { name: 'cost', label: 'Cost (₹)', type: 'number', min: 0, step: '0.01', required: true },
      { name: 'next_service_date', label: 'Next service due', type: 'date' },
      { name: 'description', label: 'Notes', type: 'textarea', span: 2 },
    ],
    onSubmit: async (v) => {
      const { id, warnings } = await api.post('/maintenance', v);
      toast(`Job #${id} opened. The vehicle is now in maintenance.`);
      if (warnings.length) {
        toast(`${warnings.length} confirmed booking(s) on this vehicle from the service date (#${warnings.map((w) => w.rental_id).join(', #')}). Please rebook them.`, 'error');
      }
      invalidateLookups();
      reload();
    },
  });
}
