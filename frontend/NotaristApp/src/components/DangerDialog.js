import React from 'react';
import ConfirmationDialog from './ConfirmationDialog';

/**
 * Destructive confirmation (delete conversation, reject all, logout). Same as ConfirmationDialog but
 * pinned to the danger tone with a destructive default confirm label.
 */
export default function DangerDialog({ confirmLabel = 'Hapus', ...props }) {
  return <ConfirmationDialog tone="danger" confirmLabel={confirmLabel} {...props} />;
}
