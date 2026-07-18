import React from 'react';
import { View } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import { caseStatusMeta, caseStatusToStage } from '../constants/workflow';
import AppText from './AppText';
import StatusChip from './StatusChip';
import WorkflowStepper from './WorkflowStepper';

/**
 * Case detail header: case number + supporting detail + status chip, with the workflow stepper
 * reflecting how far the case has progressed. Pure presentation — pass a normalized case object.
 *
 * Leads with `caseNumber` for the same reason CaseCard does: it is the only identifying field the
 * real CaseResponse carries. See CaseCard's note.
 */
export default function CaseHeader({ item, showStepper = true, style }) {
  const theme = useTheme();
  if (!item) return null;
  const meta = caseStatusMeta(item.status);
  const detail = [item.caseType, item.debtorName, item.nomorAkta].filter(Boolean).join(' · ');
  return (
    <View style={style} accessibilityRole="header">
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: theme.spacing.sm }}>
        <View style={{ flex: 1, paddingRight: theme.spacing.sm }}>
          <AppText variant="h2" numberOfLines={1}>{item.caseNumber ?? '—'}</AppText>
          <AppText variant="caption" color="textFaint" numberOfLines={1}>{detail || '—'}</AppText>
        </View>
        <StatusChip label={meta.label} color={meta.color} />
      </View>
      {showStepper ? <WorkflowStepper currentIndex={caseStatusToStage(item.status)} style={{ marginTop: theme.spacing.xs }} /> : null}
    </View>
  );
}
