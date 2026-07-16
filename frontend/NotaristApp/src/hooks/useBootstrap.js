import { useEffect, useState } from 'react';
import { Asset } from 'expo-asset';
import * as Font from 'expo-font';
import { useAuth } from '../context/AuthContext';
import { usePreferences } from '../context/PreferencesContext';
import { durations } from '../theme/tokens';

// Images decoded before the first real screen paints, so nothing pops in late.
const PRELOAD_IMAGES = [
  require('../../assets/icon.png'),
  require('../../assets/splash-icon.png'),
];

// No custom typeface is bundled yet — the app uses the platform system font. The seam is here so
// adding one later is a single entry (e.g. { 'Inter-Bold': require('../../assets/Inter-Bold.ttf') })
// and the splash automatically waits for it.
const PRELOAD_FONTS = {};

/**
 * Gates the Splash screen. The app is "ready" only when:
 *   - the auth session check finished (AuthContext.loading === false),
 *   - preferences have hydrated,
 *   - fonts and image assets have preloaded,
 *   - AND a minimum splash time elapsed (so a fast check doesn't cause a flicker).
 *
 * Asset/font failures do NOT block startup: the app falls back to the system font and lazily
 * decoded images rather than stranding the user on the splash forever.
 */
export default function useBootstrap() {
  const { loading: authLoading } = useAuth();
  const { hydrated } = usePreferences();
  const [assetsLoaded, setAssetsLoaded] = useState(false);
  const [minElapsed, setMinElapsed] = useState(false);

  useEffect(() => {
    const t = setTimeout(() => setMinElapsed(true), durations.splashMin);
    return () => clearTimeout(t);
  }, []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        await Promise.all([
          Asset.loadAsync(PRELOAD_IMAGES),
          Object.keys(PRELOAD_FONTS).length ? Font.loadAsync(PRELOAD_FONTS) : Promise.resolve(),
        ]);
      } catch (_) {
        // Non-fatal — see above.
      } finally {
        if (!cancelled) setAssetsLoaded(true);
      }
    })();
    return () => { cancelled = true; };
  }, []);

  const ready = !authLoading && hydrated && assetsLoaded && minElapsed;
  return { ready };
}
