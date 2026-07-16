import React, { useEffect, useRef, useState } from 'react';
import { Animated, View, Platform } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTheme } from '../context/ThemeContext';
import AppText from './AppText';
import useConnectivity from '../hooks/useConnectivity';
import { NetworkStatus } from '../api/connectivity';
import { relativeTime } from '../utils/format';

/**
 * Global connectivity banner (Sprint 4, Task 8): Offline · Reconnecting · Last synced.
 *
 * Mounted once, as an overlay sibling of the navigator in App.js. It is absolutely positioned and
 * therefore adds no layout: no screen shifts down when it appears, and no existing padding changes.
 *
 * Visibility rules, in order of how much they matter:
 *
 *   offline       — shown and kept. The user is looking at data that may be stale; that is worth a
 *                   permanent strip. "Last synced" rides along here, because staleness only matters
 *                   when you are offline — it is the one moment the age of the data is the story.
 *   reconnecting  — shown while a retry is actually in flight (retry.js drives the signal).
 *   online        — shown for RECOVERED_MS after a recovery, then hidden. Never shown on a cold
 *                   start: an app that greets you with "Tersambung" has told you nothing.
 *
 * The recovery flash is the only piece of state here, and it is deliberately local — the banner
 * animating is not something the rest of the app needs to know about.
 */

const RECOVERED_MS = 2200;

export default function NetworkBanner() {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const { status, lastSyncedAt } = useConnectivity();

  // "Was offline, is now online" — the only way to know a recovery is worth announcing.
  const wasDisconnected = useRef(false);
  const [showRecovered, setShowRecovered] = useState(false);

  useEffect(() => {
    if (status === NetworkStatus.OFFLINE || status === NetworkStatus.RECONNECTING) {
      wasDisconnected.current = true;
      setShowRecovered(false);
      return undefined;
    }
    if (status === NetworkStatus.ONLINE && wasDisconnected.current) {
      wasDisconnected.current = false;
      setShowRecovered(true);
      const t = setTimeout(() => setShowRecovered(false), RECOVERED_MS);
      return () => clearTimeout(t);
    }
    return undefined;
  }, [status]);

  const visible =
    status === NetworkStatus.OFFLINE || status === NetworkStatus.RECONNECTING || showRecovered;

  // Driven by `visible` rather than by a per-branch call so a status change mid-animation cannot
  // strand the banner half-faded.
  const anim = useRef(new Animated.Value(0)).current;
  useEffect(() => {
    Animated.timing(anim, {
      toValue: visible ? 1 : 0,
      duration: theme.durations.base,
      useNativeDriver: true,
    }).start();
  }, [visible, anim, theme.durations.base]);

  // Mounted-but-transparent would still swallow touches near the top of the screen, so unmount.
  if (!visible) return null;

  const tone =
    status === NetworkStatus.OFFLINE
      ? theme.colors.danger
      : status === NetworkStatus.RECONNECTING
      ? theme.colors.warning
      : theme.colors.success;

  const label =
    status === NetworkStatus.OFFLINE
      ? 'Anda sedang offline'
      : status === NetworkStatus.RECONNECTING
      ? 'Menyambungkan kembali…'
      : 'Tersambung kembali';

  const icon =
    status === NetworkStatus.OFFLINE ? '📡' : status === NetworkStatus.RECONNECTING ? '🔄' : '✅';

  // Only meaningful while disconnected, and only once a sync has actually happened.
  const syncedLabel =
    status === NetworkStatus.OFFLINE && lastSyncedAt
      ? `Terakhir disinkronkan ${relativeTime(lastSyncedAt)}`
      : null;

  return (
    <Animated.View
      pointerEvents="none"
      // Announced by the screen reader when it appears, without stealing focus from the user's task.
      accessibilityRole="alert"
      accessibilityLiveRegion="polite"
      accessibilityLabel={syncedLabel ? `${label}. ${syncedLabel}` : label}
      style={{
        position: 'absolute',
        top: insets.top,
        left: 0,
        right: 0,
        // Above screen content, below `dialog` — a modal asking for a decision must not be covered
        // by a status strip.
        zIndex: theme.zIndex.overlay,
        opacity: anim,
        transform: [{ translateY: anim.interpolate({ inputRange: [0, 1], outputRange: [-8, 0] }) }],
        paddingHorizontal: theme.spacing.md,
        paddingTop: theme.spacing.xs,
      }}
    >
      <View
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'center',
          backgroundColor: theme.colors.surface,
          borderColor: tone,
          borderWidth: 1,
          borderRadius: theme.radius.md,
          paddingVertical: theme.spacing.sm,
          paddingHorizontal: theme.spacing.md,
          // Legible over whatever screen is behind it.
          ...Platform.select({
            android: { elevation: 4 },
            default: {
              shadowColor: '#000',
              shadowOpacity: 0.18,
              shadowRadius: 6,
              shadowOffset: { width: 0, height: 2 },
            },
          }),
        }}
      >
        <AppText
          accessibilityElementsHidden
          importantForAccessibility="no"
          style={{ marginRight: theme.spacing.sm }}
        >
          {icon}
        </AppText>
        {/* The banner's own accessibilityLabel already reads both lines; hide the duplicates from
            the reader so it is announced once, not three times. */}
        <View style={{ flexShrink: 1 }} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
          <AppText variant="caption" style={{ color: tone, fontWeight: theme.typography.semibold }}>
            {label}
          </AppText>
          {syncedLabel ? (
            <AppText variant="micro" color="textFaint">
              {syncedLabel}
            </AppText>
          ) : null}
        </View>
      </View>
    </Animated.View>
  );
}
