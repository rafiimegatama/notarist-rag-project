import React from 'react';
import { approvalStatusMeta } from '../constants/workflow';
import TimelineCard from './TimelineCard';
import ApprovalChip from './ApprovalChip';

/**
 * Approval sequence timeline. `steps` = [{ id, label, status, at }] where status is an APPROVAL_STATUS
 * key. Each row shows an ApprovalChip. Generic counterpart to DirectorTimeline.
 */
export default function ApprovalTimeline({ steps = [], style }) {
  const items = steps.map((s) => ({ id: s.id, label: s.label, at: s.at, done: s.status === 'APPROVED' }));
  const statusOf = (id) => steps.find((s) => s.id === id)?.status;
  return (
    <TimelineCard
      items={items}
      style={style}
      renderMeta={(it) => <ApprovalChip status={statusOf(it.id)} size="sm" />}
    />
  );
}
