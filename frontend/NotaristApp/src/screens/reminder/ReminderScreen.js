import React, { useCallback, useMemo } from 'react';
import { View, FlatList, RefreshControl } from 'react-native';
import Screen from '../../components/Screen';
import FilterBar from '../../components/FilterBar';
import ReminderCard from '../../components/ReminderCard';
import MockBanner from '../../components/MockBanner';
import OfflineBanner from '../../components/OfflineBanner';
import Banner from '../../components/Banner';
import EmptyState from '../../components/EmptyState';
import ErrorState from '../../components/ErrorState';
import { SkeletonList } from '../../components/Skeleton';
import { useTheme } from '../../context/ThemeContext';
import { spacing } from '../../theme';
import { relativeTime } from '../../utils/format';
import usePolledResource from '../../hooks/usePolledResource';
import { useReminders } from '../../state';

const WINDOWS = [
  { value: 'today', label: 'Hari Ini' },
  { value: '7d', label: '7 Hari' },
  { value: '30d', label: '30 Hari' },
];

// Module scope: one stable identity for the list instead of a new one per render. Spacing tokens are
// mode-independent, so reading them outside the component is safe (see theme/tokens.js).
const SEPARATOR_HEIGHT = spacing.md;
const CONTENT_STYLE = { padding: spacing.lg, flexGrow: 1 };
const Separator = () => <View style={{ height: SEPARATOR_HEIGHT }} />;

// Overdue first, then by nearest due date. Hoisted so the comparator is not re-allocated per sort.
const byDueDate = (a, b) => {
  const da = a.dueDate ? Date.parse(a.dueDate) : Infinity;
  const db = b.dueDate ? Date.parse(b.dueDate) : Infinity;
  return da - db;
};

export default function ReminderScreen() {
  const theme = useTheme();
  const {
    filtered, loading, refreshing, error, offline, usingMock, fromCache, lastSyncedAt,
    window, setWindow, refresh,
  } = useReminders();

  // Keep the deadline queue fresh while it is on screen. ReminderContext registers the 'reminders'
  // refresher, but registration alone polls nothing (services/polling.js) — a visible subscriber is
  // what arms focus-refresh and the poll timer, and until this line NO screen subscribed: the one
  // list of statutory deadlines only ever refreshed on mount or a manual pull.
  usePolledResource('reminders');

  // Memoized (Sprint 4, Task 10): this copied and sorted the whole list on EVERY render — including
  // every render caused by pull-to-refresh state — and then handed FlatList a brand-new array, so
  // the list re-diffed all of it each time. It now re-sorts only when the filtered set changes.
  const sorted = useMemo(() => [...filtered].sort(byDueDate), [filtered]);

  const renderItem = useCallback(({ item }) => <ReminderCard item={item} />, []);
  const keyExtractor = useCallback((item) => item.id, []);

  // Reminders are deadlines — an expired SKMHT is the thing this screen exists to catch. Cached rows
  // are worth showing offline, but only if their age is stated.
  const staleNotice = useMemo(() => {
    if (!filtered.length) return null;
    if (error) {
      return lastSyncedAt
        ? `Menampilkan data tersimpan dari ${relativeTime(lastSyncedAt)}. Pembaruan gagal.`
        : 'Menampilkan data tersimpan. Pembaruan gagal.';
    }
    if (fromCache && lastSyncedAt) return `Data tersimpan dari ${relativeTime(lastSyncedAt)} — memperbarui…`;
    return null;
  }, [filtered.length, error, fromCache, lastSyncedAt]);

  const header = useMemo(() => (
    <View style={{ gap: theme.spacing.md, marginBottom: theme.spacing.md }}>
      {usingMock ? <MockBanner entity="/reminders" /> : null}
      {offline ? <OfflineBanner /> : null}
      {staleNotice ? <Banner variant="warning" message={staleNotice} /> : null}
      <FilterBar options={WINDOWS} selected={window} onSelect={setWindow} />
    </View>
  ), [theme, usingMock, offline, staleNotice, window, setWindow]);

  if (loading && !filtered.length) {
    return <Screen padded={false}><View style={{ padding: theme.spacing.lg }}>{header}</View><SkeletonList count={5} /></Screen>;
  }
  if (error && !filtered.length) {
    return <Screen>{header}<ErrorState error={error} message="Gagal memuat pengingat." onRetry={refresh} /></Screen>;
  }

  return (
    <Screen padded={false}>
      <FlatList
        data={sorted}
        keyExtractor={keyExtractor}
        renderItem={renderItem}
        ListHeaderComponent={header}
        ItemSeparatorComponent={Separator}
        contentContainerStyle={CONTENT_STYLE}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} tintColor={theme.colors.primary} />}
        initialNumToRender={8}
        maxToRenderPerBatch={8}
        windowSize={11}
        removeClippedSubviews
        ListEmptyComponent={
          <EmptyState icon="🔔" title="Tidak ada pengingat" description="Tidak ada pengingat dalam rentang waktu ini." />
        }
      />
    </Screen>
  );
}
