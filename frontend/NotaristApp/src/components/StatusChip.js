import React from 'react';
import { View, TouchableOpacity } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import AppText from './AppText';

/**
 * Generic status pill. `color` is a theme color KEY (e.g. 'success', 'warning', 'danger') or a raw
 * hex. `tone` = 'soft' (tinted background) | 'solid' (filled) | 'outline'. Higher-level chips
 * (ApprovalChip, or screens using workflow meta) resolve label+color and pass them here so all
 * statuses look identical across the app.
 *
 * `accessibilityLabel` is optional and overrides the default (the visible label). Pass it when the
 * label does not stand alone once read aloud: "⏳ 2 tertunda" tells a sighted user "two pending
 * writes" from surrounding context a screen reader does not have. Omitting it keeps the old
 * behaviour exactly.
 */
export default function StatusChip({ label, color = 'textMuted', tone = 'soft', icon, size = 'md', onPress, accessibilityLabel, style }) {
  const theme = useTheme();
  const resolved = theme.colors[color] || color;
  const pad = size === 'sm' ? { pv: 2, ph: theme.spacing.sm } : { pv: 4, ph: theme.spacing.md };

  const bg = tone === 'solid' ? resolved : tone === 'outline' ? 'transparent' : withAlpha(resolved, 0.16);
  const fg = tone === 'solid' ? '#fff' : resolved;
  const borderColor = tone === 'outline' ? resolved : 'transparent';
  const Container = onPress ? TouchableOpacity : View;

  return (
    <Container
      onPress={onPress}
      activeOpacity={0.7}
      accessibilityRole={onPress ? 'button' : 'text'}
      accessibilityLabel={accessibilityLabel ?? (typeof label === 'string' ? label : undefined)}
      style={[
        {
          flexDirection: 'row',
          alignItems: 'center',
          alignSelf: 'flex-start',
          backgroundColor: bg,
          borderColor,
          borderWidth: tone === 'outline' ? 1 : 0,
          borderRadius: theme.radius.pill,
          paddingVertical: pad.pv,
          paddingHorizontal: pad.ph,
        },
        style,
      ]}
    >
      {icon ? <AppText style={{ fontSize: size === 'sm' ? 10 : 12, marginRight: 4 }}>{icon}</AppText> : null}
      <AppText style={{ color: fg, fontSize: size === 'sm' ? theme.typography.micro : theme.typography.caption, fontWeight: theme.typography.semibold }}>
        {label}
      </AppText>
    </Container>
  );
}

// Adds an alpha channel to a #RRGGBB hex. Falls back to the color unchanged for non-hex input.
function withAlpha(hex, alpha) {
  if (typeof hex !== 'string' || hex[0] !== '#' || hex.length < 7) return hex;
  const a = Math.round(alpha * 255).toString(16).padStart(2, '0');
  return `${hex.slice(0, 7)}${a}`;
}
