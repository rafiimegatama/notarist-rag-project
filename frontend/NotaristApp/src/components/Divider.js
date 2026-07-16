import React from 'react';
import { View, StyleSheet } from 'react-native';
import { useTheme } from '../context/ThemeContext';

/** 1px themed separator. `inset` indents it to align with row content past a leading icon. */
export default function Divider({ inset = 0 }) {
  const theme = useTheme();
  return (
    <View
      style={{
        height: StyleSheet.hairlineWidth || 1,
        backgroundColor: theme.colors.border,
        marginLeft: inset,
      }}
    />
  );
}
