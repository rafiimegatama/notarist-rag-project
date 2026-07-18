import React, { useEffect, useRef, useState } from 'react';
import { Modal, View, Animated, Pressable, PanResponder } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTheme } from '../context/ThemeContext';
import useReducedMotion from '../hooks/useReducedMotion';

const OFFSCREEN = 700;
const DISMISS_DISTANCE = 120;

// A bottom sheet (Sprint 12): slides up from the bottom edge with a fading backdrop, and can be
// dragged down to dismiss. Built on Animated + PanResponder (react-native-reanimated / gesture-handler
// sheets are not dependencies here). It self-manages a mounted flag so the exit animation finishes
// before the Modal unmounts. Reduce-motion drops the slide/fade timing (opens and closes instantly).
//
// Tapping the backdrop or dragging past a threshold calls onClose; the caller owns `visible`.
export default function BottomSheet({ visible, onClose, children, style }) {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const reduced = useReducedMotion();
  const translateY = useRef(new Animated.Value(OFFSCREEN)).current;
  const backdrop = useRef(new Animated.Value(0)).current;
  const [mounted, setMounted] = useState(visible);

  useEffect(() => {
    if (visible) {
      setMounted(true);
      translateY.setValue(OFFSCREEN);
      Animated.parallel([
        Animated.timing(backdrop, { toValue: 1, duration: reduced ? 0 : 200, useNativeDriver: true }),
        reduced
          ? Animated.timing(translateY, { toValue: 0, duration: 0, useNativeDriver: true })
          : Animated.spring(translateY, { toValue: 0, useNativeDriver: true, friction: 9, tension: 70 }),
      ]).start();
    } else if (mounted) {
      Animated.parallel([
        Animated.timing(backdrop, { toValue: 0, duration: reduced ? 0 : 180, useNativeDriver: true }),
        Animated.timing(translateY, { toValue: OFFSCREEN, duration: reduced ? 0 : 180, useNativeDriver: true }),
      ]).start(() => setMounted(false));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [visible]);

  const pan = useRef(
    PanResponder.create({
      onMoveShouldSetPanResponder: (_, g) => g.dy > 6 && Math.abs(g.dy) > Math.abs(g.dx),
      onPanResponderMove: (_, g) => { if (g.dy > 0) translateY.setValue(g.dy); },
      onPanResponderRelease: (_, g) => {
        if (g.dy > DISMISS_DISTANCE) {
          onClose && onClose();
        } else {
          Animated.spring(translateY, { toValue: 0, useNativeDriver: true, friction: 9, tension: 70 }).start();
        }
      },
    }),
  ).current;

  if (!mounted) return null;

  return (
    <Modal visible transparent animationType="none" onRequestClose={onClose}>
      <View style={{ flex: 1, justifyContent: 'flex-end' }}>
        <Animated.View style={{ ...StyleSheetAbsolute, backgroundColor: theme.colors.overlay, opacity: backdrop }}>
          <Pressable style={{ flex: 1 }} onPress={onClose} accessibilityLabel="Tutup" accessibilityRole="button" />
        </Animated.View>

        <Animated.View
          {...pan.panHandlers}
          accessibilityViewIsModal
          style={[
            {
              backgroundColor: theme.colors.elevated,
              borderTopLeftRadius: theme.radius.xl,
              borderTopRightRadius: theme.radius.xl,
              borderWidth: 1,
              borderColor: theme.colors.border,
              paddingHorizontal: theme.spacing.xl,
              paddingTop: theme.spacing.sm,
              paddingBottom: theme.spacing.xl + insets.bottom,
              transform: [{ translateY }],
              ...theme.shadows.lg,
            },
            style,
          ]}
        >
          {/* Drag handle — also the visual affordance that this can be pulled down. */}
          <View
            accessibilityElementsHidden
            importantForAccessibility="no"
            style={{ alignSelf: 'center', width: 40, height: 4, borderRadius: 2, backgroundColor: theme.colors.border, marginBottom: theme.spacing.md }}
          />
          {children}
        </Animated.View>
      </View>
    </Modal>
  );
}

const StyleSheetAbsolute = { position: 'absolute', top: 0, left: 0, right: 0, bottom: 0 };
