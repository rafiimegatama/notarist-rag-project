import React from 'react';
import { View } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import AppText from './AppText';

/**
 * Inline status banner. Used to communicate honest system state — e.g. "Backend endpoint
 * unavailable" on Register / Notification — without blocking the rest of the screen.
 */
const ICONS = { info: 'ℹ️', warning: '⚠️', danger: '⛔', success: '✅' };

export default function Banner({ variant = 'info', title, message, children, style }) {
  const theme = useTheme();
  const tone = {
    info: theme.colors.info,
    warning: theme.colors.warning,
    danger: theme.colors.danger,
    success: theme.colors.success,
  }[variant] || theme.colors.info;

  // Body precedence: explicit `message` prop, else string/element children.
  const body = message != null ? message : children;

  return (
    <View
      style={[
        {
          flexDirection: 'row',
          alignItems: 'flex-start',
          backgroundColor: theme.colors.surface,
          borderColor: tone,
          borderWidth: 1,
          borderLeftWidth: 3,
          borderRadius: theme.radius.md,
          padding: theme.spacing.md,
        },
        style,
      ]}
    >
      <AppText style={{ marginRight: theme.spacing.sm }}>{ICONS[variant]}</AppText>
      <View style={{ flex: 1 }}>
        {title ? <AppText variant="bodyStrong" style={{ color: tone }}>{title}</AppText> : null}
        {body != null ? (
          typeof body === 'string' ? (
            <AppText variant="caption" color="textMuted" style={{ marginTop: title ? 2 : 0 }}>
              {body}
            </AppText>
          ) : (
            body
          )
        ) : null}
      </View>
    </View>
  );
}
