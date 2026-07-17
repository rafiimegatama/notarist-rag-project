import React from 'react';
import { View } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import AppText from './AppText';

/** Uppercase section label used above grouped rows (Settings, Profile, etc.). */
export default function SectionHeader({ title, style }) {
  const theme = useTheme();
  return (
    <View style={[{ marginTop: theme.spacing.xl, marginBottom: theme.spacing.sm, paddingHorizontal: theme.spacing.xs }, style]}>
      <AppText
        variant="label"
        color="textMuted"
        style={{ textTransform: 'uppercase', letterSpacing: 0.8 }}
      >
        {title}
      </AppText>
    </View>
  );
}
