import React from 'react';
import { View, ScrollView } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import { useSync } from '../state/SyncContext';
import { QueueStatus, FailureReason, MAX_ATTEMPTS } from '../services/mutationQueue';
import AppText from './AppText';
import Card from './Card';
import StatusChip from './StatusChip';
import Button from './Button';
import SecondaryButton from './SecondaryButton';
import EmptyState from './EmptyState';
// formatDateTime, not formatDate: these timestamps are minutes old and "17 Jul 2026" on a write that
// failed two minutes ago tells the reader nothing about whether it is stuck.
import { formatDateTime } from '../utils/format';

/**
 * Queue Inspector (Sprint 1) — every write that has not reached the server, why, and what can be
 * done about it.
 *
 * This exists because "2 pending" is not accountable. A notary who approved a checklist on a train
 * is entitled to know exactly WHICH approvals are still in their pocket, and to get one back out.
 * So each row names the intent ("Setujui NIK"), not the request, and the failure copy says what
 * happened rather than showing a status code.
 *
 * Grouped by resource because that is how the work is grouped in the notary's head: three decisions
 * on one bundle is one problem, not three. `groups` is derived by the queue itself, so this renders
 * an ordering it does not invent.
 *
 * Presentational: no fetching, no queue logic. Drop it in a screen, a modal, or a drawer.
 */
export default function QueueInspector({ style }) {
  const theme = useTheme();
  const { groups, entries, pendingCount, failedCount, flushing, lastFlushAt, flush, cancel, retry } = useSync();

  if (!entries.length) {
    return (
      <View style={style}>
        <EmptyState
          icon="✅"
          title="Semua tersinkron"
          description="Tidak ada perubahan yang menunggu dikirim ke server."
          fill={false}
        />
      </View>
    );
  }

  return (
    <View style={[{ gap: theme.spacing.md }, style]}>
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
        <View style={{ flex: 1 }}>
          <AppText variant="bodyStrong">
            {pendingCount} menunggu{failedCount ? ` · ${failedCount} gagal` : ''}
          </AppText>
          {lastFlushAt ? (
            <AppText variant="micro" color="textFaint">Sinkron terakhir {formatDateTime(lastFlushAt)}</AppText>
          ) : null}
        </View>
        {/* Manual flush. Disabled while a flush is running: the queue is single-flight, so a second
            press is a no-op — better to show that than to accept a tap that does nothing. */}
        <SecondaryButton
          title={flushing ? 'Mengirim…' : 'Sinkron sekarang'}
          onPress={flush}
          disabled={flushing || !pendingCount}
        />
      </View>

      <ScrollView contentContainerStyle={{ gap: theme.spacing.md }} showsVerticalScrollIndicator={false}>
        {groups.map((group) => (
          <View key={group.resource} style={{ gap: theme.spacing.sm }}>
            <AppText variant="micro" color="textFaint">{resourceLabel(group.resource)}</AppText>
            {group.items.map((entry) => (
              <QueueRow key={entry.id} entry={entry} onCancel={cancel} onRetry={retry} />
            ))}
          </View>
        ))}
      </ScrollView>
    </View>
  );
}

function QueueRow({ entry, onCancel, onRetry }) {
  const theme = useTheme();
  const meta = statusMeta(entry);
  const parked = entry.status === QueueStatus.FAILED;

  return (
    <Card>
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start', gap: theme.spacing.sm }}>
        <View style={{ flex: 1 }}>
          <AppText variant="bodySm" numberOfLines={2}>{entry.label}</AppText>
          <AppText variant="micro" color="textFaint" style={{ marginTop: 2 }}>
            Dibuat {formatDateTime(entry.queuedAt)}
            {/* Attempt count only once it means something. "Percobaan 1/5" before anything has been
                tried reads as a problem; the first attempt is just the queue working. */}
            {entry.attempts > 1 ? ` · Percobaan ${entry.attempts}/${MAX_ATTEMPTS}` : ''}
          </AppText>
        </View>
        <StatusChip label={meta.label} color={meta.color} size="sm" accessibilityLabel={meta.hint} />
      </View>

      {parked ? (
        <View style={{ marginTop: theme.spacing.sm, gap: theme.spacing.sm }}>
          {/* The reason, in the app's own error vocabulary (api/errors#messageForKind) — never a raw
              server string and never a status code. */}
          <AppText variant="micro" color="danger">{entry.failureMessage}</AppText>
          {explain(entry.failure) ? (
            <AppText variant="micro" color="textFaint">{explain(entry.failure)}</AppText>
          ) : null}
          <View style={{ flexDirection: 'row', gap: theme.spacing.sm }}>
            {/* Retry is offered only where retrying can actually work. A CONFLICT means the server
                already disagreed with this write; re-sending it unchanged asks the same question and
                gets the same answer. The honest action there is to discard and re-read. */}
            {entry.failure !== FailureReason.CONFLICT ? (
              <Button title="Coba lagi" variant="secondary" fullWidth={false} icon="↻" onPress={() => onRetry(entry.id)} />
            ) : null}
            <Button title="Batalkan" variant="ghost" fullWidth={false} onPress={() => onCancel(entry.id)} />
          </View>
        </View>
      ) : null}
    </Card>
  );
}

/** Resource -> section heading. Falls back to the raw key rather than guessing a nicer name. */
function resourceLabel(resource) {
  return {
    verification: 'Verifikasi',
    checklist: 'Checklist verifikasi',
    ocrField: 'Koreksi OCR',
    caseStatus: 'Status case',
    conversation: 'Percakapan',
  }[resource] ?? resource;
}

function statusMeta(entry) {
  if (entry.status === QueueStatus.IN_FLIGHT) {
    return { label: '↻ Mengirim', color: 'info', hint: 'Sedang dikirim ke server' };
  }
  if (entry.status === QueueStatus.FAILED) {
    return { label: '⚠ Gagal', color: 'danger', hint: 'Gagal dikirim, menunggu tindakan Anda' };
  }
  return { label: '⏳ Menunggu', color: 'warning', hint: 'Menunggu koneksi untuk dikirim' };
}

/**
 * The second line under a failure: what the app actually knows about the server's state.
 *
 * UNSAFE_REPLAY is the one that earns its words. "We do not know if it landed" sounds like a cop-out
 * and is the literal truth — the request was delivered and the answer never came — and it is the
 * reason the app will not quietly re-send it. Telling a notary to go and look is the only correct
 * instruction; guessing on their behalf is how a deed gets approved twice.
 */
function explain(failure) {
  return {
    [FailureReason.CONFLICT]: 'Data di server sudah berubah. Muat ulang untuk melihat versi terbaru sebelum memutuskan lagi.',
    [FailureReason.UNSAFE_REPLAY]: 'Permintaan terkirim tetapi jawabannya tidak diterima, jadi statusnya di server tidak diketahui. Periksa dulu sebelum mengirim ulang agar tidak tersimpan dua kali.',
    [FailureReason.SERVER]: 'Server bermasalah saat memproses. Periksa statusnya sebelum mengirim ulang.',
    [FailureReason.EXHAUSTED]: 'Tidak dapat menghubungi server setelah beberapa percobaan.',
    [FailureReason.REJECTED]: 'Server menolak perubahan ini.',
  }[failure] ?? null;
}
