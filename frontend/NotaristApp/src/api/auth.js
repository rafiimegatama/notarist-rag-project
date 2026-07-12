import client from './client';
import * as SecureStore from 'expo-secure-store';

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

function decodeJwtPayload(token) {
  try {
    const payload = token.split('.')[1];
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
    const binary = base64Decode(base64);
    const json = decodeURIComponent(
      binary.split('').map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0')).join('')
    );
    return JSON.parse(json);
  } catch (_) {
    return null;
  }
}

export async function login(username, password) {
  const response = await client.post('/auth/login', { username, password });
  const { accessToken, refreshToken, sessionId } = response.data.data;
  await SecureStore.setItemAsync('jwt_token', accessToken);
  if (refreshToken) {
    await SecureStore.setItemAsync('refresh_token', refreshToken);
  }
  if (sessionId) {
    await SecureStore.setItemAsync('session_id', sessionId);
  }
  return response.data.data;
}

export async function logout() {
  try {
    const sessionId = await SecureStore.getItemAsync('session_id');
    const token = await SecureStore.getItemAsync('jwt_token');
    if (sessionId) {
      const params = { sessionId };
      const claims = token ? decodeJwtPayload(token) : null;
      if (claims?.jti && claims?.exp) {
        const remainingTtlSeconds = Math.max(0, claims.exp - Math.floor(Date.now() / 1000));
        params.jti = claims.jti;
        params.remainingTtlSeconds = remainingTtlSeconds;
      }
      await client.post('/auth/logout', null, { params });
    }
  } catch (_) {
    // ignore server errors on logout
  } finally {
    await SecureStore.deleteItemAsync('jwt_token');
    await SecureStore.deleteItemAsync('refresh_token');
    await SecureStore.deleteItemAsync('session_id');
  }
}

export async function getStoredToken() {
  return SecureStore.getItemAsync('jwt_token');
}
