import React from 'react';
import FilterBar from './FilterBar';

const MODES = [
  { value: 'semantic', label: '🧠 Semantic' },
  { value: 'structured', label: '🗂️ Structured' },
];

/** Segmented toggle between semantic and structured search modes. Thin preset over FilterBar. */
export default function SearchModeToggle({ mode, onChange, style }) {
  return <FilterBar options={MODES} selected={mode} onSelect={onChange} style={style} />;
}
