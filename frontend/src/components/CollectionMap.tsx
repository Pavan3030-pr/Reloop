import { useEffect, useRef, useState } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import type { CollectionPoint } from '../lib/types';
import type { Coordinates } from '../lib/geo';

/**
 * A real map for the collection directory: Leaflet with OpenStreetMap tiles, plotting the
 * coordinates each record already carries. Nothing is invented — a pin exists only because an
 * administrator or verified collector saved that point, and unverified records are visually
 * distinct from verified ones rather than blended in.
 *
 * Marker icons are drawn as inline HTML instead of Leaflet's bundled PNGs, which keeps the mark
 * consistent with the app's identity and avoids the well-known bundler asset issue.
 */
export function CollectionMap({
  points,
  selectedId,
  onSelect,
  user,
  height = 420,
}: {
  points: CollectionPoint[];
  selectedId: string | null;
  onSelect: (id: string) => void;
  user?: Coordinates | null;
  height?: number;
}) {
  const container = useRef<HTMLDivElement | null>(null);
  const map = useRef<L.Map | null>(null);
  const layer = useRef<L.LayerGroup | null>(null);
  const [tilesFailed, setTilesFailed] = useState(false);

  useEffect(() => {
    if (!container.current || map.current) return;

    const instance = L.map(container.current, {
      scrollWheelZoom: false, // don't hijack page scrolling
      attributionControl: true,
    });
    const tiles = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
    });
    tiles.on('tileerror', () => setTilesFailed(true));
    tiles.addTo(instance);

    layer.current = L.layerGroup().addTo(instance);
    map.current = instance;

    return () => {
      instance.remove();
      map.current = null;
      layer.current = null;
    };
  }, []);

  useEffect(() => {
    const instance = map.current;
    const group = layer.current;
    if (!instance || !group) return;

    group.clearLayers();
    const bounds = L.latLngBounds([]);

    points.forEach((point) => {
      if (point.latitude === null || point.longitude === null) return;
      const latlng: L.LatLngExpression = [point.latitude, point.longitude];
      bounds.extend(latlng);

      const selected = point.id === selectedId;
      const icon = L.divIcon({
        className: 'reloop-pin-wrap',
        html:
          `<span class="reloop-pin${point.verified ? ' verified' : ' unverified'}${selected ? ' selected' : ''}" ` +
          `role="img" aria-label="${point.verified ? 'Verified' : 'Unverified'} collection point: ${escapeHtml(point.name)}">` +
          `<span class="pin-glyph" aria-hidden="true">${RECYCLE_GLYPH}</span></span>`,
        iconSize: [30, 30],
        iconAnchor: [15, 15],
        popupAnchor: [0, -14],
      });

      const materials = point.materials.length
        ? point.materials.map((material) => escapeHtml(material.name)).join(', ')
        : 'No materials listed';

      const marker = L.marker(latlng, { icon, title: point.name, riseOnHover: true }).addTo(group);
      marker.bindPopup(
        `<div class="map-popup">` +
          `<div class="mp-name">${escapeHtml(point.name)}</div>` +
          `<div class="mp-meta">${escapeHtml(point.address)}, ${escapeHtml(point.city)}</div>` +
          `<div class="mp-badge ${point.verified ? 'ok' : 'plain'}">` +
          `${point.verified ? 'Verified by a platform administrator' : 'Unverified directory record'}</div>` +
          `<div class="mp-meta">Accepts: ${materials}</div>` +
          (point.operatingHours ? `<div class="mp-meta">${escapeHtml(point.operatingHours)}</div>` : '') +
          `<a class="mp-link" href="https://www.openstreetmap.org/?mlat=${point.latitude}&mlon=${point.longitude}` +
          `#map=17/${point.latitude}/${point.longitude}" target="_blank" rel="noreferrer">Open directions</a>` +
          `</div>`,
      );
      marker.on('click', () => onSelect(point.id));
      if (selected) marker.openPopup();
    });

    if (user) {
      bounds.extend([user.lat, user.lng]);
      L.marker([user.lat, user.lng], {
        icon: L.divIcon({
          className: 'reloop-pin-wrap',
          html: '<span class="reloop-me" role="img" aria-label="Your approximate location"></span>',
          iconSize: [16, 16],
          iconAnchor: [8, 8],
        }),
        title: 'Your approximate location',
      }).addTo(group);
    }

    if (bounds.isValid()) {
      instance.fitBounds(bounds, { padding: [36, 36], maxZoom: 15 });
    }
  }, [points, selectedId, onSelect, user]);

  useEffect(() => {
    // The panel is inside a responsive grid; Leaflet needs a nudge when it changes size.
    const instance = map.current;
    if (!instance) return;
    const observer = new ResizeObserver(() => instance.invalidateSize());
    if (container.current) observer.observe(container.current);
    return () => observer.disconnect();
  }, []);

  return (
    <div className="map-shell">
      <div ref={container} className="map-canvas" style={{ height }} role="application" aria-label="Map of matching collection points" />
      {tilesFailed ? (
        <p className="map-note">
          The OpenStreetMap basemap could not be loaded (offline or blocked). Pins and popups still show the real
          coordinates from the directory records.
        </p>
      ) : null}
    </div>
  );
}

/** The ♻ geometry used on pins, inlined so the icon needs no image request. */
const RECYCLE_GLYPH =
  '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" ' +
  'stroke-linejoin="round" aria-hidden="true"><g transform="rotate(0 12 12)">' +
  '<path d="M12 5 A7 7 0 0 1 18.06 15.5"/><path d="M16.67 14.7 L19.45 16.3 L16.96 17.4"/></g>' +
  '<g transform="rotate(120 12 12)"><path d="M12 5 A7 7 0 0 1 18.06 15.5"/>' +
  '<path d="M16.67 14.7 L19.45 16.3 L16.96 17.4"/></g>' +
  '<g transform="rotate(240 12 12)"><path d="M12 5 A7 7 0 0 1 18.06 15.5"/>' +
  '<path d="M16.67 14.7 L19.45 16.3 L16.96 17.4"/></g></svg>';

/** Place names come from admin-entered records, so they are escaped before reaching innerHTML. */
function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}
