import { Link } from 'react-router-dom';
import { Card, EmptyState, Loading, Note, Stat, StatusPill, useAsync } from '../components/ui';
import { api } from '../lib/api';
import { dateOnly, kg, relativeTime } from '../lib/format';
import { useAuth } from '../context/AuthContext';

export function Dashboard() {
  const { user } = useAuth();
  const impact = useAsync(() => api.impact(), []);
  const pickups = useAsync(() => api.pickups(undefined, 0, 5), []);
  const scans = useAsync(() => api.scans(0, 3), []);
  const notifications = useAsync(() => api.notifications(0, 3), []);

  const loading = impact.loading || pickups.loading;
  const error = impact.error ?? pickups.error;

  const openPickups = pickups.data?.content.filter((p) => !['RECOVERED', 'CANCELLED'].includes(p.status)) ?? [];
  const co2e = impact.data?.byCategory.reduce((sum, c) => sum + Number(c.estimatedCo2eKgSaved), 0) ?? 0;

  return (
    <>
      <div className="hero" style={{ marginBottom: 20 }}>
        <div className="page-head" style={{ marginBottom: 12 }}>
          <div>
            <h1>
              Hi {user?.email?.split('@')[0]}, ready to close the loop?
            </h1>
            <p className="lede">
              Scan an item to learn how to recycle it, find a verified collection point, or book a pickup — then watch the
              real kilograms add up.
            </p>
          </div>
        </div>
        <div className="btn-row">
          <Link className="btn" to="/scan">
            📷 Scan waste
          </Link>
          <Link className="btn secondary" to="/pickups">
            🚚 Book a pickup
          </Link>
          <Link className="btn ghost" to="/points">
            📍 Find a collection point
          </Link>
        </div>
      </div>

      {error ? <Note tone="error">{error}</Note> : null}
      {loading ? <Loading label="Loading your dashboard…" /> : null}

      <div className="grid cols-4">
        <Stat
          label="Recycled"
          value={kg(impact.data?.totalCollectedKg ?? 0)}
          hint="From completed collections only"
          accent
        />
        <Stat label="Completed pickups" value={impact.data?.completedPickups ?? 0} hint="Recovered by a collector" />
        <Stat label="Scans recorded" value={scans.data?.totalElements ?? 0} hint="AI-assisted and manual" />
        <Stat
          label="Est. CO₂e avoided"
          value={`${co2e.toFixed(1)} kg`}
          hint="Conservative literature estimates"
        />
      </div>

      <div className="grid cols-2" style={{ marginTop: 16 }}>
        <Card
          title="Active pickups"
          action={
            <Link className="btn ghost small" to="/pickups">
              View all
            </Link>
          }
        >
          {pickups.loading ? (
            <Loading />
          ) : openPickups.length === 0 ? (
            <EmptyState icon="🚚" title="No pickups in progress">
              Book a pickup and a verified collector will take it from here.
            </EmptyState>
          ) : (
            <div className="list">
              {openPickups.map((pickup) => (
                <div className="list-item" key={pickup.code}>
                  <div className="grow">
                    <div className="list-title">
                      <Link to={`/pickups/${pickup.code}`}>{pickup.code}</Link>{' '}
                      <span className="muted small">{pickup.category?.name}</span>
                    </div>
                    <div className="list-meta">
                      {kg(pickup.actualQuantityKg ?? pickup.estimatedQuantityKg)} ·{' '}
                      {pickup.pickupDate ? dateOnly(pickup.pickupDate) : ''} · {pickup.timeSlot.toLowerCase()}
                      {pickup.collectorOrganization ? ` · ${pickup.collectorOrganization}` : ''}
                    </div>
                  </div>
                  <StatusPill status={pickup.status} />
                </div>
              ))}
            </div>
          )}
        </Card>

        <Card
          title="Recent activity"
          action={
            <Link className="btn ghost small" to="/notifications">
              All notifications
            </Link>
          }
        >
          {notifications.loading ? (
            <Loading />
          ) : (notifications.data?.content.length ?? 0) === 0 ? (
            <EmptyState icon="🔔" title="Nothing yet">
              Updates about your pickups will appear here.
            </EmptyState>
          ) : (
            <div className="list">
              {notifications.data?.content.map((item) => (
                <div className="list-item" key={item.id}>
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

      <Card
        title="Recent scans"
        className=""
        action={
          <Link className="btn ghost small" to="/scan">
            New scan
          </Link>
        }
      >
        {scans.loading ? (
          <Loading />
        ) : (scans.data?.content.length ?? 0) === 0 ? (
          <EmptyState icon="📷" title="No scans yet">
            Photograph an item and ReLoop will suggest how to recycle it.
          </EmptyState>
        ) : (
          <div className="list">
            {scans.data?.content.map((scan) => (
              <div className="list-item" key={scan.id}>
                {scan.imageUrl ? <img className="thumb" src={scan.imageUrl} alt="" /> : null}
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
    </>
  );
}
