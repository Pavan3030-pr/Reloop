import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Button, Card, EmptyState, Field, Loading, Note, PageHead, useAsync } from '../components/ui';
import { Icon } from '../components/icons';
import { api, ApiError } from '../lib/api';
import { getBrowserLocation, type Coordinates } from '../lib/geo';

interface Applied {
  city?: string;
  material?: string;
  q?: string;
  lat?: number;
  lng?: number;
  radiusKm?: number;
}

export function CollectionPoints() {
  const categories = useAsync(() => api.categories(), []);
  const [city, setCity] = useState('');
  const [material, setMaterial] = useState('');
  const [query, setQuery] = useState('');
  const [radiusKm, setRadiusKm] = useState(25);
  const [coords, setCoords] = useState<Coordinates | null>(null);
  const [locating, setLocating] = useState(false);
  const [applied, setApplied] = useState<Applied>({});
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const points = useAsync(
    () => api.collectionPoints(applied),
    [applied.city, applied.material, applied.q, applied.lat, applied.lng, applied.radiusKm],
  );
  const [actionError, setActionError] = useState<string | null>(null);

  const search = () => setApplied({ city, material, q: query, lat: coords?.lat, lng: coords?.lng, radiusKm });

  const useMyLocation = async () => {
    setLocating(true);
    setActionError(null);
    try {
      const position = await getBrowserLocation();
      if (!position) {
        setActionError('Location permission was declined. You can still search by city or material.');
        setLocating(false);
        return;
      }
      setCoords(position);
      setApplied({ city, material, q: query, lat: position.lat, lng: position.lng, radiusKm });
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Could not read your location.');
    } finally {
      setLocating(false);
    }
  };

  const clear = () => {
    setCity('');
    setMaterial('');
    setQuery('');
    setApplied({});
    setSelectedId(null);
  };

  const results = points.data ?? [];
  const mapped = results.filter((point) => point.latitude !== null && point.longitude !== null);

  /**
   * Plots real coordinates on a bounded panel — no external map service, so the
   * page stays fast and every pin is a record you can open.
   */
  const plot = useMemo(() => {
    const latList = [...mapped.map((point) => point.latitude as number)];
    const lngList = [...mapped.map((point) => point.longitude as number)];
    if (coords) {
      latList.push(coords.lat);
      lngList.push(coords.lng);
    }
    if (latList.length === 0) return null;

    const minLat = Math.min(...latList);
    const maxLat = Math.max(...latList);
    const minLng = Math.min(...lngList);
    const maxLng = Math.max(...lngList);
    const latPad = Math.max((maxLat - minLat) * 0.18, 0.004);
    const lngPad = Math.max((maxLng - minLng) * 0.18, 0.004);

    return { minLat: minLat - latPad, maxLat: maxLat + latPad, minLng: minLng - lngPad, maxLng: maxLng + lngPad };
  }, [mapped, coords]);

  const positionOf = (lat: number, lng: number) => {
    if (!plot) return { left: '50%', top: '50%' };
    const spanLat = plot.maxLat - plot.minLat || 1;
    const spanLng = plot.maxLng - plot.minLng || 1;
    const left = ((lng - plot.minLng) / spanLng) * 100;
    const top = 100 - ((lat - plot.minLat) / spanLat) * 100;
    const clamp = (value: number) => Math.min(94, Math.max(6, value));
    return { left: `${clamp(left)}%`, top: `${clamp(top)}%` };
  };

  const hasFilters = Boolean(applied.city || applied.material || applied.q || applied.lat);

  return (
    <>
      <PageHead
        eyebrow="Directory"
        title="Collection points"
        lede="Verified drop-off locations with the materials each one accepts. Search by city, material or keyword, or sort by distance from your location."
      />

      <Card>
        <div className="inline-fields">
          <Field label="City" htmlFor="city">
            <input id="city" value={city} onChange={(e) => setCity(e.target.value)} placeholder="Hyderabad" />
          </Field>
          <Field label="Material" htmlFor="material">
            <select id="material" value={material} onChange={(e) => setMaterial(e.target.value)}>
              <option value="">Any material</option>
              {categories.data
                ?.filter((c) => c.active)
                .map((category) => (
                  <option key={category.id} value={category.code}>
                    {category.name}
                  </option>
                ))}
            </select>
          </Field>
          <Field label="Keyword" htmlFor="q">
            <input id="q" value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Hub, address…" />
          </Field>
          <Field label="Radius (km)" htmlFor="radius">
            <input
              id="radius"
              type="number"
              min={1}
              max={200}
              value={radiusKm}
              onChange={(e) => setRadiusKm(Number(e.target.value) || 25)}
            />
          </Field>
        </div>
        <div className="btn-row">
          <Button type="button" onClick={search}>
            <Icon name="search" size={17} />
            Search
          </Button>
          <Button type="button" className="secondary" onClick={useMyLocation} disabled={locating}>
            <Icon name="pin" size={16} />
            {locating ? 'Locating…' : coords ? 'Update my location' : 'Use my location'}
          </Button>
          {hasFilters || coords ? (
            <Button type="button" className="ghost" onClick={clear}>
              Clear
            </Button>
          ) : null}
        </div>
        {coords ? (
          <div className="small muted" style={{ marginTop: 12 }}>
            Searching within {radiusKm} km of {coords.lat.toFixed(3)}, {coords.lng.toFixed(3)}.
          </div>
        ) : null}
        {actionError ? (
          <div style={{ marginTop: 14 }}>
            <Note tone="warning">{actionError}</Note>
          </div>
        ) : null}
      </Card>

      {points.error ? <Note tone="error">{points.error}</Note> : null}

      {points.loading ? (
        <Loading label="Loading collection points…" />
      ) : results.length === 0 ? (
        <div style={{ marginTop: 18 }}>
          <EmptyState
            icon={<Icon name="pin" size={20} />}
            title="No collection points matched"
            action={
              hasFilters ? (
                <Button type="button" className="secondary small" onClick={clear}>
                  Clear filters
                </Button>
              ) : undefined
            }
          >
            Try widening the radius, clearing the material filter, or searching another city. Administrators and
            verified collectors maintain the records shown here.
          </EmptyState>
        </div>
      ) : (
        <div className="grid cols-2" style={{ marginTop: 18, alignItems: 'start' }}>
          <div>
            <div className="map-panel" role="img" aria-label="Map of matching collection points">
              {mapped.length === 0 ? (
                <div className="map-legend">These records have no coordinates on file.</div>
              ) : null}
              {mapped.map((point) => {
                const selected = selectedId === point.id;
                return (
                  <button
                    type="button"
                    key={point.id}
                    className="map-pin"
                    style={positionOf(point.latitude as number, point.longitude as number)}
                    onClick={() => setSelectedId(point.id)}
                    aria-label={`${point.name}, ${point.city}`}
                  >
                    <span
                      className="pin-dot"
                      style={selected ? { background: 'var(--forest-3)', transform: 'rotate(-45deg) scale(1.12)' } : undefined}
                      aria-hidden="true"
                    >
                      <Icon name="recycle" size={13} />
                    </span>
                    {mapped.length <= 8 || selected ? <span className="pin-label">{point.name}</span> : null}
                  </button>
                );
              })}
              {coords && plot ? <span className="map-me" style={positionOf(coords.lat, coords.lng)} title="Your location" /> : null}
              <span className="map-legend">
                {mapped.length} of {results.length} records plotted · real coordinates
              </span>
            </div>
            {coords ? (
              <p className="caption">
                Your approximate location is shown as a dot. Collection point positions come from the directory records
                themselves — verify the address before travelling.
              </p>
            ) : (
              <p className="caption">
                Positions come from the directory records. Use “Use my location” to see how far each point is from you.
              </p>
            )}
          </div>

          <div className="stack">
            {results.map((point) => (
              <Card key={point.id} className={`lift${selectedId === point.id ? ' selected' : ''}`}>
                <div className="card-head">
                  <span className="icon-tile" aria-hidden="true">
                    <Icon name="pin" size={17} />
                  </span>
                  <h3 style={{ margin: 0 }}>{point.name}</h3>
                  <span className="spacer" />
                  {point.verified ? (
                    <span className="pill green">
                      <Icon name="shield" size={12} />
                      Verified
                    </span>
                  ) : (
                    <span className="pill grey">Unverified</span>
                  )}
                </div>

                <div className="list-meta">
                  {point.address}, {point.city} {point.pincode ?? ''}
                </div>

                <div className="inline wrap" style={{ gap: 8, margin: '12px 0' }}>
                  {point.distanceKm !== null ? (
                    <span className="pill blue">
                      <Icon name="route" size={12} />
                      {point.distanceKm} km away
                    </span>
                  ) : null}
                  {point.operatingHours ? (
                    <span className="pill grey">
                      <Icon name="clock" size={12} />
                      {point.operatingHours}
                    </span>
                  ) : null}
                  {point.contactPhone ? (
                    <span className="pill grey">
                      <Icon name="phone" size={12} />
                      {point.contactPhone}
                    </span>
                  ) : null}
                </div>

                <div className="inline wrap" style={{ gap: 6 }}>
                  {point.materials.map((item) => (
                    <span className="chip" key={item.code}>
                      <span className="swatch" style={{ background: item.colorHex ?? '#6B705C' }} aria-hidden="true" />
                      {item.name}
                    </span>
                  ))}
                </div>

                <div className="btn-row" style={{ marginTop: 16 }}>
                  {point.latitude !== null && point.longitude !== null ? (
                    <a
                      className="btn secondary small"
                      href={`https://www.openstreetmap.org/?mlat=${point.latitude}&mlon=${point.longitude}#map=17/${point.latitude}/${point.longitude}`}
                      target="_blank"
                      rel="noreferrer"
                    >
                      <Icon name="external" size={15} />
                      Open directions
                    </a>
                  ) : null}
                  {point.verified ? (
                    <span className="small muted">
                      <Icon name="info" size={13} /> Verified by a platform administrator
                    </span>
                  ) : null}
                </div>
              </Card>
            ))}
          </div>
        </div>
      )}

      <Card title="No drop-off near you?">
        <p className="muted small" style={{ marginBottom: 16, maxWidth: '68ch' }}>
          Request a pickup instead. A verified collector accepts the job in their city, schedules a visit and records
          the weight collected on site.
        </p>
        <div className="btn-row">
          <Link className="btn small" to="/pickups">
            Book a pickup
            <Icon name="arrowRight" size={15} />
          </Link>
          <Link className="btn ghost small" to="/scan">
            Scan an item first
          </Link>
        </div>
      </Card>
    </>
  );
}
