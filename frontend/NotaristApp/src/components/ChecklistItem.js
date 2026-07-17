import React from 'react';
import { View, TouchableOpacity, TextInput } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import { fieldStatusMeta } from '../constants/workflow';
import AppText from './AppText';
import StatusChip from './StatusChip';

const DECISIONS = [
  { key: 'APPROVED', label: 'Setuju', tone: 'success' },
  { key: 'REJECTED', label: 'Tolak', tone: 'danger' },
  { key: 'NEEDS_CHECK', label: 'Cek Manual', tone: 'warning' },
];

/**
 * A single verification/QC checklist row: title + subtitle, a 3-way decision selector, and an optional
 * comment field. Controlled via `decision`/`comment` + `onDecide`/`onComment`. Reused by ChecklistCard
 * and directly in the playground.
 */
export default function ChecklistItem({ title, subtitle, decision, comment, onDecide, onComment, showComment = true, style }) {
  const theme = useTheme();
  const meta = fieldStatusMeta(decision ?? 'PENDING');
  return (
    <View style={style}>
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
        <AppText variant="bodyStrong" numberOfLines={1} style={{ flex: 1 }}>{title}</AppText>
        <StatusChip label={meta.label} color={meta.color} size="sm" />
      </View>
      {subtitle ? <AppText variant="micro" color="textFaint" style={{ marginTop: 2 }}>{subtitle}</AppText> : null}

      <View style={{ flexDirection: 'row', gap: theme.spacing.sm, marginTop: theme.spacing.md }}>
        {DECISIONS.map((d) => {
          const active = decision === d.key;
          const col = theme.colors[d.tone];
          return (
            <TouchableOpacity
              key={d.key}
              onPress={() => onDecide?.(d.key)}
              accessibilityRole="radio"
              accessibilityState={{ selected: active }}
              accessibilityLabel={d.label}
              activeOpacity={0.8}
              style={{
                flex: 1, alignItems: 'center', justifyContent: 'center',
                minHeight: theme.touchTarget.min,
                borderRadius: theme.radius.md, borderWidth: 1,
                borderColor: active ? col : theme.colors.border,
                backgroundColor: active ? col + '22' : 'transparent',
              }}
            >
              <AppText variant="bodySm" style={{ color: active ? col : theme.colors.textMuted, fontWeight: theme.typography.semibold }}>{d.label}</AppText>
            </TouchableOpacity>
          );
        })}
      </View>

      {showComment ? (
        <TextInput
          value={comment}
          onChangeText={onComment}
          placeholder="Komentar (opsional)…"
          placeholderTextColor={theme.colors.textFaint}
          style={{
            marginTop: theme.spacing.md, color: theme.colors.text, fontSize: theme.typography.bodySm,
            backgroundColor: theme.colors.surfaceAlt, borderRadius: theme.radius.md,
            borderWidth: 1, borderColor: theme.colors.border, padding: theme.spacing.md, minHeight: theme.touchTarget.min,
          }}
          multiline
          accessibilityLabel={`Komentar untuk ${title}`}
        />
      ) : null}
    </View>
  );
}
