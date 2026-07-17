import React from 'react';
import { View } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import { bundleStages } from '../models/Bundle';
import { stepStatusMeta } from '../constants/workflow';
import ProgressIndicator from './ProgressIndicator';
import StatusChip from './StatusChip';

/**
 * The 5-stage bundle status (OCR / Verifikasi / Draft / QC / Approval) as a segmented bar plus a chip
 * legend. Pure props: a normalized bundle. Shared by BundleCard and the Bundle screen so the two never
 * drift. `compact` hides the chip legend (used inside dense cards).
 */
export default function BundleProgress({ bundle, compact = false, style }) {
  const theme = useTheme();
  if (!bundle) return null;
  const stages = bundleStages(bundle);
  return (
    <View style={style}>
      <ProgressIndicator steps={stages} />
      {!compact ? (
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: theme.spacing.xs, marginTop: theme.spacing.md }}>
          {stages.map((s) => {
            const meta = stepStatusMeta(s.status);
            return <StatusChip key={s.key} label={`${s.label}: ${meta.label}`} color={meta.color} size="sm" />;
          })}
        </View>
      ) : null}
    </View>
  );
}
