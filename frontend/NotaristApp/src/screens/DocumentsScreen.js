import React, { useState, useEffect, useCallback, useRef } from 'react';
import {
  View,
  FlatList,
  TouchableOpacity,
  ActivityIndicator,
  Alert,
  RefreshControl,
  Modal,
} from 'react-native';
import * as DocumentPicker from 'expo-document-picker';
import { listDocuments, initiateUpload, uploadFileToSignedUrl, confirmUpload, computeFileSha256, getIngestionStatus } from '../api/documents';
import { useTheme } from '../context/ThemeContext';
import useThemedStyles from '../hooks/useThemedStyles';
import AppText from '../components/AppText';
import EmptyState from '../components/EmptyState';
import { SkeletonList } from '../components/Skeleton';

const TERMINAL_PIPELINE_STATUSES = new Set(['COMPLETED', 'FAILED', 'DLQ']);

const DOC_TYPE_LABELS = {
  AKTA: 'Akta',
  REGULASI: 'Regulasi',
  SOP: 'SOP',
};

const DOCUMENT_TYPES = ['AKTA', 'REGULASI', 'SOP'];
const CLASSIFICATION_LEVELS = ['PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'STRICTLY_CONFIDENTIAL'];
const CLASSIFICATION_LABELS = {
  PUBLIC: 'Publik',
  INTERNAL: 'Internal',
  CONFIDENTIAL: 'Rahasia',
  STRICTLY_CONFIDENTIAL: 'Sangat Rahasia',
};

// Pipeline status → semantic tone. Anything mid-pipeline is "in progress" (warning), the two
// terminal failures are danger, INDEXED is success. Unknown statuses fall back to a muted tone
// rather than rendering an uncolored badge.
const IN_PROGRESS_STATUSES = new Set([
  'OCR_QUEUE', 'OCR_PROCESSING',
  'NER_QUEUE', 'NER_PROCESSING',
  'CHUNKING_QUEUE', 'CHUNKING_PROCESSING',
  'EMBEDDING_QUEUE', 'EMBEDDING_PROCESSING',
  'INDEXING_QUEUE', 'INDEXING_PROCESSING',
]);

function statusColor(theme, status) {
  if (status === 'UPLOADED') return theme.colors.primary;
  if (status === 'INDEXED') return theme.colors.success;
  if (status === 'FAILED' || status === 'DLQ') return theme.colors.danger;
  if (IN_PROGRESS_STATUSES.has(status)) return theme.colors.warning;
  return theme.colors.textFaint;
}

function DocumentCard({ doc, onPress, styles, theme }) {
  const color = statusColor(theme, doc.status);
  return (
    <TouchableOpacity style={styles.card} onPress={() => onPress(doc)}>
      <View style={styles.cardHeader}>
        <AppText style={styles.cardTitle} numberOfLines={1}>{doc.documentTitle}</AppText>
        <View style={[styles.badge, { backgroundColor: color + '22', borderColor: color }]}>
          <AppText style={[styles.badgeText, { color }]}>{doc.status}</AppText>
        </View>
      </View>
      <View style={styles.cardMeta}>
        <AppText style={styles.metaText}>{DOC_TYPE_LABELS[doc.documentType] || doc.documentType}</AppText>
        <AppText style={styles.metaDot}>·</AppText>
        <AppText style={styles.metaText}>{doc.classificationLevel}</AppText>
        {doc.createdAt && (
          <>
            <AppText style={styles.metaDot}>·</AppText>
            <AppText style={styles.metaText}>{new Date(doc.createdAt).toLocaleDateString('id-ID')}</AppText>
          </>
        )}
      </View>
    </TouchableOpacity>
  );
}

export default function DocumentsScreen() {
  const theme = useTheme();
  const styles = useThemedStyles(makeStyles);
  const [documents, setDocuments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [hasMore, setHasMore] = useState(true);
  const [pendingFile, setPendingFile] = useState(null);
  // Pagination is tracked in refs, not state: loadDocuments must read the CURRENT next page
  // without being re-created on every page change (a stale `page` closure was re-fetching page 0
  // on the first load-more, appending the whole first page again).
  const nextPageRef = useRef(0);      // page index to fetch on the next load-more
  const inFlightRef = useRef(false);  // guards overlapping load-more calls (onEndReached fires repeatedly)
  const generationRef = useRef(0);    // invalidates an in-flight load-more once a reset supersedes it
  const [showPicker, setShowPicker] = useState(false);
  const [selectedDocType, setSelectedDocType] = useState('AKTA');
  const [selectedClassification, setSelectedClassification] = useState('INTERNAL');

  const loadDocuments = useCallback(async (reset = false) => {
    // A reset (initial load / pull-to-refresh) always runs and supersedes any in-flight load-more.
    // A load-more is skipped while one is already running, so onEndReached firing repeatedly can
    // never append the same page twice.
    if (!reset && inFlightRef.current) return;
    if (reset) {
      generationRef.current += 1;
    } else {
      inFlightRef.current = true;
    }
    const generation = generationRef.current;
    const pageToLoad = reset ? 0 : nextPageRef.current;

    try {
      const data = await listDocuments(pageToLoad, 20);
      // Drop a stale load-more that resolved after a reset replaced the list underneath it.
      if (generation !== generationRef.current) return;

      const items = data.data?.items ?? [];
      setDocuments(prev => (reset ? items : [...prev, ...items]));
      const totalPages = data.data?.page?.totalPages ?? 1;
      setHasMore(pageToLoad < totalPages - 1);
      nextPageRef.current = pageToLoad + 1;
    } catch (err) {
      if (err.response?.status !== 404) {
        Alert.alert('Error', 'Gagal memuat dokumen');
      }
    } finally {
      if (!reset) inFlightRef.current = false;
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => { loadDocuments(true); }, []);

  const onRefresh = () => {
    setRefreshing(true);
    loadDocuments(true);
  };

  const handlePickFile = async () => {
    const result = await DocumentPicker.getDocumentAsync({
      type: ['application/pdf', 'image/*'],
      copyToCacheDirectory: true,
    });

    if (result.canceled) return;

    setPendingFile(result.assets[0]);
    setSelectedDocType('AKTA');
    setSelectedClassification('INTERNAL');
    setShowPicker(true);
  };

  const pollIngestionStatus = useCallback(async (jobId) => {
    for (let attempt = 0; attempt < 10; attempt++) {
      await new Promise((resolve) => setTimeout(resolve, 3000));
      try {
        const status = await getIngestionStatus(jobId);
        if (TERMINAL_PIPELINE_STATUSES.has(status.pipelineStatus)) {
          if (status.pipelineStatus !== 'COMPLETED') {
            Alert.alert(
              'Pemrosesan Gagal',
              `Dokumen gagal diproses pada tahap ${status.failureStage || 'tidak diketahui'}`
            );
          }
          loadDocuments(true);
          return;
        }
      } catch (_) {
        return; // status endpoint unreachable — stop polling silently, list still refreshes on next manual pull
      }
    }
  }, [loadDocuments]);

  const handleConfirmUpload = async () => {
    const file = pendingFile;
    if (!file) return;
    setShowPicker(false);
    setUploading(true);
    try {
      const checksumSha256 = await computeFileSha256(file.uri);

      const { jobId, signedUrl, requiredHeaders } = await initiateUpload({
        originalFilename: file.name,
        checksumSha256,
        documentType: selectedDocType,
        classificationLevel: selectedClassification,
      });

      await uploadFileToSignedUrl(signedUrl, file.uri, requiredHeaders);
      await confirmUpload(jobId, checksumSha256);

      Alert.alert('Berhasil', 'Dokumen berhasil diunggah dan sedang diproses');
      loadDocuments(true);
      pollIngestionStatus(jobId);
    } catch (err) {
      const msg = err.response?.data?.errorMessage || 'Upload gagal';
      Alert.alert('Upload Gagal', msg);
    } finally {
      setUploading(false);
      setPendingFile(null);
    }
  };

  // Was a bespoke inline empty block with its own icon/title/description styles — the app's only
  // duplicate of <EmptyState>, and the reason this screen's empty copy had drifted from every other
  // list's. Now the shared component (Sprint 4, Task 6).
  const renderEmpty = () => (
    <EmptyState icon="📂" title="Belum ada dokumen" description="Unggah dokumen pertama Anda" fill={false} />
  );

  const renderFooter = () => {
    if (!hasMore) return null;
    return <ActivityIndicator color={theme.colors.primary} style={{ marginVertical: 16 }} />;
  };

  return (
    <View style={styles.container}>
      <FlatList
        data={documents}
        keyExtractor={(item) => item.documentId || item.id || String(Math.random())}
        renderItem={({ item }) => (
          <DocumentCard doc={item} styles={styles} theme={theme} onPress={(doc) => {
            Alert.alert(doc.documentTitle, `ID: ${doc.documentId}\nStatus: ${doc.status}`);
          }} />
        )}
        contentContainerStyle={documents.length === 0 ? styles.emptyContainer : styles.listContent}
        ListEmptyComponent={!loading ? renderEmpty : null}
        ListFooterComponent={renderFooter}
        onEndReached={() => hasMore && loadDocuments()}
        onEndReachedThreshold={0.3}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={theme.colors.primary} />
        }
      />

      {/* Initial load previews the incoming rows instead of a centred spinner: the list is the
          content, so a skeleton says "documents are coming" where a spinner said only "wait".
          The FAB's upload spinner below stays a spinner — that is action feedback on a button, not
          a content placeholder, and a skeleton cannot express it. */}
      {loading && documents.length === 0 && (
        <View style={styles.skeletonOverlay} pointerEvents="none">
          <SkeletonList count={5} />
        </View>
      )}

      <TouchableOpacity
        style={[styles.fab, uploading && styles.fabDisabled]}
        onPress={handlePickFile}
        disabled={uploading}
        // Sprint 4, Task 11: the accessible name was the glyph "+", so this announced as "plus" —
        // the screen's primary action, unidentifiable. `busy` reports the upload state that the
        // spinner conveys visually.
        accessibilityRole="button"
        accessibilityLabel={uploading ? 'Mengunggah dokumen' : 'Unggah dokumen'}
        accessibilityState={{ disabled: uploading, busy: uploading }}
      >
        {uploading ? (
          <ActivityIndicator color={theme.colors.primaryText} size="small" />
        ) : (
          <AppText accessibilityElementsHidden importantForAccessibility="no" style={styles.fabIcon}>+</AppText>
        )}
      </TouchableOpacity>

      <Modal visible={showPicker} transparent animationType="slide" onRequestClose={() => setShowPicker(false)}>
        <View style={styles.modalOverlay}>
          <View style={styles.modalCard}>
            <AppText style={styles.modalTitle} numberOfLines={1}>{pendingFile?.name}</AppText>

            <AppText style={styles.modalLabel}>Jenis Dokumen</AppText>
            <View style={styles.chipRow}>
              {DOCUMENT_TYPES.map((type) => (
                <TouchableOpacity
                  key={type}
                  style={[styles.chip, selectedDocType === type && styles.chipSelected]}
                  onPress={() => setSelectedDocType(type)}
                >
                  <AppText style={[styles.chipText, selectedDocType === type && styles.chipTextSelected]}>
                    {DOC_TYPE_LABELS[type]}
                  </AppText>
                </TouchableOpacity>
              ))}
            </View>

            <AppText style={styles.modalLabel}>Tingkat Klasifikasi</AppText>
            <View style={styles.chipRow}>
              {CLASSIFICATION_LEVELS.map((level) => (
                <TouchableOpacity
                  key={level}
                  style={[styles.chip, selectedClassification === level && styles.chipSelected]}
                  onPress={() => setSelectedClassification(level)}
                >
                  <AppText style={[styles.chipText, selectedClassification === level && styles.chipTextSelected]}>
                    {CLASSIFICATION_LABELS[level]}
                  </AppText>
                </TouchableOpacity>
              ))}
            </View>

            <View style={styles.modalActions}>
              <TouchableOpacity
                style={styles.modalCancelBtn}
                onPress={() => { setShowPicker(false); setPendingFile(null); }}
              >
                <AppText style={styles.modalCancelText}>Batal</AppText>
              </TouchableOpacity>
              <TouchableOpacity style={styles.modalUploadBtn} onPress={handleConfirmUpload}>
                <AppText style={styles.modalUploadText}>Unggah</AppText>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>
    </View>
  );
}

const makeStyles = (theme) => ({
  container: { flex: 1, backgroundColor: theme.colors.background },
  listContent: { padding: theme.spacing.lg, paddingBottom: 80 },
  emptyContainer: { flex: 1, justifyContent: 'center', alignItems: 'center', padding: theme.spacing.xxxl },
  card: {
    backgroundColor: theme.colors.surface,
    borderRadius: theme.radius.lg,
    padding: theme.spacing.lg,
    marginBottom: 10,
    borderWidth: 1,
    borderColor: theme.colors.border,
  },
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: theme.spacing.sm,
  },
  cardTitle: {
    color: theme.colors.text,
    fontSize: theme.typography.body,
    fontWeight: theme.typography.semibold,
    flex: 1,
    marginRight: theme.spacing.sm,
  },
  badge: {
    borderRadius: theme.radius.sm,
    borderWidth: 1,
    paddingHorizontal: 6,
    paddingVertical: 2,
  },
  badgeText: { fontSize: 10, fontWeight: theme.typography.semibold },
  cardMeta: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  metaText: { color: theme.colors.textFaint, fontSize: theme.typography.caption },
  metaDot: { color: theme.colors.borderStrong, fontSize: theme.typography.caption },
  // `empty`/`emptyIcon`/`emptyTitle`/`emptyDesc` removed with the inline empty block — <EmptyState>
  // owns that look now. `emptyContainer` stays: it centres the shared component in the empty list.
  // Covers the list area while the first page loads. `loadingCenter` (the old centred-spinner
  // position) went with the spinner it positioned.
  skeletonOverlay: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: theme.colors.background,
  },
  fab: {
    position: 'absolute',
    bottom: theme.spacing.xxl,
    right: theme.spacing.xxl,
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: theme.colors.primary,
    justifyContent: 'center',
    alignItems: 'center',
    elevation: 6,
    shadowColor: theme.colors.primary,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.4,
    shadowRadius: 8,
  },
  fabDisabled: { opacity: 0.6 },
  fabIcon: { color: theme.colors.primaryText, fontSize: 28, lineHeight: 32 },
  modalOverlay: {
    flex: 1,
    backgroundColor: theme.colors.overlay,
    justifyContent: 'flex-end',
  },
  modalCard: {
    backgroundColor: theme.colors.surface,
    borderTopLeftRadius: theme.radius.xl,
    borderTopRightRadius: theme.radius.xl,
    padding: theme.spacing.xl,
    paddingBottom: theme.spacing.xxxl,
  },
  modalTitle: {
    color: theme.colors.text,
    fontSize: theme.typography.h3,
    fontWeight: theme.typography.bold,
    marginBottom: theme.spacing.lg,
  },
  modalLabel: {
    color: theme.colors.textMuted,
    fontSize: theme.typography.caption,
    fontWeight: theme.typography.semibold,
    textTransform: 'uppercase',
    letterSpacing: 0.6,
    marginBottom: theme.spacing.sm,
    marginTop: theme.spacing.md,
  },
  chipRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: theme.spacing.sm,
  },
  chip: {
    backgroundColor: theme.colors.surfaceAlt,
    borderRadius: theme.radius.pill,
    paddingHorizontal: 14,
    paddingVertical: theme.spacing.sm,
    borderWidth: 1,
    borderColor: theme.colors.border,
  },
  chipSelected: {
    backgroundColor: theme.colors.primary,
    borderColor: theme.colors.primary,
  },
  chipText: { color: theme.colors.textMuted, fontSize: theme.typography.bodySm },
  chipTextSelected: { color: theme.colors.primaryText, fontWeight: theme.typography.semibold },
  modalActions: {
    flexDirection: 'row',
    gap: theme.spacing.md,
    marginTop: theme.spacing.xxl,
  },
  modalCancelBtn: {
    flex: 1,
    padding: 14,
    borderRadius: theme.radius.md,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: theme.colors.borderStrong,
  },
  modalCancelText: { color: theme.colors.textMuted, fontWeight: theme.typography.semibold },
  modalUploadBtn: {
    flex: 1,
    padding: 14,
    borderRadius: theme.radius.md,
    alignItems: 'center',
    backgroundColor: theme.colors.primary,
  },
  modalUploadText: { color: theme.colors.primaryText, fontWeight: theme.typography.semibold },
});
