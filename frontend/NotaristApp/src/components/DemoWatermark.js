import React from 'react';
import { View } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import AppText from './AppText';

// A loud, un-dismissable marker that the OCR/Verification data on screen is SAMPLE data, not a real
// extracted document (Sprint 4/5 demo mode). This is the guardrail that makes an opt-in demo of legal
// screens defensible: it is dev-build only (the toggle that shows demo data is gated on __DEV__), it
// is off by default, and while it is on this sits at the top of the screen and cannot be closed — so
// no one can mistake a fabricated NIK or checklist decision for a real one.
//
// Deliberately not a soft "data contoh" chip like the mock banners elsewhere: OCR fields and
// verification decisions are extracted legal FACTS, so their sample marker is stronger than a case
// list's — a solid danger-toned bar with a persistent watermark word.
export default function DemoWatermark({ label = 'DATA CONTOH — BUKAN DATA ASLI' }) {
  const theme = useTheme();
  return (
    <View
      accessibilityRole="alert"
      accessibilityLabel={`Peringatan: ${label}. Data ini hanya contoh untuk demonstrasi, bukan dokumen asli.`}
      style={{
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'center',
        gap: theme.spacing.sm,
        backgroundColor: theme.colors.danger,
        paddingVertical: theme.spacing.sm,
        paddingHorizontal: theme.spacing.md,
      }}
    >
      <AppText style={{ fontSize: 14 }}>⚠️</AppText>
      <AppText
        variant="label"
        style={{ color: '#fff', letterSpacing: 0.5, fontWeight: theme.typography.bold, textAlign: 'center' }}
      >
        {label}
      </AppText>
    </View>
  );
}
