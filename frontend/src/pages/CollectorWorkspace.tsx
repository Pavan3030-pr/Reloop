import { useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { Button, Card, EmptyState, Field, Loading, Note, PageHead, Spinner, StatusPill, useAsync } from '../components/ui';
import { Icon } from '../components/icons';
import { api, ApiError } from '../lib/api';
import { dateOnly, kg, relativeTime, stageProgress, toLocalDateTimeInput } from '../lib/format';
import { getBrowserLocation, type Coordinates } from '../lib/geo';
import type { Pickup, PickupSummary, TimeSlot } from '../lib/types';

/** Visit windows run earliest to latest, so jobs can be worked in the order they are promised. */
const SLOT_ORDER: Record<TimeSlot, number> = { MORNING: 0, AFTERNOON: 1, EVENING: 2 };

/** A job is finished once it has left the operational pipeline. */
const TERMINAL: string[] = ['RECOVERED', 'RECYCLED', 'CANCELLED'];

function localToday(): string {
  const now = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

function byVisitOrder(a: Pickup, b: Pickup): number {
  if (a.pickupDate !== b.pickupDate) return a.pickupDate < b.pickupDate ? -1 : 1;
  return (SLOT_ORDER[a.timeSlot] ?? 99) - (SLOT_ORDER[b.timeSlot] ?? 99);
}

/** What this collector has to do next, stated in the workspace rather than implied by a button. */
function nextAction(status: string): string | null {
  switch (status) {
    case 'ACCEPTED':
      return 'Next: schedule the visit, then record the weight on site.';
    case 'SCHEDULED':
      return 'Next: record the weight when you load the material.';
    case 'PICKED_UP':
      return 'Next: move it to processing once it reaches your facility.';
    case 'PROCESSING':
      return 'Next: close the loop by marking the material recovered or recycled.';
    default:
      return null;
  }
}

/** Radius choices offered in the pool filters. Empty means no distance limit. */
const RADIUS_OPTIONS = ['10', '25', '50', '100'];

export function CollectorWorkspace() {
  const dashboard = useAsync(() => api.collectorDashboard(), []);
  const partner = useAsync(() => api.myCollectorApplication(), []);
  // The filter options are the cities and materials that are actually waiting, so no choice can
  // lead to an empty result the collector could have predicted. Category names come from the
  // public catalogue so the material filter reads as a material, not a database code.
  const filters = useAsync(() => api.collectorPoolFilters(), []);
  const categories = useAsync(() => api.categories(), []);
  // Reading the pool with coordinates adds the coarse distance and lets the list sort nearest-first.
  const [coords, setCoords] = useState<Coordinates | null>(null);
  const [city, setCity] = useState('');
  const [material, setMaterial] = useState('');
  const [radiusKm, setRadiusKm] = useState('');
  // Filtering is server-side, so the client only ever downloads the requests it can serve rather
  // than pulling every city's pool and hiding rows in React.
  const available = useAsync(
    () =>
      api.availablePickups({
        lat: coords?.lat,
        lng: coords?.lng,
        city: city || undefined,
        material: material || undefined,
        maxDistanceKm: coords && radiusKm ? Number(radiusKm) : undefined,
        size: 50,
      }),
    [coords, city, material, radiusKm],
  );
  const mine = useAsync(() => api.myPickups(0, 50), []);

  const [locating, setLocating] = useState(false);
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
    filters.reload();
    available.reload();
    mine.reload();
  };

  const materialName = (code: string) =>
    categories.data?.find((category) => category.code === code)?.name ?? code;

  const filtersActive = city !== '' || material !== '' || radiusKm !== '';

  const clearFilters = () => {
    setCity('');
    setMaterial('');
    setRadiusKm('');
  };

  /**
   * A radius is only meaningful against a real position, so choosing one asks for the browser
   * location once. Declining leaves the other filters usable instead of silently doing nothing.
   */
  const changeRadius = async (value: string) => {
    setRadiusKm(value);
    setError(null);
    if (value && !coords) {
      const position = await getBrowserLocation();
      if (position) {
        setCoords(position);
      } else {
        setRadiusKm('');
        setError('Filtering by distance needs your location. Choose a city or a material instead.');
      }
    }
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

  /**
   * Distance is only meaningful against a real position, so it is requested explicitly and the
   * pool is re-read with the coordinates. Declining just leaves the pool sorted by recency.
   */
  const sortByDistance = async () => {
    setLocating(true);
    setError(null);
    try {
      const position = await getBrowserLocation();
      if (!position) {
        setError('Location permission was declined. The list stays sorted by most recent request.');
        return;
      }
      setCoords(position);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not read your location.');
    } finally {
      setLocating(false);
    }
  };

  if (dashboard.loading) return <Loading label="Loading collector workspace…" />;

  if (dashboard.error) {
    return (
      <Note tone="warning">
        <div>
          <strong>Collector actions need an approved application.</strong>
          <div className="small" style={{ marginTop: 4 }}>
            {dashboard.error} <Link to="/profile">Check your application status →</Link>
          </div>
        </div>
      </Note>
    );
  }

  const stats = dashboard.data;
  const jobs = mine.data?.content ?? [];
  const today = localToday();

  const open = jobs.filter((job) => !TERMINAL.includes(job.status));
  const dueToday = open.filter((job) => job.pickupDate === today).sort(byVisitOrder);
  const upcoming = open.filter((job) => job.pickupDate !== today).sort(byVisitOrder);
  const completed = jobs
    .filter((job) => TERMINAL.includes(job.status))
    .sort((a, b) => (a.updatedAt < b.updatedAt ? 1 : -1));

  const poolTotal = available.data?.totalElements ?? 0;

  const pool = [...(available.data?.content ?? [])].sort((a, b) => {
    if (coords) {
      const left = a.approximateDistanceKm ?? Number.POSITIVE_INFINITY;
      const right = b.approximateDistanceKm ?? Number.POSITIVE_INFINITY;
      if (left !== right) return left - right;
    }
    return a.createdAt < b.createdAt ? 1 : -1;
  });

  /** Card for a request in the open pool: material, city and a coarse distance only. */
  const renderAvailable = (summary: PickupSummary) => {
    const busy = busyCode === summary.code;

    return (
      <Card key={summary.code} className="lift">
        <div className="card-head">
          <span className="icon-tile" aria-hidden="true">
            <Icon name="truck" size={17} />
          </span>
          <h3 style={{ margin: 0 }} className="mono">
            {summary.code}
          </h3>
          <span className="spacer" />
          <StatusPill status={summary.status} />
        </div>

        <dl className="readout" style={{ marginBottom: 14 }}>
          <div className="readout-row">
            <dt>Material</dt>
            <dd>
              {summary.category?.name ?? '—'}
              <span className="muted small"> · est. {kg(summary.estimatedQuantityKg)}</span>
            </dd>
          </div>
          <div className="readout-row">
            <dt>Area</dt>
            <dd>{summary.city}</dd>
          </div>
          {summary.approximateDistanceKm !== null ? (
            <div className="readout-row">
              <dt>Distance</dt>
              <dd>≈ {summary.approximateDistanceKm} km</dd>
            </div>
          ) : null}
          <div className="readout-row">
            <dt>Window</dt>
            <dd>
              {dateOnly(summary.pickupDate)} · {summary.timeSlot.toLowerCase()}
              <div className="list-meta">requested {relativeTime(summary.createdAt)}</div>
            </dd>
          </div>
        </dl>

        <div className="small muted" style={{ marginBottom: 12 }}>
          The resident's address, contact details and photo are released once you accept this job.
        </div>

        <div className="btn-row">
          <Button
            type="button"
            className="small"
            disabled={busy}
            onClick={() => run(summary.code, () => api.acceptPickup(summary.code), `You accepted ${summary.code}.`)}
          >
            {busy ? <Spinner onPrimary /> : <Icon name="check" size={15} />}
            Accept job
          </Button>
        </div>
      </Card>
    );
  };

  /** Card for a job assigned to this organisation: full detail, and the lifecycle actions. */
  const renderJob = (pickup: Pickup) => {
    const busy = busyCode === pickup.code;
    const active = ['ACCEPTED', 'SCHEDULED', 'PICKED_UP', 'PROCESSING'].includes(pickup.status);
    const hint = nextAction(pickup.status);

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
            {hint ? (
              <div className="small" style={{ marginTop: 8, color: 'var(--success)' }}>
                <Icon name="info" size={13} /> {hint}
              </div>
            ) : null}
          </>
        ) : null}

        <div className="btn-row" style={{ marginTop: 16 }}>
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
            <>
              <Button
                type="button"
                className="small"
                disabled={busy}
                onClick={() => run(pickup.code, () => api.updatePickupStatus(pickup.code, 'RECOVERED'), `${pickup.code} marked recovered.`)}
              >
                <Icon name="archive" size={15} />
                Mark recovered
              </Button>
              <Button
                type="button"
                className="secondary small"
                disabled={busy}
                onClick={() => run(pickup.code, () => api.updatePickupStatus(pickup.code, 'RECYCLED'), `${pickup.code} marked recycled.`)}
              >
                <Icon name="recycle" size={15} />
                Mark recycled
              </Button>
            </>
          ) : null}
          {pickup.status === 'ACCEPTED' || pickup.status === 'SCHEDULED' ? (
            <Button
              type="button"
              className="ghost small"
              disabled={busy}
              onClick={() => {
                // Releasing hands the job back to the pool, so it is confirmed rather than one-click.
                if (!window.confirm(`Release ${pickup.code} back to the open pool? The resident keeps their request.`)) return;
                run(pickup.code, () => api.releasePickup(pickup.code), `${pickup.code} released back to the pool.`);
              }}
            >
              Release
            </Button>
          ) : null}
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

  const section = (key: string, title: string, count: number, hint: string, body: ReactNode, empty: ReactNode) => (
    <section className="work-section" key={key}>
      <div className="work-head">
        <h2>{title}</h2>
        <span className={`pill ${count > 0 ? 'green' : 'grey'}`}>{count}</span>
        <span className="muted small">{hint}</span>
      </div>
      {count === 0 ? empty : body}
    </section>
  );

  return (
    <>
      <PageHead
        eyebrow="Collector"
        title="Collector workspace"
        lede="Work the queue in the order it was promised: today's visits first, then the jobs already assigned to you, then requests still waiting for a collector."
        actions={
          <>
            <Button type="button" className="secondary" onClick={sortByDistance} disabled={locating}>
              <Icon name="route" size={16} />
              {locating ? 'Locating…' : coords ? 'Nearest first' : 'Sort by distance'}
            </Button>
            <Button type="button" className="ghost" onClick={refresh}>
              <Icon name="refresh" size={16} />
              Refresh
            </Button>
          </>
        }
      />

      <div className="collector-strip">
        <div className="grow">
          <div className="list-title">
            {partner.data?.organizationName ?? 'Your organisation'}
            {partner.data ? (
              <span className={`pill ${partner.data.status === 'VERIFIED' ? 'green' : partner.data.status === 'REJECTED' ? 'red' : 'amber'}`}>
                {partner.data.status.toLowerCase()}
              </span>
            ) : (
              <span className="pill grey">no application on file</span>
            )}
          </div>
          <div className="list-meta">
            {partner.data
              ? `Materials: ${partner.data.materialCodes.join(', ') || '—'} · ${partner.data.city}`
              : 'Apply as a collector from your profile to receive pickup requests.'}
          </div>
        </div>
        <dl className="collector-figures">
          <div>
            <dt>Open requests</dt>
            <dd className="mono">{stats?.availableRequests ?? 0}</dd>
          </div>
          <div>
            <dt>Active jobs</dt>
            <dd className="mono">{stats?.activeJobs ?? 0}</dd>
          </div>
          <div>
            <dt>Collected</dt>
            <dd className="mono">{kg(stats?.totalKgCollected ?? 0)}</dd>
          </div>
          <div>
            <dt>Collections recorded</dt>
            <dd className="mono">{stats?.totalCollections ?? 0}</dd>
          </div>
        </dl>
      </div>

      {error ? <Note tone="error">{error}</Note> : null}
      {message ? <Note tone="success">{message}</Note> : null}

      {mine.error ? <Note tone="error">{mine.error}</Note> : null}

      {mine.loading ? (
        <Loading label="Loading your jobs…" />
      ) : (
        <>
          {section(
            'today',
            'Today',
            dueToday.length,
            `Visits promised for ${dateOnly(today)}`,
            <div className="grid cols-2" style={{ alignItems: 'start' }}>
              {dueToday.map(renderJob)}
            </div>,
            <EmptyState icon={<Icon name="calendar" size={20} />} title="Nothing scheduled for today">
              Jobs you accept with a visit date of today appear here first.
            </EmptyState>,
          )}

          {section(
            'active',
            'Active jobs',
            upcoming.length,
            'Assigned to you and still in the pipeline',
            <div className="grid cols-2" style={{ alignItems: 'start' }}>
              {upcoming.map(renderJob)}
            </div>,
            <EmptyState icon={<Icon name="box" size={20} />} title="No other jobs in progress">
              Everything else you have accepted has been closed out.
            </EmptyState>,
          )}
        </>
      )}

      <section className="work-section">
        <div className="work-head">
          <h2>New requests</h2>
          <span className={`pill ${poolTotal > 0 ? 'green' : 'grey'}`}>{poolTotal}</span>
          <span className="muted small">
            Waiting for a collector{coords ? ' · nearest first' : ''} — the resident's address stays private until you accept
          </span>
        </div>

        <div className="pool-filters">
          <Field label="City / area" htmlFor="pool-city">
            <select id="pool-city" value={city} onChange={(event) => setCity(event.target.value)}>
              <option value="">Any city</option>
              {(filters.data?.cities ?? []).map((name) => (
                <option key={name} value={name}>
                  {name}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Material" htmlFor="pool-material">
            <select id="pool-material" value={material} onChange={(event) => setMaterial(event.target.value)}>
              <option value="">Any material</option>
              {(filters.data?.materialCodes ?? []).map((code) => (
                <option key={code} value={code}>
                  {materialName(code)}
                </option>
              ))}
            </select>
          </Field>
          <Field
            label="Within"
            htmlFor="pool-radius"
            hint={coords ? undefined : 'Uses your location'}
          >
            <select id="pool-radius" value={radiusKm} onChange={(event) => changeRadius(event.target.value)}>
              <option value="">Any distance</option>
              {RADIUS_OPTIONS.map((option) => (
                <option key={option} value={option}>
                  {option} km
                </option>
              ))}
            </select>
          </Field>
          <div className="pool-filter-actions">
            {filtersActive ? (
              <Button type="button" className="ghost small" onClick={clearFilters}>
                <Icon name="close" size={15} />
                Clear filters
              </Button>
            ) : null}
          </div>
        </div>

        {available.error ? <Note tone="error">{available.error}</Note> : null}
        {available.loading ? (
          <Loading label="Loading open requests…" />
        ) : pool.length === 0 ? (
          <EmptyState
            icon={<Icon name="truck" size={20} />}
            title={filtersActive ? 'No open requests match these filters' : 'No open requests right now'}
          >
            {filtersActive
              ? 'Widen the city, material or distance filter to see the rest of the pool.'
              : 'New resident requests appear here as soon as they are created.'}
          </EmptyState>
        ) : (
          <div className="grid cols-2" style={{ alignItems: 'start' }}>
            {pool.map(renderAvailable)}
          </div>
        )}
      </section>

      <section className="work-section">
        <div className="work-head">
          <h2>Completed</h2>
          <span className={`pill ${completed.length > 0 ? 'green' : 'grey'}`}>{completed.length}</span>
          <span className="muted small">Closed by you, with the weight recorded on site</span>
        </div>
        {completed.length === 0 ? (
          <EmptyState icon={<Icon name="checkCircle" size={20} />} title="Nothing completed yet">
            Once you mark a job recovered or recycled it moves out of the queue and is listed here.
          </EmptyState>
        ) : (
          <div className="grid cols-2" style={{ alignItems: 'start' }}>
            {completed.slice(0, 6).map(renderJob)}
          </div>
        )}
      </section>
    </>
  );
}
