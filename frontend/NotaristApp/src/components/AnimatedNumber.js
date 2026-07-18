import React, { useEffect, useRef, useState } from 'react';
import { Animated, Easing } from 'react-native';
import useReducedMotion from '../hooks/useReducedMotion';

// A number that counts up to its value when it changes — the dashboard's "animated numbers". It
// eases from the PREVIOUS value to the new one, so a refresh that moves "3 → 5" tallies up rather
// than snapping, and the first paint counts from 0.
//
// Two correctness rules the count-up must not break, because these are workload counters a notary
// reads for decisions:
//   * the value SHOWN when the animation settles is exactly the target — the interpolation rounds,
//     but the final frame is pinned to `value`, never a rounding artefact like "4" for 5.
//   * reduce-motion (or a non-finite value like the "—" a missing counter renders) shows the target
//     immediately with no animation at all.
//
// Built on the RN Animated driver (react-native-reanimated is not a dependency). useNativeDriver is
// false on purpose: the animated output is TEXT, which the native driver cannot drive — the value is
// read on the JS thread via a listener. The work is one setState per frame for ~500ms, only while a
// counter is actually changing.
function AnimatedNumberInner({ value, duration = 600, style }) {
  const reducedMotion = useReducedMotion();
  const anim = useRef(new Animated.Value(0)).current;
  const from = useRef(0);
  // Start at 0 so the first paint shows the count BEGINNING, not a flash of the final value that the
  // effect then resets to 0 and re-counts. Reduce-motion corrects to the target in its effect branch.
  const [display, setDisplay] = useState(0);

  useEffect(() => {
    if (reducedMotion) { setDisplay(value); from.current = value; return undefined; }

    anim.setValue(0);
    const start = from.current;
    const delta = value - start;
    const id = anim.addListener(({ value: t }) => {
      setDisplay(Math.round(start + delta * t));
    });
    const animation = Animated.timing(anim, {
      toValue: 1,
      duration,
      easing: Easing.out(Easing.cubic),
      useNativeDriver: false,
    });
    animation.start(({ finished }) => {
      // Pin to the exact target so the resting number is never an interpolation rounding artefact.
      if (finished) setDisplay(value);
      from.current = value;
    });
    return () => {
      anim.removeListener(id);
      animation.stop();
      from.current = value; // if interrupted mid-count, the next change eases from where we are
    };
  }, [value, duration, reducedMotion, anim]);

  return <Animated.Text style={style}>{display}</Animated.Text>;
}

/**
 * Count-up number. `value` must be finite to animate; a non-finite value (e.g. an unmeasured counter
 * rendered as text) is handed to the fallback, which shows it verbatim. `format` optionally maps the
 * current integer to a string (e.g. thousands separators).
 */
export default function AnimatedNumber({ value, duration, style, format }) {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    return <Animated.Text style={style}>{value}</Animated.Text>;
  }
  if (format) return <FormattedAnimatedNumber value={value} duration={duration} style={style} format={format} />;
  return <AnimatedNumberInner value={value} duration={duration} style={style} />;
}

// Same count-up, but the displayed integer runs through `format`. Kept separate so the common,
// unformatted path stays a plain <Animated.Text> with no per-frame formatting cost.
function FormattedAnimatedNumber({ value, duration = 600, style, format }) {
  const reducedMotion = useReducedMotion();
  const anim = useRef(new Animated.Value(0)).current;
  const from = useRef(0);
  // Start at 0 so the first paint shows the count BEGINNING, not a flash of the final value that the
  // effect then resets to 0 and re-counts. Reduce-motion corrects to the target in its effect branch.
  const [display, setDisplay] = useState(0);

  useEffect(() => {
    if (reducedMotion) { setDisplay(value); from.current = value; return undefined; }
    anim.setValue(0);
    const start = from.current;
    const delta = value - start;
    const id = anim.addListener(({ value: t }) => setDisplay(Math.round(start + delta * t)));
    const animation = Animated.timing(anim, { toValue: 1, duration, easing: Easing.out(Easing.cubic), useNativeDriver: false });
    animation.start(({ finished }) => { if (finished) setDisplay(value); from.current = value; });
    return () => { anim.removeListener(id); animation.stop(); from.current = value; };
  }, [value, duration, reducedMotion, anim]);

  return <Animated.Text style={style}>{format(display)}</Animated.Text>;
}
