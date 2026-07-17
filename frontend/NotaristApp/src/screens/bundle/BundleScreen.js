import React from 'react';
import { View, RefreshControl } from 'react-native';
import Screen from '../../components/Screen';
import Card from '../../components/Card';
import BundleHeader from '../../components/BundleHeader';
import BundleProgress from '../../components/BundleProgress';
import SectionHeader from '../../components/SectionHeader';
import DocumentCard from '../../components/DocumentCard';
import PrimaryButton from '../../components/PrimaryButton';
import EmptyState from '../../components/EmptyState';
import ErrorState from '../../components/ErrorState';
import MockBanner from '../../components/MockBanner';
import { Skeleton } from '../../components/Skeleton';
import { useTheme } from '../../context/ThemeContext';
import useAsync from '../../hooks/useAsync';
import { BundleService } from '../../services';

export default function BundleScreen({ navigation, route }) {
  const theme = useTheme();
  const { bundleId, caseId } = route.params ?? {};
  const bundleQuery = useAsync(() => BundleService.getBundle(bundleId), [bundleId]);
  const docsQuery = useAsync(() => BundleService.getDocuments(bundleId), [bundleId]);

  const bundle = bundleQuery.data;
  const docs = docsQuery.data ?? [];

  const refreshAll = () => { bundleQuery.reload(); docsQuery.reload(); };

  if (bundleQuery.loading && !bundle) {
    return (
      <Screen scroll>
        <Skeleton width="70%" height={20} />
        <Skeleton width="100%" height={80} style={{ marginTop: 16 }} />
        <Skeleton width="100%" height={200} style={{ marginTop: 16 }} />
      </Screen>
    );
  }
  if (bundleQuery.error && !bundle) {
    return <Screen><ErrorState message="Gagal memuat bundle." onRetry={bundleQuery.reload} /></Screen>;
  }

  return (
    <Screen
      scroll
      refreshControl={<RefreshControl refreshing={bundleQuery.loading} onRefresh={refreshAll} tintColor={theme.colors.primary} />}
    >
      {BundleService.usingMock ? <MockBanner entity="bundle" style={{ marginBottom: theme.spacing.md }} /> : null}

      <BundleHeader bundle={bundle} documentCount={docs.length} />

      {/* Per-stage status */}
      <Card style={{ marginTop: theme.spacing.lg }}>
        <BundleProgress bundle={bundle} />
      </Card>

      {/* Dokumen Masuk */}
      <SectionHeader title="Dokumen Masuk" />
      {docsQuery.loading && !docs.length ? (
        <Skeleton width="100%" height={120} />
      ) : docs.length ? (
        <View style={{ gap: theme.spacing.sm }}>
          {docs.map((d) => (
            <DocumentCard key={d.id} item={d} onPress={() => navigation.navigate('OcrReview', { documentId: d.id, bundleId, documentName: d.name })} />
          ))}
        </View>
      ) : (
        <EmptyState icon="📄" title="Belum ada dokumen" description="Bundle ini belum berisi dokumen." fill={false} />
      )}

      {/* Verification entry */}
      <SectionHeader title="Verifikasi" />
      <PrimaryButton
        title="Buka Verifikasi Bundle"
        icon="🔍"
        onPress={() => navigation.navigate('Verification', { bundleId, caseId, bundleName: bundle?.name })}
      />
      <View style={{ height: theme.spacing.xxl }} />
    </Screen>
  );
}
