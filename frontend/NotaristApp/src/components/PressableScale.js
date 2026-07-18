import React, { useRef } from 'react';
import { Animated, Pressable, StyleSheet } from 'react-native';
import useReducedMotion from '../hooks/useReducedMotion';

// Style keys that size/position the component within ITS PARENT. These must live on the OUTER
// Pressable — that is the flex item the parent lays out. With everything on the inner Animated.View,
// a percentage width (StatCard passes width:'23%') resolved against the content-sized Pressable
// wrapper instead of the flex row: on web (RC browser acceptance) every dashboard tile collapsed to
// a zero-width sliver with an invisible label. Native Yoga happened to be forgiving; web flexbox is
// not, and the app ships to web.
const LAYOUT_KEYS = [
  'width', 'flex', 'flexGrow', 'flexShrink', 'flexBasis', 'alignSelf',
  'margin', 'marginTop', 'marginBottom', 'marginLeft', 'marginRight',
  'marginHorizontal', 'marginVertical', 'marginStart', 'marginEnd',
];
// Sizing constraints are honoured on the outer box AND kept on the inner card so the visual surface
// (background, border) still fills them.
const SIZE_KEYS = ['height', 'minHeight', 'maxHeight', 'minWidth', 'maxWidth'];

function splitStyle(style) {
  const flat = StyleSheet.flatten(style) || {};
  const outer = {};
  const inner = { ...flat };
  for (const k of LAYOUT_KEYS) {
    if (k in inner) { outer[k] = inner[k]; delete inner[k]; }
  }
  for (const k of SIZE_KEYS) {
    if (k in inner) outer[k] = inner[k];
  }
  return { outer, inner };
}

// A press wrapper that dips its child slightly while held — the tactile "this is pressable" feedback
// for cards and tiles (Sprint 12). Built on the RN Animated driver (react-native-reanimated is not a
// dependency) with useNativeDriver, so the scale runs off the JS thread.
//
// Accessibility: the Pressable carries the role/label, and reduce-motion disables the scale entirely
// (the press still works — only the animation is dropped). Not a replacement for TouchableOpacity
// everywhere; it is for the surfaces where a scale reads better than an opacity dip (whole cards).
export default function PressableScale({
  onPress,
  onLongPress,
  disabled = false,
  scaleTo = 0.97,
  style,
  children,
  accessibilityRole = 'button',
  accessibilityLabel,
  accessibilityHint,
  accessibilityState,
  ...rest
}) {
  const reduced = useReducedMotion();
  const scale = useRef(new Animated.Value(1)).current;

  const to = (value) => {
    if (reduced) return;
    Animated.spring(scale, { toValue: value, useNativeDriver: true, friction: 7, tension: 120 }).start();
  };

  const { outer, inner } = splitStyle(style);

  return (
    <Pressable
      onPress={onPress}
      onLongPress={onLongPress}
      disabled={disabled}
      onPressIn={() => to(scaleTo)}
      onPressOut={() => to(1)}
      accessibilityRole={accessibilityRole}
      accessibilityLabel={accessibilityLabel}
      accessibilityHint={accessibilityHint}
      accessibilityState={{ disabled, ...(accessibilityState || {}) }}
      style={outer}
      {...rest}
    >
      <Animated.View style={[inner, { transform: [{ scale }] }]}>{children}</Animated.View>
    </Pressable>
  );
}
