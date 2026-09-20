import { useState, type FormEvent } from 'react';
import { Button, Card, EmptyState, Field, Loading, Note, Spinner, Stat, useAsync } from '../components/ui';
import { api, ApiError } from '../lib/api';
import { dateTime, kg, num } from '../lib/format';

type Tab = 'applications' | 'users' | 'points' | 'analytics';

export function AdminConsole() {
  const [tab, setTab] = useState<Tab>('applications');
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  return (
    <>
      {error ? <Note tone="error">{error}</Note> : null}
      {message ? <Note tone="success">{message}</Note> : null}

      <div className="tabs">
        {(
          [
            ['applications', 'Collector applications'],
            ['users', 'Users'],
            ['points', 'Collection points'],
            ['analytics', 'Analytics'],
          ] as [Tab, string][]
        ).map(([key, label]) => (
          <button key={key} type="button" className={`tab${tab === key ? ' active' : ''}`} onClick={() => setTab(key)}>
            {label}
          </button>
        ))}
      </div>

      {tab === 'applications' ? <Applications onError={setError} onMessage={setMessage} /> : null}
      {tab === 'users' ? <Users onError={setError} onMessage={setMessage} /> : null}
      {tab === 'points' ? <Points onError={setError} onMessage={setMessage} /> : null}
      {tab === 'analytics' ? <Analytics /> : null}
    </>
  );
}

interface TabProps {
  onError: (value: string | null) => void;
  onMessage: (value: string | null) => void;
}

function Applications({ onError, onMessage }: TabProps) {
  const [status, setStatus] = useState('PENDING');
  const list = useAsync(() => api.adminCollectors(status || undefined, 0, 50), [status]);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [rejecting, setRejecting] = useState<string | null>(null);
  const [reason, setReason] = useState('');

  const verify = async (id: string, name: string) => {
    setBusyId(id);
    onError(null);
    try {
      await api.verifyCollector(id);
      onMessage(`${name} is now a verified collector and can accept pickups.`);
      list.reload();
    } catch (err) {
      onError(err instanceof ApiError ? err.message : 'Could not verify this collector.');
    } finally {
      setBusyId(null);
    }
  };

  const reject = async (event: FormEvent, id: string, name: string) => {
    event.preventDefault();
    setBusyId(id);
    onError(null);
    try {
      await api.rejectCollector(id, reason);
      onMessage(`Application from ${name} rejected.`);
      setRejecting(null);
      setReason('');
      list.reload();
    } catch (err) {
      onError(err instanceof ApiError ? err.message : 'Could not reject this application.');
    } finally {
      setBusyId(null);
    }
  };

  return (
    <Card
      title="Collector directory"
      action={
        <select value={status} onChange={(e) => setStatus(e.target.value)} style={{ width: 190 }}>
          <option value="PENDING">Pending review</option>
          <option value="VERIFIED">Verified</option>
          <option value="REJECTED">Rejected</option>
          <option value="">All applications</option>
        </select>
      }
    >
      {list.error ? <Note tone="error">{list.error}</Note> : null}
      {list.loading ? (
        <Loading />
      ) : (list.data?.content.length ?? 0) === 0 ? (
        <EmptyState icon="🧾" title="Nothing to review here">
          Collector applications appear in this queue as soon as residents apply.
        </EmptyState>
      ) : (
        <div className="list">
          {list.data?.content.map((partner) => (
            <div className="list-item" key={partner.id}>
              <div className="grow">
                <div className="list-title">
                  {partner.organizationName}{' '}
                  <span className={`pill ${partner.status === 'VERIFIED' ? 'green' : partner.status === 'REJECTED' ? 'red' : 'amber'}`}>
                    {partner.status.toLowerCase()}
                  </span>
                </div>
                <div className="list-meta">
                  {partner.contactPerson} · {partner.phone} · {partner.email ?? partner.userEmail}
                </div>
                <div className="list-meta">
                  {partner.address}, {partner.city} {partner.pincode ?? ''} · materials: {partner.materialCodes.join(', ') || '—'}
                </div>
                <div className="list-meta">
                  Applied {dateTime(partner.createdAt)}
                  {partner.registrationNumber ? ` · Reg. ${partner.registrationNumber}` : ''}
                </div>
                {partner.rejectionReason ? <div className="list-meta">Reason: {partner.rejectionReason}</div> : null}

                {rejecting === partner.id ? (
                  <form style={{ marginTop: 10 }} onSubmit={(e) => reject(e, partner.id, partner.organizationName)}>
                    <Field label="Rejection reason" htmlFor={`reason-${partner.id}`}>
                      <input id={`reason-${partner.id}`} required value={reason} onChange={(e) => setReason(e.target.value)} />
                    </Field>
                    <div className="btn-row">
                      <Button type="submit" className="danger small" disabled={busyId === partner.id}>
                        Confirm rejection
                      </Button>
                      <Button type="button" className="ghost small" onClick={() => setRejecting(null)}>
                        Cancel
                      </Button>
                    </div>
                  </form>
                ) : null}
              </div>

              {partner.status !== 'VERIFIED' ? (
                <div className="btn-row" style={{ flexDirection: 'column', alignItems: 'stretch' }}>
                  <Button
                    type="button"
                    className="small"
                    disabled={busyId === partner.id}
                    onClick={() => verify(partner.id, partner.organizationName)}
                  >
                    {busyId === partner.id ? <Spinner onPrimary /> : null}
                    Verify
                  </Button>
                  {partner.status !== 'REJECTED' ? (
                    <Button type="button" className="ghost small" onClick={() => setRejecting(partner.id)}>
                      Reject
                    </Button>
                  ) : null}
                </div>
              ) : null}
            </div>
          ))}
        </div>
      )}
    </Card>
  );
}

