import React, { useState } from 'react';
import { View, TextInput, TouchableOpacity, StyleSheet } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import AppText from './AppText';

/**
 * Labeled text input with inline error and an optional secure-entry visibility toggle. Border turns
 * danger when `error` is set. Used by the Register form; reusable for any future form.
 */
export default function TextField({
  label,
  value,
  onChangeText,
  placeholder,
  error,
  secureTextEntry = false,
  keyboardType = 'default',
  autoCapitalize = 'none',
  editable = true,
  onBlur,
  returnKeyType,
  onSubmitEditing,
}) {
  const theme = useTheme();
  const [hidden, setHidden] = useState(secureTextEntry);

  return (
    <View style={{ marginBottom: theme.spacing.md }}>
      {label ? (
        <AppText variant="label" color="textMuted" style={{ marginBottom: 6 }}>
          {label}
        </AppText>
      ) : null}
      <View
        style={[
          styles.inputWrap,
          {
            backgroundColor: theme.colors.background,
            borderColor: error ? theme.colors.danger : theme.colors.border,
            borderRadius: theme.radius.md,
          },
        ]}
      >
        <TextInput
          style={[styles.input, { color: theme.colors.text, fontSize: theme.typography.body }]}
          value={value}
          onChangeText={onChangeText}
          placeholder={placeholder}
          placeholderTextColor={theme.colors.textFaint}
          secureTextEntry={hidden}
          keyboardType={keyboardType}
          autoCapitalize={autoCapitalize}
          autoCorrect={false}
          editable={editable}
          onBlur={onBlur}
          returnKeyType={returnKeyType}
          onSubmitEditing={onSubmitEditing}
          // Sprint 4, Task 11. The <AppText> label above is purely visual — nothing tied it to this
          // input, so a screen reader announced the field as unlabelled. RN has no htmlFor, so the
          // label has to be restated as the accessible name.
          accessibilityLabel={label}
          // The error text below is visual-only too. Folding it into the hint means a blind user
          // hears WHY the field was rejected on focus instead of hitting a silent wall.
          accessibilityHint={error || undefined}
          accessibilityState={{ disabled: !editable }}
        />
        {secureTextEntry ? (
          <TouchableOpacity
            onPress={() => setHidden((h) => !h)}
            style={styles.toggle}
            accessibilityRole="button"
            accessibilityLabel={hidden ? 'Tampilkan kata sandi' : 'Sembunyikan kata sandi'}
            hitSlop={theme.hitSlop}
          >
            <AppText color="textMuted" variant="caption">{hidden ? 'Tampilkan' : 'Sembunyikan'}</AppText>
          </TouchableOpacity>
        ) : null}
      </View>
      {error ? (
        <AppText variant="caption" style={{ color: theme.colors.danger, marginTop: 4 }}>
          {error}
        </AppText>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  inputWrap: { flexDirection: 'row', alignItems: 'center', borderWidth: 1, paddingHorizontal: 14 },
  input: { flex: 1, paddingVertical: 12 },
  toggle: { paddingLeft: 10 },
});
