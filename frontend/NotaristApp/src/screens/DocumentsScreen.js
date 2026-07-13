import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  FlatList,
  StyleSheet,
  TouchableOpacity,
  ActivityIndicator,
  Alert,
  RefreshControl,
  Modal,
} from 'react-native';
import * as DocumentPicker from 'expo-document-picker';
import { listDocuments, initiateUpload, uploadFileToSignedUrl, confirmUpload, computeFileSha256, getIngestionStatus } from '../api/documents';

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

const STATUS_COLORS = {
  UPLOADED: '#3B82F6',
  OCR_QUEUE: '#F59E0B',
  OCR_PROCESSING: '#F59E0B',
  NER_QUEUE: '#F59E0B',
  NER_PROCESSING: '#F59E0B',
  CHUNKING_QUEUE: '#F59E0B',
  CHUNKING_PROCESSING: '#F59E0B',
  EMBEDDING_QUEUE: '#F59E0B',
  EMBEDDING_PROCESSING: '#F59E0B',
  INDEXING_QUEUE: '#F59E0B',
  INDEXING_PROCESSING: '#F59E0B',
  INDEXED: '#10B981',
  FAILED: '#EF4444',
  DLQ: '#991B1B',
};

function DocumentCard({ doc, onPress }) {
  const statusColor = STATUS_COLORS[doc.status] || '#64748B';
  return (
    <TouchableOpacity style={styles.card} onPress={() => onPress(doc)}>
      <View style={styles.cardHeader}>
        <Text style={styles.cardTitle} numberOfLines={1}>{doc.documentTitle}</Text>
        <View style={[styles.badge, { backgroundColor: statusColor + '22', borderColor: statusColor }]}>
          <Text style={[styles.badgeText, { color: statusColor }]}>{doc.status}</Text>
        </View>
      </View>
      <View style={styles.cardMeta}>
        <Text style={styles.metaText}>{DOC_TYPE_LABELS[doc.documentType] || doc.documentType}</Text>
        <Text style={styles.metaDot}>·</Text>
        <Text style={styles.metaText}>{doc.classificationLevel}</Text>
        {doc.createdAt && (
          <>
            <Text style={styles.metaDot}>·</Text>
            <Text style={styles.metaText}>{new Date(doc.createdAt).toLocaleDateString('id-ID')}</Text>
          </>
        )}
      </View>
    </TouchableOpacity>
  );
}

