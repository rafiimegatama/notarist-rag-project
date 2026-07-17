import React from 'react';
import { View } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import AppText from './AppText';
import Card from './Card';
import StatusChip from './StatusChip';

/**
 * OCR Review authority panel: summarizes stamp/signature detection and the required approvals for a
 * document. Pure props: `stampDetected`, `signatureDetected`, and an optional `note`.
 */
export default function AuthorityPanel({ stampDetected = false, signatureDetected = false, note, style }) {
  const theme = useTheme();
  return (
    <Card style={style}>
      <AppText variant="label" color="textMuted" style={{ marginBottom: theme.spacing.sm }}>Authority Panel</AppText>
      <View style={{ flexDirection: 'row', gap: theme.spacing.sm, marginBottom: theme.spacing.sm }}>
        <StatusChip label={stampDetected ? '🔖 Stempel ✓' : '🔖 Stempel ✕'} color={stampDetected ? 'success' : 'danger'} size="sm" />
        <StatusChip label={signatureDetected ? '✒️ Tanda tangan ✓' : '✒️ Tanda tangan ✕'} color={signatureDetected ? 'success' : 'danger'} size="sm" />
      </View>
      <AppText variant="bodySm" color="textMuted">
        {note || 'Persetujuan final memerlukan otorisasi Notaris dan Direksi Bank untuk jaminan bernilai tinggi.'}
      </AppText>
    </Card>
  );
}
