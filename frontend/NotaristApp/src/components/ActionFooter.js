import React from 'react';
import BottomActionBar from './BottomActionBar';

/** Semantic alias of BottomActionBar for screens that read better as an "action footer". */
export default function ActionFooter({ children, style }) {
  return <BottomActionBar style={style}>{children}</BottomActionBar>;
}
