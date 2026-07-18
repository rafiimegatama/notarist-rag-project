import React from 'react';
import { Modal, View, ActivityIndicator, TouchableOpacity } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTheme } from '../context/ThemeContext';
import { useGlobalLoading, useLoadingTasks } from '../context/LoadingContext';
import AppText from './AppText';
import ProgressIndicator from './ProgressIndicator';
import SecondaryButton from './SecondaryButton';

// The app-level loading UI (Sprint 9). Mounted ONCE as a sibling of the navigator in App.js, like
// NetworkBanner — it adds no layout and paints above every screen and stack swap.
//
// It renders two distinct treatments, because "the app is busy" and "something is happening in the
// background" are different claims:
//
//   BLOCKING tasks  -> a full-screen scrim + card. Reserved for work the user must not act around
//                      (signing out). Spinner or determinate progress, a queue count, and Batalkan.
//   BACKGROUND      -> a slim top strip that does NOT capture touches, so the user keeps working while
//   (blocking:false)   it runs (a document upload). Shows the current step, a coarse progress fill or
//                      a small spinner, and — when the task allows it — a cancel affordance.
//
// The blocking scrim wins the screen; the background strip sits under the status bar. When both exist,
// the scrim covers the strip, which is correct: a blocking wait outranks a background one.
export default function GlobalLoadingOverlay() {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const tasks = useLoadingTasks();
  const { cancel } = useGlobalLoading();

  const blocking = tasks.filter((t) => t.blocking);
  const background = tasks.filter((t) => !t.blocking);
  const top = blocking.length ? blocking[blocking.length - 1] : null;
  const bg = background.length ? background[background.length - 1] : null;
  const others = blocking.length - 1;
  const bgOthers = background.length - 1;
  const determinate = top && top.progress != null;
  const bgDeterminate = bg && bg.progress != null;

  return (
    <>
      {/* Background strip — absolutely positioned, box-none so taps fall through to the screen except
          on the cancel button itself. */}
      {bg ? (
        <View
          pointerEvents="box-none"
          style={{ position: 'absolute', top: insets.top, left: 0, right: 0, paddingHorizontal: theme.spacing.md, paddingTop: theme.spacing.xs }}
        >
          <View
            pointerEvents="auto"
            accessibilityRole="progressbar"
            accessibilityLabel={bg.message}
            accessibilityValue={bgDeterminate ? { min: 0, max: 100, now: Math.round(bg.progress * 100) } : undefined}
            style={{
              backgroundColor: theme.colors.elevated,
              borderRadius: theme.radius.md,
              borderWidth: 1,
              borderColor: theme.colors.border,
              paddingHorizontal: theme.spacing.md,
              paddingVertical: theme.spacing.sm,
              ...theme.shadows.sm,
            }}
          >
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: theme.spacing.sm }}>
              {!bgDeterminate ? <ActivityIndicator size="small" color={theme.colors.primary} /> : null}
              <AppText variant="micro" color="textMuted" numberOfLines={1} style={{ flex: 1 }}>
                {bg.message}{bgOthers > 0 ? ` (+${bgOthers})` : ''}
              </AppText>
              {bg.cancelable ? (
                <TouchableOpacity
                  onPress={() => cancel(bg.id)}
                  accessibilityRole="button"
                  accessibilityLabel="Batalkan"
                  hitSlop={theme.hitSlop}
                  style={{ minWidth: theme.touchTarget.min, minHeight: 24, alignItems: 'flex-end', justifyContent: 'center' }}
                >
                  <AppText variant="micro" color="danger" style={{ fontWeight: theme.typography.semibold }}>Batalkan</AppText>
                </TouchableOpacity>
              ) : null}
            </View>
            {/* Coarse STEP progress, no percentage label — the bar tracks stages completing, and this
                app does not invent a byte-level number the upload transport cannot actually report. */}
            {bgDeterminate ? (
              <ProgressIndicator value={bg.progress} style={{ marginTop: 6 }} />
            ) : null}
          </View>
        </View>
      ) : null}

      {/* Blocking scrim + card. */}
      <Modal
        visible={!!top}
        transparent
        animationType="fade"
        onRequestClose={() => { if (top && top.cancelable) cancel(top.id); }}
      >
        <View style={{ flex: 1, backgroundColor: theme.colors.overlay, alignItems: 'center', justifyContent: 'center', padding: theme.spacing.xl }}>
          {top ? (
            <View
              accessibilityViewIsModal
              accessibilityRole="progressbar"
              accessibilityLabel={top.message}
              accessibilityValue={determinate ? { min: 0, max: 100, now: Math.round(top.progress * 100) } : undefined}
              style={{
                width: '100%',
                maxWidth: 340,
                backgroundColor: theme.colors.elevated,
                borderRadius: theme.radius.xl,
                borderWidth: 1,
                borderColor: theme.colors.border,
                padding: theme.spacing.xl,
                alignItems: 'center',
                ...theme.shadows.lg,
              }}
            >
              {determinate ? (
                <ProgressIndicator value={top.progress} label={top.message} style={{ width: '100%' }} />
              ) : (
                <>
                  <ActivityIndicator size="large" color={theme.colors.primary} />
                  <AppText color="text" variant="bodySm" align="center" style={{ marginTop: theme.spacing.md }}>
                    {top.message}
                  </AppText>
                </>
              )}

              {others > 0 ? (
                <AppText color="textFaint" variant="micro" style={{ marginTop: theme.spacing.md }}>
                  +{others} tugas lainnya…
                </AppText>
              ) : null}

              {top.cancelable ? (
                <SecondaryButton
                  title="Batalkan"
                  onPress={() => cancel(top.id)}
                  style={{ marginTop: theme.spacing.lg, alignSelf: 'stretch' }}
                />
              ) : null}
            </View>
          ) : null}
        </View>
      </Modal>
    </>
  );
}
