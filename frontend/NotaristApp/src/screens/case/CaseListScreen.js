import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { View, FlatList, RefreshControl, Alert } from 'react-native';
import Screen from '../../components/Screen';
import SearchBar from '../../components/SearchBar';
import FilterBar from '../../components/FilterBar';
import CaseCard from '../../components/CaseCard';
import EmptyState from '../../components/EmptyState';
import ErrorState from '../../components/ErrorState';
import MockBanner from '../../components/MockBanner';
import OfflineBanner from '../../components/OfflineBanner';
import Banner from '../../components/Banner';
import LoadingSkeleton from '../../components/LoadingSkeleton';
import { SkeletonList } from '../../components/Skeleton';
import { useTheme } from '../../context/ThemeContext';
import { spacing } from '../../theme';
import { relativeTime } from '../../utils/format';
import { CASE_STATUS } from '../../constants/workflow';
import { useCases } from '../../state';

const STATUS_OPTIONS = [
  { value: null, label: 'Semua' },
  ...Object.entries(CASE_STATUS).map(([value, meta]) => ({ value, label: meta.label })),
];

// Module scope so the FlatList sees one stable object/type instead of a fresh one each render.
// Safe to read off the tokens directly: the spacing scale is mode-independent (see theme/tokens.js),
// so unlike a color it cannot change when the theme flips.
const SEPARATOR_HEIGHT = spacing.md;
const CONTENT_STYLE = { padding: spacing.lg, flexGrow: 1 };

