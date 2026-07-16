// Read-only JWT payload decoder for display purposes (Profile screen). It does NOT verify the
// signature — verification is the backend's job; this only reads non-sensitive claims already
// present on the device to render the profile. Mirrors the base64url decoding used in the auth
// layer, kept here so screens don't reach into the auth module.

const BASE64_CHARS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';

function base64Decode(input) {
  let output = '';
  let buffer = 0;
  let bits = 0;
  for (const char of input) {
    if (char === '=') break;
    const value = BASE64_CHARS.indexOf(char);
    if (value === -1) continue;
    buffer = (buffer << 6) | value;
    bits += 6;
    if (bits >= 8) {
      bits -= 8;
      output += String.fromCharCode((buffer >> bits) & 0xff);
    }
  }
  return output;
}

export function decodeJwtPayload(token) {
  if (!token || typeof token !== 'string') return null;
  try {
    const part = token.split('.')[1];
    if (!part) return null;
    const base64 = part.replace(/-/g, '+').replace(/_/g, '/');
    const binary = base64Decode(base64);
    const json = decodeURIComponent(
      binary
        .split('')
        .map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
        .join('')
    );
    return JSON.parse(json);
  } catch (_) {
    return null;
  }
}

// Seconds since epoch → Date, or null. JWT exp/iat are numeric seconds.
export function claimDate(seconds) {
  if (!seconds || Number.isNaN(Number(seconds))) return null;
  return new Date(Number(seconds) * 1000);
}
