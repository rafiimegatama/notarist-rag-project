// Repository contract for notifications + a local (no-backend) implementation.
//
// There is NO notifications endpoint yet. Rather than fake server data, the local repository holds
// an in-memory list that starts EMPTY, so the screen shows a genuine empty state. When a real
// GET /notifications lands, add an ApiNotificationRepository implementing the same shape and inject
// it into NotificationService — nothing else changes.

import { normalizeNotification } from '../models/Notification';

/** The contract every notification source must satisfy. */
export class NotificationRepository {
  // eslint-disable-next-line no-unused-vars
  async fetchAll() { throw new Error('NotificationRepository.fetchAll not implemented'); }
  // eslint-disable-next-line no-unused-vars
  async markRead(id) { throw new Error('NotificationRepository.markRead not implemented'); }
  async markAllRead() { throw new Error('NotificationRepository.markAllRead not implemented'); }
}

/**
 * Local, in-memory repository. Backed by no server: fetchAll() returns whatever is in memory
 * (empty by default). markRead/markAllRead mutate the local copy so the UI stays consistent within
 * a session. Deliberately does not persist and does not invent notifications.
 */
export class LocalNotificationRepository extends NotificationRepository {
  constructor(seed = []) {
    super();
    this._items = seed.map(normalizeNotification).filter(Boolean);
    this.backendAvailable = false;
  }

  async fetchAll() {
    // Simulate async I/O boundary without inventing latency-based behavior.
    return this._items.slice();
  }

  async markRead(id) {
    this._items = this._items.map((n) => (n.id === id ? { ...n, read: true } : n));
    return this._items.slice();
  }

  async markAllRead() {
    this._items = this._items.map((n) => ({ ...n, read: true }));
    return this._items.slice();
  }
}
