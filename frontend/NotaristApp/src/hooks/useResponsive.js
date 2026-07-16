import { useWindowDimensions } from 'react-native';

// Breakpoints (in dp). Phone < 600 <= large phone/small tablet < 900 <= tablet.
export const BREAKPOINTS = { phone: 0, md: 600, lg: 900 };

/**
 * Responsive layout primitive. Derives tablet/landscape/split-view flags and a sensible grid column
 * count from the live window size, so a screen can adapt (stack on phone, split on tablet/landscape)
 * without hardcoding widths. Recomputes on rotation and multitasking resize.
 */
export default function useResponsive() {
  const { width, height } = useWindowDimensions();
  const isLandscape = width > height;
  const isTablet = width >= BREAKPOINTS.md;
  const isLarge = width >= BREAKPOINTS.lg;
  // Two-pane split when there's genuinely room: a tablet, or any device in landscape past ~720dp.
  const splitView = isLarge || (isLandscape && width >= 720);
  const columns = isLarge ? 3 : isTablet ? 2 : 1;
  return { width, height, isLandscape, isTablet, isLarge, splitView, columns };
}
