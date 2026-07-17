import React from 'react';
import Banner from './Banner';

/**
 * Honest "sample data" notice, shown whenever a service is serving fixtures because its backend
 * endpoint does not exist yet. `entity` names what is mocked (e.g. "dashboard", "case").
 */
export default function MockBanner({ entity, message, style }) {
  const text = message || `Menampilkan data contoh${entity ? ` — endpoint ${entity} belum tersedia di backend` : ''}.`;
  return <Banner variant="warning" message={text} style={style} />;
}
