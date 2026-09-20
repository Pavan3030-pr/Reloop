import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Button, Card, EmptyState, Field, Loading, Note, PageHead, Spinner, Stat, StatusPill, useAsync } from '../components/ui';
import { Icon } from '../components/icons';
import { api, ApiError } from '../lib/api';
import { PICKUP_STAGES, dateOnly, kg, relativeTime, toLocalDateTimeInput } from '../lib/format';
import type { Pickup } from '../lib/types';

function stageProgress(status: Pickup['status']): number {
  const index = PICKUP_STAGES.findIndex((stage) => stage.key === status);
  if (index < 0) return 0;
  return ((index + 1) / PICKUP_STAGES.length) * 100;
}

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

  if (dashboard.loading) return <Loading label="Loading collector workspace…" />;

  if (dashboard.error && (!jobs.data || jobs.error)) {
    return (
      <Note tone="warning">
        <div>
          <strong>Collector actions need an approved application.</strong>
          <div className="small" style={{ marginTop: 4 }}>
            {dashboard.error ?? jobs.error} <Link to="/profile">Check your application status →</Link>
          </div>
        </div>
      </Note>
    );
  }

  const stats = dashboard.data;

  const renderJob = (pickup: Pickup, mode: 'available' | 'mine') => {
    const busy = busyCode === pickup.code;
    const active = ['ACCEPTED', 'SCHEDULED', 'PICKED_UP', 'PROCESSING'].includes(pickup.status);

    return (
      <Card key={pickup.code} className="lift">
        <div className="card-head">
          <span className="icon-tile" aria-hidden="true">
            <Icon name="truck" size={17} />
          </span>
          <h3 style={{ margin: 0 }}>
            <Link to={`/pickups/${pickup.code}`} className="mono">
              {pickup.code}
            </Link>
          </h3>
          <span className="spacer" />
          <StatusPill status={pickup.status} />
        </div>

        <dl className="readout" style={{ marginBottom: 14 }}>
          <div className="readout-row">
            <dt>Material</dt>
            <dd>
              {pickup.category?.name ?? '—'}
              {pickup.actualQuantityKg !== null ? (
                <span className="muted small"> · actual {kg(pickup.actualQuantityKg)}</span>
              ) : (
                <span className="muted small"> · est. {kg(pickup.estimatedQuantityKg)}</span>
              )}
            </dd>
          </div>
          <div className="readout-row">
            <dt>Requested by</dt>
            <dd>{pickup.requesterName ?? 'Resident'}</dd>
          </div>
          <div className="readout-row">
            <dt>Address</dt>
            <dd>
              {pickup.address}, {pickup.city} {pickup.pincode ?? ''}
            </dd>
          </div>
          <div className="readout-row">
            <dt>Window</dt>
            <dd>
              {dateOnly(pickup.pickupDate)} · {pickup.timeSlot.toLowerCase()}
              <div className="list-meta">requested {relativeTime(pickup.createdAt)}</div>
            </dd>
          </div>
          {pickup.scheduledAt ? (
            <div className="readout-row">
              <dt>Scheduled</dt>
              <dd>{new Date(pickup.scheduledAt).toLocaleString()}</dd>
            </div>
          ) : null}
        </dl>

        {pickup.photoUrl ? (
          <div style={{ marginBottom: 12 }}>
            <img className="thumb" src={pickup.photoUrl} alt="Waste photo supplied by the resident" />
          </div>
        ) : null}

        {pickup.notes ? <div className="small muted" style={{ marginBottom: 12 }}>“{pickup.notes}”</div> : null}

        {active ? (
          <>
            <div className="bar" aria-hidden="true">
              <span style={{ width: `${stageProgress(pickup.status)}%` }} />
            </div>
            <div className="small muted" style={{ marginTop: 8 }}>
              {PICKUP_STAGES.find((stage) => stage.key === pickup.status)?.label ?? pickup.status}
            </div>
          </>
        ) : null}

        <div className="btn-row" style={{ marginTop: 16 }}>
          {mode === 'available' ? (
            <Button
              type="button"
              className="small"
              disabled={busy}
              onClick={() => run(pickup.code, () => api.acceptPickup(pickup.code), `You accepted ${pickup.code}.`)}
            >
              {busy ? <Spinner onPrimary /> : <Icon name="check" size={15} />}
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
                  <Icon name="calendar" size={15} />
                  Schedule
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
                  <Icon name="scale" size={15} />
                  Record collection
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
                  <Icon name="factory" size={15} />
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
                  <Icon name="recycle" size={15} />
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
            className="panel soft"
            style={{ marginTop: 16, padding: 16 }}
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
            className="panel soft"
            style={{ marginTop: 16, padding: 16 }}
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
                <input
                  id={`notes-${pickup.code}`}
                  value={collectNotes}
                  onChange={(e) => setCollectNotes(e.target.value)}
                  placeholder="Bags, condition…"
                />
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
      <PageHead
        eyebrow="Collector"
        title="Collector workspace"
        lede="Accept jobs in the materials you handle, schedule the visit, weigh the material on site and take it through to recovery."
        actions={
          <Button type="button" className="secondary" onClick={refresh}>
            <Icon name="refresh" size={16} />
            Refresh
          </Button>
        }
      />

      <div className="grid cols-3" style={{ marginBottom: 18 }}>
        <Stat label="Available requests" value={stats?.availableRequests ?? 0} accent icon="truck" hint="Waiting for a collector" />
        <Stat label="Active jobs" value={stats?.activeJobs ?? 0} icon="box" hint="Accepted, scheduled or in processing" />
        <Stat
          label="Completed"
          value={stats?.completedJobs ?? 0}
          icon="checkCircle"
          hint={`${stats?.totalCollections ?? 0} recorded collections`}
        />
      </div>
      <div className="grid cols-3" style={{ marginBottom: 20 }}>
        <Stat label="Jobs touched today" value={stats?.todayPickups ?? 0} icon="calendar" hint="Across every status" />
        <Stat label="Weight collected" value={kg(stats?.totalKgCollected ?? 0)} icon="scale" hint="Actual weighed weight" />
        <Stat label="Organisation" value="Verified" icon="shield" hint="Approved by an administrator" />
      </div>

      {error ? <Note tone="error">{error}</Note> : null}
      {message ? <Note tone="success">{message}</Note> : null}

      <div className="tabs" role="tablist" aria-label="Job scope">
        <button
          type="button"
          role="tab"
          aria-selected={scope === 'available'}
          className={`tab${scope === 'available' ? ' active' : ''}`}
          onClick={() => setScope('available')}
        >
          Available jobs
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={scope === 'mine'}
          className={`tab${scope === 'mine' ? ' active' : ''}`}
          onClick={() => setScope('mine')}
        >
          My jobs
        </button>
      </div>

      {jobs.error ? <Note tone="error">{jobs.error}</Note> : null}
      {jobs.loading ? (
        <Loading label="Loading jobs…" />
      ) : (jobs.data?.content.length ?? 0) === 0 ? (
        <EmptyState
          icon={<Icon name={scope === 'available' ? 'truck' : 'box'} size={20} />}
          title={scope === 'available' ? 'No open requests right now' : 'You have no assigned jobs'}
        >
          {scope === 'available'
            ? 'New resident requests appear here as soon as they are created.'
            : 'Accept a request from the available list to start collecting.'}
        </EmptyState>
      ) : (
        <div className="grid cols-2" style={{ alignItems: 'start' }}>
          {jobs.data?.content.map((pickup) => renderJob(pickup, scope))}
        </div>
      )}
    </>
  );
}
