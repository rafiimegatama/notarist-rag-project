import React from 'react';
import { View } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import AppText from './AppText';
import PipelineProgress from './PipelineProgress';

/**
 * Bundle screen header: bundle name + document count, with the ingestion PipelineProgress.
 * `pipelineIndex` is how far the OCR→…→Completed pipeline has run (default derived from ocrStatus).
 */
export default function BundleHeader({ bundle, documentCount = 0, pipelineIndex, showPipeline = true, style }) {
  const theme = useTheme();
  if (!bundle) return null;
  // Coarse default: DONE OCR implies at least through the OCR stage; refine when the pipeline API ships.
  const idx = pipelineIndex ?? (bundle.ocrStatus === 'DONE' ? 5 : bundle.ocrStatus === 'IN_PROGRESS' ? 1 : 0);
  return (
    <View style={style} accessibilityRole="header">
      <AppText variant="h2" numberOfLines={2}>{bundle.name}</AppText>
      <AppText variant="caption" color="textFaint" style={{ marginTop: 2 }}>{documentCount} dokumen masuk</AppText>
      {showPipeline ? <PipelineProgress currentIndex={idx} style={{ marginTop: theme.spacing.lg }} /> : null}
    </View>
  );
}
