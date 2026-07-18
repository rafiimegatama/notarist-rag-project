import React, { useEffect, useMemo, useRef, useState } from 'react';
import { View, ScrollView, TouchableOpacity, Alert } from 'react-native';
import Screen from '../../components/Screen';
import AppText from '../../components/AppText';
import StatusChip from '../../components/StatusChip';
import ConfidenceBadge from '../../components/ConfidenceBadge';
import FieldConfidenceRow from '../../components/FieldConfidenceRow';
import AuthorityPanel from '../../components/AuthorityPanel';
import DirectorTimeline from '../../components/DirectorTimeline';
import PrimaryButton from '../../components/PrimaryButton';
import DangerButton from '../../components/DangerButton';
import Button from '../../components/Button';
import SearchBar from '../../components/SearchBar';
import BottomActionBar from '../../components/BottomActionBar';
import ErrorState from '../../components/ErrorState';
import DemoWatermark from '../../components/DemoWatermark';
import ZoomPanRotate from '../../components/ZoomPanRotate';
import { Skeleton } from '../../components/Skeleton';
import { useTheme } from '../../context/ThemeContext';
import useAsync from '../../hooks/useAsync';
import useResponsive from '../../hooks/useResponsive';
import { OCRService } from '../../services';
import { confidenceColorKey } from '../../components/ConfidenceBadge';
import { MOCK_OCR_FIELDS } from '../../mocks/fixtures';

// Demo data is DEV-ONLY (__DEV__). A production build never renders the demo toggle, so it always
// behaves exactly as before — honest 404 for the un-provisioned OCR endpoint, never a fabricated NIK.
const DEMO_AVAILABLE = typeof __DEV__ !== 'undefined' && __DEV__;
const DEMO_OCR = MOCK_OCR_FIELDS['doc-1'];

// The page + its overlaid field bounding boxes. Lives INSIDE ZoomPanRotate so the boxes transform
// with the page and a highlight stays locked to its field through zoom/pan/rotate. Edited fields
// (value differs from the original OCR read) get a dashed border — the "difference highlight".
function PageWithBoxes({ data, fields, activeId, editedIds, onPick, layers }) {
  const theme = useTheme();
  return (
    <View style={{ flex: 1 }}>
      <View style={{ position: 'absolute', top: 8, left: 8, right: 8, flexDirection: 'row', gap: 6, zIndex: 1 }}>
        {data.signatureDetected && layers.signature ? <StatusChip label="✒️ Tanda tangan" color="info" size="sm" /> : null}
        {data.stampDetected && layers.stamp ? <StatusChip label="🔖 Stempel" color="warning" size="sm" /> : null}
      </View>
      <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center' }}>
        <AppText color="textFaint" variant="micro">Pratinjau {data.documentName}</AppText>
      </View>
      {layers.ocr && fields.map((f) => {
        const active = f.id === activeId;
        const edited = editedIds.has(f.id);
        const col = theme.colors[layers.confidence ? confidenceColorKey(f.confidence) : 'primary'];
        return (
          <TouchableOpacity
            key={f.id}
            onPress={() => onPick(f.id)}
            accessibilityLabel={`Sorot ${f.label}${edited ? ', diubah' : ''}`}
            activeOpacity={0.7}
            style={{
              position: 'absolute',
              left: `${f.bbox.x * 100}%`, top: `${f.bbox.y * 100}%`,
              width: `${f.bbox.w * 100}%`, height: `${f.bbox.h * 100}%`,
              borderWidth: active ? 2 : 1,
              borderStyle: edited ? 'dashed' : 'solid',
              borderColor: edited ? theme.colors.warning : col,
              backgroundColor: active ? col + '33' : col + '18',
              borderRadius: 3,
            }}
          />
        );
      })}
    </View>
  );
}

// A small overview of the page with every field box, the active one highlighted. Tapping a box
// focuses that field — the "mini map". Fixed aspect, so it reads as a page thumbnail.
function MiniMap({ fields, activeId, editedIds, onPick }) {
  const theme = useTheme();
  return (
    <View style={{ width: 84, aspectRatio: 0.72, backgroundColor: theme.colors.surfaceAlt, borderRadius: theme.radius.sm, borderWidth: 1, borderColor: theme.colors.border, overflow: 'hidden' }}>
      {fields.map((f) => {
        const active = f.id === activeId;
        return (
          <TouchableOpacity
            key={f.id}
            onPress={() => onPick(f.id)}
            accessibilityLabel={`Peta mini: ${f.label}`}
            style={{
              position: 'absolute',
              left: `${f.bbox.x * 100}%`, top: `${f.bbox.y * 100}%`,
              width: `${f.bbox.w * 100}%`, height: `${Math.max(f.bbox.h, 0.03) * 100}%`,
              backgroundColor: active ? theme.colors.primary : (editedIds.has(f.id) ? theme.colors.warning : theme.colors.border),
              borderRadius: 1,
            }}
          />
        );
      })}
    </View>
  );
}

