import { palettes } from './palette';
import { spacing, radius, typography, durations, motion, hitSlop, iconSize, touchTarget, elevation, zIndex } from './tokens';

// Merges the mode-independent shadow geometry with the palette's shadow color so a component can do
// `style={theme.shadows.md}` and get a correctly-tinted shadow in both light and dark.
function buildShadows(colors) {
  const out = {};
  for (const [key, geo] of Object.entries(elevation)) {
    out[key] = { ...geo, shadowColor: colors.shadowColor };
  }
  return out;
}

// Scales the NUMERIC typography sizes by `textScale`, leaving the weight strings untouched. This is
// how the in-app Large Font control (Sprint 11) reaches every component: AppText and any component
// reading theme.typography[...] for a fontSize gets the scaled value, with no per-component change.
// Rounded so a 15px body at 1.15 becomes a clean 17, not 17.25.
function scaleTypography(scale) {
  if (!scale || scale === 1) return typography;
  const out = {};
  for (const [key, value] of Object.entries(typography)) {
    out[key] = typeof value === 'number' ? Math.round(value * scale) : value;
  }
  return out;
}

// Assembles a full theme object for a given color scheme. `textScale` (default 1) multiplies font
// sizes for the Large Font accessibility setting. Consumed by ThemeContext.
export function buildTheme(mode, { textScale = 1 } = {}) {
  const colors = palettes[mode] || palettes.dark;
  return {
    mode,
    dark: mode === 'dark',
    colors,
    spacing,
    radius,
    typography: scaleTypography(textScale),
    textScale,
    durations,
    motion,
    hitSlop,
    iconSize,
    touchTarget,
    elevation,
    zIndex,
    shadows: buildShadows(colors),
  };
}

export { spacing, radius, typography, durations, motion, hitSlop, iconSize, touchTarget, elevation, zIndex, palettes };
export default buildTheme;
