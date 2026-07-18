// Sprint 6: a private base64Decode + decodeJwtPayload pair lived here — a second, subtly weaker copy
// of utils/jwt.js (that one guards a non-string token and a missing payload segment; this one threw
// past its own try/catch on a malformed token). Its only caller was logout(), which decoded the
// access token to compute the `jti`/`remainingTtlSeconds` params the backend deliberately stopped
// accepting. With those params gone the whole decoder is unreachable, so it goes with them rather
// than sitting here as a duplicate implementation of a security-adjacent primitive.
//
// utils/jwt.js remains the one JWT reader (models/User.js uses it for the profile claims).
import client from './client';
// tokenStore, not SecureStore directly: on web SecureStore rejects every call (no SDK 57 web
// implementation) and login could never persist a token. See utils/tokenStore.js.
import { getToken, setToken, deleteToken } from '../utils/tokenStore';
import { unwrapOrThrow } from './envelope';
import { ApiError, ErrorKind } from './errors';

// POST /auth/login -> ApiResponse<TokenResponse>
// TokenResponse { accessToken, refreshToken, tokenType, expiresIn, userId, roles, tenantId, sessionId }
export async function login(username, password) {
  const response = await client.post('/auth/login', { username, password });
  // unwrapOrThrow, not `response.data.data`: the bare destructure throws a raw TypeError the moment a
  // proxy, captive portal or gateway timeout answers with something that is not the envelope — and a
  // TypeError has no `kind`, so the login screen could not classify it and showed "terjadi kesalahan"
  // for what is really a network problem. It also rejects a 200-with-status:"ERROR", which this path
  // previously read straight past into a destructure of `null`.
  const data = unwrapOrThrow(response, null);

  // An envelope that unwrapped cleanly but carries no token is not a login. Fail loudly rather than
  // storing `undefined` and leaving the app in a signed-in state holding no credential.
  if (!data || !data.accessToken) {
    throw new ApiError({
      kind: ErrorKind.SERVER,
      status: response && response.status,
      message: 'Masuk gagal. Coba lagi.',
      diagnostic: 'POST /auth/login returned no accessToken in the response envelope',
      retryable: true,
    });
  }

  await setToken('jwt_token', data.accessToken);
  if (data.refreshToken) {
    await setToken('refresh_token', data.refreshToken);
  }
  if (data.sessionId) {
    await setToken('session_id', data.sessionId);
  }
  return data;
}

// POST /auth/logout?sessionId={uuid}
//
// `sessionId` is the WHOLE contract (AuthController.logout). This used to also send `jti` and
// `remainingTtlSeconds`, computed by decoding the access token here on the device. Those params were
// removed from the backend on purpose: it now derives both from the caller's own bearer token,
// because trusting a client-supplied jti let any caller revoke any token, and a client that simply
// omitted it got a logout that revoked nothing.
//
// Spring ignores unknown query params, so this was stale rather than broken — the logout worked, and
// the two extra params were decoration on the wire. Removed because dead code that mirrors a
// deliberately-removed contract is a standing invitation to "restore" it.
export async function logout() {
  try {
    const sessionId = await getToken('session_id');
    if (sessionId) {
      await client.post('/auth/logout', null, { params: { sessionId } });
    }
  } catch (_) {
    // ignore server errors on logout
  } finally {
    await deleteToken('jwt_token');
    await deleteToken('refresh_token');
    await deleteToken('session_id');
  }
}

export async function getStoredToken() {
  return getToken('jwt_token');
}

// Read-only accessor for display purposes. The session id is returned by /auth/login but is NOT a
// JWT claim, so after an app relaunch (session restored from the token alone) it is only available
// from storage. Profile reads it through here instead of showing an empty field.
export async function getStoredSessionId() {
  return getToken('session_id');
}
