import React, { useRef, useState } from 'react';
import { View, Animated, PanResponder, TouchableOpacity } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import AppText from './AppText';

const MIN = 1;
const MAX = 4;
const STEP = 0.5;
const clamp = (n, lo, hi) => Math.max(lo, Math.min(hi, n));

// A zoom / pan / rotate viewport for the OCR page (Sprint 4). Zoom is stepped via +/- buttons and
// rotate is a 90° button — deliberately buttons rather than pinch/twist gestures, which would need
// react-native-gesture-handler's multi-touch handlers wired through; the buttons are reliable on
// every target and keyboard/screen-reader operable. Pan is a drag, enabled only once zoomed in (at
// 1x there is nothing to pan). Children (the page + its bounding boxes) transform together, so a
// field highlight stays locked to the page through every zoom, pan and rotation.
export default function ZoomPanRotate({ children, aspectRatio = 0.72, style }) {
  const theme = useTheme();
  const [scale, setScale] = useState(1);
  const [rotation, setRotation] = useState(0);
  const scaleRef = useRef(1);
  scaleRef.current = scale;

  const pan = useRef(new Animated.ValueXY({ x: 0, y: 0 })).current;
  const offset = useRef({ x: 0, y: 0 });

  const panResponder = useRef(
    PanResponder.create({
      onMoveShouldSetPanResponder: (_, g) => scaleRef.current > 1 && (Math.abs(g.dx) > 4 || Math.abs(g.dy) > 4),
      onPanResponderGrant: () => { pan.setOffset(offset.current); pan.setValue({ x: 0, y: 0 }); },
      onPanResponderMove: Animated.event([null, { dx: pan.x, dy: pan.y }], { useNativeDriver: false }),
      onPanResponderRelease: (_, g) => {
        offset.current = { x: offset.current.x + g.dx, y: offset.current.y + g.dy };
        pan.flattenOffset();
      },
    }),
  ).current;

  const zoom = (dir) => setScale((s) => clamp(Number((s + dir * STEP).toFixed(2)), MIN, MAX));
  const rotate = () => setRotation((r) => (r + 90) % 360);
  const reset = () => {
    setScale(1);
    setRotation(0);
    offset.current = { x: 0, y: 0 };
    pan.setOffset({ x: 0, y: 0 });
    pan.setValue({ x: 0, y: 0 });
  };

  const CtrlBtn = ({ label, glyph, onPress, disabled }) => (
    <TouchableOpacity
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityLabel={label}
      style={{
        width: 34, height: 34, borderRadius: 17,
        backgroundColor: theme.colors.elevated,
        borderWidth: 1, borderColor: theme.colors.border,
        alignItems: 'center', justifyContent: 'center',
        opacity: disabled ? 0.4 : 1,
        ...theme.shadows.sm,
      }}
    >
      <AppText style={{ fontSize: 15, color: theme.colors.text }}>{glyph}</AppText>
    </TouchableOpacity>
  );

  return (
    <View style={style}>
      <View
        {...panResponder.panHandlers}
        style={{
          aspectRatio,
          backgroundColor: theme.colors.surfaceAlt,
          borderRadius: theme.radius.md,
          borderWidth: 1,
          borderColor: theme.colors.border,
          overflow: 'hidden',
        }}
      >
        <Animated.View
          style={{
            flex: 1,
            transform: [
              { translateX: pan.x },
              { translateY: pan.y },
              { scale },
              { rotate: `${rotation}deg` },
            ],
          }}
        >
          {children}
        </Animated.View>

        {/* Controls float over the page. box-none lets pans through the empty space between buttons. */}
        <View pointerEvents="box-none" style={{ position: 'absolute', right: 8, bottom: 8, gap: 6 }}>
          <CtrlBtn label="Perbesar" glyph="＋" onPress={() => zoom(1)} disabled={scale >= MAX} />
          <CtrlBtn label="Perkecil" glyph="－" onPress={() => zoom(-1)} disabled={scale <= MIN} />
          <CtrlBtn label="Putar 90 derajat" glyph="⟳" onPress={rotate} />
          <CtrlBtn label="Atur ulang tampilan" glyph="⤢" onPress={reset} disabled={scale === 1 && rotation === 0} />
        </View>

        {scale > 1 || rotation ? (
          <View style={{ position: 'absolute', left: 8, bottom: 8, backgroundColor: theme.colors.elevated, borderRadius: theme.radius.sm, paddingHorizontal: 8, paddingVertical: 3, borderWidth: 1, borderColor: theme.colors.border }}>
            <AppText variant="micro" color="textMuted">{Math.round(scale * 100)}%{rotation ? ` · ${rotation}°` : ''}</AppText>
          </View>
        ) : null}
      </View>
    </View>
  );
}
