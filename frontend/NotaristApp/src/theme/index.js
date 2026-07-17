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

// Assembles a full theme object for a given color scheme. Consumed by ThemeContext.
export function buildTheme(mode) {
  const colors = palettes[mode] || palettes.dark;
  return {
    mode,
    dark: mode === 'dark',
    colors,
    spacing,
    radius,
    typography,
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
