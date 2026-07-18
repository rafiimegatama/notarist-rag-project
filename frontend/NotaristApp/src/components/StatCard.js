import React from 'react';
import { View } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import AppText from './AppText';
import AnimatedNumber from './AnimatedNumber';
import PressableScale from './PressableScale';

/**
 * Dashboard metric tile: big value + label with a left accent bar. Optional `icon`, and `onPress`
 * to drill into the underlying list. `tone` is a theme color key for the accent. Non-interactive
 * when no onPress is given (renders a plain View).
 *
 * `animate` (default true) counts a NUMERIC value up when it changes; a non-numeric value (e.g. the
 * "—" a missing counter renders) is shown verbatim. The accessible name always reads the final value,
 * never the mid-animation number — the count-up is decoration, the label is the fact.
 *
 * Accessibility (Sprint 4, Task 11): the tile is read as ONE node — "Menunggu Verifikasi: 3, tombol"
 * — rather than as a stray number followed by a stray label. Previously a tappable tile exposed no
 * button role, so a screen-reader user had no way to know the number could be opened at all.
 */
function StatCard({ label, value, tone = 'primary', icon, onPress, animate = true, style }) {
  const theme = useTheme();
  const accent = theme.colors[tone] || theme.colors.primary;
  const valueStyle = { color: theme.colors.text, fontSize: theme.typography.h1, fontWeight: theme.typography.bold };
  const numeric = typeof value === 'number' && Number.isFinite(value);

  const cardStyle = [
    {
      backgroundColor: theme.colors.surface,
      borderRadius: theme.radius.lg,
      borderWidth: 1,
      borderColor: theme.colors.border,
      borderLeftWidth: 3,
      borderLeftColor: accent,
      padding: theme.spacing.lg,
      minHeight: 84,
      justifyContent: 'center',
    },
    style,
  ];

  const inner = (
    <>
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
        {animate && numeric ? (
          <AnimatedNumber value={value} style={valueStyle} />
        ) : (
          <AppText style={valueStyle}>{value}</AppText>
        )}
        {icon ? (
          <AppText accessibilityElementsHidden importantForAccessibility="no" style={{ fontSize: 18 }}>
            {icon}
          </AppText>
        ) : null}
      </View>
      <AppText color="textFaint" variant="micro" numberOfLines={2} style={{ marginTop: 2 }}>
        {label}
      </AppText>
    </>
  );

  // A tappable tile scales on press (Sprint 12) and is read as ONE button node; a non-interactive
  // tile is a plain grouped View. `accessible` groups the number + label into one announcement.
  if (onPress) {
    return (
      <PressableScale
        onPress={onPress}
        accessible
        accessibilityLabel={`${label}: ${value}`}
        accessibilityHint="Buka daftar terkait"
        style={cardStyle}
      >
        {inner}
      </PressableScale>
    );
  }
  return (
    <View accessible accessibilityLabel={`${label}: ${value}`} style={cardStyle}>
      {inner}
    </View>
  );
}

// Memoized: the dashboard renders eight of these, and a refresh that changes one counter previously
// re-rendered all eight. Props are primitives plus a stable `onPress` (Sprint 4, Task 10).
export default React.memo(StatCard);
