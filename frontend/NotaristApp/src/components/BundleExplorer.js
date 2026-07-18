import React, { useMemo, useState } from 'react';
import { View } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import { stepStatusMeta, STEP_STATUS } from '../constants/workflow';
import { formatDateTime, titleCase } from '../utils/format';
import { bundleStages } from '../models/Bundle';
import AppText from './AppText';
import SearchBar from './SearchBar';
import StatusChip from './StatusChip';
import TreeView from './TreeView';
import BottomSheet from './BottomSheet';
import PrimaryButton from './PrimaryButton';
import SecondaryButton from './SecondaryButton';

const TYPE_ICON = { KTP: '🪪', KK: '👪', NPWP: '🧾', AKTA: '📜', SERTIFIKAT: '🏷️' };
const docIcon = (d) => TYPE_ICON[d.type] || '📄';

// A small status chip built from the shared step-status metadata, so a stage in the tree is coloured
// and labelled exactly like the same status anywhere else in the app.
function StepChip({ status }) {
  const meta = stepStatusMeta(status);
  return <StatusChip label={meta.label} color={meta.color} size="sm" />;
}

// Filter chips over document OCR status. `null` = all. Only the statuses that actually occur in the
// current document set are offered, so a filter never yields a guaranteed-empty result.
function FilterRow({ present, value, onChange, theme }) {
  const options = [{ key: null, label: 'Semua' }, ...present.map((s) => ({ key: s, label: stepStatusMeta(s).label }))];
  return (
    <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: theme.spacing.xs, marginTop: theme.spacing.sm }}>
      {options.map((o) => {
        const active = value === o.key;
        return (
          <StatusChip
            key={o.key ?? 'all'}
            label={o.label}
            color={active ? 'primary' : 'textMuted'}
            tone={active ? 'solid' : 'soft'}
            size="sm"
            onPress={() => onChange(o.key)}
            accessibilityLabel={`Filter ${o.label}${active ? ', aktif' : ''}`}
          />
        );
      })}
    </View>
  );
}

/**
 * Bundle Explorer (Sprint 7) — the bundle as an expandable tree: metadata, workflow stages (with
 * status icons), and documents (searchable, filterable, each opening a preview). One screen answers
 * "what is in this bundle and where is each part", instead of a flat scroll.
 *
 * Honest degrade is baked in, not bolted on: the document list has NO live backend route
 * (api/bundles#getBundleDocuments), so `docsError` renders an explanatory leaf INSIDE the Dokumen
 * branch rather than an empty "no documents" — the count on the bundle can say four while the list is
 * simply unavailable, and the tree must not contradict it.
 */
