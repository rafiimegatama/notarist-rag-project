import React from 'react';
import { View } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import AppText from './AppText';

/**
 * Count badge (e.g. unread notifications). Hidden entirely when count <= 0. `dot` renders a small
 * dot instead of a number. Caps display at 99+.
 */
export default function Badge({ count = 0, dot = false, color, style }) {
  const theme = useTheme();
  if (!dot && (!count || count <= 0)) return null;

  const bg = color || theme.colors.badge;

  if (dot) {
    return (
      <View
        style={[
          { width: 10, height: 10, borderRadius: 5, backgroundColor: bg },
          style,
        ]}
      />
    );
  }

  const label = count > 99 ? '99+' : String(count);
  return (
    <View
      // Sprint 4, Task 11 (dynamic font scaling): `height: 18` was a hard cap, so at a large OS font
      // size the pill stayed 18pt while the numeral inside grew — clipping "99+" to an unreadable
      // sliver. minHeight lets the badge grow with its text and renders identically at 1×.
      style={[
        {
          minWidth: 18,
          minHeight: 18,
          borderRadius: 9,
          paddingHorizontal: 5,
          backgroundColor: bg,
          alignItems: 'center',
          justifyContent: 'center',
        },
        style,
      ]}
      // A count badge sits next to the thing it counts (a tab icon, a row); read on its own, "3"
      // means nothing, so give it a unit.
      accessibilityLabel={`${label} baru`}
    >
      <AppText
        style={{ color: theme.colors.badgeText, fontSize: 10, fontWeight: '700' }}
        // The one place a cap is right: past ~1.5× the numeral cannot fit a circular badge at any
        // size that still reads as a badge. The count is also announced via the label above, so the
        // information is never lost — only the decoration stops growing.
        maxFontSizeMultiplier={1.5}
      >
        {label}
      </AppText>
    </View>
  );
}
