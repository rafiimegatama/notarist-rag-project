import React, { useCallback, useMemo } from 'react';
import { View, RefreshControl, TouchableOpacity } from 'react-native';
import Screen from '../../components/Screen';
import AppText from '../../components/AppText';
import StatCard from '../../components/StatCard';
import Accordion from '../../components/Accordion';
import SectionHeader from '../../components/SectionHeader';
import MockBanner from '../../components/MockBanner';
import OfflineBanner from '../../components/OfflineBanner';
import SyncBadge from '../../components/SyncBadge';
import RealtimeBadge from '../../components/RealtimeBadge';
import Banner from '../../components/Banner';
import Card from '../../components/Card';
import BarChart from '../../components/BarChart';
import ErrorState from '../../components/ErrorState';
import { SkeletonList } from '../../components/Skeleton';
import { useTheme } from '../../context/ThemeContext';
import { relativeTime } from '../../utils/format';
import useUser from '../../hooks/useUser';
import useResponsive from '../../hooks/useResponsive';
import usePolledResource from '../../hooks/usePolledResource';
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
  const { isTablet, isLarge } = useResponsive();
  const { summary, loading, refreshing, error, offline, usingMock, fromCache, lastSyncedAt, refresh } = useDashboard();

  // Activate live polling while this screen is focused. DashboardContext only REGISTERS the resource
  // (see its note) — nothing polls until a visible screen subscribes, which is what makes the numbers,
  // the animated counters and the realtime badge below actually live rather than fetch-once.
  usePolledResource('dashboard');

  // Responsive tile grid: 2-up on a phone, 3-up on a tablet, 4-up on a large/landscape tablet or web.
  const cardWidth = isLarge ? '23%' : isTablet ? '31%' : '48%';

  // Honest freshness signal for the animated numbers. Order matters: offline first (nothing is live),
  // then an in-flight refresh, then cached/failed, else genuinely live.
  const realtimeStatus = offline ? 'offline' : refreshing ? 'syncing' : (error || fromCache) ? 'stale' : 'live';

  // Stable identities: these end up as StatCard's `onPress`, and StatCard is memoized. Inline arrows
  // here would hand it a new prop every render and defeat the memo entirely (Sprint 4, Task 10).
  // Always sends the `status` key — null means "clear the filter". CaseListScreen's deep-link effect
  // switches on key presence, so `{}` would be ignored and the Total Case tile would land on whatever
  // filter the list last had.
  const goCases = useCallback(
    (status) => navigation.navigate('Kasus', { status: status ?? null }),
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

  // Pipeline distribution for the workload chart — the five active stages a case moves through, in
  // order. Each bar drills into the same filtered case list its matching tile opens, so the chart is
  // not a second dead-end view of the numbers. Colours mirror the tiles' tones.
  const chartData = useMemo(() => (summary ? [
    { label: 'Draft', value: summary.draft, color: 'textFaint', status: 'DRAFT' },
    { label: 'Menunggu Verifikasi', value: summary.waitingVerification, color: 'warning', status: 'WAITING_VERIFICATION' },
    { label: 'Menunggu QC', value: summary.waitingQc, color: 'info', status: 'WAITING_QC' },
    { label: 'Menunggu Approval', value: summary.waitingApproval, color: 'primary', status: 'WAITING_APPROVAL' },
    { label: 'Siap Kirim', value: summary.readyToSend, color: 'success', status: 'READY_TO_SEND' },
  ] : []), [summary]);

  const onChartPress = useCallback((item) => goCases(item.status), [goCases]);

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
      {/* The greeting row carries the sync badge because the dashboard is the screen a notary opens
          first and returns to between tasks — the one place "your work has not left this device yet"
          is seen without going looking for it. SyncBadge renders null on an empty queue, so this row
          is unchanged in the normal case; the badge routes to the inspector in Settings, which owns
          the detail. */}
      <View style={{ marginBottom: theme.spacing.md, flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between', gap: theme.spacing.sm }}>
        <View style={{ flex: 1 }}>
          <AppText variant="bodySm" color="textMuted">Selamat datang kembali</AppText>
          <AppText variant="h2" numberOfLines={1}>{user?.displayName ?? 'Notaris'}</AppText>
          {/* Freshness of the numbers below — pulses green while live, states plainly when not. Only
              shown once there is a summary to be fresh OR stale about. */}
          {summary ? <RealtimeBadge status={realtimeStatus} style={{ marginTop: theme.spacing.xs }} /> : null}
        </View>
        <SyncBadge onPress={() => navigation.navigate('Settings')} style={{ marginTop: theme.spacing.xs }} />
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
              style={{ width: cardWidth, flexGrow: 1 }}
            />
          ))}
        </View>
      )}

      {/* Workload distribution — the pipeline stages as bars. Rendered only alongside real numbers,
          never over a skeleton or an error. */}
      {summary && !loading ? (
        <>
          <SectionHeader title="Distribusi Beban Kerja" />
          <Card>
            <BarChart data={chartData} onPressItem={onChartPress} />
          </Card>
        </>
      ) : null}

      <SectionHeader title="Aksi Cepat" />
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: theme.spacing.sm }}>
        {ACTIONS.map((a) => (
          <QuickAction key={a.key} icon={a.icon} label={a.label} onPress={actionHandlers[a.key]} />
        ))}
      </View>

      <Accordion title="Alur Kerja" icon="🔄" style={{ marginTop: theme.spacing.xl }}>
        <AppText variant="bodySm" color="textMuted" style={{ lineHeight: 20 }}>
          Case → Bundle → Upload → OCR Review → Verifikasi → Draft → QC → Approval → Terkirim ke Bank.
          Pantau setiap tahap melalui kartu status di atas.
        </AppText>
      </Accordion>
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
