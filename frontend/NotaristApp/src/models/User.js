// A view model for the signed-in user, assembled from whatever the client actually has:
//   1) the TokenResponse captured at login (userId, roles, tenantId, sessionId), and
//   2) the JWT access-token claims (username, fullName, roles, tenantId, sub, exp, iat).
// Anything not present degrades to null — the Profile screen renders a placeholder rather than
// inventing data. There is NO /auth/me endpoint, so email/avatar are intentionally absent.

import { decodeJwtPayload, claimDate } from '../utils/jwt';
import { initials as toInitials, titleCase } from '../utils/format';

const ROLE_LABELS = {
  ADMIN: 'Administrator',
  PIMPINAN: 'Pimpinan',
  NOTARIS: 'Notaris',
  PPAT_OFFICER: 'Pejabat PPAT',
  STAFF: 'Staf',
};

export function buildUser(authUser) {
  if (!authUser) return null;

  const token = authUser.token || authUser.accessToken || null;
  const claims = decodeJwtPayload(token) || {};

  const roles = authUser.roles || claims.roles || [];
  const primaryRole = roles[0] || null;

  return {
    // identity
    userId: authUser.userId || claims.sub || null,
    username: authUser.username || claims.username || null,
    fullName: authUser.fullName || claims.fullName || null,
    email: authUser.email || claims.email || null, // not issued today → null
    // org
    tenantId: authUser.tenantId || claims.tenantId || null,
    roles,
    primaryRole,
    primaryRoleLabel: primaryRole ? (ROLE_LABELS[primaryRole] || titleCase(primaryRole)) : null,
    // session
    sessionId: authUser.sessionId || null,
    issuedAt: claimDate(claims.iat),
    expiresAt: claimDate(claims.exp),
    hasToken: !!token,
    // derived
    get displayName() {
      return this.fullName || this.username || 'Pengguna';
    },
    get initialsLabel() {
      return toInitials(this.fullName || this.username || '?');
    },
  };
}

export { ROLE_LABELS };
