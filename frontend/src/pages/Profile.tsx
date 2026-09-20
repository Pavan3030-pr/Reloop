import { useEffect, useState, type FormEvent } from 'react';
import { Button, Card, Field, Loading, Note, PageHead, Spinner, useAsync } from '../components/ui';
import { Icon } from '../components/icons';
import { api, ApiError } from '../lib/api';
import { dateTime } from '../lib/format';
import { useAuth } from '../context/AuthContext';

export function Profile() {
  const { user } = useAuth();
  const profile = useAsync(() => api.profile(), []);
  const categories = useAsync(() => api.categories(), []);

  const [fullName, setFullName] = useState('');
  const [phone, setPhone] = useState('');
  const [city, setCity] = useState('');
  const [addressLine, setAddressLine] = useState('');
  const [saveState, setSaveState] = useState<{ tone: 'success' | 'error'; message: string } | null>(null);
  const [saving, setSaving] = useState(false);

  const application = useAsync(
    () =>
      api
        .myCollectorApplication()
        .catch((err) => (err instanceof ApiError && err.status === 404 ? null : Promise.reject(err))),
    [],
  );

  const [applying, setApplying] = useState(false);
  const [orgName, setOrgName] = useState('');
  const [contactPerson, setContactPerson] = useState('');
  const [appPhone, setAppPhone] = useState('');
  const [appAddress, setAppAddress] = useState('');
  const [appCity, setAppCity] = useState('');
  const [appPincode, setAppPincode] = useState('');
  const [operatingHours, setOperatingHours] = useState('');
  const [registrationNumber, setRegistrationNumber] = useState('');
  const [materialCodes, setMaterialCodes] = useState<string[]>([]);
  const [appError, setAppError] = useState<string | null>(null);
  const [appBusy, setAppBusy] = useState(false);

  useEffect(() => {
    if (!profile.data) return;
    setFullName(profile.data.fullName ?? '');
    setPhone(profile.data.phone ?? '');
    setCity(profile.data.city ?? '');
    setAddressLine(profile.data.addressLine ?? '');
  }, [profile.data]);

  const save = async (event: FormEvent) => {
    event.preventDefault();
    setSaving(true);
    setSaveState(null);
    try {
      await api.updateProfile({
        fullName,
        phone: phone || undefined,
        city: city || undefined,
        addressLine: addressLine || undefined,
      });
      setSaveState({ tone: 'success', message: 'Profile updated.' });
      profile.reload();
    } catch (err) {
      setSaveState({ tone: 'error', message: err instanceof ApiError ? err.message : 'Could not save your profile.' });
    } finally {
      setSaving(false);
    }
  };

  const toggleMaterial = (code: string) => {
    setMaterialCodes((current) => (current.includes(code) ? current.filter((c) => c !== code) : [...current, code]));
  };

  const submitApplication = async (event: FormEvent) => {
    event.preventDefault();
    setAppBusy(true);
    setAppError(null);
    try {
      await api.applyCollector({
        organizationName: orgName,
        contactPerson,
        phone: appPhone,
        address: appAddress,
        city: appCity,
        pincode: appPincode || undefined,
        operatingHours: operatingHours || undefined,
        registrationNumber: registrationNumber || undefined,
        materialCodes,
      });
      application.reload();
      setApplying(false);
    } catch (err) {
      setAppError(err instanceof ApiError ? err.message : 'Could not submit your application.');
    } finally {
      setAppBusy(false);
    }
  };

  if (profile.loading) return <Loading label="Loading your profile…" />;

  const partner = application.data;
  const canApply = user?.role === 'USER' && !partner;

  return (
    <>
      <PageHead
        eyebrow="Account"
        title="Profile"
        lede="Your details are used to prefill pickup requests, and collectors use your phone number to confirm them."
      />

      <div className="grid cols-2" style={{ alignItems: 'start' }}>
        <Card title="Your details">
          {profile.error ? <Note tone="error">{profile.error}</Note> : null}
          {saveState ? <Note tone={saveState.tone}>{saveState.message}</Note> : null}
          <form onSubmit={save} style={{ marginTop: 14 }}>
            <Field label="Full name" htmlFor="fullName">
              <input id="fullName" required value={fullName} onChange={(e) => setFullName(e.target.value)} />
            </Field>
            <Field label="Email" htmlFor="email" hint="Email cannot be changed">
              <input id="email" value={user?.email ?? ''} readOnly />
            </Field>
            <Field label="Phone" htmlFor="phone">
              <input id="phone" value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="+91 90000 00000" />
            </Field>
            <Field label="City" htmlFor="city" hint="Used to prefill pickup requests">
              <input id="city" value={city} onChange={(e) => setCity(e.target.value)} placeholder="Hyderabad" />
            </Field>
            <Field label="Address" htmlFor="addressLine">
              <input
                id="addressLine"
                value={addressLine}
                onChange={(e) => setAddressLine(e.target.value)}
                placeholder="Flat, street, landmark"
              />
            </Field>
            <Button type="submit" disabled={saving}>
              {saving ? <Spinner onPrimary /> : null}
              {saving ? 'Saving…' : 'Save changes'}
            </Button>
          </form>
          <div className="divider" />
          <div className="small muted">
            Account created {dateTime(profile.data?.user.createdAt)} · role {profile.data?.user.role.toLowerCase()}
          </div>
        </Card>

        <div>
          {partner ? (
            <Card title="Collector application">
              <span
                className={`pill ${partner.status === 'VERIFIED' ? 'green' : partner.status === 'REJECTED' ? 'red' : 'amber'}`}
              >
                <Icon name={partner.status === 'VERIFIED' ? 'shield' : partner.status === 'REJECTED' ? 'alert' : 'clock'} size={12} />
                {partner.status.toLowerCase()}
              </span>

              <dl className="readout" style={{ marginTop: 16 }}>
                <div className="readout-row">
                  <dt>Organisation</dt>
                  <dd>{partner.organizationName}</dd>
                </div>
                <div className="readout-row">
                  <dt>Contact</dt>
                  <dd>
                    {partner.contactPerson} · {partner.phone}
                  </dd>
                </div>
                <div className="readout-row">
                  <dt>Location</dt>
                  <dd>
                    {partner.address}, {partner.city}
                  </dd>
                </div>
                <div className="readout-row">
                  <dt>Materials</dt>
                  <dd>{partner.materialCodes.join(', ') || '—'}</dd>
                </div>
                {partner.verifiedAt ? (
                  <div className="readout-row">
                    <dt>Verified</dt>
                    <dd>{dateTime(partner.verifiedAt)}</dd>
                  </div>
                ) : null}
                {partner.rejectionReason ? (
                  <div className="readout-row">
                    <dt>Reason</dt>
                    <dd>{partner.rejectionReason}</dd>
                  </div>
                ) : null}
              </dl>

              {partner.status === 'PENDING' ? (
                <div style={{ marginTop: 16 }}>
                  <Note tone="info">
                    An administrator reviews new collector applications. You will be notified once it is processed.
                  </Note>
                </div>
              ) : null}
              {partner.status === 'VERIFIED' ? (
                <div style={{ marginTop: 16 }}>
                  <Note tone="success">
                    Your organisation is verified. Open the collector workspace to start accepting jobs.
                  </Note>
                </div>
              ) : null}
            </Card>
          ) : null}

          {canApply ? (
            <Card title="Become a collector">
              <p className="muted small" style={{ maxWidth: '58ch' }}>
                Collectors accept pickup requests in their city and record the actual weight of the waste they collect.
                Applications are reviewed by an administrator before any job becomes visible.
              </p>
              {appError ? <Note tone="error">{appError}</Note> : null}
              {!applying ? (
                <Button type="button" onClick={() => setApplying(true)}>
                  <Icon name="truck" size={17} />
                  Apply to collect with ReLoop
                </Button>
              ) : (
                <form onSubmit={submitApplication} style={{ marginTop: 14 }}>
                  <div className="inline-fields">
                    <Field label="Organisation name" htmlFor="org" hint="Your business or cooperative">
                      <input id="org" required value={orgName} onChange={(e) => setOrgName(e.target.value)} />
                    </Field>
                    <Field label="Contact person" htmlFor="contact">
                      <input id="contact" required value={contactPerson} onChange={(e) => setContactPerson(e.target.value)} />
                    </Field>
                  </div>
                  <div className="inline-fields">
                    <Field label="Contact phone" htmlFor="appPhone">
                      <input id="appPhone" required value={appPhone} onChange={(e) => setAppPhone(e.target.value)} />
                    </Field>
                    <Field label="Registration number" htmlFor="reg">
                      <input id="reg" value={registrationNumber} onChange={(e) => setRegistrationNumber(e.target.value)} />
                    </Field>
                  </div>
                  <Field label="Address" htmlFor="appAddress">
                    <input id="appAddress" required value={appAddress} onChange={(e) => setAppAddress(e.target.value)} />
                  </Field>
                  <div className="inline-fields">
                    <Field label="City" htmlFor="appCity">
                      <input id="appCity" required value={appCity} onChange={(e) => setAppCity(e.target.value)} />
                    </Field>
                    <Field label="Pincode" htmlFor="appPin">
                      <input id="appPin" value={appPincode} onChange={(e) => setAppPincode(e.target.value)} />
                    </Field>
                  </div>
                  <Field label="Operating hours" htmlFor="hours">
                    <input
                      id="hours"
                      value={operatingHours}
                      onChange={(e) => setOperatingHours(e.target.value)}
                      placeholder="Mon–Sat 9am–6pm"
                    />
                  </Field>
                  <Field label="Materials you accept" hint="Select at least one">
                    <div className="inline wrap" style={{ gap: 8 }}>
                      {(categories.data ?? []).map((category) => (
                        <label className="chip selectable" key={category.id}>
                          <input
                            type="checkbox"
                            checked={materialCodes.includes(category.code)}
                            onChange={() => toggleMaterial(category.code)}
                          />
                          {category.name}
                        </label>
                      ))}
                    </div>
                  </Field>
                  <div className="btn-row">
                    <Button type="submit" disabled={appBusy || materialCodes.length === 0}>
                      {appBusy ? <Spinner onPrimary /> : null}
                      {appBusy ? 'Submitting…' : 'Submit application'}
                    </Button>
                    <Button type="button" className="ghost" onClick={() => setApplying(false)}>
                      Cancel
                    </Button>
                  </div>
                </form>
              )}
            </Card>
          ) : null}

          {user?.role === 'ADMIN' ? (
            <Note tone="info">You are signed in as an administrator — the admin console is in your account menu.</Note>
          ) : null}
        </div>
      </div>
    </>
  );
}
