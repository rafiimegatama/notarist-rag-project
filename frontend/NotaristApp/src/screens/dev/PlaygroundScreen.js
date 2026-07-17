import React, { useState } from 'react';
import { View } from 'react-native';
import Screen from '../../components/Screen';
import SectionHeader from '../../components/SectionHeader';
import AppText from '../../components/AppText';
import {
  PrimaryButton, SecondaryButton, DangerButton,
  StatusChip, PriorityChip, ApprovalChip, ApprovalBadge, ConfidenceBadge,
  StatCard, InfoCard, CaseCard, BundleCard, DocumentCard, ReminderCard, GeneratedDocumentCard,
  WorkflowStepper, PipelineProgress, ProgressIndicator,
  TimelineCard, AuditTimeline, ApprovalTimeline, DirectorTimeline,
  AuthorityPanel, DocumentMetadata, FieldConfidenceRow, ChecklistCard,
  CaseHeader, BundleHeader, CitationCard,
  SearchBar, SearchModeToggle, FilterBar, SearchFilterBar,
  LoadingSkeleton, LoadingState, EmptyState, ErrorState, OfflineBanner, MockBanner,
  ConfirmationDialog, DangerDialog, SuccessCheck, FloatingReviewToolbar, Card,
  ActionFooter, StickyBottomAction,
} from '../../components';
import { useTheme } from '../../context/ThemeContext';
import { normalizeCase } from '../../models/Case';
import { normalizeBundle } from '../../models/Bundle';
import { normalizeReminder } from '../../models/Reminder';
import { MOCK_CASES, MOCK_BUNDLES, MOCK_DOCUMENTS, MOCK_OCR_FIELDS, MOCK_REMINDERS, MOCK_NOW } from '../../mocks/fixtures';

// Everything below consumes the SAME centralized fixtures the real screens use — no bespoke mock data.
const sampleCase = normalizeCase(MOCK_CASES[0]);
const sampleBundle = normalizeBundle(MOCK_BUNDLES['case-001'][0]);
const sampleDoc = MOCK_DOCUMENTS['bnd-001'][0];
const sampleFields = MOCK_OCR_FIELDS['doc-1'].fields;
const sampleReminder = normalizeReminder(MOCK_REMINDERS[0], MOCK_NOW);
const sampleAuthority = MOCK_OCR_FIELDS['doc-1'].authorityTimeline;
const sampleCitation = {
  chunkId: 'c1', sourceType: 'AKTA', relevanceScore: 0.88, retrievalReason: 'SEMANTIC',
  citationText: 'Pasal 15 UUJN mengatur kewenangan notaris dalam pembuatan akta autentik…',
};

function Group({ title, children }) {
  const theme = useTheme();
  return (
    <View style={{ marginBottom: theme.spacing.md }}>
      <SectionHeader title={title} />
      <View style={{ gap: theme.spacing.sm }}>{children}</View>
    </View>
  );
}
function Row({ children }) {
  const theme = useTheme();
  return <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: theme.spacing.sm, alignItems: 'center' }}>{children}</View>;
}

/**
 * Developer Component Playground — a pure-UI showcase of every reusable component in its notable
 * states (loading / empty / error / offline / approved / rejected / draft / completed). No backend,
 * no navigation side-effects. Reachable only when FEATURES.devPlayground is true.
 */
