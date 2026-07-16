import React from 'react';
import {
  View,
  ScrollView,
  KeyboardAvoidingView,
  Platform,
  StyleSheet,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTheme } from '../context/ThemeContext';

/**
 * Standard screen container: themed background, safe-area padding, optional scroll and
 * keyboard-avoidance. Every screen renders inside one of these so spacing/safe-area/keyboard
 * behavior is consistent instead of re-implemented per screen.
 */
export default function Screen({
  children,
  scroll = false,
  keyboardAware = false,
  padded = true,
  edges = ['top', 'bottom'],
  refreshControl,
  contentContainerStyle,
  style,
}) {
  const theme = useTheme();
  const insets = useSafeAreaInsets();

  const pad = {
    paddingTop: edges.includes('top') ? insets.top : 0,
    paddingBottom: edges.includes('bottom') ? insets.bottom : 0,
    paddingLeft: edges.includes('left') ? insets.left : 0,
    paddingRight: edges.includes('right') ? insets.right : 0,
  };

  const inner = padded ? { padding: theme.spacing.lg } : null;

  const body = scroll ? (
    <ScrollView
      style={styles.flex}
      contentContainerStyle={[inner, styles.grow, contentContainerStyle]}
      keyboardShouldPersistTaps="handled"
      refreshControl={refreshControl}
      showsVerticalScrollIndicator={false}
    >
      {children}
    </ScrollView>
  ) : (
    <View style={[styles.flex, inner, contentContainerStyle]}>{children}</View>
  );

  const content = (
    <View style={[styles.flex, { backgroundColor: theme.colors.background }, pad, style]}>
      {body}
    </View>
  );

  if (keyboardAware) {
    return (
      <KeyboardAvoidingView
        style={[styles.flex, { backgroundColor: theme.colors.background }]}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        {content}
      </KeyboardAvoidingView>
    );
  }
  return content;
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  grow: { flexGrow: 1 },
});
