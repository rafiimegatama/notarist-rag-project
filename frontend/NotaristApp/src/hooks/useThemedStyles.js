import { useMemo } from 'react';
import { StyleSheet } from 'react-native';
import { useTheme } from '../context/ThemeContext';

/**
 * Builds a StyleSheet from a theme-aware factory and rebuilds it when the theme changes. Lets a
 * screen keep a conventional StyleSheet block while sourcing every color from the active palette,
 * so switching light/dark re-styles it. `factory` must be defined at module scope (a stable
 * reference), otherwise the sheet is recreated on every render.
 */
export default function useThemedStyles(factory) {
  const theme = useTheme();
  return useMemo(() => StyleSheet.create(factory(theme)), [factory, theme]);
}
