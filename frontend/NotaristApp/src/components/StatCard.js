import React from 'react';
import { TouchableOpacity, View } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import AppText from './AppText';

/**
 * Dashboard metric tile: big value + label with a left accent bar. Optional `icon`, and `onPress`
 * to drill into the underlying list. `tone` is a theme color key for the accent. Non-interactive
 * when no onPress is given (renders a plain View).
 *
 * Accessibility (Sprint 4, Task 11): the tile is read as ONE node — "Menunggu Verifikasi: 3, tombol"
 * — rather than as a stray number followed by a stray label. Previously a tappable tile exposed no
 * button role, so a screen-reader user had no way to know the number could be opened at all.
 */
function StatCard({ label, value, tone = 'primary', icon, onPress, style }) {
  const theme = useTheme();
  const accent = theme.colors[tone] || theme.colors.primary;
  const Container = onPress ? TouchableOpacity : View;

  return (
    <Container
      onPress={onPress}
      activeOpacity={0.85}
      accessible
      accessibilityRole={onPress ? 'button' : undefined}
      accessibilityLabel={`${label}: ${value}`}
      accessibilityHint={onPress ? 'Buka daftar terkait' : undefined}
      style={[
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
      ]}
    >
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
        <AppText style={{ color: theme.colors.text, fontSize: theme.typography.h1, fontWeight: theme.typography.bold }}>
          {value}
        </AppText>
        {icon ? (
          <AppText accessibilityElementsHidden importantForAccessibility="no" style={{ fontSize: 18 }}>
            {icon}
          </AppText>
        ) : null}
      </View>
      <AppText color="textFaint" variant="micro" numberOfLines={2} style={{ marginTop: 2 }}>
        {label}
      </AppText>
    </Container>
  );
}

// Memoized: the dashboard renders eight of these, and a refresh that changes one counter previously
// re-rendered all eight. Props are primitives plus a stable `onPress` (Sprint 4, Task 10).
export default React.memo(StatCard);
