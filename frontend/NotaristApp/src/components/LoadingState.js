import React from 'react';
import { View, ActivityIndicator } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import AppText from './AppText';

/** Centered spinner + optional message. Use for full-screen or in-container loading. */
export default function LoadingState({ message, fill = true }) {
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
      <ActivityIndicator size="large" color={theme.colors.primary} />
      {message ? (
        <AppText color="textMuted" variant="bodySm" style={{ marginTop: theme.spacing.md }}>
          {message}
        </AppText>
      ) : null}
    </View>
  );
}
