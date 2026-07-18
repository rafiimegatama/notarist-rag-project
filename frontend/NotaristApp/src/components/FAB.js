import React, { useEffect, useRef } from 'react';
import { Animated, Pressable, ActivityIndicator } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import AppText from './AppText';
import useReducedMotion from '../hooks/useReducedMotion';

// Floating action button (Sprint 12). Pops in on mount and dips on press, on the Animated driver
// (react-native-reanimated is not a dependency). While `busy`, it shows a spinner and is disabled —
// the same action-feedback role the DocumentsScreen upload button already relied on, now animated.
//
// Reduce-motion skips the entrance and the press dip; the button appears and works immediately.
export default function FAB({ icon = '+', onPress, busy = false, disabled = false, accessibilityLabel, style }) {
  const theme = useTheme();
  const reduced = useReducedMotion();
  const enter = useRef(new Animated.Value(reduced ? 1 : 0)).current;
  const press = useRef(new Animated.Value(1)).current;
  const isDisabled = disabled || busy;

  useEffect(() => {
    if (reduced) { enter.setValue(1); return undefined; }
    const animation = Animated.spring(enter, { toValue: 1, useNativeDriver: true, friction: 6, tension: 90 });
    animation.start();
    return () => animation.stop();
  }, [reduced, enter]);

  const to = (value) => {
    if (reduced) return;
    Animated.spring(press, { toValue: value, useNativeDriver: true, friction: 7, tension: 140 }).start();
  };

  return (
    <Animated.View
      style={[
        {
          position: 'absolute',
          right: theme.spacing.xl,
          bottom: theme.spacing.xl,
          transform: [{ scale: Animated.multiply(enter, press) }],
          opacity: enter,
        },
        style,
      ]}
    >
      <Pressable
        onPress={onPress}
        onPressIn={() => to(0.9)}
        onPressOut={() => to(1)}
        disabled={isDisabled}
        accessibilityRole="button"
        accessibilityLabel={accessibilityLabel}
        accessibilityState={{ disabled: isDisabled, busy }}
        style={{
          width: 56,
          height: 56,
          borderRadius: 28,
          backgroundColor: theme.colors.primary,
          alignItems: 'center',
          justifyContent: 'center',
          opacity: isDisabled ? 0.6 : 1,
          ...theme.shadows.lg,
        }}
      >
        {busy ? (
          <ActivityIndicator color={theme.colors.primaryText} />
        ) : (
          <AppText style={{ color: theme.colors.primaryText, fontSize: 28, fontWeight: theme.typography.bold, lineHeight: 30 }}>
            {icon}
          </AppText>
        )}
      </Pressable>
    </Animated.View>
  );
}
