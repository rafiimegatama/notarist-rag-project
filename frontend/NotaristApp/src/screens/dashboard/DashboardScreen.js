import React, { useCallback, useMemo } from 'react';
import { View, RefreshControl, TouchableOpacity } from 'react-native';
import Screen from '../../components/Screen';
import AppText from '../../components/AppText';
import StatCard from '../../components/StatCard';
import InfoCard from '../../components/InfoCard';
import SectionHeader from '../../components/SectionHeader';
import MockBanner from '../../components/MockBanner';
import OfflineBanner from '../../components/OfflineBanner';
import Banner from '../../components/Banner';
import ErrorState from '../../components/ErrorState';
import { SkeletonList } from '../../components/Skeleton';
import { useTheme } from '../../context/ThemeContext';
import { relativeTime } from '../../utils/format';
import useUser from '../../hooks/useUser';
import { useDashboard } from '../../state';

// Quick actions per the workflow: Upload, New Case, Search, Assistant, QC Checklist.
const ACTIONS = [
  { key: 'upload', icon: '⬆️', label: 'Upload Dokumen', to: ['Dokumen'] },
  { key: 'newcase', icon: '➕', label: 'Case Baru', to: ['Kasus', { intent: 'new' }] },
  { key: 'search', icon: '🔍', label: 'Cari', to: ['Cari'] },
  { key: 'assistant', icon: '🤖', label: 'Asisten', to: ['Asisten'] },
  { key: 'qc', icon: '✅', label: 'QC Checklist', to: ['Kasus', { status: 'WAITING_QC' }] },
];

export default function DashboardScreen({ navigation }) {
  const theme = useTheme();
  const user = useUser();
  const { summary, loading, refreshing, error, offline, usingMock, fromCache, lastSyncedAt, refresh } = useDashboard();

  // Stable identities: these end up as StatCard's `onPress`, and StatCard is memoized. Inline arrows
  // here would hand it a new prop every render and defeat the memo entirely (Sprint 4, Task 10).
  const goCases = useCallback(
    (status) => navigation.navigate('Kasus', status ? { status } : {}),
    [navigation],
  );
  const goReminders = useCallback(() => navigation.navigate('Pengingat'), [navigation]);

  // One stable handler per quick action, built once. QuickAction is memoized, so an inline
  // `() => navigation.navigate(...)` in the map below would give it a new prop on every render.
  const actionHandlers = useMemo(() => {
    const out = {};
    for (const a of ACTIONS) out[a.key] = () => navigation.navigate(...a.to);
    return out;
  }, [navigation]);

  // The 8 dashboard cards, wired to their drill-down destination. Rebuilt only when the summary or a
  // navigation callback actually changes, so an unrelated re-render does not re-create eight objects.
  const cards = useMemo(() => (summary ? [
    { label: 'Total Case', value: summary.totalCase, tone: 'primary', icon: '📁', onPress: () => goCases() },
    { label: 'Draft', value: summary.draft, tone: 'textFaint', icon: '📝', onPress: () => goCases('DRAFT') },
    { label: 'Menunggu Verifikasi', value: summary.waitingVerification, tone: 'warning', icon: '🔍', onPress: () => goCases('WAITING_VERIFICATION') },
    { label: 'Menunggu QC', value: summary.waitingQc, tone: 'info', icon: '✅', onPress: () => goCases('WAITING_QC') },
    { label: 'Menunggu Approval', value: summary.waitingApproval, tone: 'primary', icon: '✍️', onPress: () => goCases('WAITING_APPROVAL') },
    { label: 'Siap Kirim', value: summary.readyToSend, tone: 'success', icon: '📤', onPress: () => goCases('READY_TO_SEND') },
    { label: 'SKMHT Terlambat', value: summary.overdueSkmht, tone: 'danger', icon: '⏰', onPress: goReminders },
    { label: 'Pengingat', value: summary.reminderCount, tone: 'warning', icon: '🔔', onPress: goReminders },
  ] : []), [summary, goCases, goReminders]);

  // Only worth saying when there ARE numbers on screen whose freshness is in doubt: cached data still
  // waiting on its background refresh, or data whose refresh has already failed.
  const staleNotice = useMemo(() => {
    if (!summary) return null;
    if (error) {
      return lastSyncedAt
        ? `Menampilkan data tersimpan dari ${relativeTime(lastSyncedAt)}. Pembaruan gagal.`
        : 'Menampilkan data tersimpan. Pembaruan gagal.';
    }
    if (fromCache && lastSyncedAt) {
      return `Data tersimpan dari ${relativeTime(lastSyncedAt)} — memperbarui…`;
    }
    return null;
  }, [summary, error, fromCache, lastSyncedAt]);

  return (
    <Screen
      scroll
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} tintColor={theme.colors.primary} />}
    >
      <View style={{ marginBottom: theme.spacing.md }}>
        <AppText variant="bodySm" color="textMuted">Selamat datang kembali</AppText>
        <AppText variant="h2" numberOfLines={1}>{user?.displayName ?? 'Notaris'}</AppText>
      </View>

      {usingMock ? <MockBanner entity="dashboard" style={{ marginBottom: theme.spacing.md }} /> : null}
      {offline ? <OfflineBanner style={{ marginBottom: theme.spacing.md }} /> : null}

      {/* Cached counters are shown rather than withheld, but never passed off as live: if the refresh
          behind them failed, say so and date them. Silent stale numbers on a workload dashboard are
          worse than no numbers — a notary could read "0 menunggu approval" and stop looking. */}
      {staleNotice ? (
        <Banner variant="warning" message={staleNotice} style={{ marginBottom: theme.spacing.md }} />
      ) : null}

      {loading ? (
        <SkeletonList count={4} />
      ) : error && !summary ? (
        <ErrorState error={error} message="Gagal memuat ringkasan dashboard." onRetry={refresh} fill={false} />
      ) : (
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: theme.spacing.sm }}>
          {cards.map((c) => (
            <StatCard
              key={c.label}
              label={c.label}
              value={c.value}
              tone={c.tone}
              icon={c.icon}
              onPress={c.onPress}
              style={{ width: '48%', flexGrow: 1 }}
            />
          ))}
        </View>
      )}

      <SectionHeader title="Aksi Cepat" />
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: theme.spacing.sm }}>
        {ACTIONS.map((a) => (
          <QuickAction key={a.key} icon={a.icon} label={a.label} onPress={actionHandlers[a.key]} />
        ))}
      </View>

      <InfoCard title="Alur Kerja" icon="🔄" tone="info" style={{ marginTop: theme.spacing.xl }}>
        Case → Bundle → Upload → OCR Review → Verifikasi → Draft → QC → Approval → Terkirim ke Bank.
        Pantau setiap tahap melalui kartu status di atas.
      </InfoCard>
      <View style={{ height: theme.spacing.xxl }} />
    </Screen>
  );
}

