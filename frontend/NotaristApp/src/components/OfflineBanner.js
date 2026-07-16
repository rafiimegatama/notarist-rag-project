import React from 'react';
import Banner from './Banner';

/** Standard offline notice. Render when a service call failed with no network response. */
export default function OfflineBanner({ message = 'Anda sedang offline. Menampilkan data terakhir yang tersimpan.', style }) {
  return <Banner variant="danger" message={message} style={style} />;
}
