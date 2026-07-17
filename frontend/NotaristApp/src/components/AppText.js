import React from 'react';
import { Text } from 'react-native';
import { useTheme } from '../context/ThemeContext';

/**
 * Themed text primitive. `variant` maps to a size+weight; `color` maps to a theme color key.
 * Keeps typography consistent and avoids scattering fontSize/color literals across screens.
 */
const VARIANTS = {
  display: { size: 'display', weight: 'bold' },
  h1: { size: 'h1', weight: 'bold' },
  h2: { size: 'h2', weight: 'bold' },
  h3: { size: 'h3', weight: 'semibold' },
  body: { size: 'body', weight: 'regular' },
  bodyStrong: { size: 'body', weight: 'semibold' },
  bodySm: { size: 'bodySm', weight: 'regular' },
  caption: { size: 'caption', weight: 'regular' },
  micro: { size: 'micro', weight: 'regular' },
  label: { size: 'caption', weight: 'semibold' },
};

export default function AppText({
  children,
  variant = 'body',
  color = 'text',
  style,
  numberOfLines,
  align,
  ...rest
}) {
  const theme = useTheme();
  const v = VARIANTS[variant] || VARIANTS.body;
  return (
    <Text
      numberOfLines={numberOfLines}
      style={[
        {
          color: theme.colors[color] || color,
          fontSize: theme.typography[v.size],
          fontWeight: theme.typography[v.weight],
        },
        align ? { textAlign: align } : null,
        style,
      ]}
      {...rest}
    >
      {children}
    </Text>
  );
}
