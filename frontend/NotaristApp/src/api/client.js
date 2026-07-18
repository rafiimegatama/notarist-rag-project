import axios from 'axios';
import { getToken, setToken, deleteToken } from '../utils/tokenStore';
import { normalizeError, ErrorKind } from './errors';
import { unwrap } from './envelope';
import { installRetry } from './retry';
import { installLogger } from './logger';
import { markOnline, markOffline, markReconnecting } from './connectivity';

// ---------------------------------------------------------------------------------------------
// THE base URL. One mechanism, one default, one place (Sprint 6, finding 12).
//
// Set EXPO_PUBLIC_API_URL to point the app at a backend. That is the whole switch — and per Sprint 6
// STEP 8 it is meant to stay that way: when Supabase lands, integrating should be this variable plus
// FEATURES flags, with no code edit anywhere.
//
// There used to be a SECOND mechanism: app.json's `expo.extra.apiUrl`, set to
// "http://localhost:8080/api/v1". Nothing read it — no module imports expo-constants — so it was dead
// config that disagreed with the live default below (localhost vs 10.0.2.2). Two documented defaults
// where only one is real is how someone spends an afternoon "fixing" the URL in the file that does
// nothing. It has been removed from app.json rather than wired up, because one mechanism is the
// requirement and this is the one the code actually uses.
//
// The default targets the Android emulator: 10.0.2.2 is the emulator's alias for the HOST's loopback,
// so it reaches a backend running on the developer's machine. `localhost` inside the emulator is the
// emulator itself — which is exactly why app.json's value would have been wrong had anything read it.
// On a physical device or iOS simulator, set EXPO_PUBLIC_API_URL to the host's LAN address.
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
  await deleteToken('jwt_token');
  await deleteToken('refresh_token');
  await deleteToken('session_id');
}

let refreshPromise = null;

async function performRefresh() {
  const refreshToken = await getToken('refresh_token');
  if (!refreshToken) {
    throw new Error('No refresh token available');
  }
  // Deliberately the bare `axios`, not `client`: this call must not pass back through the request
  // interceptor (which would attach the expired token) or the response interceptor (which would see a
  // 401 and recurse into another refresh).
  const response = await axios.post(`${BASE_URL}/auth/refresh`, { refreshToken });
  // `response.data.data` destructured directly here until Sprint 6. On the ONE path where the
  // envelope is least trustworthy — a captive portal or gateway intercepting an unauthenticated
  // POST — that throws a TypeError instead of a refresh failure, so the caller could not tell "the
  // refresh was rejected" from "the response was not JSON", and neither cleared the session.
  const data = unwrap(response, null);
  if (!data || !data.accessToken) {
    throw new Error('Refresh response carried no accessToken');
  }
  await setToken('jwt_token', data.accessToken);
  if (data.refreshToken) {
    await setToken('refresh_token', data.refreshToken);
  }
  if (data.sessionId) {
    await setToken('session_id', data.sessionId);
  }
  return data.accessToken;
}

client.interceptors.request.use(async (config) => {
  const token = await getToken('jwt_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Statuses that mean "your credential was not accepted". 401 is the textbook answer; the REAL
// backend answers 403 for every credential failure — no token, malformed token, expired token,
// revoked-by-logout token all return 403 (verified against the running service, RC acceptance).
// Matching only 401 left the refresh interceptor dead in production: a notary's 15-minute access
// token expired and every screen turned into "Anda tidak memiliki akses" with no refresh and no
// sign-out. A genuine RBAC 403 costs one extra refresh+retry round (guarded by _retry) and then
// surfaces as FORBIDDEN exactly as before.
const AUTH_FAILURE_STATUSES = [401, 403];

client.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    const isAuthEndpoint = originalRequest?.url?.includes('/auth/');

    if (AUTH_FAILURE_STATUSES.includes(error.response?.status) && !isAuthEndpoint && !originalRequest._retry) {
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

    if (AUTH_FAILURE_STATUSES.includes(error.response?.status) && isAuthEndpoint) {
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
