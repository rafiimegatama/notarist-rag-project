// Application service the Notification screen talks to. Hides the repository behind a stable API
// and carries the backend-availability flag so the UI can show an honest "not connected" banner
// instead of pretending an empty inbox is a real server response.

import { LocalNotificationRepository } from '../repositories/NotificationRepository';
import { unreadCount } from '../models/Notification';
import { FEATURES } from '../constants/config';

export class NotificationService {
  constructor(repository = new LocalNotificationRepository()) {
    this.repository = repository;
    this.backendAvailable = FEATURES.notificationsEndpoint;
  }

  async list() {
    const items = await this.repository.fetchAll();
    return { items, unread: unreadCount(items), backendAvailable: this.backendAvailable };
  }

  async markAsRead(id) {
    return this.repository.markRead(id);
  }

  async markAllAsRead() {
    return this.repository.markAllRead();
  }
}

// Singleton used by the screen/hook. Swap the injected repository here when a real API exists.
export const notificationService = new NotificationService();
