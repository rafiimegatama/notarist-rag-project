import React from 'react';
import { View } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import AppText from './AppText';
import Button from './Button';
import { normalizeError, ErrorKind } from '../api/errors';

/**
 * The single error panel for the app (Sprint 4, Task 7). Retry action + diagnostic.
 *
 * Two ways to use it, and the first is preferred:
 *
 *   <ErrorState error={err} onRetry={refresh} />        // derives icon/title/message from the kind
 *   <ErrorState title="…" message="…" onRetry={fn} />   // explicit copy, still supported
 *
 * Passing `error` means a 403 stops reading "Gagal memuat data" — the user is told they lack access,
 * which is true and actionable, instead of being invited to retry something that cannot succeed.
 * Explicit props always win, so every existing call site keeps its exact wording.
 *
 * The diagnostic line ("GET /cases → 503") renders in __DEV__ only. It is built in api/errors.js from
 * method + path + status and never contains the token or a response body, but there is still no
 * reason to show transport internals to a notary — so it stays behind the dev flag.
 */

const KIND_ICON = {
  [ErrorKind.UNAUTHORIZED]: '🔒',
  [ErrorKind.FORBIDDEN]: '🚫',
  [ErrorKind.NOT_FOUND]: '🔍',
  [ErrorKind.CONFLICT]: '🔀',
  [ErrorKind.VALIDATION]: '📝',
  [ErrorKind.RATE_LIMITED]: '⏳',
  [ErrorKind.SERVER]: '⚠️',
  [ErrorKind.UNAVAILABLE]: '🛠️',
  [ErrorKind.OFFLINE]: '📡',
  [ErrorKind.TIMEOUT]: '⏱️',
  [ErrorKind.UNREACHABLE]: '📡',
  [ErrorKind.UNKNOWN]: '⚠️',
};

const KIND_TITLE = {
  [ErrorKind.UNAUTHORIZED]: 'Sesi berakhir',
  [ErrorKind.FORBIDDEN]: 'Tidak ada akses',
  [ErrorKind.NOT_FOUND]: 'Tidak ditemukan',
  [ErrorKind.CONFLICT]: 'Data berubah',
  [ErrorKind.VALIDATION]: 'Data tidak valid',
  [ErrorKind.RATE_LIMITED]: 'Terlalu banyak permintaan',
  [ErrorKind.SERVER]: 'Gangguan server',
  [ErrorKind.UNAVAILABLE]: 'Layanan tidak tersedia',
  [ErrorKind.OFFLINE]: 'Tidak ada koneksi',
  [ErrorKind.TIMEOUT]: 'Permintaan terlalu lama',
  [ErrorKind.UNREACHABLE]: 'Server tidak dapat dihubungi',
  [ErrorKind.UNKNOWN]: 'Terjadi kesalahan',
};

export default function ErrorState({
  error,
  title,
  message,
  onRetry,
  icon,
  fill = true,
  showDiagnostic = true,
}) {
  const theme = useTheme();

  // normalizeError is total and idempotent: safe on an ApiError, a raw axios error, or junk.
  const normalized = error ? normalizeError(error) : null;

  const resolvedIcon = icon || (normalized && KIND_ICON[normalized.kind]) || '⚠️';
  const resolvedTitle = title || (normalized && KIND_TITLE[normalized.kind]) || 'Terjadi kesalahan';
  const resolvedMessage =
    message || (normalized && normalized.message) || 'Gagal memuat data. Silakan coba lagi.';

  // Hide Retry where retrying is known to be pointless — a 403 will be a 403 again. With no `error`
  // to reason about, the button stays: the caller asked for it.
  const retryIsUseful = !normalized || normalized.retryable || normalized.kind === ErrorKind.UNKNOWN;
  const showRetry = !!onRetry && retryIsUseful;

  const isDev = typeof __DEV__ !== 'undefined' && __DEV__;
  const diagnostic = showDiagnostic && isDev && normalized ? normalized.diagnostic : null;

  return (
    <View
      accessibilityRole="alert"
      style={{
        flex: fill ? 1 : undefined,
        alignItems: 'center',
        justifyContent: 'center',
        padding: theme.spacing.xxl,
      }}
    >
      {/* Decorative. The title and message carry the meaning, so the glyph is hidden from screen
          readers rather than announced as a bare emoji name. */}
      <AppText
        accessibilityElementsHidden
        importantForAccessibility="no"
        style={{ fontSize: 40, marginBottom: theme.spacing.md }}
      >
        {resolvedIcon}
      </AppText>
      <AppText variant="h3" align="center" style={{ marginBottom: theme.spacing.xs }}>
        {resolvedTitle}
      </AppText>
      <AppText color="textMuted" variant="bodySm" align="center" style={{ marginBottom: theme.spacing.lg }}>
        {resolvedMessage}
      </AppText>
      {showRetry ? (
        <Button title="Coba Lagi" onPress={onRetry} variant="secondary" fullWidth={false} icon="↻" />
      ) : null}
      {diagnostic ? (
        <AppText
          variant="micro"
          color="textFaint"
          align="center"
          selectable
          style={{ marginTop: theme.spacing.lg, fontFamily: 'monospace' }}
        >
          {diagnostic}
        </AppText>
      ) : null}
    </View>
  );
}
