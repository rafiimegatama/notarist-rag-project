// Color palettes for dark (default) and light modes. The dark palette matches the existing
// MVP screens (#0F172A / #1E293B / #3B82F6) so nothing looks out of place while screens migrate.
// Every screen/component reads colors through useTheme() — never hardcode a hex outside this file.

export const darkPalette = {
  mode: 'dark',
  // surfaces
  background: '#0F172A',
  surface: '#1E293B',
  surfaceAlt: '#172033',
  elevated: '#243247',
  overlay: 'rgba(0,0,0,0.5)',
  // text
  text: '#F1F5F9',
  textMuted: '#94A3B8',
  textFaint: '#64748B',
  textInverse: '#0F172A',
  // brand / states
  primary: '#3B82F6',
  primaryText: '#FFFFFF',
  success: '#10B981',
  warning: '#F59E0B',
  danger: '#EF4444',
  info: '#2563EB',
  // lines
  border: '#334155',
  borderStrong: '#475569',
  // misc
  skeleton: '#243247',
  skeletonHighlight: '#334155',
  badge: '#EF4444',
  badgeText: '#FFFFFF',
  shadowColor: '#000000',
  // priority accents (reused by PriorityChip / reminders)
  priorityHigh: '#EF4444',
  priorityMedium: '#F59E0B',
  priorityLow: '#64748B',
};

export const lightPalette = {
  mode: 'light',
  background: '#F8FAFC',
  surface: '#FFFFFF',
  surfaceAlt: '#F1F5F9',
  elevated: '#FFFFFF',
  overlay: 'rgba(15,23,42,0.35)',
  text: '#0F172A',
  textMuted: '#475569',
  textFaint: '#94A3B8',
  textInverse: '#FFFFFF',
  primary: '#2563EB',
  primaryText: '#FFFFFF',
  success: '#059669',
  warning: '#D97706',
  danger: '#DC2626',
  info: '#2563EB',
  border: '#E2E8F0',
  borderStrong: '#CBD5E1',
  skeleton: '#E2E8F0',
  skeletonHighlight: '#F1F5F9',
  badge: '#DC2626',
  badgeText: '#FFFFFF',
  shadowColor: '#0F172A',
  priorityHigh: '#DC2626',
  priorityMedium: '#D97706',
  priorityLow: '#94A3B8',
};

export const palettes = { dark: darkPalette, light: lightPalette };
