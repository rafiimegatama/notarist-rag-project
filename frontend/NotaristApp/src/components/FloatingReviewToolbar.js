import React from 'react';
import { View, TouchableOpacity } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import AppText from './AppText';

/**
 * Floating quick-action toolbar for review screens (OCR field review): a rounded pill of icon buttons
 * that hovers above the content. `actions` = [{ key, icon, label, onPress, tone }]. Absolutely
 * positioned by the parent (pass positioning via `style`).
 */
export default function FloatingReviewToolbar({ actions = [], style }) {
  const theme = useTheme();
  return (
    <View
      style={[
        {
          flexDirection: 'row',
          alignSelf: 'center',
          backgroundColor: theme.colors.elevated,
          borderRadius: theme.radius.pill,
          borderWidth: 1,
          borderColor: theme.colors.border,
          paddingHorizontal: theme.spacing.sm,
          paddingVertical: theme.spacing.xs,
          zIndex: theme.zIndex.floating,
          ...theme.shadows.md,
        },
        style,
      ]}
    >
      {actions.map((a) => (
        <TouchableOpacity
          key={a.key}
          onPress={a.onPress}
          accessibilityRole="button"
          accessibilityLabel={a.label}
          hitSlop={theme.hitSlop}
          style={{ minWidth: theme.touchTarget.min, minHeight: theme.touchTarget.min, alignItems: 'center', justifyContent: 'center', paddingHorizontal: theme.spacing.sm }}
        >
          <AppText style={{ fontSize: theme.iconSize.md, color: a.tone ? theme.colors[a.tone] : theme.colors.text }}>{a.icon}</AppText>
          {a.label ? <AppText variant="micro" color="textFaint">{a.label}</AppText> : null}
        </TouchableOpacity>
      ))}
    </View>
  );
}
