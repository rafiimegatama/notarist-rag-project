import React, { useEffect, useRef } from 'react';
import { View, Animated, Easing } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import AppText from './AppText';
import useReducedMotion from '../hooks/useReducedMotion';

// A small status pill telling the notary whether the numbers above it are LIVE (polling, online and
// fresh) or something less: refreshing, showing saved data, or offline. It is the honest counterpart
// to the animated counters — a count-up looks authoritative, so the dashboard must also say plainly
// when what it is counting is stale. Copy is deliberately blunt because "0 menunggu approval" read as
// live when it is a day old is a decision made on wrong data.
//
// The dot pulses only in the two ACTIVE states (live / syncing); a resting state gets a still dot, and
// reduce-motion stills all of them.
const STATUS_META = {
  live:    { color: 'success', label: 'Langsung', pulse: true },
  syncing: { color: 'info',    label: 'Memperbarui…', pulse: true },
  stale:   { color: 'warning', label: 'Data tersimpan', pulse: false },
  offline: { color: 'textFaint', label: 'Luring', pulse: false },
};

export default function RealtimeBadge({ status = 'live', label, style }) {
  const theme = useTheme();
  const reducedMotion = useReducedMotion();
  const meta = STATUS_META[status] || STATUS_META.live;
  const color = theme.colors[meta.color] || theme.colors.textFaint;
  const pulse = useRef(new Animated.Value(1)).current;
  const animate = meta.pulse && !reducedMotion;

  useEffect(() => {
    if (!animate) { pulse.setValue(1); return undefined; }
    const loop = Animated.loop(
      Animated.sequence([
        Animated.timing(pulse, { toValue: 0.3, duration: 700, easing: Easing.inOut(Easing.ease), useNativeDriver: true }),
        Animated.timing(pulse, { toValue: 1, duration: 700, easing: Easing.inOut(Easing.ease), useNativeDriver: true }),
      ]),
    );
    loop.start();
    return () => loop.stop();
  }, [animate, pulse]);

  return (
    <View
      accessibilityRole="text"
      accessibilityLabel={`Status data: ${label || meta.label}`}
      style={[
        {
          flexDirection: 'row',
          alignItems: 'center',
          alignSelf: 'flex-start',
          backgroundColor: theme.colors.surfaceAlt,
          borderRadius: theme.radius.pill,
          paddingHorizontal: theme.spacing.sm,
          paddingVertical: 3,
          gap: 6,
        },
        style,
      ]}
    >
      <Animated.View style={{ width: 7, height: 7, borderRadius: 4, backgroundColor: color, opacity: pulse }} />
      <AppText variant="micro" style={{ color, fontWeight: theme.typography.semibold }}>
        {label || meta.label}
      </AppText>
    </View>
  );
}
