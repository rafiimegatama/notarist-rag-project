import { useEffect, useState } from 'react';
import { AccessibilityInfo } from 'react-native';

// True when the user has asked the OS to reduce motion ("Reduce Motion" on iOS, "Remove animations"
// on Android). Animations that merely decorate — count-ups, chart grows — must honour this: for some
// users motion is not polish but a trigger. Components read this and jump straight to the final state
// instead of animating.
//
// Reads the current value on mount and subscribes to changes, so toggling the OS setting takes effect
// without a reload. Defaults to false (animate) when the platform cannot answer.
export default function useReducedMotion() {
  const [reduced, setReduced] = useState(false);

  useEffect(() => {
    let alive = true;
    AccessibilityInfo.isReduceMotionEnabled?.().then((v) => { if (alive) setReduced(!!v); }).catch(() => {});
    const sub = AccessibilityInfo.addEventListener?.('reduceMotionChanged', (v) => setReduced(!!v));
    return () => {
      alive = false;
      // RN >= 0.65 returns a subscription with .remove(); older returns void (removeEventListener).
      sub?.remove?.();
    };
  }, []);

  return reduced;
}
