// Resolves the active theme from the user's themeMode preference + the OS color scheme, and exposes
// it via useTheme(). Every component styles itself from this theme, so a mode change re-themes the
// whole tree. Defaults to dark to match app.json userInterfaceStyle and the existing screens.

import React, { createContext, useContext, useMemo } from 'react';
import { useColorScheme } from 'react-native';
import { buildTheme } from '../theme';
import { usePreferences } from './PreferencesContext';

const ThemeContext = createContext(null);

export function ThemeProvider({ children }) {
  const { themeMode } = usePreferences();
  const system = useColorScheme(); // 'light' | 'dark' | null

  const value = useMemo(() => {
    const resolved = themeMode === 'system' ? (system || 'dark') : themeMode;
    const scheme = resolved === 'light' ? 'light' : 'dark';
    return { theme: buildTheme(scheme), scheme, mode: themeMode };
  }, [themeMode, system]);

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme() {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error('useTheme must be used within ThemeProvider');
  return ctx.theme;
}

export function useThemeMeta() {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error('useThemeMeta must be used within ThemeProvider');
  return ctx; // { theme, scheme, mode }
}
