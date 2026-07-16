// Pure, reusable field validators. Return null when valid, otherwise a human message (Indonesian
// to match the app copy). Used by the Register form; no side effects, easy to unit-test later.

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function required(value, label = 'Kolom ini') {
  return value && String(value).trim().length > 0 ? null : `${label} wajib diisi`;
}

export function minLength(value, n, label = 'Kolom ini') {
  return String(value || '').length >= n ? null : `${label} minimal ${n} karakter`;
}

export function email(value) {
  if (!value || !value.trim()) return 'Email wajib diisi';
  return EMAIL_RE.test(value.trim()) ? null : 'Format email tidak valid';
}

export function username(value) {
  const v = String(value || '').trim();
  if (!v) return 'Username wajib diisi';
  if (v.length < 3) return 'Username minimal 3 karakter';
  if (!/^[a-zA-Z0-9._-]+$/.test(v)) return 'Username hanya boleh huruf, angka, titik, garis';
  return null;
}

export function password(value) {
  const v = String(value || '');
  if (!v) return 'Password wajib diisi';
  if (v.length < 8) return 'Password minimal 8 karakter';
  if (!/[A-Za-z]/.test(v) || !/[0-9]/.test(v)) return 'Password harus mengandung huruf dan angka';
  return null;
}

export function matches(value, other, label = 'Konfirmasi password') {
  return value === other ? null : `${label} tidak cocok`;
}

// Runs a map of { field: validatorFn } and returns { errors, isValid }.
export function runValidators(validators) {
  const errors = {};
  for (const [field, fn] of Object.entries(validators)) {
    const msg = fn();
    if (msg) errors[field] = msg;
  }
  return { errors, isValid: Object.keys(errors).length === 0 };
}
