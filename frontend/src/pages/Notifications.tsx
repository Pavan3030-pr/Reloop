import { Button, Card, EmptyState, Loading, Note, useAsync } from '../components/ui';
import { api, ApiError } from '../lib/api';
import { relativeTime } from '../lib/format';
import { useState } from 'react';

export function Notifications() {
  const list = useAsync(() => api.notifications(0, 50), []);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const markRead = async (id: string) => {
    setError(null);
    try {
      await api.markNotificationRead(id);
      list.reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not update this notification.');
    }
  };

  const markAll = async () => {
    setBusy(true);
    setError(null);
    try {
      await api.markAllNotificationsRead();
      list.reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not update your notifications.');
    } finally {
      setBusy(false);
    }
  };

  const unread = list.data?.content.filter((item) => !item.read).length ?? 0;

  return (
    <Card
      title="Notifications"
      action={
        <Button type="button" className="secondary small" onClick={markAll} disabled={busy || unread === 0}>
          {busy ? 'Updating…' : `Mark all read${unread ? ` (${unread})` : ''}`}
        </Button>
      }
    >
      {error ? <Note tone="error">{error}</Note> : null}
      {list.error ? <Note tone="error">{list.error}</Note> : null}
      {list.loading ? (
        <Loading />
      ) : (list.data?.content.length ?? 0) === 0 ? (
        <EmptyState icon="🔔" title="You are all caught up">
          Pickup updates and account messages will appear here.
        </EmptyState>
      ) : (
        <div className="list">
          {list.data?.content.map((item) => (
            <div className="list-item" key={item.id}>
              {!item.read ? <span className="unread-dot" aria-label="Unread" /> : null}
              <div className="grow">
                <div className="list-title">{item.title}</div>
                <div className="list-meta">{item.message}</div>
                <div className="small muted" style={{ marginTop: 4 }}>
                  {item.type.toLowerCase().replace(/_/g, ' ')} · {relativeTime(item.createdAt)}
                </div>
              </div>
              {!item.read ? (
                <Button type="button" className="ghost small" onClick={() => markRead(item.id)}>
                  Mark read
                </Button>
              ) : null}
            </div>
          ))}
        </div>
      )}
    </Card>
  );
}
