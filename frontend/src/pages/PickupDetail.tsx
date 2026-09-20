import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button, Card, Loading, Note, PageHead, StatusPill, useAsync } from '../components/ui';
import { Icon } from '../components/icons';
import { api, ApiError } from '../lib/api';
import { PICKUP_STAGES, dateOnly, dateTime, kg } from '../lib/format';

export function PickupDetail() {
  const { code = '' } = useParams();
  const pickup = useAsync(() => api.pickup(code), [code]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const cancel = async () => {
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
  const stageIndex = PICKUP_STAGES.findIndex((stage) => stage.key === data.status);
  const cancelled = data.status === 'CANCELLED';

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
                    <span className="strong">{stage.label}</span>
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

          {data.status === 'REQUESTED' ? (
            <div className="btn-row" style={{ marginTop: 16 }}>
              <Button type="button" className="danger small" onClick={cancel} disabled={busy}>
                {busy ? 'Cancelling…' : 'Cancel request'}
              </Button>
              <span className="small muted">Requests can be cancelled until a collector accepts them.</span>
            </div>
          ) : null}

          {data.status === 'RECOVERED' ? (
            <div className="divider" />
          ) : null}

          {['PICKED_UP', 'PROCESSING', 'RECOVERED'].includes(data.status) && data.actualQuantityKg !== null ? (
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
            <div className="readout-row">
              <dt>Material</dt>
              <dd>{data.category?.name ?? '—'}</dd>
            </div>
            <div className="readout-row">
              <dt>Estimated weight</dt>
              <dd className="mono">{kg(data.estimatedQuantityKg)}</dd>
            </div>
            <div className="readout-row">
              <dt>Actual collected weight</dt>
              <dd className="mono">{data.actualQuantityKg !== null ? kg(data.actualQuantityKg) : 'Pending collection'}</dd>
            </div>
            <div className="readout-row">
              <dt>Pickup window</dt>
              <dd>
                {dateOnly(data.pickupDate)} · {data.timeSlot.toLowerCase()}
              </dd>
            </div>
            <div className="readout-row">
              <dt>Address</dt>
              <dd>
                {data.address}, {data.city} {data.pincode ?? ''}
              </dd>
            </div>
            <div className="readout-row">
              <dt>Collector</dt>
              <dd>{data.collectorOrganization ?? 'Awaiting a verified collector'}</dd>
            </div>
            {data.scheduledAt ? (
              <div className="readout-row">
                <dt>Scheduled for</dt>
                <dd>{dateTime(data.scheduledAt)}</dd>
              </div>
            ) : null}
            {data.notes ? (
              <div className="readout-row">
                <dt>Notes</dt>
                <dd>{data.notes}</dd>
              </div>
            ) : null}
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
