import React from 'react';
import { useSync } from '../state/SyncContext';
import StatusChip from './StatusChip';

/**
 * The pending-writes badge (Sprint 1).
 *
 * Renders NOTHING when the queue is empty, which is the common case and the point: a permanent "0
 * pending" chip is furniture, and furniture stops being read. It appears exactly when a notary has
 * work that has not reached the server.
 *
 * Three states, ranked by what the reader must do:
 *   failed   danger   — needs a person. Ranked above pending: a parked write is not going to fix
 *                       itself, and it must not hide behind a spinner that implies progress.
 *   flushing info     — in flight right now.
 *   pending  warning  — queued, waiting for the network.
 *
 * Built on StatusChip so it is coloured and sized by the same rules as every other status in the app
 * (workflow chips, approval chips). No new pill styling — see StatusChip's header.
 *
 * @param {Function} [onPress] usually opens the QueueInspector.
 */
export default function SyncBadge({ onPress, size = 'sm', style }) {
  const { pendingCount, failedCount, flushing } = useSync();

  if (!pendingCount && !failedCount) return null;

  const { label, color, hint } = describe({ pendingCount, failedCount, flushing });

  return (
    <StatusChip
      label={label}
      color={color}
      size={size}
      onPress={onPress}
      style={style}
      // StatusChip derives its own accessibilityLabel from `label`, but "2 tertunda" alone does not
      // say tertunda WHAT. Screen-reader users get the whole sentence.
      accessibilityLabel={hint}
    />
  );
}

/** Exported for the inspector's header, so the two cannot describe the same queue differently. */
export function describe({ pendingCount, failedCount, flushing }) {
  if (failedCount) {
    return {
      label: `⚠ ${failedCount} gagal`,
      color: 'danger',
      hint: `${failedCount} perubahan gagal dikirim dan menunggu tindakan Anda`,
    };
  }
  if (flushing) {
    return {
      label: `↻ Mengirim ${pendingCount}`,
      color: 'info',
      hint: `Mengirim ${pendingCount} perubahan yang tertunda`,
    };
  }
  return {
    label: `⏳ ${pendingCount} tertunda`,
    color: 'warning',
    hint: `${pendingCount} perubahan belum terkirim ke server`,
  };
}
