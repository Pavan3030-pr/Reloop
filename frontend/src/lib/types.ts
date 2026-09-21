/** Types mirroring the backend DTOs (see backend/src/main/java/app/reloop/dto). */

export type Role = 'USER' | 'COLLECTOR' | 'ADMIN';
export type UserStatus = 'ACTIVE' | 'DISABLED';

export interface UserDto {
  id: string;
  email: string;
  role: Role;
  status: UserStatus;
  emailVerified: boolean;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserDto;
}

export interface WasteCategory {
  id: string;
  code: string;
  name: string;
  description: string | null;
  colorHex: string | null;
  defaultDisposalInstructions: string | null;
  active: boolean;
}

export interface WasteAnalysis {
  item: string;
  categoryCode: string;
  categoryName: string;
  confidence: number | null;
  recyclable: boolean;
  hazardous: boolean;
  disposalInstruction: string | null;
  lowConfidence: boolean;
  aiModel: string | null;
  aiRawResponse: string | null;
}

export interface Scan {
  id: string;
  imageUrl: string | null;
  detectedItem: string | null;
  category: WasteCategory | null;
  confidence: number | null;
  recyclable: boolean | null;
  hazardous: boolean | null;
  disposalInstruction: string | null;
  source: 'AI' | 'MANUAL';
  createdAt: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

export interface CollectionPointMaterial {
  code: string;
  name: string;
  colorHex: string | null;
}

export interface CollectionPoint {
  id: string;
  name: string;
  address: string;
  city: string;
  pincode: string | null;
  latitude: number | null;
  longitude: number | null;
  operatingHours: string | null;
  contactPhone: string | null;
  verified: boolean;
  source: string;
  materials: CollectionPointMaterial[];
  distanceKm: number | null;
}

export type PickupStatus =
  | 'REQUESTED'
  | 'ACCEPTED'
  | 'SCHEDULED'
  | 'PICKED_UP'
  | 'PROCESSING'
  | 'RECOVERED'
  | 'RECYCLED'
  | 'CANCELLED';

export type TimeSlot = 'MORNING' | 'AFTERNOON' | 'EVENING';

/**
 * What a collector sees about a request before it is assigned to them: material, city and a
 * kilometre-rounded distance. The resident's address, coordinates, notes and photo are not part of
 * this payload — the API withholds them until the request is assigned. See Pickup for the full
 * record, which a collector gets for their own jobs.
 */
export interface PickupSummary {
  code: string;
  status: PickupStatus;
  category: WasteCategory | null;
  estimatedQuantityKg: number;
  city: string;
  pickupDate: string;
  timeSlot: TimeSlot;
  createdAt: string;
  approximateDistanceKm: number | null;
}

export interface Pickup {
  code: string;
  status: PickupStatus;
  category: WasteCategory | null;
  estimatedQuantityKg: number;
  actualQuantityKg: number | null;
  address: string;
  city: string;
  pincode: string | null;
  latitude: number | null;
  longitude: number | null;
  pickupDate: string;
  timeSlot: TimeSlot;
  photoUrl: string | null;
  notes: string | null;
  requesterName: string | null;
  collectorOrganization: string | null;
  scheduledAt: string | null;
  acceptedAt: string | null;
  pickedUpAt: string | null;
  processingAt: string | null;
  recoveredAt: string | null;
  cancelledAt: string | null;
  cancelReason: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CollectorDashboard {
  availableRequests: number;
  activeJobs: number;
  completedJobs: number;
  todayPickups: number;
  totalKgCollected: number;
  totalCollections: number;
}

export type PartnerStatus = 'PENDING' | 'VERIFIED' | 'REJECTED';

export interface CollectionPartner {
  id: string;
  organizationName: string;
  contactPerson: string;
  phone: string;
  email: string | null;
  address: string;
  city: string;
  pincode: string | null;
  operatingHours: string | null;
  registrationNumber: string | null;
  status: PartnerStatus;
  rejectionReason: string | null;
  verifiedAt: string | null;
  userEmail: string | null;
  materialCodes: string[];
  createdAt: string;
}

export interface HistoryEntry {
  id: string;
  pickupCode: string;
  pickupStatus: PickupStatus;
  categoryCode: string;
  categoryName: string;
  categoryColor: string | null;
  quantityKg: number;
  collectionDate: string;
  collectorOrganization: string | null;
  notes: string | null;
}

export interface HistoryResponse {
  entries: Page<HistoryEntry>;
  totalKg: number;
  byCategory: { code: string; name: string; kg: number }[];
}

export interface CategoryImpact {
  code: string;
  name: string;
  colorHex: string | null;
  kg: number;
  estimatedCo2eKgSaved: number;
}

export interface ImpactResponse {
  totalCollectedKg: number;
  completedPickups: number;
  estimatesAreApproximations: boolean;
  methodology: string;
  byCategory: CategoryImpact[];
}

export interface Notification {
  id: string;
  type: string;
  title: string;
  message: string;
  referenceId: string | null;
  read: boolean;
  createdAt: string;
}

export interface Profile {
  user: UserDto;
  fullName: string;
  phone: string | null;
  city: string | null;
  addressLine: string | null;
}

export interface AdminUser {
  id: string;
  email: string;
  role: Role;
  status: UserStatus;
  emailVerified: boolean;
  fullName: string | null;
  lastLoginAt: string | null;
  createdAt: string;
}

export interface AdminAnalytics {
  totalUsers: number;
  verifiedCollectors: number;
  pendingCollectorApplications: number;
  pickupsByStatus: Record<string, number>;
  totalPickups: number;
  completedCollections: number;
  totalCollectedKg: number;
  collectedKgByCategory: Record<string, number>;
}

export interface ApiErrorBody {
  status: number;
  error: string;
  message: string;
  fieldErrors?: Record<string, string>;
  timestamp?: string;
}
