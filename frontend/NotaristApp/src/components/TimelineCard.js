import React from 'react';
import { View } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import { formatDateTime } from '../utils/format';
import AppText from './AppText';

/**
 * Vertical event timeline. `items` = [{ id, label, at, actor, done }]. Completed events show a
 * filled dot, the current/pending one an outlined dot. Used by Case Detail (workflow timeline) and
 * OCR Review (Timeline Direksi / authority trail — pass `renderMeta` for custom right-side content).
 */
export default function TimelineCard({ items = [], renderMeta, style }) {
  const theme = useTheme();
  return (
    <View
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
      {items.length === 0 ? (
        <AppText variant="bodySm" color="textFaint">Belum ada aktivitas.</AppText>
      ) : (
        items.map((it, i) => {
          const last = i === items.length - 1;
          const dotColor = it.done ? theme.colors.success : theme.colors.primary;
          return (
            <View key={it.id ?? i} style={{ flexDirection: 'row' }}>
              {/* rail: dot + connector */}
              <View style={{ alignItems: 'center', width: 20 }}>
                <View
                  style={{
                    width: 12, height: 12, borderRadius: 6,
                    backgroundColor: it.done ? dotColor : theme.colors.surface,
                    borderWidth: 2, borderColor: dotColor,
                  }}
                />
                {!last ? <View style={{ flex: 1, width: 2, backgroundColor: theme.colors.border, marginVertical: 2 }} /> : null}
              </View>
              {/* content */}
              <View style={{ flex: 1, paddingBottom: last ? 0 : theme.spacing.lg, paddingLeft: theme.spacing.md }}>
                <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                  <AppText variant="bodySm" style={{ flex: 1 }}>{it.label}</AppText>
                  {renderMeta ? renderMeta(it) : null}
                </View>
                <AppText variant="micro" color="textFaint" style={{ marginTop: 2 }}>
                  {[it.actor, it.at ? formatDateTime(it.at) : null].filter(Boolean).join(' · ') || '—'}
                </AppText>
              </View>
            </View>
          );
        })
      )}
    </View>
  );
}
