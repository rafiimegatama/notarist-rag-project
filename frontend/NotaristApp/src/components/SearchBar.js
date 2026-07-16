import React from 'react';
import { View, TextInput, TouchableOpacity } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import AppText from './AppText';

/**
 * Themed search input with a leading icon and a clear button. Controlled: pass `value`/`onChangeText`.
 * `onSubmit` fires on keyboard submit. Used by the Search screen, Case list, Conversation history.
 */
export default function SearchBar({
  value,
  onChangeText,
  onSubmit,
  placeholder = 'Cari…',
  autoFocus = false,
  style,
}) {
  const theme = useTheme();
  return (
    <View
      style={[
        {
          flexDirection: 'row',
          alignItems: 'center',
          backgroundColor: theme.colors.surfaceAlt,
          borderColor: theme.colors.border,
          borderWidth: 1,
          borderRadius: theme.radius.md,
          paddingHorizontal: theme.spacing.md,
        },
        style,
      ]}
    >
      {/* Decorative — the input's own label already says this is a search field. */}
      <AppText
        accessibilityElementsHidden
        importantForAccessibility="no"
        style={{ marginRight: theme.spacing.sm }}
      >
        🔍
      </AppText>
      <TextInput
        style={{ flex: 1, color: theme.colors.text, paddingVertical: theme.spacing.md, fontSize: theme.typography.body }}
        value={value}
        onChangeText={onChangeText}
        onSubmitEditing={onSubmit}
        returnKeyType="search"
        placeholder={placeholder}
        placeholderTextColor={theme.colors.textFaint}
        autoCapitalize="none"
        autoCorrect={false}
        autoFocus={autoFocus}
        // Sprint 4, Task 11: a placeholder is not an accessible name — it disappears the moment the
        // user types, leaving the field anonymous exactly when they are editing it.
        accessibilityLabel={placeholder}
        accessibilityRole="search"
      />
      {value ? (
        <TouchableOpacity
          onPress={() => onChangeText?.('')}
          hitSlop={theme.hitSlop}
          accessibilityRole="button"
          accessibilityLabel="Hapus teks pencarian"
          style={{
            paddingLeft: theme.spacing.sm,
            minWidth: theme.touchTarget.min,
            minHeight: theme.touchTarget.min,
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <AppText color="textFaint">✕</AppText>
        </TouchableOpacity>
      ) : null}
    </View>
  );
}
