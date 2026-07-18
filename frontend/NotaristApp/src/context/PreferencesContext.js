// User preferences: theme mode, language, and AI-runtime display preferences. Persisted via the
// storage wrapper (SecureStore with in-memory fallback). These are LOCAL preferences only — none
// of them changes backend behavior. AI preferences are surfaced in Settings for a future sprint to
// wire into the Assistant; today they are stored, not yet consumed (documented as tech debt).

import React, { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { getItem, setItem } from '../utils/storage';

// Mirror of backend AssistantSafetyMode
// (backend/notarist-assistant/.../domain/model/AssistantSafetyMode.java). AssistantRequest.safetyMode
// deserializes into this enum, so a value outside this list is a 400.
//
// Sprint 6: this said PERMISSIVE, which is not a member and never was. Latent rather than live only
// because AssistantScreen calls askAssistant() without options, so the stored preference was never
// actually sent — a setting the user could change that reached nothing. The moment anyone wired the
// preference through (which is the stated plan, see the Settings copy), every Assistant call from a
// user who had touched that toggle would have 400'd.
export const SAFETY_MODES = ['STRICT', 'BALANCED', 'EXPLORATORY'];

// In-app text-size levels and their font multipliers (Sprint 11 — Large Font). Applied to the theme's
// typography sizes, so every AppText and every component reading theme.typography scales together.
// This is IN ADDITION to the OS font-scale (Dynamic Type / TalkBack font size), which AppText keeps
// honouring because it never disables allowFontScaling — a user can set either or both.
export const TEXT_SCALES = { normal: 1, large: 1.15, xlarge: 1.3 };
export const TEXT_SCALE_LEVELS = Object.keys(TEXT_SCALES);
export const textScaleValue = (level) => TEXT_SCALES[level] || 1;

const KEYS = {
  theme: 'pref_theme_mode',       // 'system' | 'light' | 'dark'
  language: 'pref_language',      // 'id' | 'en'
  aiSafety: 'pref_ai_safety',     // one of SAFETY_MODES
  aiMaxResults: 'pref_ai_max_results',
  analytics: 'pref_analytics',    // 'on' | 'off'
  textScale: 'pref_text_scale',   // one of TEXT_SCALE_LEVELS
};

const DEFAULTS = {
  themeMode: 'system',
  language: 'id',
  aiSafetyMode: 'STRICT',
  aiMaxResults: 10,
  analyticsEnabled: false,
  textScale: 'normal',
};

const PreferencesContext = createContext(null);

export function PreferencesProvider({ children }) {
  const [prefs, setPrefs] = useState(DEFAULTS);
  const [hydrated, setHydrated] = useState(false);

  useEffect(() => {
    (async () => {
      try {
        const [themeMode, language, aiSafety, aiMax, analytics, textScale] = await Promise.all([
          getItem(KEYS.theme),
          getItem(KEYS.language),
          getItem(KEYS.aiSafety),
          getItem(KEYS.aiMaxResults),
          getItem(KEYS.analytics),
          getItem(KEYS.textScale),
        ]);
        setPrefs((p) => ({
          themeMode: themeMode || p.themeMode,
          language: language || p.language,
          // Validated out of storage like aiSafetyMode: an unknown level (older/newer build) falls
          // back to 'normal' rather than becoming a NaN multiplier in the theme.
          textScale: TEXT_SCALE_LEVELS.indexOf(textScale) !== -1 ? textScale : p.textScale,
          // Validated on the way OUT of storage, not just on the way in. Renaming the enum in this
          // file does not reach a device that already persisted 'PERMISSIVE' under the old build —
          // storage outlives the code that wrote it, so an unvalidated read would resurrect the exact
          // impossible value this sprint removed. Anything unrecognised falls back to the default
          // (STRICT), which is also the safest direction to be wrong in for a legal assistant.
          aiSafetyMode: SAFETY_MODES.indexOf(aiSafety) !== -1 ? aiSafety : p.aiSafetyMode,
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
      // Guarded at the setter too: this value is destined for a backend enum, so a caller passing
      // something not in SAFETY_MODES should fail here, in dev, rather than become a 400 from the
      // Assistant later — a long way from the line that caused it.
      if (SAFETY_MODES.indexOf(mode) === -1) {
        throw new Error(`Unknown AssistantSafetyMode '${mode}'. Expected one of: ${SAFETY_MODES.join(' | ')}`);
      }
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
    async setTextScale(level) {
      if (TEXT_SCALE_LEVELS.indexOf(level) === -1) {
        throw new Error(`Unknown text scale '${level}'. Expected one of: ${TEXT_SCALE_LEVELS.join(' | ')}`);
      }
      setPrefs((p) => ({ ...p, textScale: level }));
      await setItem(KEYS.textScale, level);
    },
  }), [prefs, hydrated]);

  return <PreferencesContext.Provider value={api}>{children}</PreferencesContext.Provider>;
}

export function usePreferences() {
  const ctx = useContext(PreferencesContext);
  if (!ctx) throw new Error('usePreferences must be used within PreferencesProvider');
  return ctx;
}