export default function OcrReviewScreen({ route }) {
  const theme = useTheme();
  const { documentId } = route.params ?? {};
  const { splitView } = useResponsive();
  const query = useAsync(() => OCRService.getFields(documentId), [documentId]);

  const [demo, setDemo] = useState(false);
  const [fields, setFields] = useState([]);
  const [activeId, setActiveId] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [search, setSearch] = useState('');
  const [layers, setLayers] = useState({ ocr: true, stamp: true, signature: true, confidence: false });

  const data = demo ? DEMO_OCR : query.data;

  // Original OCR values, captured once per loaded document, so an edit can be detected (difference
  // highlight) and shown against what the scanner actually read.
  const originalById = useMemo(() => {
    const map = {};
    (data?.fields ?? []).forEach((f) => { map[f.id] = f.value; });
    return map;
  }, [data]);

  // Undo/redo history over the field set. Every human decision is reversible before submit.
  const undoStack = useRef([]);
  const redoStack = useRef([]);
  const [histTick, setHistTick] = useState(0); // re-render the undo/redo buttons on stack change

  useEffect(() => {
    setFields(data?.fields ?? []);
    undoStack.current = [];
    redoStack.current = [];
    setHistTick((t) => t + 1);
  }, [data]);

  const commit = (updater) => {
    setFields((prev) => {
      const next = typeof updater === 'function' ? updater(prev) : updater;
      undoStack.current.push(prev);
      redoStack.current = [];
      setHistTick((t) => t + 1);
      return next;
    });
  };
  const undo = () => {
    if (!undoStack.current.length) return;
    setFields((prev) => { redoStack.current.push(prev); const restored = undoStack.current.pop(); setHistTick((t) => t + 1); return restored; });
  };
  const redo = () => {
    if (!redoStack.current.length) return;
    setFields((prev) => { undoStack.current.push(prev); const restored = redoStack.current.pop(); setHistTick((t) => t + 1); return restored; });
  };

  const setField = (id, patch) => setFields((fs) => fs.map((f) => (f.id === id ? { ...f, ...patch } : f)));
  const toggleLayer = (k) => setLayers((l) => ({ ...l, [k]: !l[k] }));

  // Server write — SKIPPED in demo mode (the endpoint 404s and there is nothing to persist; the demo
  // is a UI showcase, so its decisions live only in local state under the watermark).
  const writeField = async (id, decision, value, previous) => {
    if (demo) return;
    try {
      await OCRService.submitFieldDecision(documentId, id, decision, value);
    } catch (err) {
      setField(id, previous);
      Alert.alert('Gagal menyimpan', err?.message ?? 'Keputusan tidak tersimpan. Coba lagi.');
    }
  };

  const onEdit = async (id, value) => {
    const before = fields.find((f) => f.id === id);
    commit((fs) => fs.map((f) => (f.id === id ? { ...f, value } : f)));
    await writeField(id, 'NEEDS_CHECK', value, { value: before ? before.value : null });
  };
  const onDecision = async (id, decision) => {
    const before = fields.find((f) => f.id === id);
    commit((fs) => fs.map((f) => (f.id === id ? { ...f, status: decision } : f)));
    await writeField(id, decision, undefined, { status: before ? before.status : null });
  };

  // One bulk decision path for both buttons. "Tolak Semua" used to be a bare local commit() — no
  // server call at all, unlike approveAll and every single-field decision — so a notary saw every
  // field REJECTED while nothing was recorded anywhere: exactly the "write that only pretends to
  // persist" this app's api layer deletes mocks over. Both bulk actions now write per field and
  // only mark the fields whose POST actually landed.
  const decideAll = async (decision, verb) => {
    setSubmitting(true);
    try {
      const targets = fields.filter((f) => f.status !== decision);
      if (demo) {
        commit((fs) => fs.map((f) => ({ ...f, status: decision })));
        return;
      }
      const results = await Promise.allSettled(
        targets.map((f) => OCRService.submitFieldDecision(documentId, f.id, decision)),
      );
      const landed = {};
      results.forEach((r, i) => { if (r.status === 'fulfilled') landed[targets[i].id] = true; });
      commit((fs) => fs.map((f) => (landed[f.id] ? { ...f, status: decision } : f)));
      const failed = results.length - Object.keys(landed).length;
      if (failed > 0) {
        Alert.alert('Sebagian gagal', `${failed} dari ${results.length} field gagal ${verb} dan tetap belum diputuskan. Coba lagi untuk sisanya.`);
      }
    } finally {
      setSubmitting(false);
    }
  };

  const approveAll = () => decideAll('APPROVED', 'disetujui');
  const rejectAll = () => decideAll('REJECTED', 'ditolak');

  const editedIds = useMemo(() => {
    const set = new Set();
    fields.forEach((f) => { if (originalById[f.id] !== undefined && f.value !== originalById[f.id]) set.add(f.id); });
    return set;
  }, [fields, originalById]);

  const visibleFields = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return fields;
    return fields.filter((f) => `${f.label || ''} ${f.value || ''}`.toLowerCase().includes(q));
  }, [fields, search]);

  const pending = useMemo(() => fields.filter((f) => f.status !== 'APPROVED').length, [fields]);

  if (!demo && query.loading && !query.data) {
    return <Screen scroll><Skeleton width="100%" height={220} /><Skeleton width="100%" height={120} style={{ marginTop: 16 }} /></Screen>;
  }
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

  const rightPane = (
    <View style={{ flex: 1, gap: theme.spacing.md }}>
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
        <AppText variant="label" color="textMuted">Field Terekstrak ({visibleFields.length}/{fields.length})</AppText>
        <ConfidenceBadge value={data.overallConfidence} />
      </View>

      <SearchBar value={search} onChangeText={setSearch} placeholder="Cari field…" />

      {visibleFields.map((f) => (
        <View key={f.id}>
          <FieldConfidenceRow field={f} active={f.id === activeId} onFocus={setActiveId} onEdit={onEdit} onDecision={onDecision} />
          {editedIds.has(f.id) ? (
            <AppText variant="micro" color="warning" style={{ marginTop: 2, marginLeft: theme.spacing.sm }}>
              ✎ Diubah dari “{originalById[f.id]}”
            </AppText>
          ) : null}
        </View>
      ))}
      {!visibleFields.length ? (
        <AppText variant="bodySm" color="textFaint" style={{ paddingVertical: theme.spacing.md }}>Tidak ada field yang cocok.</AppText>
      ) : null}

      <AppText variant="label" color="textMuted" style={{ marginTop: theme.spacing.sm }}>Timeline Direksi</AppText>
      <DirectorTimeline entries={data.authorityTimeline ?? []} />
      <AuthorityPanel stampDetected={data.stampDetected} signatureDetected={data.signatureDetected} />
      <View style={{ height: theme.spacing.xxl }} />
    </View>
  );

  const leftPane = (
    <View style={{ flex: 1 }}>
      <View style={{ flexDirection: 'row', gap: theme.spacing.md }}>
        <ZoomPanRotate style={{ flex: 1 }}>
          <PageWithBoxes data={data} fields={fields} activeId={activeId} editedIds={editedIds} onPick={setActiveId} layers={layers} />
        </ZoomPanRotate>
        <View style={{ gap: theme.spacing.sm }}>
          <MiniMap fields={fields} activeId={activeId} editedIds={editedIds} onPick={setActiveId} />
          <AppText variant="micro" color="textFaint" align="center">Peta</AppText>
        </View>
      </View>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 4, marginTop: theme.spacing.sm }}>
        {['ocr', 'stamp', 'signature', 'confidence'].map((k) => (
          <StatusChip
            key={k}
            label={{ ocr: 'OCR', stamp: 'Stempel', signature: 'Tanda Tangan', confidence: 'Heatmap' }[k]}
            color={layers[k] ? 'primary' : 'textFaint'}
            tone={layers[k] ? 'soft' : 'outline'}
            size="sm"
            onPress={() => toggleLayer(k)}
          />
        ))}
      </View>
    </View>
  );

  return (
    <Screen padded={false} edges={['top']}>
      {demo ? <DemoWatermark /> : null}

      {/* Undo / redo toolbar (Sprint 4). */}
      <View style={{ flexDirection: 'row', gap: theme.spacing.sm, paddingHorizontal: theme.spacing.lg, paddingTop: theme.spacing.sm }}>
        <StatusChip label="↶ Urungkan" color={undoStack.current.length ? 'primary' : 'textFaint'} tone="outline" size="sm" onPress={undo} />
        <StatusChip label="↷ Ulangi" color={redoStack.current.length ? 'primary' : 'textFaint'} tone="outline" size="sm" onPress={redo} />
      </View>

      {splitView ? (
        <View style={{ flex: 1, flexDirection: 'row', gap: theme.spacing.lg, padding: theme.spacing.lg }}>
          <View style={{ flex: 1 }}>{leftPane}</View>
          <ScrollView style={{ flex: 1 }} showsVerticalScrollIndicator={false}>{rightPane}</ScrollView>
        </View>
      ) : (
        <ScrollView contentContainerStyle={{ padding: theme.spacing.lg }} showsVerticalScrollIndicator={false}>
          {leftPane}
          <View style={{ height: theme.spacing.lg }} />
          {rightPane}
        </ScrollView>
      )}

      <BottomActionBar>
        <DangerButton title="Tolak Semua" disabled={submitting} onPress={rejectAll} />
        <PrimaryButton title={pending ? `Setujui Semua (${pending})` : 'Semua Disetujui'} loading={submitting} disabled={pending === 0} onPress={approveAll} />
      </BottomActionBar>
    </Screen>
  );
}
