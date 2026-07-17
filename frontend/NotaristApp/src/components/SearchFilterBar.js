import React from 'react';
import { View } from 'react-native';
import { useTheme } from '../context/ThemeContext';
import SearchBar from './SearchBar';
import FilterBar from './FilterBar';

/**
 * Composed search header: a SearchBar over a FilterBar of facets (document type, status, etc.).
 * Controlled — pass `query`/`onChangeQuery`, `filters` (=[{value,label}]) + `selected`/`onSelectFilter`.
 */
export default function SearchFilterBar({
  query, onChangeQuery, onSubmit, placeholder,
  filters = [], selected, onSelectFilter, style,
}) {
  const theme = useTheme();
  return (
    <View style={[{ gap: theme.spacing.md }, style]}>
      <SearchBar value={query} onChangeText={onChangeQuery} onSubmit={onSubmit} placeholder={placeholder} />
      {filters.length ? <FilterBar options={filters} selected={selected} onSelect={onSelectFilter} /> : null}
    </View>
  );
}
