import type {
  AdminAnalytics,
  AdminUser,
  AuthResponse,
  CategoryImpact,
  CollectionPartner,
  CollectionPoint,
  CollectorDashboard,
  HistoryResponse,
  ImpactResponse,
  Notification,
  Page,
  Pickup,
  PickupSummary,
  PickupStatus,
  Profile,
  Scan,
  TimeSlot,
  UserDto,
  WasteAnalysis,
  WasteCategory,
} from './types';

const ACCESS_KEY = 'reloop.accessToken';
const REFRESH_KEY = 'reloop.refreshToken';

export class ApiError extends Error {
  status: number;
  fieldErrors: Record<string, string>;

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

/** Thrown when the AI provider is not configured or unreachable (HTTP 503). */
export class AiUnavailableError extends ApiError {}

export const tokens = {
  access: () => localStorage.getItem(ACCESS_KEY),
  refresh: () => localStorage.getItem(REFRESH_KEY),
  save(auth: AuthResponse) {
    localStorage.setItem(ACCESS_KEY, auth.accessToken);
    localStorage.setItem(REFRESH_KEY, auth.refreshToken);
  },
  clear() {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
  },
};

type OnUnauthorized = () => void;
let onUnauthorized: OnUnauthorized = () => {};
export function setUnauthorizedHandler(handler: OnUnauthorized) {
  onUnauthorized = handler;
}

async function parseError(response: Response): Promise<ApiError> {
  let message = `Request failed (${response.status})`;
  let fieldErrors: Record<string, string> = {};
  try {
    const body = await response.json();
    if (body?.message) message = body.message;
    if (body?.fieldErrors) fieldErrors = body.fieldErrors;
  } catch {
    /* non-JSON error body */
  }
  return response.status === 503 ? new AiUnavailableError(response.status, message) : new ApiError(response.status, message, fieldErrors);
}

/** Single-flight refresh so concurrent 401s don't burn multiple refresh tokens. */
let refreshInFlight: Promise<boolean> | null = null;

async function refreshTokens(): Promise<boolean> {
  const refreshToken = tokens.refresh();
  if (!refreshToken) return false;
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      try {
        const response = await fetch('/api/auth/refresh', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken }),
        });
        if (!response.ok) return false;
        const auth = (await response.json()) as AuthResponse;
        tokens.save(auth);
        return true;
      } catch {
        return false;
      } finally {
        refreshInFlight = null;
      }
    })();
  }
  return refreshInFlight;
}

interface RequestOptions {
  method?: string;
  body?: unknown;
  form?: FormData;
  auth?: boolean;
  query?: Record<string, string | number | undefined | null>;
  retryOn401?: boolean;
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, form, auth = true, query, retryOn401 = true } = options;

  let url = path;
  if (query) {
    const params = new URLSearchParams();
    Object.entries(query).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') params.append(key, String(value));
    });
    const qs = params.toString();
    if (qs) url += `?${qs}`;
  }

  const headers: Record<string, string> = {};
  if (auth) {
    const token = tokens.access();
    if (token) headers.Authorization = `Bearer ${token}`;
  }
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  const response = await fetch(url, {
    method,
    headers,
    body: form ?? (body !== undefined ? JSON.stringify(body) : undefined),
  });

  if (response.status === 401 && auth && retryOn401) {
    const refreshed = await refreshTokens();
    if (refreshed) {
      return request<T>(path, { ...options, retryOn401: false });
    }
    tokens.clear();
    onUnauthorized();
    throw await parseError(response);
  }

  if (!response.ok) throw await parseError(response);
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

/** Payload for creating or updating a collection point (admin catalog). */
export interface CollectionPointInput {
  name: string;
  address: string;
  city: string;
  pincode?: string;
  latitude: number;
  longitude: number;
  operatingHours?: string;
  contactPhone?: string;
  materialCodes: string[];
}

