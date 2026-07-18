import React from 'react';
import { View, RefreshControl } from 'react-native';
import Screen from '../../components/Screen';
import BundleHeader from '../../components/BundleHeader';
import SectionHeader from '../../components/SectionHeader';
import BundleExplorer from '../../components/BundleExplorer';
import PrimaryButton from '../../components/PrimaryButton';
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

      {/* documentCount comes from the BUNDLE, not from docs.length. BundleResponse carries a real
          `documentCount`, while the document list is a separate call that can fail — or, against the
          real backend, cannot be served at all. Counting the rows we happened to fetch would report
          "0 dokumen" for a bundle the server itself says holds four. */}
      <BundleHeader bundle={bundle} documentCount={bundle?.documentCount ?? docs.length} />

      {/* Bundle Explorer (Sprint 7): metadata + workflow stages + searchable/filterable documents as
          an expandable tree. The document branch degrades honestly — the docs list has no live route
          (api/bundles#getBundleDocuments), so its failure renders inside the branch, never as an empty
          "no documents" that would contradict the bundle's own documentCount. */}
      <View style={{ marginTop: theme.spacing.lg }}>
        <BundleExplorer
          bundle={bundle}
          docs={docs}
          docsLoading={docsQuery.loading}
          docsError={docsQuery.error && !docs.length ? docsQuery.error : null}
          onOpenDocument={(d) => navigation.navigate('OcrReview', { documentId: d.id, bundleId, documentName: d.name })}
        />
      </View>

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
