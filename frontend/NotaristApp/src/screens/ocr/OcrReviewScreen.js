import React, { useEffect, useMemo, useState } from 'react';
import { View, ScrollView, TouchableOpacity } from 'react-native';
import Screen from '../../components/Screen';
import AppText from '../../components/AppText';
import StatusChip from '../../components/StatusChip';
import ConfidenceBadge from '../../components/ConfidenceBadge';
import FieldConfidenceRow from '../../components/FieldConfidenceRow';
import AuthorityPanel from '../../components/AuthorityPanel';
import DirectorTimeline from '../../components/DirectorTimeline';
import PrimaryButton from '../../components/PrimaryButton';
import DangerButton from '../../components/DangerButton';
import BottomActionBar from '../../components/BottomActionBar';
import MockBanner from '../../components/MockBanner';
import ErrorState from '../../components/ErrorState';
import { Skeleton } from '../../components/Skeleton';
import { useTheme } from '../../context/ThemeContext';
import useAsync from '../../hooks/useAsync';
import useResponsive from '../../hooks/useResponsive';
import { OCRService } from '../../services';
import { confidenceColorKey } from '../../components/ConfidenceBadge';

// LEFT pane: a placeholder document page with extracted-field bounding boxes overlaid. Real PDF
// rendering needs a native pdf component + the file URI (not available yet) — the boxes use each
// field's relative bbox so the highlight interaction is real against a stand-in page.
function PreviewPane({ data, fields, activeId, onPick, layers, onToggleLayer }) {
  const theme = useTheme();
  return (
    <View style={{ flex: 1 }}>
      <View style={{ aspectRatio: 0.72, backgroundColor: theme.colors.surfaceAlt, borderRadius: theme.radius.md, borderWidth: 1, borderColor: theme.colors.border, overflow: 'hidden' }}>
        <View style={{ position: 'absolute', top: 8, left: 8, right: 8, flexDirection: 'row', gap: 6, zIndex: 1 }}>
          {data.signatureDetected && layers.signature ? <StatusChip label="✒️ Tanda tangan" color="info" size="sm" /> : null}
          {data.stampDetected && layers.stamp ? <StatusChip label="🔖 Stempel" color="warning" size="sm" /> : null}
        </View>
        <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center' }}>
          <AppText color="textFaint" variant="micro">Pratinjau {data.documentName}</AppText>
        </View>
        {layers.ocr && fields.map((f) => {
          const active = f.id === activeId;
          const col = theme.colors[layers.confidence ? confidenceColorKey(f.confidence) : 'primary'];
          return (
            <TouchableOpacity
              key={f.id}
              onPress={() => onPick(f.id)}
              accessibilityLabel={`Sorot ${f.label}`}
              activeOpacity={0.7}
              style={{
                position: 'absolute',
                left: `${f.bbox.x * 100}%`, top: `${f.bbox.y * 100}%`,
                width: `${f.bbox.w * 100}%`, height: `${f.bbox.h * 100}%`,
                borderWidth: active ? 2 : 1, borderColor: col,
                backgroundColor: active ? col + '33' : col + '18', borderRadius: 3,
              }}
            />
          );
        })}
      </View>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 4, marginTop: theme.spacing.sm }}>
        {['ocr', 'stamp', 'signature', 'confidence'].map((k) => (
          <StatusChip
            key={k}
            label={{ ocr: 'OCR', stamp: 'Stempel', signature: 'Tanda Tangan', confidence: 'Confidence' }[k]}
            color={layers[k] ? 'primary' : 'textFaint'}
            tone={layers[k] ? 'soft' : 'outline'}
            size="sm"
            onPress={() => onToggleLayer(k)}
          />
        ))}
      </View>
    </View>
  );
}

export default function OcrReviewScreen({ route }) {
  const theme = useTheme();
  const { documentId } = route.params ?? {};
  const { splitView } = useResponsive();
  const query = useAsync(() => OCRService.getFields(documentId), [documentId]);

  const [fields, setFields] = useState([]);
  const [activeId, setActiveId] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [layers, setLayers] = useState({ ocr: true, stamp: true, signature: true, confidence: false });

  useEffect(() => { if (query.data) setFields(query.data.fields ?? []); }, [query.data]);

  const setField = (id, patch) => setFields((fs) => fs.map((f) => (f.id === id ? { ...f, ...patch } : f)));
  const toggleLayer = (k) => setLayers((l) => ({ ...l, [k]: !l[k] }));
  const onEdit = async (id, value) => { setField(id, { value }); await OCRService.submitFieldDecision(documentId, id, 'NEEDS_CHECK', value); };
  const onDecision = async (id, decision) => { setField(id, { status: decision }); await OCRService.submitFieldDecision(documentId, id, decision); };

  const approveAll = async () => {
    setSubmitting(true);
    try {
      await Promise.all(fields.map((f) => OCRService.submitFieldDecision(documentId, f.id, 'APPROVED')));
      setFields((fs) => fs.map((f) => ({ ...f, status: 'APPROVED' })));
    } finally {
      setSubmitting(false);
    }
  };

  const pending = useMemo(() => fields.filter((f) => f.status !== 'APPROVED').length, [fields]);

  if (query.loading && !query.data) {
    return <Screen scroll><Skeleton width="100%" height={220} /><Skeleton width="100%" height={120} style={{ marginTop: 16 }} /></Screen>;
  }
  if (query.error && !query.data) {
    return <Screen><ErrorState message="Gagal memuat hasil OCR." onRetry={query.reload} /></Screen>;
  }
  const data = query.data;

  const rightPane = (
    <View style={{ flex: 1, gap: theme.spacing.md }}>
      {OCRService.usingMock ? <MockBanner entity="OCR" /> : null}
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
        <AppText variant="label" color="textMuted">Field Terekstrak ({fields.length})</AppText>
        <ConfidenceBadge value={data.overallConfidence} />
      </View>
      {fields.map((f) => (
        <FieldConfidenceRow key={f.id} field={f} active={f.id === activeId} onFocus={setActiveId} onEdit={onEdit} onDecision={onDecision} />
      ))}

      <AppText variant="label" color="textMuted" style={{ marginTop: theme.spacing.sm }}>Timeline Direksi</AppText>
      <DirectorTimeline entries={data.authorityTimeline ?? []} />

      <AuthorityPanel
        stampDetected={data.stampDetected}
        signatureDetected={data.signatureDetected}
      />
      <View style={{ height: theme.spacing.xxl }} />
    </View>
  );

  const leftPane = (
    <PreviewPane data={data} fields={fields} activeId={activeId} onPick={setActiveId} layers={layers} onToggleLayer={toggleLayer} />
  );

  return (
    <Screen padded={false} edges={['top']}>
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
        <DangerButton title="Tolak Semua" onPress={() => setFields((fs) => fs.map((f) => ({ ...f, status: 'REJECTED' })))} />
        <PrimaryButton title={pending ? `Setujui Semua (${pending})` : 'Semua Disetujui'} loading={submitting} disabled={pending === 0} onPress={approveAll} />
      </BottomActionBar>
    </Screen>
  );
}
