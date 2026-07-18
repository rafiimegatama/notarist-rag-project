import React, { useEffect, useRef } from 'react';
import { View, Animated, Easing } from 'react-native';
import { useTheme } from '../context/ThemeContext';

// Three dots that rise and fade in sequence — the "assistant is thinking" state shown between sending
// a question and the first streamed token arriving. Pure presentation, no props beyond styling.
//
// Uses the RN Animated driver (react-native-reanimated is not a dependency of this app) with
// useNativeDriver so the loop runs off the JS thread and never competes with token rendering.
function Dot({ delay, color }) {
  const value = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    const loop = Animated.loop(
      Animated.sequence([
        Animated.delay(delay),
        Animated.timing(value, { toValue: 1, duration: 300, easing: Easing.inOut(Easing.ease), useNativeDriver: true }),
        Animated.timing(value, { toValue: 0, duration: 300, easing: Easing.inOut(Easing.ease), useNativeDriver: true }),
        Animated.delay(600 - delay),
      ]),
    );
    loop.start();
    return () => loop.stop();
  }, [delay, value]);

  return (
    <Animated.View
      style={{
        width: 6,
        height: 6,
        borderRadius: 3,
        marginHorizontal: 2,
        backgroundColor: color,
        opacity: value.interpolate({ inputRange: [0, 1], outputRange: [0.3, 1] }),
        transform: [{ translateY: value.interpolate({ inputRange: [0, 1], outputRange: [0, -3] }) }],
      }}
    />
  );
}

export default function TypingIndicator({ style }) {
  const theme = useTheme();
  return (
    <View
      accessibilityRole="text"
      accessibilityLabel="Asisten sedang mengetik"
      style={[{ flexDirection: 'row', alignItems: 'center', paddingVertical: 4 }, style]}
    >
      <Dot delay={0} color={theme.colors.textMuted} />
      <Dot delay={150} color={theme.colors.textMuted} />
      <Dot delay={300} color={theme.colors.textMuted} />
    </View>
  );
}
