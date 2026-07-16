// searchApi — dedicated document search, distinct from the Assistant. Backed by the REAL POST /search
// endpoint (returns grounded citations). Structured vs Semantic differ in how the query + filters are
// sent; recent/saved searches are persisted locally (no backend needed for those).
import client from './client';
import { FEATURES } from '../constants/config';
import { requireEndpoint } from './_support';
import { unwrapOrThrow } from './envelope';
import { normalizeSearchResult } from '../models/Search';
import { getItem, setItem } from '../utils/storage';

const RECENT_KEY = 'search_recent_v1';
const SAVED_KEY = 'search_saved_v1';
const MAX_RECENT = 12;

// POST /search -> SearchResponse { queryId, intent, normalizedQuery, citations[], groundingLevel, ... }
// mode: 'semantic' (natural-language, intent auto-detected) | 'structured' (explicit doc-type/level filters).
export async function runSearch({ query, mode = 'semantic', documentTypeFilter = null, maxClassificationLevel = null, maxResults = 10 }) {
  // Sprint 5, Task 10: this endpoint was ungated — it lived in the `LIVE` block, which nothing read.
  // Search has no honest mock: a result is a ranked answer to THIS query, and inventing citations
  // for a legal search would be fabricating evidence. Flag off therefore means "unavailable", loudly.
  requireEndpoint(FEATURES.searchEndpoint, 'search');

  const payload = {
    rawQuery: query,
    documentTypeFilter,
    maxClassificationLevel,
    maxResults,
    // Structured mode pins intent to a literal lookup; semantic lets the backend classify it.
    intentOverride: mode === 'structured' ? 'LOOKUP' : null,
  };
  const response = await client.post('/search', payload);
  // Was a hand-rolled `status !== 'SUCCESS'` check that threw a bare Error: no `kind`, so <ErrorState>
  // could not classify it and the retry layer could not reason about it. unwrapOrThrow routes an
  // error envelope through the Sprint-4 ApiError path like every other failure (Sprint 5, Task 1).
  const data = unwrapOrThrow(response, null);
  await pushRecent(query);
  return normalizeSearchResult(data, mode);
}

// --- Recent searches (local) ---
export async function getRecentSearches() {
  const raw = await getItem(RECENT_KEY);
  try { return raw ? JSON.parse(raw) : []; } catch (_) { return []; }
}

async function pushRecent(query) {
  const q = (query || '').trim();
  if (!q) return;
  const list = await getRecentSearches();
  const next = [q, ...list.filter((x) => x.toLowerCase() !== q.toLowerCase())].slice(0, MAX_RECENT);
  await setItem(RECENT_KEY, JSON.stringify(next));
}

export async function clearRecentSearches() {
  await setItem(RECENT_KEY, JSON.stringify([]));
}

// --- Saved searches (local) ---
export async function getSavedSearches() {
  const raw = await getItem(SAVED_KEY);
  try { return raw ? JSON.parse(raw) : []; } catch (_) { return []; }
}

export async function saveSearch({ query, mode }) {
  const list = await getSavedSearches();
  const entry = { id: `sv-${Date.now()}`, query, mode, savedAt: new Date().toISOString() };
  const next = [entry, ...list.filter((s) => !(s.query === query && s.mode === mode))];
  await setItem(SAVED_KEY, JSON.stringify(next));
  return next;
}

export async function removeSavedSearch(id) {
  const list = await getSavedSearches();
  const next = list.filter((s) => s.id !== id);
  await setItem(SAVED_KEY, JSON.stringify(next));
  return next;
}
