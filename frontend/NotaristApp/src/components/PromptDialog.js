import React, { useEffect, useRef, useState } from 'react';
import { Modal, View, TextInput, TouchableWithoutFeedback } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import AppText from './AppText';
import PrimaryButton from './PrimaryButton';
import SecondaryButton from './SecondaryButton';

/**
 * Themed single-line text prompt — the input-carrying sibling of ConfirmationDialog. Used for rename
 * flows (conversation title today). Controlled via `visible`; `initialValue` seeds the field each
 * time it opens. onConfirm receives the trimmed value; an empty value disables confirm rather than
 * silently submitting nothing.
 *
 * Built as its own primitive rather than bolting an input onto ConfirmationDialog: that dialog is an
 * `alert`-role confirmation and adding a focusable field to it would break its semantics and its
 * "tap scrim to dismiss" contract mid-typing. This one owns local draft state and keyboard submit.
 */
export default function PromptDialog({
  visible,
  title,
  message,
  initialValue = '',
  placeholder = '',
  confirmLabel = 'Simpan',
  cancelLabel = 'Batal',
  maxLength = 120,
  onConfirm,
  onCancel,
}) {
  const theme = useTheme();
  const [value, setValue] = useState(initialValue);
  const inputRef = useRef(null);

  // Reseed whenever the dialog (re)opens, so renaming a second conversation does not show the first
  // one's draft. Keyed on `visible` + `initialValue`.
  useEffect(() => {
    if (visible) setValue(initialValue);
  }, [visible, initialValue]);

  const trimmed = value.trim();
  const submit = () => { if (trimmed) onConfirm?.(trimmed); };

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onCancel}>
      <TouchableWithoutFeedback onPress={onCancel} accessibilityLabel="Tutup dialog">
        <View style={{ flex: 1, backgroundColor: theme.colors.overlay, alignItems: 'center', justifyContent: 'center', padding: theme.spacing.xl }}>
          <TouchableWithoutFeedback>
            <View
              accessibilityViewIsModal
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
              {message ? <AppText variant="bodySm" color="textMuted" style={{ marginBottom: theme.spacing.md, lineHeight: 20 }}>{message}</AppText> : null}
              <TextInput
                ref={inputRef}
                style={{
                  backgroundColor: theme.colors.surface,
                  color: theme.colors.text,
                  borderRadius: theme.radius.md,
                  borderWidth: 1,
                  borderColor: theme.colors.border,
                  paddingHorizontal: theme.spacing.md,
                  paddingVertical: theme.spacing.md,
                  fontSize: theme.typography.body,
                  marginBottom: theme.spacing.xl,
                }}
                value={value}
                onChangeText={setValue}
                placeholder={placeholder}
                placeholderTextColor={theme.colors.textFaint}
                maxLength={maxLength}
                autoFocus
                returnKeyType="done"
                onSubmitEditing={submit}
                accessibilityLabel={title || placeholder}
              />
              <View style={{ flexDirection: 'row', gap: theme.spacing.md }}>
                <View style={{ flex: 1 }}><SecondaryButton title={cancelLabel} onPress={onCancel} /></View>
                <View style={{ flex: 1 }}><PrimaryButton title={confirmLabel} onPress={submit} disabled={!trimmed} /></View>
              </View>
            </View>
          </TouchableWithoutFeedback>
        </View>
      </TouchableWithoutFeedback>
    </Modal>
  );
}
