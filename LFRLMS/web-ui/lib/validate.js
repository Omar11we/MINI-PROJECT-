// Input checks that mirror lfrlms.util.Validator in the Java app.
const { AppError } = require('./db');

const PATTERNS = {
  email: /^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/,
  phone: /^[0-9]{10}$/,
  pincode: /^[0-9]{6}$/,
  gst: /^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$/,
  registration: /^[A-Z]{2}[0-9]{2}[A-Z]{1,3}[0-9]{4}$/,
  license: /^[A-Z0-9/-]{5,30}$/,
};

const pad = (n) => String(n).padStart(2, '0');

/** Current local time as 'YYYY-MM-DD HH:MM:SS', the format date-times are stored in. */
function nowString(offsetMinutes = 0) {
  const d = new Date(Date.now() + offsetMinutes * 60000);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} `
    + `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

function todayString(offsetDays = 0) {
  return nowString(offsetDays * 1440).slice(0, 10);
}

/** Adds whole days to a 'YYYY-MM-DD' date. */
function addDays(date, days) {
  const [y, m, d] = date.split('-').map(Number);
  const result = new Date(y, m - 1, d + days);
  return `${result.getFullYear()}-${pad(result.getMonth() + 1)}-${pad(result.getDate())}`;
}

const text = (value) => (typeof value === 'string' ? value.trim() : '');
const isText = (value) => text(value).length > 0;
const matches = (value, pattern) => pattern.test(text(value));

/** Accepts 'YYYY-MM-DDTHH:MM' (from datetime-local inputs) and returns a stored date-time string. */
function dateTime(value) {
  const match = /^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2})(:\d{2})?$/.exec(text(value));
  return match ? `${match[1]} ${match[2]}${match[3] || ':00'}` : null;
}

function date(value) {
  return /^\d{4}-\d{2}-\d{2}$/.test(text(value)) ? text(value) : null;
}

/** Parses an optional number; returns null for blank input and NaN for bad input. */
function number(value) {
  if (value === null || value === undefined || value === '') return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : NaN;
}

function id(value) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
}

function normalizeLicense(value) {
  return text(value).replace(/\s+/g, '').toUpperCase();
}

function check(condition, message) {
  if (!condition) {
    throw new AppError(message);
  }
}

module.exports = {
  PATTERNS, nowString, todayString, addDays, text, isText, matches,
  dateTime, date, number, id, normalizeLicense, check,
};
