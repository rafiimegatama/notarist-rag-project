import React, { useEffect } from 'react';
import { View, RefreshControl } from 'react-native';
import Screen from '../../components/Screen';
import AppText from '../../components/AppText';
import Card from '../../components/Card';
import CaseHeader from '../../components/CaseHeader';
import DocumentMetadata from '../../components/DocumentMetadata';
import SectionHeader from '../../components/SectionHeader';
import ApprovalChip from '../../components/ApprovalChip';
import BundleCard from '../../components/BundleCard';
import ReminderCard from '../../components/ReminderCard';
import TimelineCard from '../../components/TimelineCard';
import EmptyState from '../../components/EmptyState';
import ErrorState from '../../components/ErrorState';
import MockBanner from '../../components/MockBanner';
import { Skeleton } from '../../components/Skeleton';
import { useTheme } from '../../context/ThemeContext';
import useAsync from '../../hooks/useAsync';
import { CaseService, TimelineService } from '../../services';
import { formatDate } from '../../utils/format';
import { useBundles, useReminders } from '../../state';

// Case status -> the approval outcome shown in the Approval section.
function approvalFromStatus(status) {
  if (status === 'READY_TO_SEND' || status === 'DELIVERED' || status === 'LOCKED') return 'APPROVED';
  return 'PENDING';
}

export default function CaseDetailScreen({ navigation, route }) {
  const theme = useTheme();
  const { caseId } = route.params ?? {};
  const caseQuery = useAsync(() => CaseService.getCase(caseId), [caseId]);
  const timelineQuery = useAsync(() => TimelineService.getCaseTimeline(caseId), [caseId]);
  const { bundlesFor, loadForCase, isLoading: bundlesLoading, usingMock: bundlesMock } = useBundles();
  const { reminders } = useReminders();

  useEffect(() => { loadForCase(caseId); }, [caseId, loadForCase]);

  const kase = caseQuery.data;
  const bundles = bundlesFor(caseId);
  const caseReminders = kase ? reminders.filter((r) => r.caseNumber === kase.caseNumber) : [];

  const refreshAll = () => { caseQuery.reload(); timelineQuery.reload(); loadForCase(caseId, { force: true }); };

  if (caseQuery.loading && !kase) {
    return (
      <Screen scroll>
        <Skeleton width="60%" height={20} />
        <Skeleton width="40%" height={12} style={{ marginTop: 10 }} />
        <Skeleton width="100%" height={120} style={{ marginTop: 20 }} />
      </Screen>
    );
  }
  if (caseQuery.error && !kase) {
    return <Screen><ErrorState message="Gagal memuat detail case." onRetry={caseQuery.reload} /></Screen>;
  }

  return (
    <Screen
      scroll
      refreshControl={<RefreshControl refreshing={caseQuery.loading} onRefresh={refreshAll} tintColor={theme.colors.primary} />}
    >
      {(CaseService.usingMock || bundlesMock) ? (
        <MockBanner entity="case/bundle" style={{ marginBottom: theme.spacing.md }} />
      ) : null}

      {/* Informasi Debitur + workflow stepper */}
      <CaseHeader item={kase} />
      <DocumentMetadata
        style={{ marginTop: theme.spacing.md }}
        rows={[
          { label: 'Bank', value: kase?.bank },
          { label: 'Jaminan', value: kase?.collateralType },
          { label: 'Notaris', value: kase?.notaris },
          { label: 'Dibuat', value: formatDate(kase?.createdAt) },
        ]}
      />

      {/* Timeline */}
      <SectionHeader title="Timeline Workflow" />
      {timelineQuery.loading ? (
        <Skeleton width="100%" height={100} />
      ) : (
        <TimelineCard items={timelineQuery.data ?? []} />
      )}

      {/* Bundle */}
      <SectionHeader title={`Bundle (${bundles?.length ?? 0})`} />
      {bundlesLoading(caseId) && !bundles ? (
        <Skeleton width="100%" height={120} />
      ) : bundles && bundles.length ? (
        <View style={{ gap: theme.spacing.md }}>
          {bundles.map((b) => (
            <BundleCard key={b.id} item={b} onPress={() => navigation.navigate('Bundle', { bundleId: b.id, caseId, bundleName: b.name })} />
          ))}
        </View>
      ) : (
        <EmptyState icon="📁" title="Belum ada bundle" description="Case ini belum memiliki bundle dokumen." fill={false} />
      )}

      {/* Generated Documents — no backend yet */}
      <SectionHeader title="Dokumen Hasil Generate" />
      <EmptyState
        icon="📄"
        title="Belum ada draft"
        description="Generate draft akan tersedia setelah verifikasi selesai (endpoint belum tersedia)."
        fill={false}
      />

      {/* Reminder */}
      <SectionHeader title={`Pengingat (${caseReminders.length})`} />
      {caseReminders.length ? (
        <View style={{ gap: theme.spacing.md }}>
          {caseReminders.map((r) => <ReminderCard key={r.id} item={r} />)}
        </View>
      ) : (
        <EmptyState icon="🔔" title="Tidak ada pengingat" fill={false} />
      )}

      {/* Approval Status */}
      <SectionHeader title="Status Approval" />
      <Card>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
          <AppText variant="bodySm" color="textMuted">Keputusan approval saat ini</AppText>
          <ApprovalChip status={approvalFromStatus(kase?.status)} size="md" />
        </View>
      </Card>
      <View style={{ height: theme.spacing.xxl }} />
    </Screen>
  );
}
