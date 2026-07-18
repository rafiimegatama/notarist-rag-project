import React, { useEffect, useMemo, useState } from 'react';
import { View, ScrollView, Alert } from 'react-native';
import Screen from '../../components/Screen';
import AppText from '../../components/AppText';
import ChecklistCard from '../../components/ChecklistCard';
import PrimaryButton from '../../components/PrimaryButton';
import SecondaryButton from '../../components/SecondaryButton';
import Button from '../../components/Button';
import BottomActionBar from '../../components/BottomActionBar';
import SearchBar from '../../components/SearchBar';
import StatusChip from '../../components/StatusChip';
import ProgressRing from '../../components/ProgressRing';
import BottomSheet from '../../components/BottomSheet';
import DemoWatermark from '../../components/DemoWatermark';
import EmptyState from '../../components/EmptyState';
import ErrorState from '../../components/ErrorState';
import { SkeletonList } from '../../components/Skeleton';
import { useTheme } from '../../context/ThemeContext';
import useAsync from '../../hooks/useAsync';
import { VerificationService } from '../../services';
import { MOCK_CHECKLIST } from '../../mocks/fixtures';

// DEV-ONLY demo data (see OcrReviewScreen for the rationale): a production build never shows the demo
// toggle, so verification always behaves as before — honest 404 for the un-provisioned endpoint.
const DEMO_AVAILABLE = typeof __DEV__ !== 'undefined' && __DEV__;
const DEMO_VERIF = MOCK_CHECKLIST['bnd-001'];

function checklistSubtitle(item) {
  const parts = [];
  if (item.category) parts.push(item.category);
  if (item.mandatory === true) parts.push('Wajib');
  else if (item.mandatory === false) parts.push('Opsional');
  else parts.push('Wajib: —');
  return parts.join(' · ');
}

const FILTERS = [
  { key: 'all', label: 'Semua' },
  { key: 'undecided', label: 'Belum diputus' },
  { key: 'mandatory', label: 'Wajib' },
];

