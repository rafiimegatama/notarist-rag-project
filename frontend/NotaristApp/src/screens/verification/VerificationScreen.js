import React, { useEffect, useState } from 'react';
import { View, ScrollView, Alert } from 'react-native';
import Screen from '../../components/Screen';
import AppText from '../../components/AppText';
import ChecklistCard from '../../components/ChecklistCard';
import PrimaryButton from '../../components/PrimaryButton';
import SecondaryButton from '../../components/SecondaryButton';
import BottomActionBar from '../../components/BottomActionBar';
import EmptyState from '../../components/EmptyState';
import ErrorState from '../../components/ErrorState';
import MockBanner from '../../components/MockBanner';
import { SkeletonList } from '../../components/Skeleton';
import { useTheme } from '../../context/ThemeContext';
import useAsync from '../../hooks/useAsync';
import { VerificationService } from '../../services';

// Human verification decisions per document. QC mode reuses the exact flow with QC-flavored copy.
export default function VerificationScreen({ navigation, route }) {
  const theme = useTheme();
  const { bundleId, mode } = route.params ?? {};
  const isQc = mode === 'qc';
  const query = useAsync(() => VerificationService.getChecklist(bundleId), [bundleId]);

  const [decisions, setDecisions] = useState({}); // docId -> decision
  const [comments, setComments] = useState({});   // docId -> comment
  const [submitting, setSubmitting] = useState(false);

  const docs = query.data ?? [];
  const decided = docs.filter((d) => decisions[d.id]).length;

  const decide = (id, val) => setDecisions((s) => ({ ...s, [id]: val }));
  const comment = (id, val) => setComments((s) => ({ ...s, [id]: val }));
  const approveAll = () => setDecisions(Object.fromEntries(docs.map((d) => [d.id, 'APPROVED'])));

  const submit = async () => {
    if (decided < docs.length) {
      Alert.alert('Belum lengkap', 'Masih ada dokumen yang belum diberi keputusan.');
      return;
    }
    setSubmitting(true);
    try {
      const payload = docs.map((d) => ({ fieldId: d.id, decision: decisions[d.id], comment: comments[d.id] || null }));
      await VerificationService.submit(bundleId, payload);
      Alert.alert(isQc ? 'QC Selesai' : 'Verifikasi Terkirim', 'Keputusan berhasil disimpan.', [
        { text: 'OK', onPress: () => navigation.goBack() },
      ]);
    } catch (_) {
      Alert.alert('Gagal', 'Tidak dapat mengirim keputusan. Coba lagi.');
    } finally {
      setSubmitting(false);
    }
  };

  useEffect(() => { navigation.setOptions?.({ title: isQc ? 'QC Checklist' : 'Verifikasi' }); }, [isQc, navigation]);

  if (query.loading && !query.data) return <Screen padded={false}><SkeletonList count={4} /></Screen>;
  if (query.error && !query.data) return <Screen><ErrorState message="Gagal memuat dokumen verifikasi." onRetry={query.reload} /></Screen>;

  return (
    <Screen padded={false} edges={['top']}>
      <ScrollView contentContainerStyle={{ padding: theme.spacing.lg, gap: theme.spacing.md }} showsVerticalScrollIndicator={false}>
        {VerificationService.usingMock ? <MockBanner entity="verifikasi" /> : null}
        <View>
          <AppText variant="h3">{isQc ? 'Quality Control' : 'Human Verification'}</AppText>
          <AppText variant="bodySm" color="textMuted">
            {isQc ? 'Periksa kualitas akhir setiap dokumen sebelum approval.' : 'Verifikasi setiap dokumen: setujui, tolak, atau tandai untuk cek manual.'}
          </AppText>
          <AppText variant="micro" color="textFaint" style={{ marginTop: 4 }}>{decided}/{docs.length} dokumen diputuskan</AppText>
        </View>

        {docs.length ? (
          docs.map((d) => (
            <ChecklistCard
              key={d.id}
              title={d.name}
              subtitle={`${d.type} · ${d.pages ?? 1} hal`}
              decision={decisions[d.id]}
              comment={comments[d.id] || ''}
              onDecide={(val) => decide(d.id, val)}
              onComment={(val) => comment(d.id, val)}
            />
          ))
        ) : (
          <EmptyState icon="📄" title="Tidak ada dokumen" description="Bundle ini belum berisi dokumen untuk diverifikasi." fill={false} />
        )}
        <View style={{ height: theme.spacing.xxl }} />
      </ScrollView>

      {docs.length ? (
        <BottomActionBar>
          <SecondaryButton title="Setujui Semua" onPress={approveAll} />
          <PrimaryButton title={isQc ? 'Selesaikan QC' : 'Kirim Verifikasi'} loading={submitting} onPress={submit} />
        </BottomActionBar>
      ) : null}
    </Screen>
  );
}