/**
 * Quick-action tile (Sprint 4, Task 11).
 *
 * Was a bare <AppText onPress>: a Text node with a press handler. Three problems, all invisible to a
 * sighted mouse-free tester —
 *   * no button role, so a screen reader announced it as static text and gave no hint it was tappable
 *   * the emoji itself was the accessible name, so it read out as "up arrow" with no mention of
 *     "Upload Dokumen"; the visible caption below was a separate, unrelated node
 *   * the tap target was the glyph's text box, which shrinks with the font — not a guaranteed 44pt
 *
 * Now one focusable button whose label is the caption, with the glyph marked decorative and the
 * target pinned to the accessibility floor. The visual result is unchanged.
 */
const QuickAction = React.memo(function QuickAction({ icon, label, onPress }) {
  const theme = useTheme();
  return (
    <View style={{ width: '31%', flexGrow: 1 }}>
      <TouchableOpacity
        onPress={onPress}
        activeOpacity={0.85}
        accessibilityRole="button"
        accessibilityLabel={label}
        style={{
          alignItems: 'center',
          justifyContent: 'center',
          backgroundColor: theme.colors.surface,
          borderColor: theme.colors.border,
          borderWidth: 1,
          borderRadius: theme.radius.lg,
          paddingVertical: theme.spacing.lg,
          minHeight: theme.touchTarget.min,
          overflow: 'hidden',
        }}
      >
        <AppText
          accessibilityElementsHidden
          importantForAccessibility="no"
          style={{ textAlign: 'center', fontSize: 24 }}
        >
          {icon}
        </AppText>
      </TouchableOpacity>
      {/* Hidden from the reader: the button above already carries this text as its name, so leaving
          it exposed would announce every action twice. */}
      <AppText
        accessibilityElementsHidden
        importantForAccessibility="no"
        variant="micro"
        color="textMuted"
        align="center"
        numberOfLines={2}
        style={{ marginTop: 4 }}
      >
        {label}
      </AppText>
    </View>
  );
});
