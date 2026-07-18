import { AccessibilityInfo } from 'react-native';

// Screen-reader announcements (Sprint 11). For state changes a sighted user perceives instantly but a
// VoiceOver/TalkBack user would otherwise miss — a copied reference, a completed background action —
// where the change is NOT already announced by an accessibilityRole="alert" region.
//
// Fire-and-forget and defensive: on a platform without the API, or when no screen reader is running,
// this is a silent no-op. It never throws into the caller's happy path.
export function announce(message) {
  if (!message) return;
  try {
    AccessibilityInfo.announceForAccessibility?.(String(message));
  } catch (_) {
    /* no screen reader / unsupported platform */
  }
}

/** Resolve whether a screen reader is currently active — for behaviour that should differ under one
 *  (e.g. skipping a purely decorative auto-advance). Resolves false when the query is unavailable. */
export async function isScreenReaderEnabled() {
  try {
    return !!(await AccessibilityInfo.isScreenReaderEnabled?.());
  } catch (_) {
    return false;
  }
}
