import React from 'react';
import { TouchableOpacity, View } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import { stepStatusMeta } from '../constants/workflow';
import AppText from './AppText';
import StatusChip from './StatusChip';

const TYPE_ICON = { KTP: '🪪', KK: '👪', NPWP: '🧾', AKTA: '📜', SERTIFIKAT: '🏷️' };

/** Document row within a bundle: type icon, filename, page count and OCR status chip. */
function DocumentCard({ item, onPress, style }) {
  const theme = useTheme();
  const meta = stepStatusMeta(item.ocrStatus);
  return (
    <TouchableOpacity
      onPress={onPress}
      activeOpacity={0.85}
      disabled={!onPress}
      // Sprint 4, Task 11: had no accessible name, so it announced as the type emoji followed by
      // loose fragments. Now read as one row: what the document is and where it is in OCR.
      accessibilityRole={onPress ? 'button' : 'text'}
      accessibilityLabel={`${item.name}, ${item.type}, ${item.pages ?? 1} halaman, ${meta.label}`}
      style={[
        {
          flexDirection: 'row',
          alignItems: 'center',
          backgroundColor: theme.colors.surface,
          borderRadius: theme.radius.md,
          borderWidth: 1,
          borderColor: theme.colors.border,
          padding: theme.spacing.md,
        },
        style,
      ]}
    >
      <AppText style={{ fontSize: 22, marginRight: theme.spacing.md }}>{TYPE_ICON[item.type] || '📄'}</AppText>
      <View style={{ flex: 1 }}>
        <AppText variant="bodySm" numberOfLines={1}>{item.name}</AppText>
        <AppText variant="micro" color="textFaint">{item.type} · {item.pages ?? 1} hal</AppText>
      </View>
      <StatusChip label={meta.label} color={meta.color} size="sm" />
    </TouchableOpacity>
  );
}

// Memoized: a bundle can hold many documents, and each row re-runs stepStatusMeta on render
// (Sprint 4, Task 10).
export default React.memo(DocumentCard);
