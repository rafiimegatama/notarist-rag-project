import React from 'react';
import { View } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import AppText from './AppText';
import Card from './Card';
import StatusChip from './StatusChip';

/**
 * OCR Review authority panel: summarizes stamp/signature detection and the required approvals for a
 * document. Pure props: `stampDetected`, `signatureDetected` (true | false | null), optional `note`.
 *
 * THREE states per signal, not two (Sprint 6). The props defaulted to `false` and rendered
 * `? '✓' : '✕'`, so an ABSENT value — the backend never told us — displayed as a red
 * "🔖 Stempel ✕": a confident assertion that a scanned deed carries NO notarial stamp, manufactured
 * out of a missing field. models/Ocr.js now reads these as tri-state (normalize#triBool), and null
 * renders as what it is — "—", unknown, in the muted tone this app uses for "no data" everywhere
 * else. A missing signal must look missing, not negative.
 */
function signalChip(value, icon, label) {
  if (value === true) return { label: icon + ' ' + label + ' ✓', color: 'success' };
  if (value === false) return { label: icon + ' ' + label + ' ✕', color: 'danger' };
  return { label: icon + ' ' + label + ' —', color: 'textFaint' };
}

export default function AuthorityPanel({ stampDetected = null, signatureDetected = null, note, style }) {
  const theme = useTheme();
  const stamp = signalChip(stampDetected, '🔖', 'Stempel');
  const signature = signalChip(signatureDetected, '✒️', 'Tanda tangan');
  return (
    <Card style={style}>
      <AppText variant="label" color="textMuted" style={{ marginBottom: theme.spacing.sm }}>Authority Panel</AppText>
      <View style={{ flexDirection: 'row', gap: theme.spacing.sm, marginBottom: theme.spacing.sm }}>
        <StatusChip label={stamp.label} color={stamp.color} size="sm" />
        <StatusChip label={signature.label} color={signature.color} size="sm" />
      </View>
      <AppText variant="bodySm" color="textMuted">
        {note || 'Persetujuan final memerlukan otorisasi Notaris dan Direksi Bank untuk jaminan bernilai tinggi.'}
      </AppText>
    </Card>
  );
}
