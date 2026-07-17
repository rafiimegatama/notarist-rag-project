// User preferences: theme mode, language, and AI-runtime display preferences. Persisted via the
// storage wrapper (SecureStore with in-memory fallback). These are LOCAL preferences only — none
// of them changes backend behavior. AI preferences are surfaced in Settings for a future sprint to
// wire into the Assistant; today they are stored, not yet consumed (documented as tech debt).

import React, { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { getItem, setItem } from '../utils/storage';

const KEYS = {
  theme: 'pref_theme_mode',       // 'system' | 'light' | 'dark'
  language: 'pref_language',      // 'id' | 'en'
  aiSafety: 'pref_ai_safety',     // 'STRICT' | 'BALANCED' | 'PERMISSIVE'
  aiMaxResults: 'pref_ai_max_results',
  analytics: 'pref_analytics',    // 'on' | 'off'
};

const DEFAULTS = {
  themeMode: 'system',
  language: 'id',
  aiSafetyMode: 'STRICT',
  aiMaxResults: 10,
  analyticsEnabled: false,
};

const PreferencesContext = createContext(null);

export function PreferencesProvider({ children }) {
  const [prefs, setPrefs] = useState(DEFAULTS);
  const [hydrated, setHydrated] = useState(false);

  useEffect(() => {
    (async () => {
      try {
        const [themeMode, language, aiSafety, aiMax, analytics] = await Promise.all([
          getItem(KEYS.theme),
          getItem(KEYS.language),
          getItem(KEYS.aiSafety),
          getItem(KEYS.aiMaxResults),
          getItem(KEYS.analytics),
        ]);
        setPrefs((p) => ({
          themeMode: themeMode || p.themeMode,
          language: language || p.language,
          aiSafetyMode: aiSafety || p.aiSafetyMode,
          aiMaxResults: aiMax ? Number(aiMax) : p.aiMaxResults,
          analyticsEnabled: analytics != null ? analytics === 'on' : p.analyticsEnabled,
        }));
      } finally {
        setHydrated(true);
      }
    })();
  }, []);

  const api = useMemo(() => ({
    ...prefs,
    hydrated,
    async setThemeMode(mode) {
      setPrefs((p) => ({ ...p, themeMode: mode }));
      await setItem(KEYS.theme, mode);
    },
    async setLanguage(lang) {
      setPrefs((p) => ({ ...p, language: lang }));
      await setItem(KEYS.language, lang);
    },
    async setAiSafetyMode(mode) {
      setPrefs((p) => ({ ...p, aiSafetyMode: mode }));
      await setItem(KEYS.aiSafety, mode);
    },
    async setAiMaxResults(n) {
      setPrefs((p) => ({ ...p, aiMaxResults: n }));
      await setItem(KEYS.aiMaxResults, String(n));
    },
    async setAnalyticsEnabled(enabled) {
      setPrefs((p) => ({ ...p, analyticsEnabled: enabled }));
      await setItem(KEYS.analytics, enabled ? 'on' : 'off');
    },
  }), [prefs, hydrated]);

  return <PreferencesContext.Provider value={api}>{children}</PreferencesContext.Provider>;
}

export function usePreferences() {
  const ctx = useContext(PreferencesContext);
  if (!ctx) throw new Error('usePreferences must be used within PreferencesProvider');
  return ctx;
}
