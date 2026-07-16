// Small storage abstraction for NON-SENSITIVE preferences (theme, language, ui flags).
// Tokens continue to use expo-secure-store directly in the auth layer — this wrapper is only for
// preferences and degrades to an in-memory map when SecureStore is unavailable (e.g. Expo web),
// so the app never crashes just because a preference could not be persisted.

import * as SecureStore from 'expo-secure-store';

const memory = new Map();

export async function getItem(key) {
  try {
    const v = await SecureStore.getItemAsync(key);
    return v ?? (memory.has(key) ? memory.get(key) : null);
  } catch (_) {
    return memory.has(key) ? memory.get(key) : null;
  }
}

export async function setItem(key, value) {
  memory.set(key, value);
  try {
    await SecureStore.setItemAsync(key, value);
  } catch (_) {
    // in-memory fallback already set
  }
}

export async function removeItem(key) {
  memory.delete(key);
  try {
    await SecureStore.deleteItemAsync(key);
  } catch (_) {
    // ignore
  }
}
