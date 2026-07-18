import React, { useState } from 'react';
import { View, ScrollView, TouchableOpacity } from 'react-native';
import Screen from '../../components/Screen';
import AppText from '../../components/AppText';
import Card from '../../components/Card';
import SearchBar from '../../components/SearchBar';
import SearchModeToggle from '../../components/SearchModeToggle';
import SectionHeader from '../../components/SectionHeader';
import StatusChip from '../../components/StatusChip';
import CitationCard from '../../components/CitationCard';
import SecondaryButton from '../../components/SecondaryButton';
import EmptyState from '../../components/EmptyState';
import ErrorState from '../../components/ErrorState';
import { Skeleton } from '../../components/Skeleton';
import { useTheme } from '../../context/ThemeContext';
import { useSearch } from '../../state';

// One tone per member of the backend's GroundingScore.Level enum (HIGH/MEDIUM/LOW/UNGROUNDED).
// UNGROUNDED was absent and fell through to the neutral 'textFaint' default — so the WORST possible
// grounding ("< 0.25: no retrieved chunk supports this") rendered in calmer grey than LOW rendered in
// red, inverting the warning exactly where a notary most needs it. It shares LOW's danger tone.
const GROUNDING_TONE = { HIGH: 'success', MEDIUM: 'warning', LOW: 'danger', UNGROUNDED: 'danger' };

