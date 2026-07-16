// SearchService — dedicated document search (NOT the Assistant). The search endpoint is REAL, so
// this service is never "mock"; recent/saved are persisted locally. Delegates to api/search.
import { runSearch, getRecentSearches, getSavedSearches, saveSearch, removeSavedSearch, clearRecentSearches } from '../api/search';

export const SearchService = {
  usingMock: false, // POST /search is live
  run: (params) => runSearch(params),
  getRecent: () => getRecentSearches(),
  getSaved: () => getSavedSearches(),
  save: (entry) => saveSearch(entry),
  removeSaved: (id) => removeSavedSearch(id),
  clearRecent: () => clearRecentSearches(),
};