export default function PlaygroundScreen() {
  const theme = useTheme();
  const [confirm, setConfirm] = useState(false);
  const [danger, setDanger] = useState(false);
  const [checkDecision, setCheckDecision] = useState(null);
  const [stepIndex, setStepIndex] = useState(3);

  return (
    <Screen scroll>
      <AppText variant="h2">Component Playground</AppText>
      <AppText variant="bodySm" color="textMuted" style={{ marginBottom: theme.spacing.md }}>
        Semua komponen reusable, seluruh state. Data dari fixtures terpusat. Tanpa backend.
      </AppText>

      <Group title="Buttons">
        <Row><PrimaryButton title="Primary" fullWidth={false} /><SecondaryButton title="Secondary" fullWidth={false} /><DangerButton title="Danger" fullWidth={false} /></Row>
        <Row><PrimaryButton title="Loading" loading fullWidth={false} /><PrimaryButton title="Disabled" disabled fullWidth={false} /></Row>
      </Group>

      <Group title="Status & Priority Chips">
        <Row>
          <StatusChip label="Draft" color="textFaint" /><StatusChip label="Berjalan" color="warning" />
          <StatusChip label="Selesai" color="success" /><StatusChip label="Ditolak" color="danger" tone="solid" />
          <StatusChip label="Outline" color="info" tone="outline" />
        </Row>
        <Row><PriorityChip priority="HIGH" /><PriorityChip priority="MEDIUM" /><PriorityChip priority="LOW" /></Row>
        <Row>
          <ApprovalChip status="PENDING" /><ApprovalChip status="APPROVED" /><ApprovalChip status="REJECTED" />
          <ApprovalBadge status="APPROVED" /><ApprovalBadge status="REJECTED" />
        </Row>
        <Row><ConfidenceBadge value={0.95} /><ConfidenceBadge value={0.74} /><ConfidenceBadge value={0.55} /></Row>
      </Group>

      <Group title="Stat & Info">
        <Row>
          <StatCard label="Total Case" value={12} tone="primary" icon="📁" style={{ width: '48%' }} />
          <StatCard label="Terlambat" value={3} tone="danger" icon="⏰" style={{ width: '48%' }} />
        </Row>
        <InfoCard title="Info" icon="ℹ️">Contoh callout informasi dengan aksen kiri.</InfoCard>
      </Group>

      <Group title="Workflow Stepper (tap to advance)">
        <WorkflowStepper currentIndex={stepIndex} />
        <Row><SecondaryButton title="◀" fullWidth={false} onPress={() => setStepIndex((i) => Math.max(0, i - 1))} /><SecondaryButton title="▶" fullWidth={false} onPress={() => setStepIndex((i) => Math.min(6, i + 1))} /></Row>
      </Group>

      <Group title="Pipeline & Progress">
        <PipelineProgress currentIndex={3} />
        <ProgressIndicator value={0.65} label="Case completion" />
      </Group>

      <Group title="Entity Cards">
        <CaseCard item={sampleCase} />
        <BundleCard item={sampleBundle} />
        <DocumentCard item={sampleDoc} />
        <ReminderCard item={sampleReminder} />
        <GeneratedDocumentCard item={{ title: 'Draft Akta Jual Beli', kind: 'AJB', status: 'DONE', generatedAt: new Date().toISOString() }} onPress={() => {}} />
      </Group>

      <Group title="Headers">
        <CaseHeader item={sampleCase} />
        <BundleHeader bundle={sampleBundle} documentCount={4} />
      </Group>

      <Group title="Timelines">
        <TimelineCard items={[{ id: 't1', label: 'Case dibuat', at: new Date().toISOString(), actor: 'Admin', done: true }, { id: 't2', label: 'Verifikasi', actor: 'Notaris', done: false }]} />
        <DirectorTimeline entries={sampleAuthority} />
        <ApprovalTimeline steps={[{ id: 'a', label: 'Notaris', status: 'APPROVED', at: new Date().toISOString() }, { id: 'b', label: 'Direksi', status: 'PENDING' }]} />
        <AuditTimeline events={[{ id: 'e1', actor: 'Sistem', action: 'OCR selesai', at: new Date().toISOString() }]} />
      </Group>

      <Group title="OCR & Verification">
        <AuthorityPanel stampDetected={false} signatureDetected />
        <DocumentMetadata rows={[{ label: 'Bank', value: 'Mandiri' }, { label: 'Jaminan', value: 'SHM' }]} />
        <FieldConfidenceRow field={sampleFields[0]} />
        <FieldConfidenceRow field={sampleFields[3]} />
        <ChecklistCard title="KTP Debitur.pdf" subtitle="KTP · 1 hal" decision={checkDecision} onDecide={setCheckDecision} />
      </Group>

      <Group title="Search">
        <SearchBar value="" onChangeText={() => {}} placeholder="Cari dokumen…" />
        <SearchModeToggle mode="semantic" onChange={() => {}} />
        <FilterBar options={[{ value: 'a', label: 'Semua' }, { value: 'b', label: 'AKTA' }]} selected="a" onSelect={() => {}} />
        <SearchFilterBar query="" onChangeQuery={() => {}} placeholder="Cari + filter…" filters={[{ value: 'x', label: 'Semua' }, { value: 'y', label: 'Sertifikat' }]} selected="x" onSelectFilter={() => {}} />
        <CitationCard citation={sampleCitation} index={0} />
      </Group>

      <Group title="States">
        <MockBanner entity="dashboard" />
        <OfflineBanner />
        <Card padded={false}><LoadingSkeleton count={2} /></Card>
        <Card><LoadingState message="Memuat…" fill={false} /></Card>
        <Card><EmptyState icon="📭" title="Kosong" description="Belum ada data." fill={false} /></Card>
        <Card><ErrorState message="Terjadi kesalahan." onRetry={() => {}} fill={false} /></Card>
      </Group>

      <Group title="Footers (sticky bottom bars)">
        <View style={{ borderWidth: 1, borderColor: theme.colors.border, borderRadius: theme.radius.lg, overflow: 'hidden' }}>
          <ActionFooter>
            <SecondaryButton title="Batal" />
            <PrimaryButton title="Simpan" />
          </ActionFooter>
        </View>
        <View style={{ borderWidth: 1, borderColor: theme.colors.border, borderRadius: theme.radius.lg, overflow: 'hidden' }}>
          <StickyBottomAction>
            <PrimaryButton title="Aksi Utama" />
          </StickyBottomAction>
        </View>
      </Group>

      <Group title="Success & Dialogs">
        <Row><SuccessCheck size={56} /></Row>
        <Row>
          <PrimaryButton title="Confirmation" fullWidth={false} onPress={() => setConfirm(true)} />
          <DangerButton title="Danger Dialog" fullWidth={false} onPress={() => setDanger(true)} />
        </Row>
      </Group>

      <Group title="Floating Review Toolbar">
        <View style={{ height: 60, justifyContent: 'center' }}>
          <FloatingReviewToolbar
            actions={[
              { key: 'prev', icon: '◀', label: 'Sebelum', onPress: () => {} },
              { key: 'reject', icon: '✕', label: 'Tolak', tone: 'danger', onPress: () => {} },
              { key: 'approve', icon: '✓', label: 'Setuju', tone: 'success', onPress: () => {} },
              { key: 'next', icon: '▶', label: 'Berikut', onPress: () => {} },
            ]}
          />
        </View>
      </Group>

      <View style={{ height: theme.spacing.xxxl }} />

      <ConfirmationDialog visible={confirm} title="Konfirmasi" message="Lanjutkan tindakan ini?" onConfirm={() => setConfirm(false)} onCancel={() => setConfirm(false)} />
      <DangerDialog visible={danger} title="Hapus Item" message="Tindakan ini tidak dapat dibatalkan." onConfirm={() => setDanger(false)} onCancel={() => setDanger(false)} />
    </Screen>
  );
}
