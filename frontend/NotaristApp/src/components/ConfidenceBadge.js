import React from 'react';
import StatusChip from './StatusChip';

/** A real, finite confidence — as opposed to absent, which must never render as a number. */
const known = (v) => typeof v === 'number' && Number.isFinite(v);

// OCR confidence bands -> semantic color key. Single source so every confidence readout agrees.
// An unknown confidence gets the muted "no data" key, NOT the danger key: "we did not measure this
// field" and "this field scored badly" are different facts and must not share a color.
export function confidenceColorKey(value) {
  if (!known(value)) return 'textFaint';
  return value >= 0.85 ? 'success' : value >= 0.7 ? 'warning' : 'danger';
}

/**
 * Compact confidence readout, e.g. "92%", tinted by band (high=success, med=warning, low=danger).
 * An absent value renders "—", not "0%".
 *
 * Sprint 6: this defaulted to 0 and did `(value ?? 0) * 100`, so a field the OCR never scored
 * rendered as a red "0%" — a specific, alarming measurement invented out of a missing field.
 * models/Ocr.js goes out of its way to return null rather than 0 for exactly this reason ("never 0,
 * which would render as '0% confident' rather than 'unknown'") — and then this component turned the
 * null back into 0. A normalizer's honesty only survives if the component agrees to render it.
 */
export default function ConfidenceBadge({ value = null, size = 'sm', tone = 'soft', showLabel = true, style }) {
  const label = known(value)
    ? (showLabel ? `${Math.round(value * 100)}%` : String(Math.round(value * 100)))
    : '—';
  return <StatusChip label={label} color={confidenceColorKey(value)} size={size} tone={tone} style={style} />;
}
