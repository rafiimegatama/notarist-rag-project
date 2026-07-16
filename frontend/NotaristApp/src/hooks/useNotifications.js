import { useCallback, useEffect, useRef, useState } from 'react';
import { notificationService } from '../services/NotificationService';

/**
 * Encapsulates notification loading + read-state for the screen. Exposes list/unread/loading/error
 * plus reload and mark-read actions. Backed by NotificationService (local, no backend yet).
 */
export default function useNotifications() {
  const [items, setItems] = useState([]);
  const [unread, setUnread] = useState(0);
  const [backendAvailable, setBackendAvailable] = useState(false);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState(null);
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  // A refresh keeps the current list on screen and drives the pull-to-refresh spinner; only the
  // first load (or an explicit retry) shows the skeleton. Otherwise pulling to refresh would blank
  // the list out and replace it with placeholders.
  const load = useCallback(async (mode = 'initial') => {
    if (mode === 'refresh') setRefreshing(true);
    else setLoading(true);
    setError(null);
    try {
      const { items: list, unread: u, backendAvailable: avail } = await notificationService.list();
      if (!mounted.current) return;
      setItems(list);
      setUnread(u);
      setBackendAvailable(avail);
    } catch (err) {
      if (mounted.current) setError(err);
    } finally {
      if (!mounted.current) return;
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  const reload = useCallback(() => load('initial'), [load]);
  const refresh = useCallback(() => load('refresh'), [load]);

  useEffect(() => { load('initial'); }, [load]);

  const markAllRead = useCallback(async () => {
    const list = await notificationService.markAllAsRead();
    if (!mounted.current) return;
    setItems(list);
    setUnread(0);
  }, []);

  const markRead = useCallback(async (id) => {
    const list = await notificationService.markAsRead(id);
    if (!mounted.current) return;
    setItems(list);
    setUnread(list.reduce((n, x) => (x && !x.read ? n + 1 : n), 0));
  }, []);

  return { items, unread, backendAvailable, loading, refreshing, error, reload, refresh, markAllRead, markRead };
}
