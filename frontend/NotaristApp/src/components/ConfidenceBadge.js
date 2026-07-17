import React from 'react';
import StatusChip from './StatusChip';

// OCR confidence bands -> semantic color key. Single source so every confidence readout agrees.
export function confidenceColorKey(value) {
  const c = value ?? 0;
  return c >= 0.85 ? 'success' : c >= 0.7 ? 'warning' : 'danger';
}

/** Compact confidence readout, e.g. "92%", tinted by band (high=success, med=warning, low=danger). */
export default function ConfidenceBadge({ value = 0, size = 'sm', tone = 'soft', showLabel = true, style }) {
  const pct = Math.round((value ?? 0) * 100);
  const label = showLabel ? `${pct}%` : String(pct);
  return <StatusChip label={label} color={confidenceColorKey(value)} size={size} tone={tone} style={style} />;
}
