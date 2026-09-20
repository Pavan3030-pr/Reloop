import { Button, Card, EmptyState, Loading, Note, PageHead, useAsync } from '../components/ui';
import { Icon, type IconName } from '../components/icons';
import { api, ApiError } from '../lib/api';
import { relativeTime } from '../lib/format';
import { useState } from 'react';

const TYPE_ICON: Record<string, IconName> = {
  PICKUP_REQUESTED: 'truck',
  PICKUP_ACCEPTED: 'checkCircle',
  PICKUP_SCHEDULED: 'calendar',
  PICKUP_COLLECTED: 'scale',
  PICKUP_PROCESSING: 'factory',
  PICKUP_RECOVERED: 'recycle',
  PICKUP_CANCELLED: 'close',
  COLLECTOR_APPROVED: 'shield',
  COLLECTOR_REJECTED: 'alert',
  ACCOUNT: 'user',
};

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
    <>
      <PageHead
        eyebrow="Activity"
        title="Notifications"
        lede="Pickup progress and account messages, in the order the platform recorded them."
        actions={
          <Button type="button" className="secondary" onClick={markAll} disabled={busy || unread === 0}>
            {busy ? 'Updating…' : `Mark all read${unread ? ` (${unread})` : ''}`}
          </Button>
        }
      />

      <Card>
        {error ? <Note tone="error">{error}</Note> : null}
        {list.error ? <Note tone="error">{list.error}</Note> : null}
        {list.loading ? (
          <Loading />
        ) : (list.data?.content.length ?? 0) === 0 ? (
          <EmptyState icon={<Icon name="bell" size={20} />} title="You are all caught up">
            Pickup updates and account messages will appear here.
          </EmptyState>
        ) : (
          <div className="list">
            {list.data?.content.map((item) => (
              <div className="list-item" key={item.id}>
                <span className="icon-tile" aria-hidden="true">
                  <Icon name={TYPE_ICON[item.type] ?? 'bell'} size={17} />
                </span>
                <div className="grow">
                  <div className="list-title">
                    {item.title}
                    {!item.read ? <span className="pill green">New</span> : null}
                  </div>
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
    </>
  );
}
