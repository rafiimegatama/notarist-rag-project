import React from 'react';
import { View } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import { caseStatusMeta, caseStatusToStage } from '../constants/workflow';
import AppText from './AppText';
import StatusChip from './StatusChip';
import WorkflowStepper from './WorkflowStepper';

/**
 * Case detail header: debtor + case number + status chip, with the workflow stepper reflecting how far
 * the case has progressed. Pure presentation — pass a normalized case object.
 */
export default function CaseHeader({ item, showStepper = true, style }) {
  const theme = useTheme();
  if (!item) return null;
  const meta = caseStatusMeta(item.status);
  return (
    <View style={style} accessibilityRole="header">
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: theme.spacing.sm }}>
        <View style={{ flex: 1, paddingRight: theme.spacing.sm }}>
          <AppText variant="h2" numberOfLines={1}>{item.debtorName}</AppText>
          <AppText variant="caption" color="textFaint">{item.caseNumber} · {item.bank}</AppText>
        </View>
        <StatusChip label={meta.label} color={meta.color} />
      </View>
      {showStepper ? <WorkflowStepper currentIndex={caseStatusToStage(item.status)} style={{ marginTop: theme.spacing.xs }} /> : null}
    </View>
  );
}