export const api = {
  // -------- auth
  register: (input: { email: string; password: string; fullName: string; phone?: string }) =>
    request<AuthResponse>('/api/auth/register', { method: 'POST', body: input, auth: false }),
  login: (input: { email: string; password: string }) =>
    request<AuthResponse>('/api/auth/login', { method: 'POST', body: input, auth: false }),
  logout: (refreshToken: string) =>
    request<void>('/api/auth/logout', { method: 'POST', body: { refreshToken }, auth: false }),
  me: () => request<UserDto>('/api/auth/me'),
  forgotPassword: (email: string) =>
    request<{ message: string; devResetToken: string | null }>('/api/auth/forgot-password', {
      method: 'POST',
      body: { email },
      auth: false,
    }),
  resetPassword: (token: string, newPassword: string) =>
    request<void>('/api/auth/reset-password', { method: 'POST', body: { token, newPassword }, auth: false }),

  // -------- catalog
  categories: () => request<WasteCategory[]>('/api/waste/categories', { auth: false }),

  // -------- scans
  analyze: (image: File) => {
    const form = new FormData();
    form.append('image', image);
    return request<WasteAnalysis>('/api/waste/analyze', { method: 'POST', form });
  },
  saveScan: (input: {
    categoryId: string;
    detectedItem?: string;
    confidence?: number | null;
    recyclable?: boolean;
    hazardous?: boolean;
    disposalInstruction?: string;
    aiRawResponse?: string | null;
    image?: File | null;
  }) => {
    const form = new FormData();
    form.append('categoryId', input.categoryId);
    if (input.detectedItem) form.append('detectedItem', input.detectedItem);
    if (input.confidence !== undefined && input.confidence !== null) form.append('confidence', String(input.confidence));
    if (input.recyclable !== undefined) form.append('recyclable', String(input.recyclable));
    if (input.hazardous !== undefined) form.append('hazardous', String(input.hazardous));
    if (input.disposalInstruction) form.append('disposalInstruction', input.disposalInstruction);
    if (input.aiRawResponse) form.append('aiRawResponse', input.aiRawResponse);
    if (input.image) form.append('image', input.image);
    return request<Scan>('/api/waste/scans', { method: 'POST', form });
  },
  scans: (page = 0, size = 20) => request<Page<Scan>>('/api/waste/scans', { query: { page, size } }),
  scan: (id: string) => request<Scan>(`/api/waste/scans/${id}`),

  // -------- collection points
  collectionPoints: (query: {
    material?: string;
    city?: string;
    q?: string;
    lat?: number;
    lng?: number;
    radiusKm?: number;
  }) => request<CollectionPoint[]>('/api/collection-points', { query }),
  collectionPoint: (id: string, lat?: number, lng?: number) =>
    request<CollectionPoint>(`/api/collection-points/${id}`, { query: { lat, lng } }),

  // -------- pickups (resident)
  createPickup: (input: {
    categoryId: string;
    estimatedQuantityKg: number;
    address: string;
    city: string;
    pincode?: string;
    latitude?: number | null;
    longitude?: number | null;
    pickupDate: string;
    timeSlot: TimeSlot;
    notes?: string;
    photo?: File | null;
  }) => {
    const form = new FormData();
    form.append('categoryId', input.categoryId);
    form.append('estimatedQuantityKg', String(input.estimatedQuantityKg));
    form.append('address', input.address);
    form.append('city', input.city);
    if (input.pincode) form.append('pincode', input.pincode);
    if (input.latitude != null) form.append('latitude', String(input.latitude));
    if (input.longitude != null) form.append('longitude', String(input.longitude));
    form.append('pickupDate', input.pickupDate);
    form.append('timeSlot', input.timeSlot);
    if (input.notes) form.append('notes', input.notes);
    if (input.photo) form.append('photo', input.photo);
    return request<Pickup>('/api/pickups', { method: 'POST', form });
  },
  pickups: (status?: PickupStatus | 'ALL', page = 0, size = 20) =>
    request<Page<Pickup>>('/api/pickups', {
      query: { status: status && status !== 'ALL' ? status : undefined, page, size },
    }),
  pickup: (code: string) => request<Pickup>(`/api/pickups/${code}`),
  cancelPickup: (code: string) => request<Pickup>(`/api/pickups/${code}/cancel`, { method: 'PATCH' }),

  // -------- collector
  applyCollector: (input: {
    organizationName: string;
    contactPerson: string;
    phone: string;
    email?: string;
    address: string;
    city: string;
    pincode?: string;
    operatingHours?: string;
    registrationNumber?: string;
    materialCodes: string[];
  }) => request<CollectionPartner>('/api/collectors/apply', { method: 'POST', body: input }),
  myCollectorApplication: () => request<CollectionPartner>('/api/collectors/me'),
  collectorDashboard: () => request<CollectorDashboard>('/api/collector/dashboard'),
  /** The open pool: redacted summaries only, so no resident's address is exposed before assignment. */
  availablePickups: (query: { lat?: number; lng?: number; page?: number; size?: number } = {}) =>
    request<Page<PickupSummary>>('/api/collector/pickups', {
      query: { scope: 'available', lat: query.lat, lng: query.lng, page: query.page ?? 0, size: query.size ?? 20 },
    }),
  /** Jobs assigned to this organisation — full detail, including the address and photo. */
  myPickups: (page = 0, size = 20) =>
    request<Page<Pickup>>('/api/collector/pickups', { query: { scope: 'mine', page, size } }),
  acceptPickup: (code: string) => request<Pickup>(`/api/collector/pickups/${code}/accept`, { method: 'PATCH' }),
  schedulePickup: (code: string, scheduledAt: string) =>
    request<Pickup>(`/api/collector/pickups/${code}/schedule`, { method: 'PATCH', body: { scheduledAt } }),
  collectPickup: (code: string, actualQuantityKg: number, notes?: string) =>
    request<Pickup>(`/api/collector/pickups/${code}/collect`, {
      method: 'PATCH',
      body: { actualQuantityKg, notes },
    }),
  updatePickupStatus: (code: string, status: 'PROCESSING' | 'RECOVERED' | 'RECYCLED') =>
    request<Pickup>(`/api/collector/pickups/${code}/status`, { method: 'PATCH', body: { status } }),
  releasePickup: (code: string, reason?: string) =>
    request<Pickup>(`/api/collector/pickups/${code}/release`, { method: 'PATCH', query: { reason } }),

  // -------- history / impact
  history: (query: { material?: string; status?: string; from?: string; to?: string; page?: number; size?: number }) =>
    request<HistoryResponse>('/api/history', { query }),
  impact: () => request<ImpactResponse>('/api/impact'),

  // -------- notifications
  notifications: (page = 0, size = 20) => request<Page<Notification>>('/api/notifications', { query: { page, size } }),
  unreadCount: () => request<{ count: number }>('/api/notifications/unread-count'),
  markNotificationRead: (id: string) => request<void>(`/api/notifications/${id}/read`, { method: 'PATCH' }),
  markAllNotificationsRead: () => request<void>('/api/notifications/read-all', { method: 'PATCH' }),

  // -------- profile
  profile: () => request<Profile>('/api/profile'),
  updateProfile: (input: { fullName: string; phone?: string; city?: string; addressLine?: string }) =>
    request<Profile>('/api/profile', { method: 'PUT', body: input }),

  // -------- admin
  adminUsers: (q?: string, page = 0, size = 20) => request<Page<AdminUser>>('/api/admin/users', { query: { q, page, size } }),
  setUserStatus: (id: string, status: 'ACTIVE' | 'DISABLED') =>
    request<AdminUser>(`/api/admin/users/${id}/status`, { method: 'PATCH', body: { status } }),
  adminCollectors: (status?: string, page = 0, size = 20) =>
    request<Page<CollectionPartner>>('/api/admin/collectors', { query: { status, page, size } }),
  verifyCollector: (id: string) =>
    request<CollectionPartner>(`/api/admin/collectors/${id}/verify`, { method: 'PATCH' }),
  rejectCollector: (id: string, reason: string) =>
    request<CollectionPartner>(`/api/admin/collectors/${id}/reject`, { method: 'PATCH', body: { reason } }),
  adminPickups: (status?: string, page = 0, size = 20) =>
    request<Page<Pickup>>('/api/admin/pickups', { query: { status, page, size } }),
  analytics: () => request<AdminAnalytics>('/api/admin/analytics'),
  createCollectionPoint: (input: CollectionPointInput) =>
    request<CollectionPoint>('/api/admin/collection-points', { method: 'POST', body: input }),
  updateCollectionPoint: (id: string, input: CollectionPointInput) =>
    request<CollectionPoint>(`/api/admin/collection-points/${id}`, { method: 'PUT', body: input }),
  deleteCollectionPoint: (id: string) =>
    request<void>(`/api/admin/collection-points/${id}`, { method: 'DELETE' }),
  createCategory: (input: {
    code: string;
    name: string;
    description?: string;
    colorHex?: string;
    defaultDisposalInstructions?: string;
  }) => request<WasteCategory>('/api/admin/waste-categories', { method: 'POST', body: input }),
};

export type { CategoryImpact };
