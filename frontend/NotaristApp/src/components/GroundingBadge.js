import React from 'react';
import StatusChip from './StatusChip';

// Assistant answer confidence is a DOMAIN enum (AnswerConfidence: HIGH | MEDIUM | LOW | INSUFFICIENT),
// not the 0..1 OCR score that ConfidenceBadge renders. They are different measurements — one is the
// RAG grounding verdict for a whole legal answer, the other is a per-field OCR score — so they get
// different components rather than one that guesses which it was handed.
//
// The mapping is deliberately conservative: INSUFFICIENT is the pipeline saying "I could not ground
// this", and in STRICT safety mode it means the answer is a refusal, so it reads danger, never a
// neutral grey that a notary might skim past.
const LEVEL_META = {
  HIGH:         { label: 'Grounding tinggi',  color: 'success', icon: '🟢' },
  MEDIUM:       { label: 'Grounding sedang',  color: 'warning', icon: '🟡' },
  LOW:          { label: 'Grounding rendah',  color: 'danger',  icon: '🟠' },
  INSUFFICIENT: { label: 'Tidak cukup dasar', color: 'danger',  icon: '🔴' },
};

const known = (v) => typeof v === 'number' && Number.isFinite(v);

/**
 * Grounding badge for an assistant answer. `level` is the AnswerConfidence enum name; `score` is the
 * optional grounding score (0..1) shown alongside when present. An unknown level renders nothing —
 * a missing verdict must not masquerade as a measured one.
 */
export default function GroundingBadge({ level, score = null, size = 'sm', style }) {
  if (!level) return null;
  const meta = LEVEL_META[String(level).toUpperCase()] || {
    label: String(level), color: 'textFaint', icon: '⚪',
  };
  const label = known(score) ? `${meta.label} · ${Math.round(score * 100)}%` : meta.label;
  return (
    <StatusChip
      label={label}
      icon={meta.icon}
      color={meta.color}
      size={size}
      tone="soft"
      accessibilityLabel={`Tingkat dasar jawaban: ${meta.label}${known(score) ? `, skor ${Math.round(score * 100)} persen` : ''}`}
      style={style}
    />
  );
}
