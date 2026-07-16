import React from 'react';
import { approvalStatusMeta } from '../constants/workflow';
import StatusChip from './StatusChip';

/**
 * Solid approval badge (PENDING / APPROVED / REJECTED). Same data as ApprovalChip but rendered filled
 * for use as a prominent status marker (e.g. a case/bundle header), vs the soft inline ApprovalChip.
 */
export default function ApprovalBadge({ status, size = 'sm', style }) {
  const meta = approvalStatusMeta(status);
  const icon = status === 'APPROVED' ? '✓' : status === 'REJECTED' ? '✕' : '•';
  return <StatusChip label={meta.label} color={meta.color} icon={icon} size={size} tone="solid" style={style} />;
}
