// Support-contact helper (Sprint 10). Builds a mailto: to LINKS.support pre-filled with the things a
// support agent needs and a user should not have to type: the error's reference id, its machine code,
// and the app/platform build. The user only writes what went wrong.
//
// Nothing here contains sensitive data — no token, no document content, no debtor name. The reference
// id is either the backend correlationId or a generated client ref; the diagnostic is method+path+
// status (see api/errors.buildDiagnostic). That is the same line ErrorState is willing to show on
// screen, so putting it in an email the user chooses to send is no wider a disclosure.

import { Linking } from 'react-native';
import { LINKS, APP, PLATFORM_LABEL } from '../constants/config';

export function buildSupportUrl(error, { screen } = {}) {
  const ref = (error && error.diagnosticId) || '';
  const codeSuffix = error && error.errorCode ? ` (${error.errorCode})` : '';

  const subject = ref ? `Bantuan Notarist — ${ref}` : 'Bantuan Notarist';
  const body = [
    'Mohon bantuan terkait masalah pada aplikasi Notarist.',
    '',
    `Kode rujukan: ${ref || '-'}${codeSuffix}`,
    error && error.diagnostic ? `Detail teknis: ${error.diagnostic}` : null,
    `Aplikasi: ${APP.name} ${APP.version} (build ${APP.build})`,
    `Perangkat: ${PLATFORM_LABEL}`,
    screen ? `Layar: ${screen}` : null,
    '',
    'Deskripsi masalah:',
    '',
  ].filter(Boolean).join('\n');

  const base = LINKS.support; // mailto:support@notarist.example
  const sep = base.indexOf('?') === -1 ? '?' : '&';
  return `${base}${sep}subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`;
}

/**
 * Open the user's mail client with the support message. Returns false when nothing can handle the URL
 * (e.g. a device with no mail app, or web without a mailto handler) so the caller can fall back to
 * showing the address plainly rather than a dead button.
 */
export async function openSupport(error, opts) {
  const url = buildSupportUrl(error, opts);
  try {
    const supported = await Linking.canOpenURL(url);
    // canOpenURL is unreliable for mailto on some platforms (returns false yet openURL works), so try
    // to open when it says yes, and also attempt once when it says no before reporting failure.
    if (supported) { await Linking.openURL(url); return true; }
    await Linking.openURL(url);
    return true;
  } catch (_) {
    return false;
  }
}
