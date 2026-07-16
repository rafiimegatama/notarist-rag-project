import React, { useState } from 'react';
import { View, TextInput, TouchableOpacity } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import { fieldStatusMeta } from '../constants/workflow';
import AppText from './AppText';
import Card from './Card';
import StatusChip from './StatusChip';
import ConfidenceBadge from './ConfidenceBadge';
import ProgressIndicator from './ProgressIndicator';
import PrimaryButton from './PrimaryButton';
import SecondaryButton from './SecondaryButton';

/**
 * One OCR-extracted field: label, value (inline-editable), confidence (badge + bar) and the review
 * actions (edit / mark manual / approve / reject). Pure props + callbacks — the OCR screen owns state.
 * Extracted so OCR Review, Verification detail, and the playground all render fields identically.
 */
export default function FieldConfidenceRow({ field, active, onFocus, onEdit, onDecision }) {
  const theme = useTheme();
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(field.value);
  const meta = fieldStatusMeta(field.status);

  const save = () => { onEdit?.(field.id, draft); setEditing(false); };

  return (
    <TouchableOpacity activeOpacity={0.9} onPress={() => onFocus?.(field.id)} accessibilityLabel={`Field ${field.label}, ${meta.label}`}>
      <Card style={{ borderColor: active ? theme.colors.primary : theme.colors.border }}>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
          <AppText variant="label" color="textMuted">{field.label}</AppText>
          <View style={{ flexDirection: 'row', gap: theme.spacing.xs }}>
            <ConfidenceBadge value={field.confidence} />
            <StatusChip label={meta.label} color={meta.color} size="sm" />
          </View>
        </View>

        {editing ? (
          <TextInput
            value={draft}
            onChangeText={setDraft}
            style={{ color: theme.colors.text, fontSize: theme.typography.body, borderBottomWidth: 1, borderBottomColor: theme.colors.primary, paddingVertical: 4, marginTop: theme.spacing.xs, minHeight: theme.touchTarget.min }}
            autoFocus
            accessibilityLabel={`Edit ${field.label}`}
          />
        ) : (
          <AppText variant="body" style={{ marginTop: theme.spacing.xs }}>{field.value}</AppText>
        )}

        <ProgressIndicator value={field.confidence} label="Confidence" style={{ marginTop: theme.spacing.sm }} />

        <View style={{ flexDirection: 'row', gap: theme.spacing.sm, marginTop: theme.spacing.md }}>
          {editing ? (
            <>
              <SecondaryButton title="Batal" size="sm" fullWidth={false} onPress={() => { setDraft(field.value); setEditing(false); }} />
              <PrimaryButton title="Simpan" size="sm" fullWidth={false} onPress={save} />
            </>
          ) : (
            <>
              <SecondaryButton title="✎ Edit" size="sm" fullWidth={false} onPress={() => setEditing(true)} />
              <SecondaryButton title="Cek Manual" size="sm" fullWidth={false} onPress={() => onDecision?.(field.id, 'NEEDS_CHECK')} />
              <PrimaryButton title="✓ Setuju" size="sm" fullWidth={false} onPress={() => onDecision?.(field.id, 'APPROVED')} />
              <TouchableOpacity
                onPress={() => onDecision?.(field.id, 'REJECTED')}
                accessibilityRole="button"
                accessibilityLabel={`Tolak ${field.label}`}
                hitSlop={theme.hitSlop}
                style={{ justifyContent: 'center', paddingHorizontal: theme.spacing.sm, minWidth: theme.touchTarget.min, alignItems: 'center' }}
              >
                <AppText color="danger" variant="bodyStrong">✕</AppText>
              </TouchableOpacity>
            </>
          )}
        </View>
      </Card>
    </TouchableOpacity>
  );
}