export default function DocumentsScreen({ navigation }) {
  const [documents, setDocuments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(true);
  const [pendingFile, setPendingFile] = useState(null);
  const [showPicker, setShowPicker] = useState(false);
  const [selectedDocType, setSelectedDocType] = useState('AKTA');
  const [selectedClassification, setSelectedClassification] = useState('INTERNAL');

  const loadDocuments = useCallback(async (reset = false) => {
    const currentPage = reset ? 0 : page;
    try {
      const data = await listDocuments(currentPage, 20);
      const items = data.data?.items ?? [];
      if (reset) {
        setDocuments(items);
        setPage(0);
      } else {
        setDocuments(prev => [...prev, ...items]);
      }
      const totalPages = data.data?.page?.totalPages ?? 1;
      setHasMore(currentPage < totalPages - 1);
      if (!reset) setPage(p => p + 1);
    } catch (err) {
      if (err.response?.status !== 404) {
        Alert.alert('Error', 'Gagal memuat dokumen');
      }
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [page]);

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

  const renderEmpty = () => (
    <View style={styles.empty}>
      <Text style={styles.emptyIcon}>📂</Text>
      <Text style={styles.emptyTitle}>Belum ada dokumen</Text>
      <Text style={styles.emptyDesc}>Unggah dokumen pertama Anda</Text>
    </View>
  );

  const renderFooter = () => {
    if (!hasMore) return null;
    return <ActivityIndicator color="#3B82F6" style={{ marginVertical: 16 }} />;
  };

  return (
    <View style={styles.container}>
      <FlatList
        data={documents}
        keyExtractor={(item) => item.documentId || item.id || String(Math.random())}
        renderItem={({ item }) => (
          <DocumentCard doc={item} onPress={(doc) => {
            Alert.alert(doc.documentTitle, `ID: ${doc.documentId}\nStatus: ${doc.status}`);
          }} />
        )}
        contentContainerStyle={documents.length === 0 ? styles.emptyContainer : styles.listContent}
        ListEmptyComponent={!loading ? renderEmpty : null}
        ListFooterComponent={renderFooter}
        onEndReached={() => hasMore && loadDocuments()}
        onEndReachedThreshold={0.3}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor="#3B82F6" />}
      />

      {loading && documents.length === 0 && (
        <ActivityIndicator style={styles.loadingCenter} color="#3B82F6" size="large" />
      )}

      <TouchableOpacity
        style={[styles.fab, uploading && styles.fabDisabled]}
        onPress={handlePickFile}
        disabled={uploading}
      >
        {uploading ? (
          <ActivityIndicator color="#fff" size="small" />
        ) : (
          <Text style={styles.fabIcon}>+</Text>
        )}
      </TouchableOpacity>

      <Modal visible={showPicker} transparent animationType="slide" onRequestClose={() => setShowPicker(false)}>
        <View style={styles.modalOverlay}>
          <View style={styles.modalCard}>
            <Text style={styles.modalTitle} numberOfLines={1}>{pendingFile?.name}</Text>

            <Text style={styles.modalLabel}>Jenis Dokumen</Text>
            <View style={styles.chipRow}>
              {DOCUMENT_TYPES.map((type) => (
                <TouchableOpacity
                  key={type}
                  style={[styles.chip, selectedDocType === type && styles.chipSelected]}
                  onPress={() => setSelectedDocType(type)}
                >
                  <Text style={[styles.chipText, selectedDocType === type && styles.chipTextSelected]}>
                    {DOC_TYPE_LABELS[type]}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>

            <Text style={styles.modalLabel}>Tingkat Klasifikasi</Text>
            <View style={styles.chipRow}>
              {CLASSIFICATION_LEVELS.map((level) => (
                <TouchableOpacity
                  key={level}
                  style={[styles.chip, selectedClassification === level && styles.chipSelected]}
                  onPress={() => setSelectedClassification(level)}
                >
                  <Text style={[styles.chipText, selectedClassification === level && styles.chipTextSelected]}>
                    {CLASSIFICATION_LABELS[level]}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>

            <View style={styles.modalActions}>
              <TouchableOpacity
                style={styles.modalCancelBtn}
                onPress={() => { setShowPicker(false); setPendingFile(null); }}
              >
                <Text style={styles.modalCancelText}>Batal</Text>
              </TouchableOpacity>
              <TouchableOpacity style={styles.modalUploadBtn} onPress={handleConfirmUpload}>
                <Text style={styles.modalUploadText}>Unggah</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0F172A' },
  listContent: { padding: 16, paddingBottom: 80 },
  emptyContainer: { flex: 1, justifyContent: 'center', alignItems: 'center', padding: 32 },
  card: {
    backgroundColor: '#1E293B',
    borderRadius: 10,
    padding: 16,
    marginBottom: 10,
    borderWidth: 1,
    borderColor: '#334155',
  },
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  cardTitle: {
    color: '#F1F5F9',
    fontSize: 15,
    fontWeight: '600',
    flex: 1,
    marginRight: 8,
  },
  badge: {
    borderRadius: 4,
    borderWidth: 1,
    paddingHorizontal: 6,
    paddingVertical: 2,
  },
  badgeText: { fontSize: 10, fontWeight: '600' },
  cardMeta: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  metaText: { color: '#64748B', fontSize: 12 },
  metaDot: { color: '#475569', fontSize: 12 },
  empty: { alignItems: 'center' },
  emptyIcon: { fontSize: 48, marginBottom: 12 },
  emptyTitle: { color: '#94A3B8', fontSize: 16, fontWeight: '600', marginBottom: 4 },
  emptyDesc: { color: '#475569', fontSize: 13 },
  loadingCenter: { position: 'absolute', alignSelf: 'center', top: '45%' },
  fab: {
    position: 'absolute',
    bottom: 24,
    right: 24,
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: '#3B82F6',
    justifyContent: 'center',
    alignItems: 'center',
    elevation: 6,
    shadowColor: '#3B82F6',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.4,
    shadowRadius: 8,
  },
  fabDisabled: { opacity: 0.6 },
  fabIcon: { color: '#fff', fontSize: 28, lineHeight: 32 },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'flex-end',
  },
  modalCard: {
    backgroundColor: '#1E293B',
    borderTopLeftRadius: 16,
    borderTopRightRadius: 16,
    padding: 20,
    paddingBottom: 32,
  },
  modalTitle: {
    color: '#F1F5F9',
    fontSize: 16,
    fontWeight: '700',
    marginBottom: 16,
  },
  modalLabel: {
    color: '#94A3B8',
    fontSize: 12,
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 0.6,
    marginBottom: 8,
    marginTop: 12,
  },
  chipRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  chip: {
    backgroundColor: '#0F172A',
    borderRadius: 16,
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderWidth: 1,
    borderColor: '#334155',
  },
  chipSelected: {
    backgroundColor: '#3B82F6',
    borderColor: '#3B82F6',
  },
  chipText: { color: '#94A3B8', fontSize: 13 },
  chipTextSelected: { color: '#fff', fontWeight: '600' },
  modalActions: {
    flexDirection: 'row',
    gap: 12,
    marginTop: 24,
  },
  modalCancelBtn: {
    flex: 1,
    padding: 14,
    borderRadius: 8,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#475569',
  },
  modalCancelText: { color: '#94A3B8', fontWeight: '600' },
  modalUploadBtn: {
    flex: 1,
    padding: 14,
    borderRadius: 8,
    alignItems: 'center',
    backgroundColor: '#3B82F6',
  },
  modalUploadText: { color: '#fff', fontWeight: '600' },
});
