import React from 'react';
import { TouchableOpacity, View, StyleSheet } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import AppText from './AppText';

/**
 * A single settings/profile row. Modular by design: pass a leading `icon`, a `title`/`subtitle`,
 * and EITHER a `value` (right-aligned text), a `rightElement` (e.g. a Switch), or rely on the
 * chevron shown when `onPress` is set. `danger` tints the title (used for destructive actions).
 */
export default function SettingItem({
  icon,
  title,
  subtitle,
  value,
  rightElement,
  onPress,
  danger = false,
  disabled = false,
  showChevron,
}) {
  const theme = useTheme();
  const interactive = !!onPress && !disabled;
  const chevron = showChevron ?? (interactive && !rightElement);
  const titleColor = danger ? theme.colors.danger : theme.colors.text;

  const Container = interactive ? TouchableOpacity : View;

  return (
    <Container
      activeOpacity={0.7}
      onPress={interactive ? onPress : undefined}
      style={[styles.row, { paddingVertical: theme.spacing.md, paddingHorizontal: theme.spacing.lg, opacity: disabled ? 0.5 : 1 }]}
    >
      {icon != null ? (
        <View style={[styles.iconWrap, { backgroundColor: theme.colors.surfaceAlt, borderRadius: theme.radius.md }]}>
          <AppText style={{ fontSize: 16 }}>{icon}</AppText>
        </View>
      ) : null}

      <View style={styles.center}>
        <AppText variant="body" style={{ color: titleColor }}>{title}</AppText>
        {subtitle ? (
          <AppText variant="caption" color="textFaint" style={{ marginTop: 2 }}>
            {subtitle}
          </AppText>
        ) : null}
      </View>

      <View style={styles.right}>
        {value != null ? (
          <AppText variant="bodySm" color="textMuted" numberOfLines={1} style={{ maxWidth: 160 }}>
            {value}
          </AppText>
        ) : null}
        {rightElement}
        {chevron ? <AppText color="textFaint" style={{ marginLeft: 6, fontSize: 18 }}>›</AppText> : null}
      </View>
    </Container>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center' },
  iconWrap: { width: 32, height: 32, alignItems: 'center', justifyContent: 'center', marginRight: 12 },
  center: { flex: 1 },
  right: { flexDirection: 'row', alignItems: 'center', marginLeft: 8 },
});
