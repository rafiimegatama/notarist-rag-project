import React from 'react';
import { TouchableOpacity, View } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import { caseStatusMeta } from '../constants/workflow';
import { formatDate } from '../utils/format';
import AppText from './AppText';
import StatusChip from './StatusChip';

/**
 * Case list row: debtor + case number, bank, status chip, bundle count and last-updated date.
 *
 * `onPress` receives the item: `onPress={openCase}` where `openCase = useCallback((item) => …)`.
 * That is what lets the list hand every row the SAME function identity, so React.memo below can do
 * its job. A per-row `onPress={() => go(item)}` would allocate a new prop for every row on every
 * render and make the memo a no-op. Callers that ignore the argument still work.
 */
function CaseCard({ item, onPress, style }) {
  const theme = useTheme();
  const meta = caseStatusMeta(item.status);
  return (
    <TouchableOpacity
      onPress={onPress ? () => onPress(item) : undefined}
      activeOpacity={0.85}
      accessibilityRole="button"
      accessibilityLabel={`Case ${item.caseNumber}, debitur ${item.debtorName}, ${meta.label}`}
      style={[
        {
          backgroundColor: theme.colors.surface,
          borderRadius: theme.radius.lg,
          borderWidth: 1,
          borderColor: theme.colors.border,
          padding: theme.spacing.lg,
        },
        style,
      ]}
    >
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <View style={{ flex: 1, paddingRight: theme.spacing.sm }}>
          <AppText variant="bodyStrong" numberOfLines={1}>{item.debtorName}</AppText>
          <AppText variant="caption" color="textFaint" style={{ marginTop: 2 }}>
            {item.caseNumber} · {item.bank}
          </AppText>
        </View>
        <StatusChip label={meta.label} color={meta.color} size="sm" />
      </View>

      <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: theme.spacing.md }}>
        <AppText variant="micro" color="textFaint">📁 {item.bundleCount} bundle</AppText>
        <AppText variant="micro" color="textFaint">Diperbarui {formatDate(item.updatedAt)}</AppText>
      </View>
    </TouchableOpacity>
  );
}

// Memoized: this is a FlatList row. Without it, appending page 2 re-renders every row already on
// screen, and each row re-runs caseStatusMeta + formatDate (Sprint 4, Task 10). Relies on the list
// passing a stable `onPress` — see CaseListScreen's renderItem.
export default React.memo(CaseCard);
