import React from 'react';
import { View, TouchableOpacity } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import { stepStatusMeta } from '../constants/workflow';
import { formatDate } from '../utils/format';
import AppText from './AppText';
import Card from './Card';
import StatusChip from './StatusChip';

/**
 * A generated draft document (Generate step output): title, kind, draft status and generated date,
 * with an optional open/download action. Pure props.
 */
export default function GeneratedDocumentCard({ item, onPress, style }) {
  const theme = useTheme();
  const meta = stepStatusMeta(item.status ?? 'PENDING');
  return (
    <Card style={style}>
      <View style={{ flexDirection: 'row', alignItems: 'center' }}>
        <AppText style={{ fontSize: theme.iconSize.lg, marginRight: theme.spacing.md }}>📝</AppText>
        <View style={{ flex: 1 }}>
          <AppText variant="bodyStrong" numberOfLines={1}>{item.title}</AppText>
          <AppText variant="micro" color="textFaint">{item.kind ?? 'Draft'} · {formatDate(item.generatedAt)}</AppText>
        </View>
        <StatusChip label={meta.label} color={meta.color} size="sm" />
      </View>
      {onPress ? (
        <TouchableOpacity
          onPress={onPress}
          accessibilityRole="button"
          accessibilityLabel={`Buka ${item.title}`}
          style={{ marginTop: theme.spacing.md, minHeight: theme.touchTarget.min, justifyContent: 'center' }}
        >
          <AppText variant="bodySm" color="primary">Buka dokumen →</AppText>
        </TouchableOpacity>
      ) : null}
    </Card>
  );
}