export default function CaseListScreen({ navigation, route }) {
  const theme = useTheme();
  const {
    cases, loading, refreshing, loadingMore, error, offline, usingMock, hasMore,
    status, fromCache, lastSyncedAt, unsupportedFilters, applyFilters, refresh, loadMore,
  } = useCases();
  const [localQuery, setLocalQuery] = useState('');
  const debounce = useRef(null);
  const appliedDeepLink = useRef(null);

  // Deep-link from the dashboard: preset a status filter, or open the "new case" intent.
  //
  // Keyed on the params OBJECT, not applied once-ever: this tab stays mounted, and each dashboard
  // tile navigates with a fresh params object. The old `useRef(false)` guard consumed the FIRST deep
  // link and ignored every one after it — tapping "Menunggu QC" then "Draft" switched tabs but left
  // the QC filter in place, silently showing the wrong worklist under the tile the notary pressed.
  // Same-object re-renders (focus changes, parent renders) still apply nothing, which is all the old
  // guard was actually protecting against.
  useEffect(() => {
    if (!route.params || route.params === appliedDeepLink.current) return;
    appliedDeepLink.current = route.params;
    // Key-presence, not truthiness: the "Total Case" tile deep-links with { status: null }, meaning
    // "show me everything" — that must CLEAR an active filter, not be ignored as a missing param.
    if ('status' in route.params) applyFilters({ status: route.params.status || null });
    if (route.params.intent === 'new') promptNewCase();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [route.params]);

  const promptNewCase = () => {
    // Creating a case has no backend endpoint yet — be honest rather than faking a create flow.
    Alert.alert('Case Baru', 'Pembuatan case belum tersedia — endpoint backend /cases belum ada. Fitur ini menyusul di Sprint berikutnya.');
  };

  const onChangeQuery = (text) => {
    setLocalQuery(text);
    if (debounce.current) clearTimeout(debounce.current);
    debounce.current = setTimeout(() => applyFilters({ query: text }), 400);
  };

  const showInitialLoading = loading && cases.length === 0;

  // Dates the rows when they came from disk and the refresh behind them has not landed (or failed).
  // A case list is a worklist; showing yesterday's as though it were today's is a real hazard.
  const staleNotice = useMemo(() => {
    if (!cases.length) return null;
    if (error) {
      return lastSyncedAt
        ? `Menampilkan data tersimpan dari ${relativeTime(lastSyncedAt)}. Pembaruan gagal.`
        : 'Menampilkan data tersimpan. Pembaruan gagal.';
    }
    if (fromCache && lastSyncedAt) return `Data tersimpan dari ${relativeTime(lastSyncedAt)} — memperbarui…`;
    return null;
  }, [cases.length, error, fromCache, lastSyncedAt]);

  // A filter the caller set that the backend cannot apply (api/cases#listCases). This MUST be visible:
  // the rows below are the unfiltered list, and without this banner they read as search results. A
  // notary typing a debtor's name and getting 20 unrelated cases back has been told a lie by the app,
  // not by the backend. `q` has no CaseController param at all — see the backend-blocker note there.
  const unsupportedNotice = useMemo(() => {
    if (!unsupportedFilters || !unsupportedFilters.length) return null;
    const parts = [];
    if (unsupportedFilters.indexOf('query') !== -1) parts.push('pencarian teks');
    if (unsupportedFilters.indexOf('status') !== -1) parts.push('filter status ini');
    if (!parts.length) return null;
    return `Backend belum mendukung ${parts.join(' dan ')}. Daftar di bawah BELUM difilter.`;
  }, [unsupportedFilters]);

  // --- Stable identities for the FlatList (Sprint 4, Task 10) -----------------------------------
  // Every one of these was an inline arrow. FlatList compares renderItem/ListHeaderComponent by
  // identity to decide what to re-render, so a new function each render meant every visible row
  // re-rendered on every keystroke in the search box — and made React.memo(CaseCard) a no-op.

  const openCase = useCallback(
    (item) => navigation.navigate('CaseDetail', { caseId: item.id, caseNumber: item.caseNumber }),
    [navigation],
  );

  const renderItem = useCallback(
    ({ item }) => <CaseCard item={item} onPress={openCase} />,
    [openCase],
  );

  const keyExtractor = useCallback((item) => item.id, []);

  const onSelectStatus = useCallback((v) => applyFilters({ status: v }), [applyFilters]);

  const header = useMemo(() => (
    <View style={{ gap: theme.spacing.md, marginBottom: theme.spacing.md }}>
      {usingMock ? <MockBanner entity="/cases" /> : null}
      {offline ? <OfflineBanner /> : null}
      {staleNotice ? <Banner variant="warning" message={staleNotice} /> : null}
      {unsupportedNotice ? <Banner variant="warning" message={unsupportedNotice} /> : null}
      <SearchBar value={localQuery} onChangeText={onChangeQuery} placeholder="Cari debitur, nomor case, bank…" />
      <FilterBar options={STATUS_OPTIONS} selected={status} onSelect={onSelectStatus} />
    </View>
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ), [theme, usingMock, offline, staleNotice, unsupportedNotice, localQuery, status, onSelectStatus]);

  if (showInitialLoading) {
    return <Screen padded={false}><View style={{ padding: theme.spacing.lg }}>{header}</View><SkeletonList count={6} /></Screen>;
  }

  // Only a genuine dead end gets the error page. With rows on screen — live or cached — the error
  // rides along in the header banner instead, because replacing a readable list with an error panel
  // takes away data the user still has.
  if (error && cases.length === 0) {
    return (
      <Screen>
        {header}
        <ErrorState
          error={error}
          title={offline ? 'Tidak ada koneksi' : 'Gagal memuat case'}
          message={offline ? 'Periksa koneksi internet Anda lalu coba lagi.' : 'Terjadi kesalahan saat memuat daftar case.'}
          onRetry={refresh}
        />
      </Screen>
    );
  }

  return (
    <Screen padded={false}>
      <FlatList
        data={cases}
        keyExtractor={keyExtractor}
        renderItem={renderItem}
        ListHeaderComponent={header}
        ItemSeparatorComponent={Separator}
        contentContainerStyle={CONTENT_STYLE}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} tintColor={theme.colors.primary} />}
        onEndReached={loadMore}
        onEndReachedThreshold={0.4}
        // Virtualization tuning (Sprint 4, Task 10). FlatList already windows; these bound the work
        // it does at the edges. Kept conservative — an aggressive windowSize saves memory but blanks
        // rows during a fast scroll, which reads as a bug.
        initialNumToRender={8}
        maxToRenderPerBatch={8}
        windowSize={11}
        removeClippedSubviews
        ListEmptyComponent={
          <EmptyState
            icon="📁"
            title="Belum ada case"
            description="Case yang cocok dengan filter tidak ditemukan."
            actionLabel="Case Baru"
            onAction={promptNewCase}
          />
        }
        // A skeleton row rather than a spinner: the footer's job is "more rows are coming", and a
        // row-shaped placeholder says that literally (Sprint 4, Task 5).
        ListFooterComponent={loadingMore ? <LoadingSkeleton count={1} /> : null}
      />
    </Screen>
  );
}

// Hoisted to module scope: an inline `() => <View/>` here is a new component type on every render,
// which forces FlatList to remount every separator.
const Separator = () => <View style={{ height: SEPARATOR_HEIGHT }} />;
