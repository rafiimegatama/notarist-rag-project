import React, { useRef, useState } from 'react';
import { View, TouchableOpacity, Animated, LayoutAnimation, Platform, UIManager } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import AppText from './AppText';
import useReducedMotion from '../hooks/useReducedMotion';

if (Platform.OS === 'android' && UIManager.setLayoutAnimationEnabledExperimental) {
  UIManager.setLayoutAnimationEnabledExperimental(true);
}

// A titled expand/collapse panel (Sprint 12). The chevron rotates on the Animated driver and the body
// reveals under a LayoutAnimation, so opening reads as a smooth unfold rather than a jump. Reduce-
// motion drops both — the panel still opens, instantly.
//
// A11y: the header is a button carrying { expanded } state, so a screen reader announces "expanded"/
// "collapsed" and the whole panel is reachable and operable without seeing the animation.
export default function Accordion({ title, icon, defaultOpen = false, children, style }) {
  const theme = useTheme();
  const reduced = useReducedMotion();
  const [open, setOpen] = useState(defaultOpen);
  const rotate = useRef(new Animated.Value(defaultOpen ? 1 : 0)).current;

  const toggle = () => {
    const next = !open;
    if (!reduced) {
      LayoutAnimation.configureNext(
        LayoutAnimation.create(theme.durations.fast, LayoutAnimation.Types.easeInEaseOut, LayoutAnimation.Properties.opacity),
      );
    }
    Animated.timing(rotate, { toValue: next ? 1 : 0, duration: reduced ? 0 : theme.durations.fast, useNativeDriver: true }).start();
    setOpen(next);
  };

  const spin = rotate.interpolate({ inputRange: [0, 1], outputRange: ['0deg', '180deg'] });

  return (
    <View
      style={[{
        backgroundColor: theme.colors.surface,
        borderRadius: theme.radius.lg,
        borderWidth: 1,
        borderColor: theme.colors.border,
        overflow: 'hidden',
      }, style]}
    >
      <TouchableOpacity
        onPress={toggle}
        activeOpacity={0.8}
        accessibilityRole="button"
        accessibilityState={{ expanded: open }}
        accessibilityLabel={typeof title === 'string' ? title : undefined}
        style={{ flexDirection: 'row', alignItems: 'center', padding: theme.spacing.lg, minHeight: theme.touchTarget.min }}
      >
        {icon ? <AppText style={{ fontSize: 16, marginRight: theme.spacing.sm }}>{icon}</AppText> : null}
        <AppText variant="bodyStrong" style={{ flex: 1 }}>{title}</AppText>
        <Animated.Text style={{ color: theme.colors.textMuted, transform: [{ rotate: spin }] }}>▾</Animated.Text>
      </TouchableOpacity>
      {open ? (
        <View style={{ paddingHorizontal: theme.spacing.lg, paddingBottom: theme.spacing.lg }}>
          {children}
        </View>
      ) : null}
    </View>
  );
}