function Users({ onError, onMessage }: TabProps) {
  const [query, setQuery] = useState('');
  const [applied, setApplied] = useState('');
  const list = useAsync(() => api.adminUsers(applied || undefined, 0, 50), [applied]);
  const [busyId, setBusyId] = useState<string | null>(null);

  const toggle = async (id: string, current: 'ACTIVE' | 'DISABLED', label: string) => {
    setBusyId(id);
    onError(null);
    try {
      await api.setUserStatus(id, current === 'ACTIVE' ? 'DISABLED' : 'ACTIVE');
      onMessage(`${label} is now ${current === 'ACTIVE' ? 'disabled' : 'active'}.`);
      list.reload();
    } catch (err) {
      onError(err instanceof ApiError ? err.message : 'Could not update this user.');
    } finally {
      setBusyId(null);
    }
  };

  return (
    <Card
      title="User directory"
      action={
        <form
          className="inline"
          onSubmit={(event) => {
            event.preventDefault();
            setApplied(query.trim());
          }}
        >
          <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search email or name" />
          <Button type="submit" className="secondary small">
            Search
          </Button>
        </form>
      }
    >
      {list.error ? <Note tone="error">{list.error}</Note> : null}
      {list.loading ? (
        <Loading />
      ) : (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Email</th>
                <th>Name</th>
                <th>Role</th>
                <th>Status</th>
                <th>Last login</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {list.data?.content.map((user) => (
                <tr key={user.id}>
                  <td>{user.email}</td>
                  <td>{user.fullName ?? '—'}</td>
                  <td>
                    <span className="pill grey">{user.role.toLowerCase()}</span>
                  </td>
                  <td>
                    <span className={`pill ${user.status === 'ACTIVE' ? 'green' : 'red'}`}>{user.status.toLowerCase()}</span>
                  </td>
                  <td>{user.lastLoginAt ? dateTime(user.lastLoginAt) : '—'}</td>
                  <td className="right">
                    <Button
                      type="button"
                      className="ghost small"
                      disabled={busyId === user.id}
                      onClick={() => toggle(user.id, user.status, user.email)}
                    >
                      {user.status === 'ACTIVE' ? 'Disable' : 'Enable'}
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {(list.data?.content.length ?? 0) === 0 ? <EmptyState icon="👥" title="No users matched that search" /> : null}
        </div>
      )}
    </Card>
  );
}

function Points({ onError, onMessage }: TabProps) {
  const categories = useAsync(() => api.categories(), []);
  const list = useAsync(() => api.collectionPoints({}), []);
  const [name, setName] = useState('');
  const [address, setAddress] = useState('');
  const [city, setCity] = useState('');
  const [pincode, setPincode] = useState('');
  const [latitude, setLatitude] = useState('17.3850');
  const [longitude, setLongitude] = useState('78.4867');
  const [operatingHours, setOperatingHours] = useState('');
  const [contactPhone, setContactPhone] = useState('');
  const [materialCodes, setMaterialCodes] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    onError(null);
    try {
      await api.createCollectionPoint({
        name,
        address,
        city,
        pincode: pincode || undefined,
        latitude: Number(latitude),
        longitude: Number(longitude),
        operatingHours: operatingHours || undefined,
        contactPhone: contactPhone || undefined,
        materialCodes,
      });
      onMessage(`${name} added to the public directory.`);
      setName('');
      setAddress('');
      setMaterialCodes([]);
      list.reload();
    } catch (err) {
      onError(err instanceof ApiError ? err.message : 'Could not create the collection point.');
    } finally {
      setBusy(false);
    }
  };

  const remove = async (id: string, label: string) => {
    onError(null);
    try {
      await api.deleteCollectionPoint(id);
      onMessage(`${label} removed from the directory.`);
      list.reload();
    } catch (err) {
      onError(err instanceof ApiError ? err.message : 'Could not delete the collection point.');
    }
  };

  return (
    <div className="grid cols-2">
      <Card title="Add a collection point">
        <form onSubmit={submit}>
          <Field label="Name" htmlFor="cp-name">
            <input id="cp-name" required value={name} onChange={(e) => setName(e.target.value)} />
          </Field>
          <Field label="Address" htmlFor="cp-address">
            <input id="cp-address" required value={address} onChange={(e) => setAddress(e.target.value)} />
          </Field>
          <div className="inline-fields">
            <Field label="City" htmlFor="cp-city">
              <input id="cp-city" required value={city} onChange={(e) => setCity(e.target.value)} />
            </Field>
            <Field label="Pincode" htmlFor="cp-pin">
              <input id="cp-pin" value={pincode} onChange={(e) => setPincode(e.target.value)} />
            </Field>
          </div>
          <div className="inline-fields">
            <Field label="Latitude" htmlFor="cp-lat">
              <input id="cp-lat" type="number" step="0.000001" required value={latitude} onChange={(e) => setLatitude(e.target.value)} />
            </Field>
            <Field label="Longitude" htmlFor="cp-lng">
              <input id="cp-lng" type="number" step="0.000001" required value={longitude} onChange={(e) => setLongitude(e.target.value)} />
            </Field>
          </div>
          <div className="inline-fields">
            <Field label="Operating hours" htmlFor="cp-hours">
              <input id="cp-hours" value={operatingHours} onChange={(e) => setOperatingHours(e.target.value)} placeholder="Mon–Sat 9am–7pm" />
            </Field>
            <Field label="Contact phone" htmlFor="cp-phone">
              <input id="cp-phone" value={contactPhone} onChange={(e) => setContactPhone(e.target.value)} />
            </Field>
          </div>
          <Field label="Accepted materials" hint="Select at least one">
            <div className="inline wrap" style={{ gap: 8 }}>
              {(categories.data ?? []).map((category) => (
                <label className="chip" key={category.id} style={{ cursor: 'pointer' }}>
                  <input
                    type="checkbox"
                    style={{ width: 'auto' }}
                    checked={materialCodes.includes(category.code)}
                    onChange={() =>
                      setMaterialCodes((current) =>
                        current.includes(category.code) ? current.filter((c) => c !== category.code) : [...current, category.code],
                      )
                    }
                  />
                  {category.name}
                </label>
              ))}
            </div>
          </Field>
          <Button type="submit" disabled={busy || materialCodes.length === 0}>
            {busy ? <Spinner onPrimary /> : null}
            {busy ? 'Saving…' : 'Create collection point'}
          </Button>
        </form>
      </Card>

      <Card title={`Directory (${list.data?.length ?? 0})`}>
        {list.error ? <Note tone="error">{list.error}</Note> : null}
        {list.loading ? (
          <Loading />
        ) : (list.data?.length ?? 0) === 0 ? (
          <EmptyState icon="📍" title="No collection points yet">
            Add the first verified drop-off location.
          </EmptyState>
        ) : (
          <div className="list">
            {list.data?.map((point) => (
              <div className="list-item" key={point.id}>
                <div className="grow">
                  <div className="list-title">
                    {point.name} {point.verified ? <span className="pill green">verified</span> : <span className="pill grey">unverified</span>}
                  </div>
                  <div className="list-meta">
                    {point.address}, {point.city} · {point.materials.map((m) => m.code).join(', ')}
                  </div>
                </div>
                <Button type="button" className="ghost small" onClick={() => remove(point.id, point.name)}>
                  Delete
                </Button>
              </div>
            ))}
          </div>
        )}
      </Card>
    </div>
  );
}

function Analytics() {
  const analytics = useAsync(() => api.analytics(), []);

  if (analytics.loading) return <Loading label="Loading platform analytics…" />;
  if (analytics.error) return <Note tone="error">{analytics.error}</Note>;

  const data = analytics.data;
  if (!data) return null;
  const statusEntries = Object.entries(data.pickupsByStatus ?? {});
  const categoryEntries = Object.entries(data.collectedKgByCategory ?? {});

  return (
    <>
      <div className="grid cols-4">
        <Stat label="Users" value={data.totalUsers} accent />
        <Stat label="Verified collectors" value={data.verifiedCollectors} hint={`${data.pendingCollectorApplications} pending`} />
        <Stat label="Pickups" value={data.totalPickups} hint={`${data.completedCollections} collections`} />
        <Stat label="Collected" value={kg(data.totalCollectedKg)} hint="Actual weighed weight" />
      </div>

      <div className="grid cols-2" style={{ marginTop: 16 }}>
        <Card title="Pickups by status">
          {statusEntries.length === 0 ? (
            <EmptyState icon="📊" title="No pickup activity yet" />
          ) : (
            <div className="table-wrap">
              <table className="table">
                <tbody>
                  {statusEntries.map(([status, count]) => (
                    <tr key={status}>
                      <th>{status.toLowerCase().replace('_', ' ')}</th>
                      <td className="mono right">{count}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
        <Card title="Collected weight by material">
          {categoryEntries.length === 0 ? (
            <EmptyState icon="⚖️" title="No collections recorded yet" />
          ) : (
            <div className="table-wrap">
              <table className="table">
                <tbody>
                  {categoryEntries.map(([code, value]) => (
                    <tr key={code}>
                      <th>{code.replace('_', ' ').toLowerCase()}</th>
                      <td className="mono right">{num(value)} kg</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      </div>
    </>
  );
}
