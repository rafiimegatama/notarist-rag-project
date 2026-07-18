import React, { useEffect, useRef } from 'react';
import { View, Animated, Easing, TouchableOpacity } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import AppText from './AppText';
import useReducedMotion from '../hooks/useReducedMotion';

// A horizontal bar chart built from Views — no react-native-svg, no chart library (neither is a
// dependency of this app). Horizontal bars, not vertical: labels here are words ("Menunggu QC") that
// read cleanly beside a bar and would collide under a vertical column on a phone.
//
// Each bar grows from 0 to its share of the largest value when the data first lands, easing in
// together. Reduce-motion renders them full-width immediately. Every row is one accessible node
// ("label: value") and, when onPressItem is given, a button that drills into the underlying list —
// so the chart is not a second, mouse-only way to reach data the tiles already link to.
//
// An all-zero dataset renders empty tracks with the values, not a division-by-zero: "everything is 0"
// is a real, legible state of a workload chart, not an error.
function Bar({ item, max, progress, index, onPress, theme }) {
  const pct = max > 0 ? Math.max(0, Math.min(1, item.value / max)) : 0;
  const color = theme.colors[item.color] || item.color || theme.colors.primary;
  const width = progress.interpolate({ inputRange: [0, 1], outputRange: ['0%', `${pct * 100}%`] });
  const Row = onPress ? TouchableOpacity : View;

  return (
    <Row
      onPress={onPress ? () => onPress(item) : undefined}
      activeOpacity={0.8}
      accessibilityRole={onPress ? 'button' : 'text'}
      accessibilityLabel={`${item.label}: ${item.value}`}
      accessibilityHint={onPress ? 'Buka daftar terkait' : undefined}
      style={{ marginBottom: index === undefined ? 0 : theme.spacing.md }}
    >
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginBottom: 4 }}>
        <AppText variant="micro" color="textMuted" numberOfLines={1} style={{ flex: 1 }}>{item.label}</AppText>
        <AppText variant="micro" style={{ color, fontWeight: theme.typography.bold, marginLeft: theme.spacing.sm }}>{item.value}</AppText>
      </View>
      <View style={{ height: 8, borderRadius: 4, backgroundColor: theme.colors.surfaceAlt, overflow: 'hidden' }}>
        <Animated.View style={{ height: '100%', width, borderRadius: 4, backgroundColor: color }} />
      </View>
    </Row>
  );
}

/**
 * @param {Array<{label:string, value:number, color?:string}>} data  `color` is a theme key or hex.
 * @param {(item)=>void} [onPressItem]  makes each bar a drill-down button.
 */
export default function BarChart({ data = [], onPressItem, style }) {
  const theme = useTheme();
  const reducedMotion = useReducedMotion();
  const progress = useRef(new Animated.Value(0)).current;
  const max = data.reduce((m, d) => Math.max(m, Number(d.value) || 0), 0);

  useEffect(() => {
    if (reducedMotion) { progress.setValue(1); return undefined; }
    progress.setValue(0);
    const animation = Animated.timing(progress, {
      toValue: 1,
      duration: 700,
      easing: Easing.out(Easing.cubic),
      // Width interpolation is a layout prop; the native driver cannot animate it.
      useNativeDriver: false,
    });
    animation.start();
    return () => animation.stop();
  }, [reducedMotion, progress, max, data.length]);

  if (!data.length) return null;

  return (
    <View style={style}>
      {data.map((item, i) => (
        <Bar
          key={item.label}
          item={item}
          max={max}
          progress={progress}
          index={i === data.length - 1 ? undefined : i}
          onPress={onPressItem}
          theme={theme}
        />
      ))}
    </View>
  );
}
