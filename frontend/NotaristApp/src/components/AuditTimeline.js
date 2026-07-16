import React from 'react';
import TimelineCard from './TimelineCard';

/**
 * Audit trail timeline. `events` = [{ id, actor, action, at }]. Renders on the shared TimelineCard,
 * with all events marked done (audit entries are historical facts).
 */
export default function AuditTimeline({ events = [], style }) {
  const items = events.map((e) => ({ id: e.id, label: e.action, at: e.at, actor: e.actor, done: true }));
  return <TimelineCard items={items} style={style} />;
}
