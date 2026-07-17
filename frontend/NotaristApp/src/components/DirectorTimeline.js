import React from 'react';
import { useTheme } from '../context/ThemeContext';
import TimelineCard from './TimelineCard';
import StatusChip from './StatusChip';

/**
 * "Timeline Direksi" — the board/authority approval trail. Maps authority entries
 * ({ role, name, decision, at }) onto the shared TimelineCard, with a decision chip on each row.
 */
export default function DirectorTimeline({ entries = [], style }) {
  const theme = useTheme();
  const items = entries.map((a) => ({
    id: a.id,
    label: `${a.role} — ${a.name}`,
    at: a.at,
    done: a.decision === 'APPROVED',
  }));
  const decisionOf = (id) => entries.find((e) => e.id === id)?.decision;
  return (
    <TimelineCard
      items={items}
      style={style}
      renderMeta={(it) => {
        const d = decisionOf(it.id);
        const color = d === 'APPROVED' ? 'success' : d === 'REJECTED' ? 'danger' : 'textFaint';
        const label = d === 'APPROVED' ? 'Disetujui' : d === 'REJECTED' ? 'Ditolak' : 'Menunggu';
        return <StatusChip label={label} color={color} size="sm" />;
      }}
    />
  );
}
