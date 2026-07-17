import React from 'react';
import { Modal, View, TouchableWithoutFeedback } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import AppText from './AppText';
import PrimaryButton from './PrimaryButton';
import DangerButton from './DangerButton';
import SecondaryButton from './SecondaryButton';

/**
 * Themed confirmation dialog. Controlled via `visible`. `tone` = 'primary' | 'danger' picks the confirm
 * button style. Tapping the scrim cancels. Used instead of Alert.alert where we want themed, testable,
 * screen-reader-labeled dialogs (the playground and destructive flows).
 */
export default function ConfirmationDialog({
  visible,
  title,
  message,
  confirmLabel = 'Konfirmasi',
  cancelLabel = 'Batal',
  tone = 'primary',
  loading = false,
  onConfirm,
  onCancel,
}) {
  const theme = useTheme();
  const Confirm = tone === 'danger' ? DangerButton : PrimaryButton;

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onCancel}>
      <TouchableWithoutFeedback onPress={onCancel} accessibilityLabel="Tutup dialog">
        <View style={{ flex: 1, backgroundColor: theme.colors.overlay, alignItems: 'center', justifyContent: 'center', padding: theme.spacing.xl }}>
          <TouchableWithoutFeedback>
            <View
              accessibilityViewIsModal
              accessibilityRole="alert"
              style={{
                width: '100%', maxWidth: 420,
                backgroundColor: theme.colors.elevated,
                borderRadius: theme.radius.xl,
                borderWidth: 1, borderColor: theme.colors.border,
                padding: theme.spacing.xl,
                ...theme.shadows.lg,
              }}
            >
              {title ? <AppText variant="h3" style={{ marginBottom: theme.spacing.sm }}>{title}</AppText> : null}
              {message ? <AppText variant="bodySm" color="textMuted" style={{ marginBottom: theme.spacing.xl, lineHeight: 20 }}>{message}</AppText> : null}
              <View style={{ flexDirection: 'row', gap: theme.spacing.md }}>
                <View style={{ flex: 1 }}><SecondaryButton title={cancelLabel} onPress={onCancel} /></View>
                <View style={{ flex: 1 }}><Confirm title={confirmLabel} loading={loading} onPress={onConfirm} /></View>
              </View>
            </View>
          </TouchableWithoutFeedback>
        </View>
      </TouchableWithoutFeedback>
    </Modal>
  );
}
