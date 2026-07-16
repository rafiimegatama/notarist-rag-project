import axios from 'axios';
import * as SecureStore from 'expo-secure-store';
import { normalizeError, ErrorKind } from './errors';
import { installRetry } from './retry';
import { installLogger } from './logger';
import { markOnline, markOffline, markReconnecting } from './connectivity';

// Change this to your backend IP when running on a physical device
export const BASE_URL = process.env.EXPO_PUBLIC_API_URL || 'http://10.0.2.2:8080/api/v1';

const client = axios.create({
  baseURL: BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
  },
});

let authFailureHandler = null;
export function setAuthFailureHandler(handler) {
  authFailureHandler = handler;
}

async function clearSession() {
  await SecureStore.deleteItemAsync('jwt_token');
  await SecureStore.deleteItemAsync('refresh_token');
  await SecureStore.deleteItemAsync('session_id');
}

let refreshPromise = null;

async function performRefresh() {
  const refreshToken = await SecureStore.getItemAsync('refresh_token');
  if (!refreshToken) {
    throw new Error('No refresh token available');
  }
  const response = await axios.post(`${BASE_URL}/auth/refresh`, { refreshToken });
  const { accessToken, refreshToken: newRefreshToken, sessionId } = response.data.data;
  await SecureStore.setItemAsync('jwt_token', accessToken);
  if (newRefreshToken) {
    await SecureStore.setItemAsync('refresh_token', newRefreshToken);
  }
  if (sessionId) {
    await SecureStore.setItemAsync('session_id', sessionId);
  }
  return accessToken;
}

client.interceptors.request.use(async (config) => {
  const token = await SecureStore.getItemAsync('jwt_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

client.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    const isAuthEndpoint = originalRequest?.url?.includes('/auth/');

    if (error.response?.status === 401 && !isAuthEndpoint && !originalRequest._retry) {
      originalRequest._retry = true;
      try {
        if (!refreshPromise) {
          refreshPromise = performRefresh().finally(() => {
            refreshPromise = null;
          });
        }
        const newToken = await refreshPromise;
        originalRequest.headers.Authorization = `Bearer ${newToken}`;
        return client(originalRequest);
      } catch (refreshError) {
        await clearSession();
        authFailureHandler?.();
        return Promise.reject(error);
      }
    }

    if (error.response?.status === 401 && isAuthEndpoint) {
      await clearSession();
      authFailureHandler?.();
    }

    return Promise.reject(error);
  }
);

// ---------------------------------------------------------------------------------------------
// Cross-cutting transport layers (Sprint 4, Tasks 1/2/8/9).
//
// Installed BELOW the auth interceptor above, and the order is load-bearing. Axios runs response
// interceptors in registration order, so the session-refresh handler stays first and keeps owning
// 401 recovery exactly as it did before this sprint — nothing here re-implements, wraps or races it.
// A 401 that refresh cannot rescue falls through to normalization like any other error.
//
//   1. logger        — observes every attempt, including ones that get retried (dev only)
//   2. connectivity  — updates the network signal from real outcomes
//   3. retry         — GET-only replay; on retry the request re-enters this whole chain
//   4. normalize     — LAST, so every error leaving this module is an ApiError
// ---------------------------------------------------------------------------------------------

installLogger(client);

// Only network-class failures move the connectivity signal. An HTTP error proves the opposite of a
// network problem: the server received the request and answered, so a 500 must not raise "offline".
client.interceptors.response.use(
  (response) => {
    markOnline();
    return response;
  },
  (error) => {
    const kind = normalizeError(error).kind;
    if (kind === ErrorKind.OFFLINE || kind === ErrorKind.UNREACHABLE || kind === ErrorKind.TIMEOUT) {
      markOffline(kind);
    }
    return Promise.reject(error);
  }
);

// Retry sits after connectivity so a failed attempt registers as offline first, then flips to
// "reconnecting" while the backoff is in flight — which is what the banner narrates to the user.
installRetry(client, () => markReconnecting());

// Terminal error handler: from here outward, every rejection is an ApiError with a `kind`, a
// user-safe `message` and a developer `diagnostic`. Screens switch on `kind`, never on a raw status.
client.interceptors.response.use(
  (response) => response,
  (error) => Promise.reject(normalizeError(error))
);

export default client;
