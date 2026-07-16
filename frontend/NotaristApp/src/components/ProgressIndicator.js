import React from 'react';
import { View } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import { stepStatusMeta } from '../constants/workflow';
import AppText from './AppText';

/**
 * Two modes:
 *  - `steps`: array of { label, status } (STEP_STATUS keys) -> a segmented pipeline bar, each segment
 *    colored by its status. Used on Bundle cards to show OCR→Verif→Draft→QC→Approval at a glance.
 *  - `value` (0..1): a single fill bar with optional label. Used for coarse case completion.
 */
export default function ProgressIndicator({ steps, value, label, showLabels = false, style }) {
  const theme = useTheme();

  if (Array.isArray(steps)) {
    return (
      <View style={style}>
        <View style={{ flexDirection: 'row', gap: 3 }}>
          {steps.map((s, i) => {
            const meta = stepStatusMeta(s.status);
            return (
              <View
                key={s.key ?? i}
                style={{ flex: 1, height: 6, borderRadius: 3, backgroundColor: theme.colors[meta.color] || theme.colors.border }}
              />
            );
          })}
        </View>
        {showLabels ? (
          <View style={{ flexDirection: 'row', gap: 3, marginTop: 4 }}>
            {steps.map((s, i) => (
              <AppText key={s.key ?? i} variant="micro" color="textFaint" numberOfLines={1} style={{ flex: 1, textAlign: 'center' }}>
                {s.label}
              </AppText>
            ))}
          </View>
        ) : null}
      </View>
    );
  }

  const pct = Math.max(0, Math.min(1, value ?? 0));
  return (
    <View style={style}>
      {label ? (
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginBottom: 4 }}>
          <AppText variant="micro" color="textFaint">{label}</AppText>
          <AppText variant="micro" color="textFaint">{Math.round(pct * 100)}%</AppText>
        </View>
      ) : null}
      <View style={{ height: 6, borderRadius: 3, backgroundColor: theme.colors.border, overflow: 'hidden' }}>
        <View style={{ width: `${pct * 100}%`, height: '100%', backgroundColor: theme.colors.primary }} />
      </View>
    </View>
  );
}
