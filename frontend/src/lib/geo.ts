export interface Coordinates {
  lat: number;
  lng: number;
}

/**
 * Requests the browser location. Resolves to null when the user declines or the
 * browser cannot provide a position — callers fall back to manual entry/search.
 */
export function getBrowserLocation(): Promise<Coordinates | null> {
  if (!('geolocation' in navigator)) return Promise.resolve(null);
  return new Promise((resolve) => {
    navigator.geolocation.getCurrentPosition(
      (position) => resolve({ lat: position.coords.latitude, lng: position.coords.longitude }),
      () => resolve(null),
      { enableHighAccuracy: false, timeout: 8000, maximumAge: 300000 },
    );
  });
}
