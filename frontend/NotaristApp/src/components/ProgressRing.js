import React from 'react';
import { View } from 'react-native';
import { useTheme } from '../context/ThemeContext';

const clamp01 = (n) => Math.max(0, Math.min(1, Number.isFinite(n) ? n : 0));

// A circular progress indicator built from dots on a ring (Sprint 5). Dots rather than an SVG arc on
// purpose: react-native-svg is not a dependency, and a dotted ring needs only trig for placement —
// no half-circle masking math to get subtly wrong. `filled = round(progress * dots)` lit dots read as
// a clean ring, and the centre slot holds whatever the caller puts there (a percentage, a count).
export default function ProgressRing({
  size = 96,
  dots = 20,
  dotSize = 7,
  progress = 0,
  color = 'success',
  trackColor = 'border',
  children,
  style,
}) {
  const theme = useTheme();
  const p = clamp01(progress);
  const filled = Math.round(p * dots);
  const on = theme.colors[color] || color;
  const off = theme.colors[trackColor] || trackColor;
  const center = size / 2;
  const radius = center - dotSize;

  return (
    <View
      style={[{ width: size, height: size, alignItems: 'center', justifyContent: 'center' }, style]}
      accessibilityRole="progressbar"
      accessibilityValue={{ min: 0, max: 100, now: Math.round(p * 100) }}
    >
      {Array.from({ length: dots }).map((_, i) => {
        const angle = (i / dots) * 2 * Math.PI - Math.PI / 2; // start at 12 o'clock, go clockwise
        const x = center + radius * Math.cos(angle) - dotSize / 2;
        const y = center + radius * Math.sin(angle) - dotSize / 2;
        return (
          <View
            key={i}
            style={{
              position: 'absolute',
              left: x,
              top: y,
              width: dotSize,
              height: dotSize,
              borderRadius: dotSize / 2,
              backgroundColor: i < filled ? on : off,
            }}
          />
        );
      })}
      <View style={{ alignItems: 'center', justifyContent: 'center' }}>{children}</View>
    </View>
  );
}
