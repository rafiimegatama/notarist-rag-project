import React, { useEffect, useRef } from 'react';
import { View, ScrollView, Animated } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import { CASE_WORKFLOW } from '../constants/workflow';
import AppText from './AppText';

/**
 * Case workflow stepper: OPEN → OCR → REVIEW → GENERATE → QC → READY → DELIVERED.
 * Pure props: `steps` (defaults to CASE_WORKFLOW) + `currentIndex`. Steps before the current index are
 * "done" (filled), the current one is emphasized (a subtle spring pop on change), later ones are muted.
 * Horizontal scroll keeps all 7 stages reachable on a phone; screen-reader users get a single summary.
 */
export default function WorkflowStepper({ steps = CASE_WORKFLOW, currentIndex = 0, style }) {
  const theme = useTheme();
  const pop = useRef(new Animated.Value(0.8)).current;

  useEffect(() => {
    pop.setValue(0.8);
    Animated.spring(pop, { toValue: 1, ...theme.motion.spring }).start();
  }, [currentIndex, pop, theme.motion.spring]);

  const a11y = `Alur kerja: ${steps[currentIndex]?.label ?? '—'}, langkah ${currentIndex + 1} dari ${steps.length}`;

  return (
    <ScrollView
      horizontal
      showsHorizontalScrollIndicator={false}
      style={style}
      accessibilityRole="progressbar"
      accessibilityLabel={a11y}
    >
      <View style={{ flexDirection: 'row', alignItems: 'flex-start', paddingVertical: theme.spacing.xs }}>
        {steps.map((s, i) => {
          const done = i < currentIndex;
          const active = i === currentIndex;
          const color = done ? theme.colors.success : active ? theme.colors.primary : theme.colors.borderStrong;
          const circle = (
            <View
              style={{
                width: theme.iconSize.lg, height: theme.iconSize.lg, borderRadius: theme.iconSize.lg / 2,
                alignItems: 'center', justifyContent: 'center',
                backgroundColor: done || active ? color : theme.colors.surface,
                borderWidth: 2, borderColor: color,
              }}
            >
              <AppText style={{ color: done || active ? theme.colors.primaryText : theme.colors.textFaint, fontSize: theme.typography.micro, fontWeight: theme.typography.bold }}>
                {done ? '✓' : i + 1}
              </AppText>
            </View>
          );
          return (
            <View key={s.key} style={{ alignItems: 'center', width: 72 }}>
              <View style={{ flexDirection: 'row', alignItems: 'center', width: '100%', justifyContent: 'center' }}>
                {/* left connector */}
                <View style={{ flex: 1, height: 2, backgroundColor: i === 0 ? 'transparent' : (i <= currentIndex ? theme.colors.success : theme.colors.border) }} />
                {active ? <Animated.View style={{ transform: [{ scale: pop }] }}>{circle}</Animated.View> : circle}
                {/* right connector */}
                <View style={{ flex: 1, height: 2, backgroundColor: i === steps.length - 1 ? 'transparent' : (i < currentIndex ? theme.colors.success : theme.colors.border) }} />
              </View>
              <AppText
                variant="micro"
                numberOfLines={1}
                style={{ marginTop: 4, color: active ? theme.colors.primary : theme.colors.textFaint, fontWeight: active ? theme.typography.semibold : theme.typography.regular }}
              >
                {s.label}
              </AppText>
            </View>
          );
        })}
      </View>
    </ScrollView>
  );
}
