import React, { useEffect, useRef } from 'react';
import { Animated } from 'react-native';
import { useTheme } from '../context/ThemeContext';

/**
 * A checkmark that springs in — the single "approval success" flourish. Kept deliberately minimal
 * (one spring, no loop) to honor "tasteful, not over-animated". `size` in px; `onDone` fires after.
 */
export default function SuccessCheck({ size = 64, onDone, style }) {
  const theme = useTheme();
  const scale = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    Animated.spring(scale, { toValue: 1, friction: 5, tension: 120, useNativeDriver: true }).start(({ finished }) => {
      if (finished) onDone?.();
    });
  }, [scale, onDone]);

  return (
    <Animated.View
      accessibilityRole="image"
      accessibilityLabel="Berhasil"
      style={[
        {
          width: size, height: size, borderRadius: size / 2,
          backgroundColor: theme.colors.success,
          alignItems: 'center', justifyContent: 'center',
          transform: [{ scale }],
        },
        style,
      ]}
    >
      <Animated.Text style={{ color: theme.colors.primaryText, fontSize: size * 0.5, fontWeight: '700' }}>✓</Animated.Text>
    </Animated.View>
  );
}
