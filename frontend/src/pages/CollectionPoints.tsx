import { useState } from 'react';
import { Button, Card, EmptyState, Field, Loading, Note, useAsync } from '../components/ui';
import { api, ApiError } from '../lib/api';
import { getBrowserLocation, type Coordinates } from '../lib/geo';

export function CollectionPoints() {
  const categories = useAsync(() => api.categories(), []);
  const [city, setCity] = useState('');
  const [material, setMaterial] = useState('');
  const [query, setQuery] = useState('');
  const [radiusKm, setRadiusKm] = useState(25);
  const [coords, setCoords] = useState<Coordinates | null>(null);
  const [locating, setLocating] = useState(false);
  const [applied, setApplied] = useState<{
    city?: string;
    material?: string;
    q?: string;
    lat?: number;
    lng?: number;
    radiusKm?: number;
  }>({});

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
  };

  return (
    <>
      <Card title="Find a verified collection point">
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
            🔍 Search
          </Button>
          <Button type="button" className="secondary" onClick={useMyLocation} disabled={locating}>
            {locating ? 'Locating…' : '📍 Use my location'}
          </Button>
          <Button type="button" className="ghost" onClick={clear}>
            Clear
          </Button>
        </div>
        {coords ? (
          <div className="small muted" style={{ marginTop: 10 }}>
            Searching within {radiusKm} km of {coords.lat.toFixed(3)}, {coords.lng.toFixed(3)}.
          </div>
        ) : null}
        {actionError ? (
          <div style={{ marginTop: 12 }}>
            <Note tone="warning">{actionError}</Note>
          </div>
        ) : null}
      </Card>

      {points.error ? <Note tone="error">{points.error}</Note> : null}

      {points.loading ? (
        <Loading label="Loading collection points…" />
      ) : (points.data?.length ?? 0) === 0 ? (
        <EmptyState icon="📍" title="No collection points matched">
          Try widening the radius, clearing the material filter, or searching another city. Administrators and verified
          collectors add the points shown here.
        </EmptyState>
      ) : (
        <div className="grid cols-2" style={{ marginTop: 16 }}>
          {points.data?.map((point) => (
            <Card key={point.id}>
              <div className="card-head">
                <h3 style={{ margin: 0 }}>{point.name}</h3>
                <div className="spacer" />
                {point.verified ? <span className="pill green">✓ Verified</span> : <span className="pill grey">Unverified</span>}
              </div>
              <div className="list-meta">
                {point.address}, {point.city} {point.pincode ?? ''}
              </div>
              <div className="inline wrap" style={{ gap: 8, margin: '10px 0' }}>
                {point.distanceKm !== null ? <span className="pill blue">{point.distanceKm} km away</span> : null}
                {point.operatingHours ? <span className="pill grey">🕒 {point.operatingHours}</span> : null}
                {point.contactPhone ? <span className="pill grey">📞 {point.contactPhone}</span> : null}
              </div>
              <div className="inline wrap" style={{ gap: 6 }}>
                {point.materials.map((materialItem) => (
                  <span className="chip" key={materialItem.code}>
                    <span className="swatch" style={{ background: materialItem.colorHex ?? '#6B705C' }} aria-hidden="true" />
                    {materialItem.name}
                  </span>
                ))}
              </div>
              {point.latitude !== null && point.longitude !== null ? (
                <div style={{ marginTop: 14 }}>
                  <a
                    className="btn secondary small"
                    href={`https://www.openstreetmap.org/?mlat=${point.latitude}&mlon=${point.longitude}#map=17/${point.latitude}/${point.longitude}`}
                    target="_blank"
                    rel="noreferrer"
                  >
                    🗺️ Open directions
                  </a>
                </div>
              ) : null}
            </Card>
          ))}
        </div>
      )}
    </>
  );
}
