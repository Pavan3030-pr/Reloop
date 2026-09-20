import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Button, Card, EmptyState, Field, Loading, Note, Spinner, Stat, StatusPill, useAsync } from '../components/ui';
import { api, ApiError } from '../lib/api';
import { dateOnly, kg, relativeTime, toLocalDateTimeInput } from '../lib/format';
import type { Pickup } from '../lib/types';

export function CollectorWorkspace() {
  const dashboard = useAsync(() => api.collectorDashboard(), []);
  const [scope, setScope] = useState<'available' | 'mine'>('available');
  const jobs = useAsync(() => api.collectorPickups(scope, 0, 50), [scope]);

  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [busyCode, setBusyCode] = useState<string | null>(null);
  const [scheduling, setScheduling] = useState<Pickup | null>(null);
  const [scheduledAt, setScheduledAt] = useState(toLocalDateTimeInput(new Date(Date.now() + 3600_000)));
  const [collecting, setCollecting] = useState<Pickup | null>(null);
  const [actualQuantityKg, setActualQuantityKg] = useState('');
  const [collectNotes, setCollectNotes] = useState('');

  const refresh = () => {
    dashboard.reload();
    jobs.reload();
  };

  const run = async (code: string, action: () => Promise<unknown>, successMessage: string) => {
    setBusyCode(code);
    setError(null);
    setMessage(null);
    try {
      await action();
      setMessage(successMessage);
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'The action could not be completed.');
    } finally {
      setBusyCode(null);
    }
  };

  const forbidden = dashboard.error !== null && (dashboard.data === null || jobs.error !== null);

  if (dashboard.loading) return <Loading label="Loading collector workspace…" />;

  if (forbidden || (jobs.error && jobs.error.toLowerCase().includes('verified'))) {
    return (
      <Note tone="warning" icon="🔒">
        Collector actions require an approved application. {dashboard.error ?? jobs.error}{' '}
        <Link to="/profile">Check your application status</Link>.
      </Note>
    );
  }

  const stats = dashboard.data;

  const renderJob = (pickup: Pickup, mode: 'available' | 'mine') => {
    const busy = busyCode === pickup.code;
    return (
      <Card key={pickup.code} className="tight">
        <div className="card-head">
          <h3 style={{ margin: 0 }}>
            <Link to={`/pickups/${pickup.code}`}>{pickup.code}</Link>
          </h3>
          <div className="spacer" />
          <StatusPill status={pickup.status} />
        </div>

        <div className="list-meta">
          {pickup.category?.name} · estimated {kg(pickup.estimatedQuantityKg)}
          {pickup.actualQuantityKg !== null ? ` · actual ${kg(pickup.actualQuantityKg)}` : ''}
        </div>
        <div className="list-meta">
          {pickup.requesterName ?? 'Resident'} · {pickup.address}, {pickup.city} {pickup.pincode ?? ''}
        </div>
        <div className="list-meta">
          {dateOnly(pickup.pickupDate)} · {pickup.timeSlot.toLowerCase()} · requested {relativeTime(pickup.createdAt)}
        </div>
        {pickup.photoUrl ? (
          <div style={{ marginTop: 8 }}>
            <img className="thumb" src={pickup.photoUrl} alt="Waste photo" />
          </div>
        ) : null}
        {pickup.notes ? <div className="small muted" style={{ marginTop: 6 }}>{pickup.notes}</div> : null}

        <div className="btn-row" style={{ marginTop: 12 }}>
          {mode === 'available' ? (
            <Button
              type="button"
              className="small"
              disabled={busy}
              onClick={() => run(pickup.code, () => api.acceptPickup(pickup.code), `You accepted ${pickup.code}.`)}
            >
              {busy ? <Spinner onPrimary /> : null}
              Accept job
            </Button>
          ) : (
            <>
              {pickup.status === 'ACCEPTED' ? (
                <Button
                  type="button"
                  className="secondary small"
                  disabled={busy}
                  onClick={() => {
                    setScheduling(pickup);
                    setScheduledAt(toLocalDateTimeInput(new Date(Date.now() + 3600_000)));
                  }}
                >
                  🗓️ Schedule
                </Button>
              ) : null}
              {pickup.status === 'ACCEPTED' || pickup.status === 'SCHEDULED' ? (
                <Button
                  type="button"
                  className="small"
                  disabled={busy}
                  onClick={() => {
                    setCollecting(pickup);
                    setActualQuantityKg(String(pickup.estimatedQuantityKg));
                    setCollectNotes('');
                  }}
                >
                  ⚖️ Record collection
                </Button>
              ) : null}
              {pickup.status === 'PICKED_UP' ? (
                <Button
                  type="button"
                  className="secondary small"
                  disabled={busy}
                  onClick={() =>
                    run(pickup.code, () => api.updatePickupStatus(pickup.code, 'PROCESSING'), `${pickup.code} moved to processing.`)
                  }
                >
                  Move to processing
                </Button>
              ) : null}
              {pickup.status === 'PROCESSING' ? (
                <Button
                  type="button"
                  className="small"
                  disabled={busy}
                  onClick={() =>
                    run(pickup.code, () => api.updatePickupStatus(pickup.code, 'RECOVERED'), `${pickup.code} marked recovered.`)
                  }
                >
                  Mark recovered
                </Button>
              ) : null}
              {pickup.status === 'ACCEPTED' || pickup.status === 'SCHEDULED' ? (
                <Button
                  type="button"
                  className="ghost small"
                  disabled={busy}
                  onClick={() => run(pickup.code, () => api.releasePickup(pickup.code), `${pickup.code} released back to the pool.`)}
                >
                  Release
                </Button>
              ) : null}
            </>
          )}
        </div>

        {scheduling?.code === pickup.code ? (
          <form
            style={{ marginTop: 12 }}
            onSubmit={(event) => {
              event.preventDefault();
              run(
                pickup.code,
                () => api.schedulePickup(pickup.code, new Date(scheduledAt).toISOString()),
                `${pickup.code} scheduled.`,
              ).then(() => setScheduling(null));
            }}
          >
            <Field label="Visit time" htmlFor={`sched-${pickup.code}`} hint="Must be in the future">
              <input
                id={`sched-${pickup.code}`}
                type="datetime-local"
                value={scheduledAt}
                onChange={(e) => setScheduledAt(e.target.value)}
                required
              />
            </Field>
            <div className="btn-row">
              <Button type="submit" className="small" disabled={busy}>
                Confirm schedule
              </Button>
              <Button type="button" className="ghost small" onClick={() => setScheduling(null)}>
                Cancel
              </Button>
            </div>
          </form>
        ) : null}

        {collecting?.code === pickup.code ? (
          <form
            style={{ marginTop: 12 }}
            onSubmit={(event) => {
              event.preventDefault();
              run(
                pickup.code,
                () => api.collectPickup(pickup.code, Number(actualQuantityKg), collectNotes || undefined),
                `Collected ${actualQuantityKg} kg for ${pickup.code}.`,
              ).then(() => setCollecting(null));
            }}
          >
            <div className="inline-fields">
              <Field label="Actual weight (kg)" htmlFor={`kg-${pickup.code}`} hint="Weigh the material on site">
                <input
                  id={`kg-${pickup.code}`}
                  type="number"
                  min={0.01}
                  step={0.01}
                  required
                  value={actualQuantityKg}
                  onChange={(e) => setActualQuantityKg(e.target.value)}
                />
              </Field>
              <Field label="Notes" htmlFor={`notes-${pickup.code}`}>
                <input id={`notes-${pickup.code}`} value={collectNotes} onChange={(e) => setCollectNotes(e.target.value)} placeholder="Bags, condition…" />
              </Field>
            </div>
            <div className="btn-row">
              <Button type="submit" className="small" disabled={busy}>
                Confirm collection
              </Button>
              <Button type="button" className="ghost small" onClick={() => setCollecting(null)}>
                Cancel
              </Button>
            </div>
          </form>
        ) : null}
      </Card>
    );
  };

  return (
    <>
      <div className="grid cols-3" style={{ marginBottom: 16 }}>
        <Stat label="Available requests" value={stats?.availableRequests ?? 0} hint="Waiting for a collector" accent />
        <Stat label="Active jobs" value={stats?.activeJobs ?? 0} hint="Accepted or scheduled" />
        <Stat label="Completed" value={stats?.completedJobs ?? 0} hint={`${stats?.totalCollections ?? 0} recorded collections`} />
      </div>
      <div className="grid cols-3" style={{ marginBottom: 16 }}>
        <Stat label="Collected today" value={stats?.todayPickups ?? 0} hint="Jobs touched today" />
        <Stat label="Total weight collected" value={kg(stats?.totalKgCollected ?? 0)} hint="Actual weighed weight" />
        <Stat label="Organisation" value={<span style={{ fontSize: '1.1rem' }}>Verified</span>} hint="Approved by an administrator" />
      </div>

      {error ? <Note tone="error">{error}</Note> : null}
      {message ? <Note tone="success">{message}</Note> : null}

      <div className="tabs">
        <button type="button" className={`tab${scope === 'available' ? ' active' : ''}`} onClick={() => setScope('available')}>
          Available jobs
        </button>
        <button type="button" className={`tab${scope === 'mine' ? ' active' : ''}`} onClick={() => setScope('mine')}>
          My jobs
        </button>
        <div className="spacer" />
        <Button type="button" className="ghost small" onClick={refresh}>
          Refresh
        </Button>
      </div>

      {jobs.error ? <Note tone="error">{jobs.error}</Note> : null}
      {jobs.loading ? (
        <Loading label="Loading jobs…" />
      ) : (jobs.data?.content.length ?? 0) === 0 ? (
        <EmptyState icon={scope === 'available' ? '📭' : '🧰'} title={scope === 'available' ? 'No open requests right now' : 'You have no assigned jobs'}>
          {scope === 'available'
            ? 'New resident requests in any city appear here as soon as they are created.'
            : 'Accept a request from the available list to start collecting.'}
        </EmptyState>
      ) : (
        <div className="grid cols-2">{jobs.data?.content.map((pickup) => renderJob(pickup, scope))}</div>
      )}
    </>
  );
}