export default function VerificationScreen({ navigation, route }) {
  const theme = useTheme();
  const { bundleId, mode } = route.params ?? {};
  const isQc = mode === 'qc';
  const query = useAsync(() => VerificationService.getChecklist(bundleId), [bundleId]);

  const [demo, setDemo] = useState(false);
  const [decisions, setDecisions] = useState({}); // itemId -> decision
  const [comments, setComments] = useState({});   // itemId -> comment
  const [submitting, setSubmitting] = useState(false);
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState('all');
  const [auditOpen, setAuditOpen] = useState(false);

  const verification = demo ? DEMO_VERIF : (query.data ?? null);
  const items = verification?.checklist ?? [];
  const decidedCount = items.filter((i) => decisions[i.id]).length;
  const progress = items.length ? decidedCount / items.length : 0;
  const complete = items.length > 0 && decidedCount === items.length;

  useEffect(() => { setDecisions({}); setComments({}); }, [verification]);

  const decide = (id, val) => setDecisions((s) => ({ ...s, [id]: val }));
  const comment = (id, val) => setComments((s) => ({ ...s, [id]: val }));
  const approveAll = () => setDecisions(Object.fromEntries(items.map((i) => [i.id, 'APPROVED'])));

  // Filter + search, then group by category (checklist groups).
  const groups = useMemo(() => {
    const q = search.trim().toLowerCase();
    const filtered = items.filter((i) => {
      if (q && !`${i.title || ''} ${i.category || ''}`.toLowerCase().includes(q)) return false;
      if (filter === 'undecided' && decisions[i.id]) return false;
      if (filter === 'mandatory' && i.mandatory !== true) return false;
      return true;
    });
    const byCat = new Map();
    for (const i of filtered) {
      const cat = i.category || 'Lainnya';
      if (!byCat.has(cat)) byCat.set(cat, []);
      byCat.get(cat).push(i);
    }
    return Array.from(byCat, ([category, list]) => ({ category, items: list }));
  }, [items, search, filter, decisions]);

  // Completion summary counts, by decision outcome.
  const summary = useMemo(() => {
    const out = { APPROVED: 0, REJECTED: 0, NEEDS_CHECK: 0, other: 0 };
    for (const i of items) {
      const d = decisions[i.id];
      if (!d) continue;
      if (out[d] !== undefined) out[d] += 1; else out.other += 1;
    }
    return out;
  }, [items, decisions]);

  const auditEntries = useMemo(
    () => items.filter((i) => decisions[i.id]).map((i) => ({ id: i.id, title: i.title, decision: decisions[i.id], comment: comments[i.id] || null })),
    [items, decisions, comments],
  );

  const submit = async () => {
    if (decidedCount < items.length) {
      Alert.alert('Belum lengkap', 'Masih ada item yang belum diberi keputusan.');
      return;
    }
    if (demo) {
      Alert.alert('Demo', 'Ini data contoh — keputusan tidak dikirim ke server.', [{ text: 'OK' }]);
      return;
    }
    setSubmitting(true);
    try {
      const payload = items.map((i) => ({ itemId: i.id, decision: decisions[i.id], comment: comments[i.id] || null }));
      await VerificationService.submit(bundleId, payload);
      Alert.alert(isQc ? 'QC Selesai' : 'Verifikasi Terkirim', 'Keputusan berhasil disimpan.', [
        { text: 'OK', onPress: () => navigation.goBack() },
      ]);
    } catch (err) {
      Alert.alert('Gagal', err?.message ?? 'Tidak dapat mengirim keputusan. Coba lagi.');
      query.reload();
    } finally {
      setSubmitting(false);
    }
  };

  useEffect(() => { navigation.setOptions?.({ title: isQc ? 'QC Checklist' : 'Verifikasi' }); }, [isQc, navigation]);

  if (!demo && query.loading && !query.data) return <Screen padded={false}><SkeletonList count={4} /></Screen>;
  if (!demo && query.error && !query.data) {
    return (
      <Screen>
        <ErrorState error={query.error} onRetry={query.reload} />
        {DEMO_AVAILABLE ? (
          <View style={{ paddingHorizontal: theme.spacing.xl, paddingBottom: theme.spacing.xl }}>
            <Button title="Lihat UI dengan data contoh" variant="secondary" icon="🧪" onPress={() => setDemo(true)} />
            <AppText variant="micro" color="textFaint" align="center" style={{ marginTop: theme.spacing.sm }}>
              Hanya untuk demonstrasi UI (build pengembang). Bukan data asli.
            </AppText>
          </View>
        ) : null}
      </Screen>
    );
  }

  return (
    <Screen padded={false} edges={['top']}>
      {demo ? <DemoWatermark /> : null}

      {/* Sticky summary — progress ring + counts, always visible above the scrolling checklist. */}
      {items.length ? (
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: theme.spacing.lg, padding: theme.spacing.lg, borderBottomWidth: 1, borderBottomColor: theme.colors.border, backgroundColor: theme.colors.surface }}>
          <ProgressRing size={72} dots={16} dotSize={6} progress={progress} color={complete ? 'success' : 'primary'}>
            <AppText variant="bodyStrong">{Math.round(progress * 100)}%</AppText>
          </ProgressRing>
          <View style={{ flex: 1 }}>
            <AppText variant="h3">{isQc ? 'Quality Control' : 'Human Verification'}</AppText>
            <AppText variant="bodySm" color="textMuted">{decidedCount}/{items.length} item diputuskan</AppText>
            <View style={{ flexDirection: 'row', gap: theme.spacing.sm, marginTop: theme.spacing.xs }}>
              <StatusChip label={`✓ ${summary.APPROVED}`} color="success" size="sm" />
              <StatusChip label={`✕ ${summary.REJECTED}`} color="danger" size="sm" />
              <StatusChip label="Audit" color="info" size="sm" tone="outline" onPress={() => setAuditOpen(true)} />
            </View>
          </View>
        </View>
      ) : null}

      <ScrollView contentContainerStyle={{ padding: theme.spacing.lg, gap: theme.spacing.md }} showsVerticalScrollIndicator={false}>
        {items.length ? (
          <>
            <SearchBar value={search} onChangeText={setSearch} placeholder="Cari item checklist…" />
            <View style={{ flexDirection: 'row', gap: theme.spacing.xs }}>
              {FILTERS.map((f) => (
                <StatusChip
                  key={f.key}
                  label={f.label}
                  color={filter === f.key ? 'primary' : 'textMuted'}
                  tone={filter === f.key ? 'solid' : 'soft'}
                  size="sm"
                  onPress={() => setFilter(f.key)}
                />
              ))}
            </View>

            {complete ? (
              <View style={{ padding: theme.spacing.lg, borderRadius: theme.radius.lg, borderWidth: 1, borderColor: theme.colors.success, backgroundColor: theme.colors.surface }}>
                <AppText variant="bodyStrong" color="success">✓ Semua item telah diputuskan</AppText>
                <AppText variant="bodySm" color="textMuted" style={{ marginTop: 2 }}>
                  {summary.APPROVED} disetujui · {summary.REJECTED} ditolak · {summary.NEEDS_CHECK} perlu cek. Siap {isQc ? 'menyelesaikan QC' : 'dikirim'}.
                </AppText>
              </View>
            ) : null}

            {groups.length ? groups.map((g) => (
              <View key={g.category} style={{ gap: theme.spacing.sm }}>
                <AppText variant="label" color="textMuted" accessibilityRole="header" style={{ textTransform: 'uppercase', letterSpacing: 0.6, marginTop: theme.spacing.sm }}>
                  {g.category} ({g.items.length})
                </AppText>
                {g.items.map((i) => (
                  <ChecklistCard
                    key={i.id}
                    title={i.title ?? '—'}
                    subtitle={checklistSubtitle(i)}
                    decision={decisions[i.id]}
                    comment={comments[i.id] || ''}
                    onDecide={(val) => decide(i.id, val)}
                    onComment={(val) => comment(i.id, val)}
                  />
                ))}
              </View>
            )) : (
              <AppText variant="bodySm" color="textFaint" style={{ paddingVertical: theme.spacing.md }}>Tidak ada item yang cocok.</AppText>
            )}
          </>
        ) : (
          <EmptyState icon="✅" title="Tidak ada item" description="Bundle ini belum memiliki checklist verifikasi." fill={false} />
        )}
        <View style={{ height: theme.spacing.xxl }} />
      </ScrollView>

      {items.length ? (
        <BottomActionBar>
          <SecondaryButton title="Setujui Semua" onPress={approveAll} />
          <PrimaryButton title={isQc ? 'Selesaikan QC' : 'Kirim Verifikasi'} loading={submitting} onPress={submit} />
        </BottomActionBar>
      ) : null}

      {/* Audit drawer — the decisions recorded this session (Sprint 5). */}
      <BottomSheet visible={auditOpen} onClose={() => setAuditOpen(false)}>
        <AppText variant="h3" style={{ marginBottom: theme.spacing.md }}>Jejak Audit</AppText>
        {auditEntries.length ? auditEntries.map((e) => (
          <View key={e.id} style={{ paddingVertical: theme.spacing.sm, borderBottomWidth: 1, borderBottomColor: theme.colors.border }}>
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: theme.spacing.sm }}>
              <AppText variant="bodySm" style={{ flex: 1 }} numberOfLines={2}>{e.title ?? '—'}</AppText>
              <StatusChip label={e.decision} color={e.decision === 'APPROVED' ? 'success' : e.decision === 'REJECTED' ? 'danger' : 'warning'} size="sm" />
            </View>
            {e.comment ? <AppText variant="micro" color="textFaint" style={{ marginTop: 2 }}>“{e.comment}”</AppText> : null}
          </View>
        )) : (
          <AppText variant="bodySm" color="textFaint">Belum ada keputusan tercatat.</AppText>
        )}
      </BottomSheet>
    </Screen>
  );
}
