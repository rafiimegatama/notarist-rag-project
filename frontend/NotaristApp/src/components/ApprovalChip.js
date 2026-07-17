import React from 'react';
import { approvalStatusMeta } from '../constants/workflow';
import StatusChip from './StatusChip';

/** Approval outcome chip (PENDING / APPROVED / REJECTED), resolved from workflow metadata. */
export default function ApprovalChip({ status, size = 'sm', tone = 'soft', style }) {
  const meta = approvalStatusMeta(status);
  const icon = status === 'APPROVED' ? '✓' : status === 'REJECTED' ? '✕' : '•';
  return <StatusChip label={meta.label} color={meta.color} icon={icon} size={size} tone={tone} style={style} />;
}
