import React, { useEffect, useRef } from 'react';
import { View, Animated } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import { BUNDLE_PIPELINE } from '../constants/workflow';
import AppText from './AppText';

/**
 * Bundle ingestion pipeline: OCR → NER → Chunk → Embedding → Index → Completed.
 * Pure props: `stages` (defaults to BUNDLE_PIPELINE) + `currentIndex` (how far the pipeline has run).
 * Renders a labeled dot rail with an animated fill bar showing overall completion. Data-driven only —
 * knows nothing about where the numbers come from.
 */
export default function PipelineProgress({ stages = BUNDLE_PIPELINE, currentIndex = 0, showPercent = true, style }) {
  const theme = useTheme();
  const pct = stages.length ? Math.max(0, Math.min(1, currentIndex / (stages.length - 1))) : 0;
  const fill = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    Animated.timing(fill, { toValue: pct, duration: theme.durations.slow, useNativeDriver: false }).start();
  }, [pct, fill, theme.durations.slow]);

  const width = fill.interpolate({ inputRange: [0, 1], outputRange: ['0%', '100%'] });
  const a11y = `Pipeline bundle: ${stages[currentIndex]?.label ?? '—'} (${Math.round(pct * 100)}%)`;

  return (
    <View style={style} accessibilityRole="progressbar" accessibilityLabel={a11y}>
      {/* fill bar */}
      <View style={{ height: 6, borderRadius: 3, backgroundColor: theme.colors.border, overflow: 'hidden' }}>
        <Animated.View style={{ width, height: '100%', backgroundColor: theme.colors.primary }} />
      </View>
      {/* dot rail */}
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginTop: theme.spacing.sm }}>
        {stages.map((s, i) => {
          const done = i <= currentIndex;
          return (
            <View key={s.key} style={{ alignItems: 'center', flex: 1 }}>
              <View style={{ width: 10, height: 10, borderRadius: 5, backgroundColor: done ? theme.colors.primary : theme.colors.surface, borderWidth: 2, borderColor: done ? theme.colors.primary : theme.colors.borderStrong }} />
              <AppText variant="micro" numberOfLines={1} style={{ marginTop: 4, color: done ? theme.colors.text : theme.colors.textFaint }}>{s.label}</AppText>
            </View>
          );
        })}
      </View>
      {showPercent ? (
        <AppText variant="micro" color="textFaint" style={{ marginTop: theme.spacing.xs, textAlign: 'right' }}>{Math.round(pct * 100)}% selesai</AppText>
      ) : null}
    </View>
  );
}
