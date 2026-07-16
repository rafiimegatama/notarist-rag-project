import React from 'react';
import { TouchableOpacity, ActivityIndicator, StyleSheet, View } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import AppText from './AppText';

/**
 * Reusable button. Variants: primary | secondary | danger | ghost. Handles disabled + loading
 * (spinner, non-interactive) uniformly so no screen re-implements a submit button.
 */
export default function Button({
  title,
  onPress,
  variant = 'primary',
  size = 'md',
  loading = false,
  disabled = false,
  fullWidth = true,
  icon = null,
  style,
}) {
  const theme = useTheme();
  const isDisabled = disabled || loading;

  const palette = {
    primary: { bg: theme.colors.primary, fg: theme.colors.primaryText, border: theme.colors.primary },
    secondary: { bg: 'transparent', fg: theme.colors.text, border: theme.colors.borderStrong },
    danger: { bg: theme.colors.danger, fg: '#fff', border: theme.colors.danger },
    ghost: { bg: 'transparent', fg: theme.colors.primary, border: 'transparent' },
  }[variant] || {};

  const pad = size === 'sm' ? theme.spacing.sm : theme.spacing.lg;

  return (
    <TouchableOpacity
      activeOpacity={0.85}
      onPress={onPress}
      disabled={isDisabled}
      accessibilityRole="button"
      accessibilityState={{ disabled: isDisabled, busy: loading }}
      accessibilityLabel={typeof title === 'string' ? title : undefined}
      style={[
        styles.base,
        {
          backgroundColor: palette.bg,
          borderColor: palette.border,
          paddingVertical: pad,
          minHeight: theme.touchTarget.min, // 44pt accessibility floor
          borderRadius: theme.radius.md,
          opacity: isDisabled ? 0.5 : 1,
          alignSelf: fullWidth ? 'stretch' : 'flex-start',
          paddingHorizontal: fullWidth ? theme.spacing.lg : theme.spacing.xl,
        },
        style,
      ]}
    >
      {loading ? (
        <ActivityIndicator color={palette.fg} />
      ) : (
        <View style={styles.row}>
          {icon ? <AppText style={{ marginRight: theme.spacing.sm }}>{icon}</AppText> : null}
          <AppText variant="bodyStrong" style={{ color: palette.fg }}>
            {title}
          </AppText>
        </View>
      )}
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  base: { borderWidth: 1, alignItems: 'center', justifyContent: 'center' },
  row: { flexDirection: 'row', alignItems: 'center' },
});
