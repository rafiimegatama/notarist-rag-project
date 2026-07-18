// Token/session storage: expo-secure-store on native, localStorage on web, and it NEVER throws.
//
// Why this exists (RC acceptance, verified in a real browser): expo-secure-store has NO web
// implementation in SDK 57 — every call rejects with "getValueWithKeyAsync is not a function".
// The auth layer called it directly, so on web the AuthContext mount effect died before
// setLoading(false) and every web user sat on "Memuat sesi…" forever. The app ships a web build
// (`expo export --platform web`), so this is a supported platform, not an edge case.
//
// localStorage is the web platform's storage for this. It is weaker than the native Keystore —
// an XSS can read it — which is the accepted trade for a browser client; auth still travels only
// in the Authorization header, and tokens expire in 15 minutes.
//
// utils/storage.js stays separate on purpose: that wrapper is for NON-SENSITIVE preferences and
// falls back to in-memory. Tokens must survive a page reload (session restore), so memory is not
// an acceptable fallback here.

import { Platform } from 'react-native';
import * as SecureStore from 'expo-secure-store';

const WEB = Platform.OS === 'web';
const hasLocalStorage = () => typeof window !== 'undefined' && !!window.localStorage;

export async function getToken(key) {
  if (WEB) {
    try { return hasLocalStorage() ? window.localStorage.getItem(key) : null; } catch (_) { return null; }
  }
  try { return await SecureStore.getItemAsync(key); } catch (_) { return null; }
}

export async function setToken(key, value) {
  if (WEB) {
    try { if (hasLocalStorage()) window.localStorage.setItem(key, value); } catch (_) { /* quota/private mode */ }
    return;
  }
  try { await SecureStore.setItemAsync(key, value); } catch (_) { /* keystore unavailable */ }
}

export async function deleteToken(key) {
  if (WEB) {
    try { if (hasLocalStorage()) window.localStorage.removeItem(key); } catch (_) { /* ignore */ }
    return;
  }
  try { await SecureStore.deleteItemAsync(key); } catch (_) { /* ignore */ }
}
