// Cross-platform Alert.alert.
//
// react-native-web does NOT implement Alert.alert — it is a silent no-op (verified in a real browser,
// RC acceptance): tapping "Keluar" showed no dialog and never signed the user out, and every other
// confirmation/notification alert in the app was equally invisible on web. The app ships a web build,
// so this is a broken production path, not an edge case.
//
// This shim keeps the EXACT React Native Alert.alert signature, so call sites are unchanged:
//   showAlert(title, message?, buttons?, options?)
// On native it delegates to the real Alert. On web it maps to the browser primitives:
//   - 0/1 button  -> window.alert, then fire the button's onPress (an acknowledgement)
//   - 2+ buttons  -> window.confirm; OK runs the NON-cancel button, Cancel runs the cancel button.
// The "cancel"-styled button (or a button literally labelled Batal/Cancel) is treated as the negative
// choice; the remaining button is the positive action. This preserves the one behaviour that matters
// for a destructive confirm: the action only runs when the user accepts.

import { Alert, Platform } from 'react-native';

function isCancel(btn) {
  if (!btn) return false;
  if (btn.style === 'cancel') return true;
  const t = (btn.text || '').trim().toLowerCase();
  return t === 'batal' || t === 'cancel';
}

export function showAlert(title, message, buttons, options) {
  if (Platform.OS !== 'web') {
    Alert.alert(title, message, buttons, options);
    return;
  }

  const text = [title, message].filter(Boolean).join('\n\n');

  if (!buttons || buttons.length <= 1) {
    // Informational. window.alert always resolves "acknowledged".
    // eslint-disable-next-line no-alert
    if (typeof window !== 'undefined' && window.alert) window.alert(text);
    const only = buttons && buttons[0];
    if (only && typeof only.onPress === 'function') only.onPress();
    return;
  }

  // Confirmation. Positive = the first non-cancel button; negative = the cancel button.
  const positive = buttons.find((b) => !isCancel(b)) || buttons[buttons.length - 1];
  const negative = buttons.find(isCancel);
  // eslint-disable-next-line no-alert
  const accepted = typeof window !== 'undefined' && window.confirm ? window.confirm(text) : true;
  const chosen = accepted ? positive : negative;
  if (chosen && typeof chosen.onPress === 'function') chosen.onPress();
}

export default { alert: showAlert };
