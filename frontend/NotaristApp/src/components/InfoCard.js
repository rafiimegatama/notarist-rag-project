import React from 'react';
import { View } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import AppText from './AppText';

/**
 * Informational callout card with a left accent bar, icon+title and body text. `tone` maps to a
 * theme color key (info/warning/danger/success) for the accent + title.
 */
export default function InfoCard({ title, children, icon = 'ℹ️', tone = 'info', style }) {
  const theme = useTheme();
  const accent = theme.colors[tone] || theme.colors.info;
  return (
    <View
      style={[
        {
          backgroundColor: theme.colors.surface,
          borderRadius: theme.radius.lg,
          borderWidth: 1,
          borderLeftWidth: 3,
          borderColor: theme.colors.border,
          borderLeftColor: accent,
          padding: theme.spacing.lg,
        },
        style,
      ]}
    >
      {title ? (
        <AppText variant="bodyStrong" style={{ color: accent, marginBottom: theme.spacing.sm }}>
          {icon ? `${icon} ` : ''}{title}
        </AppText>
      ) : null}
      {typeof children === 'string' ? (
        <AppText color="textMuted" variant="bodySm" style={{ lineHeight: 20 }}>{children}</AppText>
      ) : (
        children
      )}
    </View>
  );
}
