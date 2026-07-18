import React from 'react';
import { View } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import AppText from './AppText';

/** Uppercase section label used above grouped rows (Settings, Profile, etc.). */
export default function SectionHeader({ title, style }) {
  const theme = useTheme();
  return (
    <View style={[{ marginTop: theme.spacing.xl, marginBottom: theme.spacing.sm, paddingHorizontal: theme.spacing.xs }, style]}>
      {/* role="header" lets VoiceOver/TalkBack users jump between sections with the heading rotor —
          the screen-reader equivalent of scanning for a section title (Sprint 11, Focus Order). */}
      <AppText
        variant="label"
        color="textMuted"
        accessibilityRole="header"
        style={{ textTransform: 'uppercase', letterSpacing: 0.8 }}
      >
        {title}
      </AppText>
    </View>
  );
}
