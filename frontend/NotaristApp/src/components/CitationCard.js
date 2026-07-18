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

/** A real, finite score — as opposed to absent, which must never render as a number. */
const hasScore = (c) => typeof c.relevanceScore === 'number' && Number.isFinite(c.relevanceScore);

/**
 * A search/RAG citation with an expandable preview. Tapping animates the text open/closed. Pure props:
 * a citation ({ sourceType, relevanceScore, citationText, retrievalReason }) + its index.
 *
 * Those prop names are CitationResponse's own component names, and this component always had them
 * right. Until Sprint 6 models/Search.js emitted a different vocabulary (score/snippet/...), so every
 * one of these reads was undefined against the live endpoint — see normalizeCitation's note.
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
          <AppText variant="label" color="primary">[{index + 1}]{citation.sourceType ? ` ${citation.sourceType}` : ''}</AppText>
          {/* "skor —", not "skor 0.00". `?? 0` turned an ABSENT score into a fabricated measurement:
              a chip reading "skor 0.00", styled identically to a real one, asserting that a source
              backing a legal answer has zero relevance. Absent and zero are opposite claims about
              evidence, and only one of them was ever true here. */}
          <StatusChip
            label={hasScore(citation) ? `skor ${citation.relevanceScore.toFixed(2)}` : 'skor —'}
            color={hasScore(citation) ? 'info' : 'textFaint'}
            size="sm"
          />
        </View>
        <AppText variant="bodySm" numberOfLines={open ? undefined : 2} style={{ marginTop: theme.spacing.xs }}>
          {/* A citation with no text is not a citation. Say so rather than rendering an empty card
              that looks like a source the reader simply cannot be bothered to expand. */}
          {citation.citationText ?? 'Teks kutipan tidak tersedia.'}
        </AppText>
        <AppText variant="micro" color="textFaint" style={{ marginTop: theme.spacing.sm }}>
          {open ? '▲ Tutup' : '▼ Lihat kutipan lengkap'}{citation.retrievalReason ? ` · ${citation.retrievalReason}` : ''}
        </AppText>
      </Card>
    </TouchableOpacity>
  );
}