export default function SearchScreen() {
  const theme = useTheme();
  const { query, setQuery, mode, setMode, result, loading, error, offline, recent, saved, run, save, removeSaved, clearRecent } = useSearch();

  const renderResults = () => {
    if (loading) return <View style={{ gap: theme.spacing.md }}><Skeleton width="100%" height={80} /><Skeleton width="100%" height={80} /></View>;
    if (error) {
      return (
        <ErrorState
          error={error}
          title={offline ? 'Tidak ada koneksi' : 'Pencarian gagal'}
          message={offline ? 'Periksa koneksi Anda lalu coba lagi.' : 'Terjadi kesalahan saat mencari. Coba lagi.'}
          onRetry={() => run()}
          fill={false}
        />
      );
    }
    if (!result) return null;
    const citations = result.citations ?? [];
    // A search the backend REJECTED is not a search that found nothing. SearchResponse.error() sends
    // status:"ERROR" with an errorMessage and an empty citation list — so before Sprint 6 both landed
    // in the same "Tidak ada hasil · coba kata kunci lain" empty state, i.e. the app told a notary to
    // rephrase a query that had never actually run, and let them conclude the document does not
    // exist. `failed` reads the server's own verdict rather than inferring from an empty list.
    if (result.failed) {
      return (
        <ErrorState
          title="Pencarian gagal"
          message="Server tidak dapat menyelesaikan pencarian ini. Ini BUKAN berarti dokumen tidak ditemukan — coba lagi."
          onRetry={() => run()}
          fill={false}
        />
      );
    }
    if (!citations.length) {
      return <EmptyState icon="🔍" title="Tidak ada hasil" description="Tidak ditemukan dokumen yang cocok. Coba kata kunci lain." fill={false} />;
    }
    return (
      <View style={{ gap: theme.spacing.md }}>
        <View style={{ flexDirection: 'row', gap: theme.spacing.sm, alignItems: 'center', flexWrap: 'wrap' }}>
          <StatusChip label={`Grounding: ${result.groundingLevel ?? '—'}`} color={GROUNDING_TONE[result.groundingLevel] || 'textFaint'} size="sm" />
          {/* "—ms", not "0ms": an unreported duration is not an instantaneous one. */}
          <AppText variant="micro" color="textFaint">
            {result.retrievedChunkCount ?? citations.length} kutipan · {result.processingTimeMs ?? '—'}ms
          </AppText>
        </View>
        {citations.map((c, i) => <CitationCard key={c.chunkId ?? i} citation={c} index={i} />)}
      </View>
    );
  };

  const showBrowse = !result && !loading && !error;

  return (
    <Screen padded={false} edges={['top']}>
      <ScrollView contentContainerStyle={{ padding: theme.spacing.lg }} keyboardShouldPersistTaps="handled" showsVerticalScrollIndicator={false}>
        <SearchBar value={query} onChangeText={setQuery} onSubmit={() => run()} placeholder="Cari dokumen, klausul, ketentuan…" />
        <SearchModeToggle mode={mode} onChange={setMode} style={{ marginTop: theme.spacing.md }} />
        <View style={{ flexDirection: 'row', gap: theme.spacing.sm, marginTop: theme.spacing.md }}>
          <View style={{ flex: 1 }}><SecondaryButton title="Cari" icon="🔍" onPress={() => run()} disabled={!query.trim()} /></View>
          <SecondaryButton title="☆ Simpan" fullWidth={false} onPress={save} disabled={!query.trim()} />
        </View>

        <View style={{ marginTop: theme.spacing.lg }}>{renderResults()}</View>

        {showBrowse ? (
          <>
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-end' }}>
              <SectionHeader title="Pencarian Terakhir" style={{ marginBottom: 0 }} />
              {recent.length ? (
                <TouchableOpacity
                  onPress={clearRecent}
                  accessibilityRole="button"
                  // "Hapus" alone is ambiguous out of context — a reader announces it with no clue
                  // as to what gets deleted.
                  accessibilityLabel="Hapus riwayat pencarian terakhir"
                  hitSlop={theme.hitSlop}
                  style={{ minHeight: theme.touchTarget.min, justifyContent: 'center' }}
                >
                  <AppText variant="micro" color="primary">Hapus</AppText>
                </TouchableOpacity>
              ) : null}
            </View>
            {recent.length ? (
              <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: theme.spacing.sm, marginTop: theme.spacing.sm }}>
                {recent.map((q) => <StatusChip key={q} label={q} color="textMuted" tone="outline" onPress={() => run(q)} />)}
              </View>
            ) : (
              <AppText variant="bodySm" color="textFaint" style={{ marginTop: theme.spacing.xs }}>Belum ada pencarian.</AppText>
            )}

            <SectionHeader title="Pencarian Tersimpan" />
            {saved.length ? (
              <View style={{ gap: theme.spacing.sm }}>
                {saved.map((s) => (
                  <Card key={s.id}>
                    <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
                      <TouchableOpacity
                        style={{ flex: 1, minHeight: theme.touchTarget.min, justifyContent: 'center' }}
                        onPress={() => { setMode(s.mode); run(s.query); }}
                        accessibilityRole="button"
                        accessibilityLabel={`Jalankan pencarian tersimpan: ${s.query}, mode ${s.mode}`}
                      >
                        <AppText variant="bodySm" numberOfLines={1}>{s.query}</AppText>
                        <AppText variant="micro" color="textFaint">{s.mode}</AppText>
                      </TouchableOpacity>
                      <TouchableOpacity
                        onPress={() => removeSaved(s.id)}
                        hitSlop={theme.hitSlop}
                        accessibilityRole="button"
                        // Was an unlabelled "✕": announced as "times" or skipped entirely, and it is
                        // the destructive control on the row.
                        accessibilityLabel={`Hapus pencarian tersimpan: ${s.query}`}
                        style={{
                          minWidth: theme.touchTarget.min,
                          minHeight: theme.touchTarget.min,
                          alignItems: 'center',
                          justifyContent: 'center',
                        }}
                      >
                        <AppText color="danger">✕</AppText>
                      </TouchableOpacity>
                    </View>
                  </Card>
                ))}
              </View>
            ) : (
              <AppText variant="bodySm" color="textFaint" style={{ marginTop: theme.spacing.xs }}>Belum ada pencarian tersimpan.</AppText>
            )}
          </>
        ) : null}
        <View style={{ height: theme.spacing.xxl }} />
      </ScrollView>
    </Screen>
  );
}
