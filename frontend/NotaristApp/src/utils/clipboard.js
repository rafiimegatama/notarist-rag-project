// Copy-to-clipboard that works everywhere this app runs, WITHOUT adding a native dependency.
//
// The app targets react-native-web (its build script is `expo export --platform web`) and native RN.
// expo-clipboard is not installed, and pulling in a native module for one "Copy answer" button is not
// the right trade. So this reaches for whatever the current runtime already provides:
//
//   web / react-native-web  -> navigator.clipboard.writeText (async, permissioned)
//   native RN               -> the built-in Clipboard.setString, required lazily so bundling never
//                              fails on a platform where it is absent
//
// Returns true on success, false when no clipboard is reachable — the caller shows "disalin" only on
// true, and never claims a copy that did not happen.
export async function copyToClipboard(text) {
  const value = String(text ?? '');

  if (typeof navigator !== 'undefined' && navigator.clipboard && typeof navigator.clipboard.writeText === 'function') {
    try {
      await navigator.clipboard.writeText(value);
      return true;
    } catch (_) {
      // Permission denied or insecure context — fall through to the native path.
    }
  }

  try {
    // Lazy require: react-native's Clipboard export exists on native and is undefined on web; a static
    // import would still resolve, but the guard keeps intent explicit and tolerates its future removal.
    const RN = require('react-native');
    if (RN && RN.Clipboard && typeof RN.Clipboard.setString === 'function') {
      RN.Clipboard.setString(value);
      return true;
    }
  } catch (_) {
    /* no native clipboard available */
  }

  return false;
}
