import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button, Card, Loading, Note, StatusPill, useAsync } from '../components/ui';
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
      <div className="page-head">
        <div>
          <h1>
            Pickup {data.code}
          </h1>
          <p className="lede">
            {data.category?.name} · requested {dateTime(data.createdAt)}
          </p>
        </div>
        <div className="spacer" />
        <StatusPill status={data.status} />
      </div>

      {error ? <Note tone="error">{error}</Note> : null}
      {cancelled ? (
        <Note tone="warning">
          This pickup was cancelled{data.cancelReason ? `: ${data.cancelReason}` : ''}.
        </Note>
      ) : null}

      <div className="grid cols-2">
        <Card title="Progress">
          <ul className="timeline">
            {PICKUP_STAGES.map((stage, index) => {
              const done = !cancelled && stageIndex >= index && stageIndex !== -1;
              return (
                <li key={stage.key} className={done ? 'done' : ''}>
                  <span className="marker" aria-hidden="true">
                    {done ? '✓' : index + 1}
                  </span>
                  <span>
                    <span className="strong">{stage.label}</span>
                    {timestamps[stage.key] ? <span className="muted small"> · {dateTime(timestamps[stage.key])}</span> : null}
                  </span>
                </li>
              );
            })}
          </ul>
          {data.status === 'REQUESTED' ? (
            <div className="btn-row" style={{ marginTop: 12 }}>
              <Button type="button" className="danger small" onClick={cancel} disabled={busy}>
                {busy ? 'Cancelling…' : 'Cancel request'}
              </Button>
              <span className="small muted">Requests can be cancelled until a collector accepts them.</span>
            </div>
          ) : null}
        </Card>

        <Card title="Details">
          <div className="table-wrap">
            <table className="table">
              <tbody>
                <tr>
                  <th>Material</th>
                  <td>{data.category?.name ?? '—'}</td>
                </tr>
                <tr>
                  <th>Estimated weight</th>
                  <td className="mono">{kg(data.estimatedQuantityKg)}</td>
                </tr>
                <tr>
                  <th>Actual collected weight</th>
                  <td className="mono">{data.actualQuantityKg !== null ? kg(data.actualQuantityKg) : 'Pending collection'}</td>
                </tr>
                <tr>
                  <th>Pickup date</th>
                  <td>
                    {dateOnly(data.pickupDate)} · {data.timeSlot.toLowerCase()}
                  </td>
                </tr>
                <tr>
                  <th>Address</th>
                  <td>
                    {data.address}, {data.city} {data.pincode ?? ''}
                  </td>
                </tr>
                <tr>
                  <th>Collector</th>
                  <td>{data.collectorOrganization ?? 'Awaiting a verified collector'}</td>
                </tr>
                {data.scheduledAt ? (
                  <tr>
                    <th>Scheduled for</th>
                    <td>{dateTime(data.scheduledAt)}</td>
                  </tr>
                ) : null}
                {data.notes ? (
                  <tr>
                    <th>Notes</th>
                    <td>{data.notes}</td>
                  </tr>
                ) : null}
              </tbody>
            </table>
          </div>

          {data.photoUrl ? (
            <div style={{ marginTop: 14 }}>
              <img className="thumb" style={{ width: 120, height: 120 }} src={data.photoUrl} alt="Waste photo supplied with the request" />
            </div>
          ) : null}

          <div className="btn-row" style={{ marginTop: 16 }}>
            <Link className="btn secondary small" to="/pickups">
              Back to my pickups
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
