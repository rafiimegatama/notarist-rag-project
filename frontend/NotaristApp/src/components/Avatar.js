import React from 'react';
import { View } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import AppText from './AppText';
import { initials as toInitials } from '../utils/format';

/** Circular initials avatar. No image support yet (backend has no avatar) — degrades to initials. */
export default function Avatar({ name, size = 56, color }) {
  const theme = useTheme();
  const bg = color || theme.colors.primary;
  return (
    <View
      style={{
        width: size,
        height: size,
        borderRadius: size / 2,
        backgroundColor: bg,
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      <AppText style={{ color: '#fff', fontSize: size * 0.4, fontWeight: '700' }}>
        {toInitials(name)}
      </AppText>
    </View>
  );
}
