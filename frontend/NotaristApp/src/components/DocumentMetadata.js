import React from 'react';
import { View } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import AppText from './AppText';
import Card from './Card';
import Divider from './Divider';

/**
 * Key/value metadata block. `rows` = [{ label, value }]. Used for document/case attribute lists so
 * every "detail" section shares one layout instead of ad-hoc rows per screen.
 */
export default function DocumentMetadata({ rows = [], style }) {
  const theme = useTheme();
  return (
    <Card style={style}>
      {rows.map((r, i) => (
        <View key={r.label}>
          {i > 0 ? <Divider /> : null}
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', paddingVertical: theme.spacing.xs }}>
            <AppText variant="bodySm" color="textFaint">{r.label}</AppText>
            <AppText variant="bodySm" style={{ flex: 1, textAlign: 'right' }} numberOfLines={1}>{r.value ?? '—'}</AppText>
          </View>
        </View>
      ))}
    </Card>
  );
}
