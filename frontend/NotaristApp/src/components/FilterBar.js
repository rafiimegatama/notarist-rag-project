import React from 'react';
import { ScrollView, TouchableOpacity } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import AppText from './AppText';

/**
 * Horizontal, scrollable segmented filter. `options` = [{ value, label }]. Controlled via
 * `selected` + `onSelect`. Used for case status, reminder window, search mode.
 */
export default function FilterBar({ options = [], selected, onSelect, style, contentStyle }) {
  const theme = useTheme();
  return (
    <ScrollView
      horizontal
      showsHorizontalScrollIndicator={false}
      // Groups the options so a reader announces them as one radio set rather than loose buttons.
      accessibilityRole="radiogroup"
      style={style}
      contentContainerStyle={[{ gap: theme.spacing.sm, paddingVertical: theme.spacing.xs }, contentStyle]}
    >
      {options.map((opt) => {
        const active = opt.value === selected;
        return (
          <TouchableOpacity
            key={String(opt.value)}
            onPress={() => onSelect?.(opt.value)}
            activeOpacity={0.8}
            // Sprint 4, Task 11. These are radio-style options, not free-standing buttons: the
            // 'radio' role plus a selected state is what tells a screen reader WHICH filter is
            // active. Without it the bar announced a row of plain buttons with no way to tell the
            // current status filter from the rest.
            accessibilityRole="radio"
            accessibilityState={{ selected: active, checked: active }}
            accessibilityLabel={opt.label}
            style={{
              paddingVertical: theme.spacing.sm,
              paddingHorizontal: theme.spacing.lg,
              borderRadius: theme.radius.pill,
              // Meets the 44pt floor without altering the look: the pill already renders near that
              // height, and centring inside a min-height box adds no visible padding.
              minHeight: theme.touchTarget.min,
              justifyContent: 'center',
              backgroundColor: active ? theme.colors.primary : theme.colors.surfaceAlt,
              borderWidth: 1,
              borderColor: active ? theme.colors.primary : theme.colors.border,
            }}
          >
            <AppText
              variant="bodySm"
              style={{ color: active ? theme.colors.primaryText : theme.colors.textMuted, fontWeight: theme.typography.semibold }}
            >
              {opt.label}
            </AppText>
          </TouchableOpacity>
        );
      })}
    </ScrollView>
  );
}
