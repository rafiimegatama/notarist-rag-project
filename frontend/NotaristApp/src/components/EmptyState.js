import React from 'react';
import { View } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import AppText from './AppText';
import Button from './Button';

/** Empty-list placeholder with icon, title, description and an optional call to action. */
export default function EmptyState({
  icon = '📭',
  title = 'Belum ada data',
  description,
  actionLabel,
  onAction,
  fill = true,
}) {
  const theme = useTheme();
  return (
    <View
      style={{
        flex: fill ? 1 : undefined,
        alignItems: 'center',
        justifyContent: 'center',
        padding: theme.spacing.xxl,
      }}
    >
      <AppText style={{ fontSize: 48, marginBottom: theme.spacing.md }}>{icon}</AppText>
      <AppText variant="h3" align="center" style={{ marginBottom: theme.spacing.xs }}>
        {title}
      </AppText>
      {description ? (
        <AppText color="textFaint" variant="bodySm" align="center" style={{ marginBottom: theme.spacing.lg }}>
          {description}
        </AppText>
      ) : null}
      {actionLabel && onAction ? (
        <Button title={actionLabel} onPress={onAction} variant="secondary" fullWidth={false} />
      ) : null}
    </View>
  );
}
