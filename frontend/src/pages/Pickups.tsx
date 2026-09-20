import { useMemo, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { Button, Card, EmptyState, Field, Loading, Note, Spinner, StatusPill, useAsync } from '../components/ui';
import { api, ApiError } from '../lib/api';
import { dateOnly, kg, relativeTime, todayISO } from '../lib/format';
import { getBrowserLocation } from '../lib/geo';
import type { PickupStatus, TimeSlot } from '../lib/types';
import { useAuth } from '../context/AuthContext';

const FILTERS: (PickupStatus | 'ALL')[] = ['ALL', 'REQUESTED', 'ACCEPTED', 'SCHEDULED', 'PICKED_UP', 'PROCESSING', 'RECOVERED', 'CANCELLED'];

export function Pickups() {
  const { user } = useAuth();
  const categories = useAsync(() => api.categories(), []);
  const scans = useAsync(() => api.scans(0, 20), []);
  const profile = useAsync(() => api.profile(), []);
  const [filter, setFilter] = useState<PickupStatus | 'ALL'>('ALL');
  const list = useAsync(() => api.pickups(filter, 0, 50), [filter]);

  const [formOpen, setFormOpen] = useState(false);
  const [categoryId, setCategoryId] = useState('');
  const [estimatedQuantityKg, setEstimatedQuantityKg] = useState('2');
  const [address, setAddress] = useState('');
  const [city, setCity] = useState('');
  const [pincode, setPincode] = useState('');
  const [pickupDate, setPickupDate] = useState(todayISO(1));
  const [timeSlot, setTimeSlot] = useState<TimeSlot>('MORNING');
  const [notes, setNotes] = useState('');
  const [photo, setPhoto] = useState<File | null>(null);
  const [coords, setCoords] = useState<{ lat: number; lng: number } | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [success, setSuccess] = useState<string | null>(null);

  const activeCategories = useMemo(() => categories.data?.filter((c) => c.active) ?? [], [categories.data]);

  // Prefill address fields from the saved profile the first time the form opens.
  const openForm = () => {
    setFormOpen(true);
    if (!address && profile.data?.addressLine) setAddress(profile.data.addressLine);
    if (!city && profile.data?.city) setCity(profile.data.city);
  };

  const useMyLocation = async () => {
    const position = await getBrowserLocation();
    if (position) setCoords(position);
    else setError('Location permission was declined — the pickup can still be booked without coordinates.');
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    setFieldErrors({});
    setSuccess(null);
    try {
      const pickup = await api.createPickup({
        categoryId,
        estimatedQuantityKg: Number(estimatedQuantityKg),
        address,
        city,
        pincode: pincode || undefined,
        latitude: coords?.lat ?? null,
        longitude: coords?.lng ?? null,
        pickupDate,
        timeSlot,
        notes: notes || undefined,
        photo,
      });
      setSuccess(`Pickup ${pickup.code} requested. A verified collector in your city can accept it now.`);
      setNotes('');
      setPhoto(null);
      list.reload();
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
        setFieldErrors(err.fieldErrors);
      } else {
        setError('Could not create the pickup request.');
      }
    } finally {
      setBusy(false);
    }
  };

  const cancel = async (code: string) => {
    setError(null);
    try {
      await api.cancelPickup(code);
      list.reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not cancel this pickup.');
    }
  };

  return (
    <>
      <div className="page-head">
        <div>
          <h1>My pickups</h1>
          <p className="lede">
            Request a pickup and a verified collector will accept it, schedule a visit and record the actual weight
            collected.
          </p>
        </div>
        <div className="spacer" />
        <Button type="button" onClick={formOpen ? () => setFormOpen(false) : openForm}>
          {formOpen ? 'Close form' : '＋ New pickup'}
        </Button>
      </div>

      {success ? <Note tone="success">{success}</Note> : null}
      {error ? <Note tone="error">{error}</Note> : null}

      {formOpen ? (
        <Card title="Book a pickup">
          <form onSubmit={submit}>
            <div className="inline-fields">
              <Field label="Material" htmlFor="p-category" error={fieldErrors.categoryId}>
                <select id="p-category" required value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
                  <option value="">Select a category…</option>
                  {activeCategories.map((category) => (
                    <option key={category.id} value={category.id}>
                      {category.name}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label="Estimated weight (kg)" htmlFor="p-qty" hint="At least 0.1 kg" error={fieldErrors.estimatedQuantityKg}>
                <input
                  id="p-qty"
                  type="number"
                  min={0.1}
                  step={0.1}
                  required
                  value={estimatedQuantityKg}
                  onChange={(e) => setEstimatedQuantityKg(e.target.value)}
                />
              </Field>
              <Field label="Pickup date" htmlFor="p-date" hint="Today or later" error={fieldErrors.pickupDate}>
                <input
                  id="p-date"
                  type="date"
                  required
                  min={todayISO()}
                  value={pickupDate}
                  onChange={(e) => setPickupDate(e.target.value)}
                />
              </Field>
              <Field label="Time slot" htmlFor="p-slot">
                <select id="p-slot" value={timeSlot} onChange={(e) => setTimeSlot(e.target.value as TimeSlot)}>
                  <option value="MORNING">Morning</option>
                  <option value="AFTERNOON">Afternoon</option>
                  <option value="EVENING">Evening</option>
                </select>
              </Field>
            </div>

            <Field label="Address" htmlFor="p-address" error={fieldErrors.address}>
              <input
                id="p-address"
                required
                value={address}
                onChange={(e) => setAddress(e.target.value)}
                placeholder="Flat / house, street, landmark"
              />
            </Field>

            <div className="inline-fields">
              <Field label="City" htmlFor="p-city" error={fieldErrors.city}>
                <input id="p-city" required value={city} onChange={(e) => setCity(e.target.value)} placeholder="Hyderabad" />
              </Field>
              <Field label="Pincode" htmlFor="p-pin" error={fieldErrors.pincode}>
                <input id="p-pin" value={pincode} onChange={(e) => setPincode(e.target.value)} placeholder="500081" />
              </Field>
            </div>

            <Field label="Notes for the collector" htmlFor="p-notes" hint="Gate code, bag count, access instructions…">
              <textarea id="p-notes" value={notes} onChange={(e) => setNotes(e.target.value)} />
            </Field>

            <Field label="Photo of the waste (optional)" htmlFor="p-photo" hint="JPG, PNG or WEBP up to 8 MB">
              <input
                id="p-photo"
                type="file"
                accept="image/png,image/jpeg,image/webp"
                onChange={(e) => setPhoto(e.target.files?.[0] ?? null)}
              />
            </Field>

            <div className="btn-row">
              <Button type="submit" disabled={busy || activeCategories.length === 0}>
                {busy ? <Spinner onPrimary /> : null}
                {busy ? 'Requesting…' : 'Request pickup'}
              </Button>
              <Button type="button" className="secondary" onClick={useMyLocation}>
                {coords ? '📍 Coordinates attached' : '📍 Attach my location'}
              </Button>
              {scans.data && scans.data.content.length > 0 ? (
                <span className="small muted">
                  Tip: your latest scan was “{scans.data.content[0].detectedItem ?? scans.data.content[0].category?.name}”.
                </span>
              ) : null}
            </div>
          </form>
        </Card>
      ) : null}

      <div className="tabs">
        {FILTERS.map((status) => (
          <button key={status} type="button" className={`tab${filter === status ? ' active' : ''}`} onClick={() => setFilter(status)}>
            {status === 'ALL' ? 'All' : status.replace('_', ' ').toLowerCase()}
          </button>
        ))}
      </div>

      {list.error ? <Note tone="error">{list.error}</Note> : null}
      {list.loading ? (
        <Loading label="Loading pickups…" />
      ) : (list.data?.content.length ?? 0) === 0 ? (
        <EmptyState
          icon="🚚"
          title={filter === 'ALL' ? 'No pickups yet' : `No ${filter.toLowerCase().replace('_', ' ')} pickups`}
          action={
            <Button type="button" onClick={openForm}>
              Book your first pickup
            </Button>
          }
        >
          Once a collector accepts, you can follow every stage here.
        </EmptyState>
      ) : (
        <Card className="tight">
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Request</th>
                  <th>Material</th>
                  <th>Estimated</th>
                  <th>Actual</th>
                  <th>Pickup</th>
                  <th>Collector</th>
                  <th>Status</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {list.data?.content.map((pickup) => (
                  <tr key={pickup.code}>
                    <td>
                      <Link to={`/pickups/${pickup.code}`} className="strong">
                        {pickup.code}
                      </Link>
                      <div className="list-meta">{relativeTime(pickup.createdAt)}</div>
                    </td>
                    <td>{pickup.category?.name ?? '—'}</td>
                    <td className="mono">{kg(pickup.estimatedQuantityKg)}</td>
                    <td className="mono">{pickup.actualQuantityKg !== null ? kg(pickup.actualQuantityKg) : '—'}</td>
                    <td>
                      {dateOnly(pickup.pickupDate)}
                      <div className="list-meta">{pickup.timeSlot.toLowerCase()}</div>
                    </td>
                    <td>{pickup.collectorOrganization ?? <span className="muted">Awaiting collector</span>}</td>
                    <td>
                      <StatusPill status={pickup.status} />
                    </td>
                    <td className="right">
                      {pickup.status === 'REQUESTED' ? (
                        <Button type="button" className="ghost small" onClick={() => cancel(pickup.code)}>
                          Cancel
                        </Button>
                      ) : null}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      {user?.role === 'COLLECTOR' ? (
        <Note tone="info">
          You also have collector access — open the <Link to="/collector">collector workspace</Link> to accept jobs and record
          weights.
        </Note>
      ) : null}
    </>
  );
}
