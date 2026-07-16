// Design tokens shared across both color modes: spacing scale, radii, typography, durations.
// Kept mode-independent so only colors flip between dark/light.

export const spacing = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 20,
  xxl: 24,
  xxxl: 32,
};

export const radius = {
  sm: 6,
  md: 8,
  lg: 12,
  xl: 16,
  pill: 999,
};

export const typography = {
  // sizes
  display: 32,
  h1: 24,
  h2: 20,
  h3: 18,
  body: 15,
  bodySm: 13,
  caption: 12,
  micro: 11,
  // weights (strings — RN fontWeight)
  regular: '400',
  medium: '500',
  semibold: '600',
  bold: '700',
};

export const durations = {
  instant: 0,
  fast: 150,
  base: 250,
  slow: 400,
  splashMin: 1100, // minimum time the splash stays up, so it never flickers on a fast auth check
};

// Animation easing/config presets consumed via Animated + LayoutAnimation. Kept here so no screen
// invents its own timing curve — "tasteful, not over-animated" is enforced by reusing these.
export const motion = {
  // LayoutAnimation preset keys (see components/anim/useLayoutTransition).
  layout: { duration: durations.base },
  // Animated.spring config for success/emphasis pops.
  spring: { friction: 6, tension: 90, useNativeDriver: true },
  // Standard fade/timing.
  timing: { duration: durations.base, useNativeDriver: true },
};

export const hitSlop = { top: 8, bottom: 8, left: 8, right: 8 };

// Icon glyph sizes (emoji/text icons today; maps 1:1 to vector icon sizes later).
export const iconSize = {
  xs: 12,
  sm: 16,
  md: 20,
  lg: 24,
  xl: 32,
  xxl: 48,
};

// Minimum interactive target sizes. `min` meets the 44pt accessibility floor (iOS HIG / WCAG 2.5.5).
export const touchTarget = {
  min: 44,
  comfortable: 48,
};

// Shadow/elevation geometry (mode-independent). The color is injected from the palette in
// theme/index.js so light/dark get an appropriate shadow tint — see buildTheme().shadows.
export const elevation = {
  none: { shadowOffset: { width: 0, height: 0 }, shadowOpacity: 0, shadowRadius: 0, elevation: 0 },
  sm: { shadowOffset: { width: 0, height: 1 }, shadowOpacity: 0.12, shadowRadius: 3, elevation: 2 },
  md: { shadowOffset: { width: 0, height: 4 }, shadowOpacity: 0.16, shadowRadius: 8, elevation: 6 },
  lg: { shadowOffset: { width: 0, height: 8 }, shadowOpacity: 0.22, shadowRadius: 16, elevation: 12 },
};

// z-index scale for overlays (dialogs, floating toolbars, banners).
export const zIndex = {
  base: 0,
  sticky: 10,
  floating: 50,
  overlay: 100,
  dialog: 200,
};
