import React from 'react';
import { TouchableOpacity, View } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import { reminderTypeMeta, reminderSeverityMeta } from '../constants/workflow';
import { formatDate } from '../utils/format';
import AppText from './AppText';
import StatusChip from './StatusChip';

/** Reminder row: type icon + title, related case, due date and a severity chip (overdue/soon). */
function ReminderCard({ item, onPress, style }) {
  const theme = useTheme();
  const type = reminderTypeMeta(item.type);
  const sev = reminderSeverityMeta(item.severity);
  return (
    <TouchableOpacity
      onPress={onPress}
      activeOpacity={0.85}
      disabled={!onPress}
      accessibilityRole={onPress ? 'button' : 'text'}
      accessibilityLabel={`${type.label}: ${item.title}, ${sev.label}`}
      style={[
        {
          flexDirection: 'row',
          backgroundColor: theme.colors.surface,
          borderRadius: theme.radius.lg,
          borderWidth: 1,
          borderColor: theme.colors.border,
          borderLeftWidth: 3,
          borderLeftColor: theme.colors[sev.color] || theme.colors.border,
          padding: theme.spacing.lg,
        },
        style,
      ]}
    >
      <AppText style={{ fontSize: 22, marginRight: theme.spacing.md }}>{type.icon}</AppText>
      <View style={{ flex: 1 }}>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <AppText variant="label" color={type.color} style={{ flex: 1 }}>{type.label}</AppText>
          <StatusChip label={sev.label} color={sev.color} size="sm" />
        </View>
        <AppText variant="bodySm" numberOfLines={2} style={{ marginTop: 2 }}>{item.title}</AppText>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginTop: theme.spacing.sm }}>
          {item.caseNumber ? <AppText variant="micro" color="textFaint">{item.caseNumber}</AppText> : <View />}
          <AppText variant="micro" color="textFaint">Jatuh tempo {formatDate(item.dueDate)}</AppText>
        </View>
      </View>
    </TouchableOpacity>
  );
}

// Memoized: a FlatList row that re-runs reminderTypeMeta/reminderSeverityMeta/formatDate on each
// render. Switching the time-window filter re-renders the screen; the rows that survive the filter
// should not re-render with it (Sprint 4, Task 10).
export default React.memo(ReminderCard);
