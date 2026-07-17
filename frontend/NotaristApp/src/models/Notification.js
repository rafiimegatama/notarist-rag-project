// Notification view model + normaliser. Shaped to match a plausible future backend payload so that
// wiring a real GET /notifications later only means swapping the repository implementation — the
// screen, service and model stay put. NO notification is fabricated here; this only defines shape.

export const NotificationType = {
  SYSTEM: 'SYSTEM',
  DOCUMENT: 'DOCUMENT',
  INGESTION: 'INGESTION',
  SECURITY: 'SECURITY',
};

const TYPE_ICON = {
  SYSTEM: 'ℹ️',
  DOCUMENT: '📄',
  INGESTION: '⚙️',
  SECURITY: '🔒',
};

export function normalizeNotification(raw) {
  if (!raw) return null;
  const type = NotificationType[raw.type] || NotificationType.SYSTEM;
  return {
    id: raw.id != null ? String(raw.id) : String(Math.random()),
    type,
    icon: TYPE_ICON[type] || 'ℹ️',
    title: raw.title || 'Notifikasi',
    body: raw.body || raw.message || '',
    read: !!raw.read,
    createdAt: raw.createdAt || raw.timestamp || null,
  };
}

export function unreadCount(list) {
  if (!Array.isArray(list)) return 0;
  return list.reduce((n, item) => (item && !item.read ? n + 1 : n), 0);
}
