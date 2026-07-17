import React from 'react';
import { View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTheme } from '../context/ThemeContext';

/**
 * Sticky bottom bar for a screen's primary actions (Approve All, Reject, Save). Renders its children
 * in a row with a top border and safe-area padding, so action screens (OCR Review, Verification)
 * keep the CTA reachable without each re-implementing the layout.
 */
export default function BottomActionBar({ children, style }) {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  return (
    <View
      style={[
        {
          flexDirection: 'row',
          gap: theme.spacing.md,
          padding: theme.spacing.lg,
          paddingBottom: theme.spacing.lg + insets.bottom,
          backgroundColor: theme.colors.surface,
          borderTopWidth: 1,
          borderTopColor: theme.colors.border,
        },
        style,
      ]}
    >
      {children}
    </View>
  );
}
