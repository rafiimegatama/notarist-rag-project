import React from 'react';
import { TouchableOpacity, View } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import { formatDate } from '../utils/format';
import AppText from './AppText';
import BundleProgress from './BundleProgress';

/** Bundle row: name, document count, and the shared 5-stage BundleProgress (bar + chip legend). */
export default function BundleCard({ item, onPress, style }) {
  const theme = useTheme();
  return (
    <TouchableOpacity
      onPress={onPress}
      activeOpacity={0.85}
      accessibilityRole="button"
      accessibilityLabel={`Bundle ${item.name}, ${item.documentCount} dokumen`}
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
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
        <AppText variant="bodyStrong" numberOfLines={1} style={{ flex: 1 }}>{item.name}</AppText>
        <AppText variant="micro" color="textFaint">📄 {item.documentCount}</AppText>
      </View>

      <BundleProgress bundle={item} style={{ marginTop: theme.spacing.md }} />

      <AppText variant="micro" color="textFaint" style={{ marginTop: theme.spacing.md }}>
        Diperbarui {formatDate(item.updatedAt)}
      </AppText>
    </TouchableOpacity>
  );
}
