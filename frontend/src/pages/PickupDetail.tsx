import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button, Card, Loading, Note, PageHead, StatusPill, useAsync } from '../components/ui';
import { Icon } from '../components/icons';
import { api, ApiError } from '../lib/api';
import { PICKUP_STAGES, dateOnly, dateTime, kg, pickupNextStep, stageIndex as stageIndexOf } from '../lib/format';

export function PickupDetail() {
  const { code = '' } = useParams();
  const pickup = useAsync(() => api.pickup(code), [code]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const cancel = async () => {
    if (
      !window.confirm(
        `Cancel request ${code}? The collector network will stop seeing it, and this cannot be undone.`,
      )
    ) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await api.cancelPickup(code);
      pickup.reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not cancel this pickup.');
    } finally {
      setBusy(false);
    }
  };

  if (pickup.loading) return <Loading label="Loading pickup…" />;
  if (pickup.error) return <Note tone="error">{pickup.error}</Note>;
  if (!pickup.data) return null;

  const data = pickup.data;
  const stageIndex = stageIndexOf(data.status);
  const cancelled = data.status === 'CANCELLED';
  const recycled = data.status === 'RECYCLED';
  const finished = data.status === 'RECOVERED' || recycled;
  const collected = data.actualQuantityKg !== null;

  const timestamps: Record<string, string | null> = {
    REQUESTED: data.createdAt,
    ACCEPTED: data.acceptedAt,
    SCHEDULED: data.scheduledAt,
    PICKED_UP: data.pickedUpAt,
    PROCESSING: data.processingAt,
    RECOVERED: data.recoveredAt,
  };

  return (
    <>
      <PageHead
        eyebrow="Pickup"
        title={<span className="mono">{data.code}</span>}
        lede={`${data.category?.name ?? 'Material'} · requested ${dateTime(data.createdAt)}`}
        actions={<StatusPill status={data.status} />}
      />

      {error ? <Note tone="error">{error}</Note> : null}
      {cancelled ? (
        <Note tone="warning">
          This pickup was cancelled{data.cancelReason ? `: ${data.cancelReason}` : ''}.
        </Note>
      ) : null}

      {/* Answering what / where / when / who and what happens next before the timeline: the
          timeline shows how the request got here, this shows where it stands right now. */}
      <Card className="pickup-summary">
        <div className="summary-head">
          <div className="grow">
            <div className="stat-label">Current status</div>
            <div className="summary-status">
              <StatusPill status={data.status} />
              {collected ? <span className="muted small">collected {kg(data.actualQuantityKg)}</span> : null}
            </div>
          </div>
          <div className="summary-next">
            <div className="stat-label">What happens next</div>
            <p>{pickupNextStep(data.status, data.collectorOrganization)}</p>
          </div>
        </div>

        <dl className="readout summary-facts">
          <div className="readout-row">
            <dt>What</dt>
            <dd>
              {data.category?.name ?? '—'}
              <span className="muted small"> · estimated {kg(data.estimatedQuantityKg)}</span>
            </dd>
          </div>
          <div className="readout-row">
            <dt>Where</dt>
            <dd>
              {data.address}, {data.city} {data.pincode ?? ''}
            </dd>
          </div>
          <div className="readout-row">
            <dt>When</dt>
            <dd>
              {dateOnly(data.pickupDate)} · {data.timeSlot.toLowerCase()}
              {data.scheduledAt ? <span className="muted small"> · booked {dateTime(data.scheduledAt)}</span> : null}
            </dd>
          </div>
          <div className="readout-row">
            <dt>Who</dt>
            <dd>{data.collectorOrganization ?? 'Awaiting a verified collection partner'}</dd>
          </div>
        </dl>

        {data.status === 'REQUESTED' ? (
          <div className="btn-row" style={{ marginTop: 16 }}>
            <Button type="button" className="danger small" onClick={cancel} disabled={busy}>
              <Icon name="close" size={15} />
              {busy ? 'Cancelling…' : 'Cancel request'}
            </Button>
            <span className="small muted">Requests can be cancelled until a collector accepts them.</span>
          </div>
        ) : null}
      </Card>

      <div className="grid cols-2" style={{ alignItems: 'start' }}>
        <Card title="Progress">
          <ul className="timeline">
            {PICKUP_STAGES.map((stage, index) => {
              const done = !cancelled && stageIndex >= index && stageIndex !== -1;
              const current = !cancelled && stageIndex === index;
              return (
                <li key={stage.key} className={`${done ? 'done' : ''}${current ? ' current' : ''}`.trim()}>
                  <span className="marker" aria-hidden="true">
                    {done ? <Icon name="check" size={12} /> : index + 1}
                  </span>
                  <span>
                    <span className="strong">
                      {recycled && index === PICKUP_STAGES.length - 1 ? 'Material recycled' : stage.label}
                    </span>
                    {timestamps[stage.key] ? <span className="muted small"> · {dateTime(timestamps[stage.key])}</span> : null}
                    {current ? (
                      <span className="pill green" style={{ marginLeft: 8 }}>
                        Current
                      </span>
                    ) : null}
                  </span>
                </li>
              );
            })}
          </ul>

          {finished ? <div className="divider" /> : null}

          {['PICKED_UP', 'PROCESSING', 'RECOVERED', 'RECYCLED'].includes(data.status) && collected ? (
            <div className="panel soft" style={{ padding: 16, marginTop: 4 }}>
              <div className="stat-label">Collected weight</div>
              <div className="stat-value mono">{kg(data.actualQuantityKg)}</div>
              <div className="stat-hint">
                Weighed on site by {data.collectorOrganization ?? 'the collector'} — this is the figure that feeds your
                history and impact.
              </div>
            </div>
          ) : null}
        </Card>

        <Card title="Details">
          <dl className="readout">
            {data.scheduledAt ? (
              <div className="readout-row">
                <dt>Scheduled for</dt>
                <dd>{dateTime(data.scheduledAt)}</dd>
              </div>
            ) : null}
            <div className="readout-row">
              <dt>Estimated weight</dt>
              <dd className="mono">{kg(data.estimatedQuantityKg)}</dd>
            </div>
            <div className="readout-row">
              <dt>Actual collected weight</dt>
              <dd className="mono">{collected ? kg(data.actualQuantityKg) : 'Pending collection'}</dd>
            </div>
            {data.notes ? (
              <div className="readout-row">
                <dt>Notes</dt>
                <dd>{data.notes}</dd>
              </div>
            ) : null}
            <div className="readout-row">
              <dt>Requested</dt>
              <dd>{dateTime(data.createdAt)}</dd>
            </div>
          </dl>

          {data.photoUrl ? (
            <div style={{ marginTop: 16 }}>
              <img className="thumb lg" src={data.photoUrl} alt="Waste photo supplied with the request" />
            </div>
          ) : null}

          <div className="btn-row" style={{ marginTop: 18 }}>
            <Link className="btn secondary small" to="/pickups">
              Back to pickups
            </Link>
            <Link className="btn ghost small" to="/history">
              Recycling history
            </Link>
          </div>
        </Card>
      </div>
    </>
  );
}
