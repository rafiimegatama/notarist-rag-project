import React from 'react';
import BottomActionBar from './BottomActionBar';

/**
 * A sticky bottom bar carrying a single, full-width primary action (the common case). For multiple
 * actions use BottomActionBar / ActionFooter directly.
 */
export default function StickyBottomAction({ children, style }) {
  return <BottomActionBar style={style}>{children}</BottomActionBar>;
}
