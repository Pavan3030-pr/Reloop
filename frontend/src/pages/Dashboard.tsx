import { Link } from 'react-router-dom';
import { Card, EmptyState, Loading, Note, PageHead, StatusPill, useAsync } from '../components/ui';
import { Icon } from '../components/icons';
import { api } from '../lib/api';
import { PICKUP_STAGES, dateOnly, kg, relativeTime, statusLabel } from '../lib/format';
import { useAuth } from '../context/AuthContext';

function greeting(): string {
  const hour = new Date().getHours();
  if (hour < 12) return 'Good morning';
  if (hour < 18) return 'Good afternoon';
  return 'Good evening';
}

export function Dashboard() {
  const { user } = useAuth();
  const profile = useAsync(() => api.profile(), []);
  const impact = useAsync(() => api.impact(), []);
  const pickups = useAsync(() => api.pickups(undefined, 0, 5), []);
  const scans = useAsync(() => api.scans(0, 3), []);
  const notifications = useAsync(() => api.notifications(0, 4), []);

  const error = profile.error ?? impact.error ?? pickups.error;
  const firstName = (profile.data?.fullName ?? user?.email ?? '').split(/[\s@]/)[0] || 'there';

  const openPickups = pickups.data?.content.filter((p) => !['RECOVERED', 'CANCELLED'].includes(p.status)) ?? [];
  const current = openPickups[0] ?? null;
  const stageIndex = current ? PICKUP_STAGES.findIndex((stage) => stage.key === current.status) : -1;
  const progress = stageIndex >= 0 ? ((stageIndex + 1) / PICKUP_STAGES.length) * 100 : 0;
  const co2e = impact.data?.byCategory.reduce((sum, c) => sum + Number(c.estimatedCo2eKgSaved), 0) ?? 0;

  return (
    <>
      <PageHead
        eyebrow="Your account"
        title={`${greeting()}, ${firstName}.`}
        lede="Ready to give something a better destination? Scan an item to see how it should be recycled, or book a pickup and follow it to recovery."
        actions={
          <>
            <Link className="btn" to="/scan">
              <Icon name="camera" size={17} />
              Scan waste
            </Link>
            <Link className="btn secondary" to="/pickups">
              Book a pickup
            </Link>
          </>
        }
      />

      {error ? <Note tone="error">{error}</Note> : null}
      {profile.loading ? <Loading label="Loading your dashboard…" /> : null}

      <div className="grid cols-2">
        <Card
          title="Current pickup"
          action={
            <Link className="btn ghost small" to="/pickups">
              All pickups
              <Icon name="chevronRight" size={15} />
            </Link>
          }
        >
          {pickups.loading ? (
            <Loading />
          ) : !current ? (
            <EmptyState icon={<Icon name="truck" size={20} />} title="Nothing in transit">
              When you request a pickup, its progress appears here — from the moment a collector accepts it to the
              recorded recovery.
            </EmptyState>
          ) : (
            <div>
              <div className="inline wrap" style={{ gap: 10, marginBottom: 14 }}>
                <Link to={`/pickups/${current.code}`} className="strong">
                  {current.code}
                </Link>
                <StatusPill status={current.status} />
              </div>
              <div className="list-meta" style={{ marginBottom: 12 }}>
                {current.category?.name ?? 'Material'} ·{' '}
                {current.actualQuantityKg !== null
                  ? `${kg(current.actualQuantityKg)} collected`
                  : `${kg(current.estimatedQuantityKg)} estimated`}{' '}
                · {dateOnly(current.pickupDate)} · {current.timeSlot.toLowerCase()}
              </div>
              <div className="bar" aria-hidden="true">
                <span style={{ width: `${progress}%` }} />
              </div>
              <div className="small muted" style={{ marginTop: 10 }}>
                {stageIndex >= 0 ? PICKUP_STAGES[stageIndex].label : statusLabel(current.status)}
                {current.collectorOrganization ? ` · ${current.collectorOrganization}` : ' · awaiting a collector'}
              </div>
              <div className="btn-row" style={{ marginTop: 16 }}>
                <Link className="btn secondary small" to={`/pickups/${current.code}`}>
                  Track this pickup
                </Link>
                {openPickups.length > 1 ? (
                  <span className="small muted">{openPickups.length - 1} more in progress</span>
                ) : null}
              </div>
            </div>
          )}
        </Card>

        <Card
          title="Your record so far"
          action={
            <Link className="btn ghost small" to="/impact">
              Impact
              <Icon name="chevronRight" size={15} />
            </Link>
          }
        >
          {impact.loading ? (
            <Loading />
          ) : (
            <>
              <dl className="readout">
                <div className="readout-row">
                  <dt>Recycled, weighed on site</dt>
                  <dd className="mono">{kg(impact.data?.totalCollectedKg ?? 0)}</dd>
                </div>
                <div className="readout-row">
                  <dt>Completed collections</dt>
                  <dd className="mono">{impact.data?.completedPickups ?? 0}</dd>
                </div>
                <div className="readout-row">
                  <dt>Scans recorded</dt>
                  <dd className="mono">{scans.data?.totalElements ?? 0}</dd>
                </div>
                <div className="readout-row">
                  <dt>
                    Est. CO₂e avoided <span className="pill grey">estimate</span>
                  </dt>
                  <dd className="mono">{co2e.toFixed(1)} kg</dd>
                </div>
              </dl>
              {(impact.data?.totalCollectedKg ?? 0) === 0 ? (
                <p className="caption">
                  Nothing recorded yet — totals only appear once a collector weighs a collection on site.
                </p>
              ) : null}
            </>
          )}
        </Card>
      </div>

      <div className="grid cols-2" style={{ marginTop: 18 }}>
        <Card
          title="Recent scans"
          action={
            <Link className="btn ghost small" to="/scan">
              New scan
            </Link>
          }
        >
          {scans.loading ? (
            <Loading />
          ) : (scans.data?.content.length ?? 0) === 0 ? (
            <EmptyState icon={<Icon name="camera" size={20} />} title="No scans yet">
              Photograph an item and ReLoop will suggest how to recycle it.
            </EmptyState>
          ) : (
            <div className="list">
              {scans.data?.content.map((scan) => (
                <div className="list-item" key={scan.id}>
                  {scan.imageUrl ? <img className="thumb sm" src={scan.imageUrl} alt="" /> : null}
                  <div className="grow">
                    <div className="list-title">{scan.detectedItem ?? scan.category?.name ?? 'Scan'}</div>
                    <div className="list-meta">
                      {scan.category?.name ?? 'Uncategorised'} · {scan.source === 'AI' ? 'AI-assisted' : 'Manual'} ·{' '}
                      {relativeTime(scan.createdAt)}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </Card>

        <Card
          title="Latest updates"
          action={
            <Link className="btn ghost small" to="/notifications">
              All
            </Link>
          }
        >
          {notifications.loading ? (
            <Loading />
          ) : (notifications.data?.content.length ?? 0) === 0 ? (
            <EmptyState icon={<Icon name="bell" size={20} />} title="Nothing new">
              Pickup updates and account messages will appear here.
            </EmptyState>
          ) : (
            <div className="list">
              {notifications.data?.content.map((item) => (
                <div className="list-item" key={item.id}>
                  {!item.read ? <span className="unread-dot" aria-label="Unread" /> : null}
                  <div className="grow">
                    <div className="list-title">{item.title}</div>
                    <div className="list-meta">{item.message}</div>
                  </div>
                  <span className="muted small nowrap">{relativeTime(item.createdAt)}</span>
                </div>
              ))}
            </div>
          )}
        </Card>
      </div>

      {user?.role === 'USER' ? (
        <Card className="" title="Collect with ReLoop">
          <p className="muted small" style={{ marginBottom: 16, maxWidth: '70ch' }}>
            Run waste collection commercially? Apply to become a verified collector, accept pickup requests in your city
            and record what you collect. Applications are reviewed by an administrator.
          </p>
          <Link className="btn secondary small" to="/profile">
            Open profile and apply
            <Icon name="chevronRight" size={15} />
          </Link>
        </Card>
      ) : null}
    </>
  );
}
