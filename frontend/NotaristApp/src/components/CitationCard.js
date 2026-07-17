import React, { useState } from 'react';
import { View, TouchableOpacity, LayoutAnimation, Platform, UIManager } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import AppText from './AppText';
import Card from './Card';
import StatusChip from './StatusChip';

// Enable LayoutAnimation on Android (no-op on iOS where it's always on).
if (Platform.OS === 'android' && UIManager.setLayoutAnimationEnabledExperimental) {
  UIManager.setLayoutAnimationEnabledExperimental(true);
}

/**
 * A search/RAG citation with an expandable preview. Tapping animates the text open/closed. Pure props:
 * a citation ({ sourceType, relevanceScore, citationText, retrievalReason }) + its index.
 */
export default function CitationCard({ citation, index = 0, style }) {
  const theme = useTheme();
  const [open, setOpen] = useState(false);

  const toggle = () => {
    LayoutAnimation.configureNext(LayoutAnimation.create(theme.durations.fast, LayoutAnimation.Types.easeInEaseOut, LayoutAnimation.Properties.opacity));
    setOpen((o) => !o);
  };

  return (
    <TouchableOpacity activeOpacity={0.9} onPress={toggle} accessibilityRole="button" accessibilityLabel={`Kutipan ${index + 1}, ${open ? 'tutup' : 'buka'}`} style={style}>
      <Card>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
          <AppText variant="label" color="primary">[{index + 1}] {citation.sourceType}</AppText>
          <StatusChip label={`skor ${(citation.relevanceScore ?? 0).toFixed(2)}`} color="info" size="sm" />
        </View>
        <AppText variant="bodySm" numberOfLines={open ? undefined : 2} style={{ marginTop: theme.spacing.xs }}>
          {citation.citationText}
        </AppText>
        <AppText variant="micro" color="textFaint" style={{ marginTop: theme.spacing.sm }}>
          {open ? '▲ Tutup' : '▼ Lihat kutipan lengkap'}{citation.retrievalReason ? ` · ${citation.retrievalReason}` : ''}
        </AppText>
      </Card>
    </TouchableOpacity>
  );
}
