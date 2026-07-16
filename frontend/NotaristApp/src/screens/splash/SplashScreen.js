import React, { useCallback, useEffect, useRef } from 'react';
import { View, Animated, ActivityIndicator, StyleSheet } from 'react-native';
import * as NativeSplashScreen from 'expo-splash-screen';
import { useTheme } from '../../context/ThemeContext';
import AppText from '../../components/AppText';
import { APP } from '../../constants/config';

/**
 * Branded splash. Presentational only — the navigator shows it while useBootstrap() reports the app
 * is not ready (session check + preferences hydration + a minimum display time). Animates a fade +
 * scale in so the first frame is never a blank screen and the hand-off to the app feels smooth.
 */
export default function SplashScreen() {
  const theme = useTheme();
  const opacity = useRef(new Animated.Value(0)).current;
  const scale = useRef(new Animated.Value(0.92)).current;

  useEffect(() => {
    Animated.parallel([
      Animated.timing(opacity, { toValue: 1, duration: 450, useNativeDriver: true }),
      Animated.spring(scale, { toValue: 1, friction: 6, tension: 60, useNativeDriver: true }),
    ]).start();
  }, [opacity, scale]);

  // The native splash stays up (App.js prevented auto-hide) until this view has actually laid out,
  // so there is never a blank frame between the two.
  const handleLayout = useCallback(() => {
    NativeSplashScreen.hideAsync().catch(() => {});
  }, []);

  return (
    <View onLayout={handleLayout} style={[styles.container, { backgroundColor: theme.colors.background }]}>
      <Animated.View style={{ opacity, transform: [{ scale }], alignItems: 'center' }}>
        <AppText style={styles.logo}>⚖️</AppText>
        <AppText variant="display" style={{ marginTop: theme.spacing.sm }}>
          {APP.name}
        </AppText>
        <AppText color="textMuted" variant="bodySm" style={{ marginTop: theme.spacing.xs }}>
          {APP.tagline}
        </AppText>
      </Animated.View>

      <View style={styles.footer}>
        <ActivityIndicator color={theme.colors.primary} />
        <AppText color="textFaint" variant="caption" style={{ marginTop: theme.spacing.md }}>
          Memuat sesi…
        </AppText>
        <AppText color="textFaint" variant="micro" style={{ marginTop: theme.spacing.xxl }}>
          v{APP.version}
        </AppText>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  logo: { fontSize: 72 },
  footer: { position: 'absolute', bottom: 56, alignItems: 'center' },
});
