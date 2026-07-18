import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { View, TouchableOpacity, Alert } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import AppText from './AppText';
import Button from './Button';
import { normalizeError, ErrorKind } from '../api/errors';
import { openSupport } from '../utils/support';
import { copyToClipboard } from '../utils/clipboard';
import { announce } from '../utils/a11y';

/**
 * The single error panel for the app (Sprint 4, Task 7; enterprise UX in Sprint 10). Derives its copy
 * from the error's kind, and layers on the support affordances a server fault needs.
 *
 *   <ErrorState error={err} onRetry={refresh} />        // derives icon/title/message from the kind
 *   <ErrorState title="…" message="…" onRetry={fn} />   // explicit copy, still supported
 *
 * Beyond icon/title/message/retry, for a SERVER-SIDE fault (a 5xx, or any error carrying a backend
 * correlationId) it also shows:
 *   * a copyable Kode Rujukan (diagnostic id) — the reference a user reads to support;
 *   * an expandable "Detail teknis" — method/path/status/code, safe to show, collapsed by default;
 *   * a "Hubungi Dukungan" button that opens a pre-filled support email.
 * Client-side faults a user can act on (404, 403, offline, validation) stay lean — none of that
 * chrome appears, because none of it would help.
 *
 * Retry countdown: when the error is retryable AND carries a delay (429/503 Retry-After, or an
 * explicit autoRetryMs), the retry auto-fires after a visible countdown, with "Coba sekarang" to skip
 * the wait. That is the difference between hammering a rate limit and honouring it.
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
  [ErrorKind.UNAVAILABLE]: 'Sedang pemeliharaan',
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
  showSupport = true,
  autoRetryMs = null,
  screen,
}) {
  const theme = useTheme();

  // Memoized so a raw axios error is not re-normalized into a NEW ApiError (with a new diagnosticId)
  // on every render — that would re-arm the countdown every tick and it would never reach zero.
  const normalized = useMemo(() => (error ? normalizeError(error) : null), [error]);

  const isMaintenance = !!(normalized && normalized.kind === ErrorKind.UNAVAILABLE && normalized.status === 503);
  const isOffline = !!(normalized && (normalized.kind === ErrorKind.OFFLINE || normalized.kind === ErrorKind.UNREACHABLE));

  const resolvedIcon = icon || (normalized && KIND_ICON[normalized.kind]) || '⚠️';
  const resolvedTitle = title || (normalized && KIND_TITLE[normalized.kind]) || 'Terjadi kesalahan';
  const resolvedMessage =
    message
    || (isMaintenance ? 'Sistem sedang dalam pemeliharaan. Kami akan segera kembali. Coba lagi beberapa saat lagi.' : null)
    || (isOffline ? 'Periksa koneksi internet Anda. Data yang ditampilkan mungkin belum diperbarui.' : null)
    || (normalized && normalized.message)
    || 'Gagal memuat data. Silakan coba lagi.';

  // Hide Retry where retrying is known to be pointless — a 403 will be a 403 again.
  const retryIsUseful = !normalized || normalized.retryable || normalized.kind === ErrorKind.UNKNOWN;
  const showRetry = !!onRetry && retryIsUseful;

  // A server-side fault: something the backend saw (5xx) or tagged (correlationId). Only these get the
  // reference / detail / support chrome — a user's own offline or a 404 does not.
  const hasServerFault = !!(normalized && (normalized.correlationId || (normalized.status && normalized.status >= 500)));

  const isDev = typeof __DEV__ !== 'undefined' && __DEV__;
  const canShowDetail = showDiagnostic && !!(normalized && normalized.diagnostic) && (hasServerFault || isDev);

  // --- retry countdown ---
  const autoMs = showRetry && normalized && normalized.retryable ? (normalized.retryAfterMs || autoRetryMs || 0) : 0;
  const onRetryRef = useRef(onRetry);
  onRetryRef.current = onRetry;
  const intervalRef = useRef(null);
  const [remaining, setRemaining] = useState(0);

  useEffect(() => {
    if (!autoMs) { setRemaining(0); return undefined; }
    const deadline = Date.now() + autoMs;
    setRemaining(Math.ceil(autoMs / 1000));
    intervalRef.current = setInterval(() => {
      const leftMs = deadline - Date.now();
      if (leftMs <= 0) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
        setRemaining(0);
        onRetryRef.current && onRetryRef.current();
      } else {
        setRemaining(Math.ceil(leftMs / 1000));
      }
    }, 250);
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
      intervalRef.current = null;
    };
  }, [autoMs, normalized]);

  const retryNow = useCallback(() => {
    if (intervalRef.current) { clearInterval(intervalRef.current); intervalRef.current = null; }
    setRemaining(0);
    onRetryRef.current && onRetryRef.current();
  }, []);

  const countingDown = remaining > 0;

  // --- reference copy + support ---
  const [copied, setCopied] = useState(false);
  const copiedTimer = useRef(null);
  useEffect(() => () => { if (copiedTimer.current) clearTimeout(copiedTimer.current); }, []);

  const copyRef = useCallback(async () => {
    if (!normalized) return;
    const ok = await copyToClipboard(normalized.diagnosticId);
    if (!ok) return;
    announce('Kode rujukan disalin');
    setCopied(true);
    if (copiedTimer.current) clearTimeout(copiedTimer.current);
    copiedTimer.current = setTimeout(() => setCopied(false), 1800);
  }, [normalized]);

  const contactSupport = useCallback(async () => {
    const ok = await openSupport(normalized, { screen });
    if (!ok) {
      // No mail handler — do not leave a dead button; show the address to copy manually.
      Alert.alert('Hubungi Dukungan', 'support@notarist.example');
    }
  }, [normalized, screen]);

  const [detailOpen, setDetailOpen] = useState(false);

  return (
    <View
      accessibilityRole="alert"
      style={{ flex: fill ? 1 : undefined, alignItems: 'center', justifyContent: 'center', padding: theme.spacing.xxl }}
    >
      {/* Decorative — the title and message carry the meaning. */}
      <AppText accessibilityElementsHidden importantForAccessibility="no" style={{ fontSize: 40, marginBottom: theme.spacing.md }}>
        {resolvedIcon}
      </AppText>
      <AppText variant="h3" align="center" style={{ marginBottom: theme.spacing.xs }}>
        {resolvedTitle}
      </AppText>
      <AppText color="textMuted" variant="bodySm" align="center" style={{ marginBottom: theme.spacing.lg }}>
        {resolvedMessage}
      </AppText>

      {showRetry ? (
        countingDown ? (
          <View style={{ alignItems: 'center', gap: theme.spacing.sm }}>
            <AppText variant="caption" color="textMuted" accessibilityLiveRegion="polite">
              Coba lagi otomatis dalam {remaining} detik…
            </AppText>
            <Button title="Coba Sekarang" onPress={retryNow} variant="secondary" fullWidth={false} icon="↻" />
          </View>
        ) : (
          <Button title="Coba Lagi" onPress={onRetry} variant="secondary" fullWidth={false} icon="↻" />
        )
      ) : null}

      {hasServerFault ? (
        <TouchableOpacity
          onPress={copyRef}
          accessibilityRole="button"
          accessibilityLabel={`Kode rujukan ${normalized.diagnosticId}. Ketuk untuk menyalin.`}
          style={{ marginTop: theme.spacing.lg, flexDirection: 'row', alignItems: 'center', gap: 6 }}
        >
          <AppText variant="micro" color="textFaint">Kode rujukan:</AppText>
          <AppText variant="micro" color={copied ? 'success' : 'textMuted'} selectable style={{ fontFamily: 'monospace' }}>
            {normalized.diagnosticId}
          </AppText>
          <AppText variant="micro" color={copied ? 'success' : 'primary'}>{copied ? '✓' : '⧉'}</AppText>
        </TouchableOpacity>
      ) : null}

      {hasServerFault && showSupport ? (
        <TouchableOpacity onPress={contactSupport} accessibilityRole="button" style={{ marginTop: theme.spacing.sm }}>
          <AppText variant="caption" color="primary" style={{ fontWeight: theme.typography.semibold }}>
            Hubungi Dukungan
          </AppText>
        </TouchableOpacity>
      ) : null}

      {canShowDetail ? (
        <View style={{ marginTop: theme.spacing.md, alignItems: 'center' }}>
          <TouchableOpacity
            onPress={() => setDetailOpen((o) => !o)}
            accessibilityRole="button"
            accessibilityState={{ expanded: detailOpen }}
            accessibilityLabel="Detail teknis"
          >
            <AppText variant="micro" color="textFaint">{detailOpen ? '▲ Sembunyikan detail teknis' : '▼ Detail teknis'}</AppText>
          </TouchableOpacity>
          {detailOpen ? (
            <AppText
              variant="micro"
              color="textFaint"
              align="center"
              selectable
              style={{ marginTop: theme.spacing.sm, fontFamily: 'monospace' }}
            >
              {normalized.diagnostic}
              {normalized.errorCode ? `\n${normalized.errorCode}` : ''}
            </AppText>
          ) : null}
        </View>
      ) : null}
    </View>
  );
}
