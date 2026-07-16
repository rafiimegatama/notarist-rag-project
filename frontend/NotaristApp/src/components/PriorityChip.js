import React from 'react';
import { priorityMeta } from '../constants/workflow';
import StatusChip from './StatusChip';

/** Priority pill (HIGH / MEDIUM / LOW), colored from the dedicated priority palette keys. */
export default function PriorityChip({ priority = 'MEDIUM', size = 'sm', tone = 'soft', style }) {
  const meta = priorityMeta(priority);
  return <StatusChip label={meta.label} color={meta.color} icon={meta.icon} size={size} tone={tone} style={style} />;
}