export default function BundleExplorer({
  bundle,
  docs = [],
  docsLoading = false,
  docsError = null,
  onOpenDocument,
  style,
}) {
  const theme = useTheme();
  const [query, setQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState(null);
  const [preview, setPreview] = useState(null);

  const searching = query.trim().length > 0 || !!statusFilter;

  const filteredDocs = useMemo(() => {
    const q = query.trim().toLowerCase();
    return docs.filter((d) => {
      if (q && !`${d.name || ''} ${d.type || ''}`.toLowerCase().includes(q)) return false;
      if (statusFilter && d.ocrStatus !== statusFilter) return false;
      return true;
    });
  }, [docs, query, statusFilter]);

  // Which OCR statuses actually appear, in the canonical order, for the filter chips.
  const presentStatuses = useMemo(() => {
    const set = new Set(docs.map((d) => d.ocrStatus).filter(Boolean));
    return Object.keys(STEP_STATUS).filter((s) => set.has(s));
  }, [docs]);

  // --- document branch children ---
  const docChildren = useMemo(() => {
    if (docsLoading && !docs.length) return [{ id: 'docs-loading', label: 'Memuat dokumen…', muted: true }];
    if (docsError) {
      return [{
        id: 'docs-error',
        icon: '⚠️',
        label: 'Daftar dokumen tidak tersedia dari server.',
        muted: true,
      }];
    }
    if (!docs.length) return [{ id: 'docs-empty', label: 'Bundle ini belum berisi dokumen.', muted: true }];
    if (!filteredDocs.length) return [{ id: 'docs-nomatch', icon: '🔍', label: 'Tidak ada dokumen yang cocok.', muted: true }];
    return filteredDocs.map((d) => ({
      id: d.id,
      icon: docIcon(d),
      label: d.name || 'Dokumen',
      right: <StepChip status={d.ocrStatus} />,
      onPress: () => setPreview(d),
      accessibilityLabel: `${d.name || 'Dokumen'}, ${d.type || 'dokumen'}, ${stepStatusMeta(d.ocrStatus).label}`,
    }));
  }, [docs, filteredDocs, docsLoading, docsError]);

  // --- metadata branch children ---
  const metaRows = useMemo(() => {
    if (!bundle) return [];
    const rows = [
      ['ID Bundle', bundle.id],
      ['Case', bundle.caseId],
      ['Tipe', bundle.bundleType ? titleCase(bundle.bundleType) : null],
      ['Jumlah dokumen', bundle.documentCount != null ? String(bundle.documentCount) : null],
      ['Perakitan', bundle.assemblyStatus ? titleCase(bundle.assemblyStatus) : null],
      ['Status alur', bundle.workflowStatus ? titleCase(bundle.workflowStatus) : null],
      ['Dibuat', bundle.createdAt ? formatDateTime(bundle.createdAt) : null],
      ['Diperbarui', bundle.updatedAt ? formatDateTime(bundle.updatedAt) : null],
    ];
    return rows
      .filter(([, v]) => v != null && v !== '')
      .map(([k, v], i) => ({
        id: `meta-${i}`,
        label: k,
        right: <AppText variant="micro" color="textMuted" selectable style={{ maxWidth: 180 }} numberOfLines={1}>{v}</AppText>,
        accessibilityLabel: `${k}: ${v}`,
      }));
  }, [bundle]);

  const stageChildren = useMemo(
    () => (bundle ? bundleStages(bundle).map((s) => ({
      id: `stage-${s.key}`,
      label: s.label,
      right: <StepChip status={s.status} />,
      accessibilityLabel: `${s.label}: ${stepStatusMeta(s.status).label}`,
    })) : []),
    [bundle],
  );

  const rootLabel = bundle?.name || (bundle?.bundleType ? titleCase(bundle.bundleType) : 'Bundle');
  const nodes = [{
    id: 'root',
    icon: '📦',
    label: rootLabel,
    defaultExpanded: true,
    right: bundle?.workflowStatus ? <StatusChip label={titleCase(bundle.workflowStatus)} color="info" size="sm" /> : null,
    children: [
      { id: 'meta', icon: 'ℹ️', label: 'Metadata', children: metaRows },
      { id: 'workflow', icon: '🔄', label: 'Alur Kerja', children: stageChildren },
      {
        id: `docs-${searching ? 'search' : 'idle'}`, // id flip auto-opens the branch when a search starts
        icon: '📄',
        label: `Dokumen${docs.length ? ` (${filteredDocs.length}/${docs.length})` : ''}`,
        defaultExpanded: searching || undefined,
        children: docChildren,
      },
    ],
  }];

  return (
    <View style={style}>
      <SearchBar value={query} onChangeText={setQuery} placeholder="Cari dokumen dalam bundle…" />
      {presentStatuses.length > 1 ? (
        <FilterRow present={presentStatuses} value={statusFilter} onChange={setStatusFilter} theme={theme} />
      ) : null}

      <View style={{ marginTop: theme.spacing.md, borderWidth: 1, borderColor: theme.colors.border, borderRadius: theme.radius.lg, overflow: 'hidden', backgroundColor: theme.colors.surface }}>
        <TreeView nodes={nodes} />
      </View>

      {/* Document preview (Sprint 12): a draggable bottom sheet with metadata + an action to open the
          full OCR review. */}
      <BottomSheet visible={!!preview} onClose={() => setPreview(null)}>
        {preview ? (
          <>
            <View style={{ flexDirection: 'row', alignItems: 'center', marginBottom: theme.spacing.md }}>
              <AppText style={{ fontSize: 28, marginRight: theme.spacing.md }}>{docIcon(preview)}</AppText>
              <View style={{ flex: 1 }}>
                <AppText variant="bodyStrong" numberOfLines={2}>{preview.name || 'Dokumen'}</AppText>
                <AppText variant="micro" color="textFaint">{preview.type || '—'} · {preview.pages ?? 1} hal</AppText>
              </View>
            </View>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: theme.spacing.sm, marginBottom: theme.spacing.xl }}>
              <AppText variant="micro" color="textMuted">Status OCR:</AppText>
              <StepChip status={preview.ocrStatus} />
            </View>
            <View style={{ flexDirection: 'row', gap: theme.spacing.md }}>
              <View style={{ flex: 1 }}><SecondaryButton title="Tutup" onPress={() => setPreview(null)} /></View>
              {onOpenDocument ? (
                <View style={{ flex: 1 }}>
                  <PrimaryButton
                    title="Buka OCR"
                    onPress={() => { const d = preview; setPreview(null); onOpenDocument(d); }}
                  />
                </View>
              ) : null}
            </View>
          </>
        ) : null}
      </BottomSheet>
    </View>
  );
}
