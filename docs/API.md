# ReLoop API reference

Base URL: `http://localhost:8080` (dev). Interactive docs: `/swagger-ui.html`, OpenAPI JSON at
`/v3/api-docs`.

## Authentication

- JWT **access token** (HS256, 15 min default) sent as `Authorization: Bearer <token>`.
- Opaque **refresh token** (30 days default), stored server-side as a SHA-256 hash. Rotation on every
  refresh; logout revokes it.
- Stateless sessions; CSRF disabled; CORS is an explicit allow-list.

Unauthenticated access is permitted only for: `/api/auth/register`, `/api/auth/login`,
`/api/auth/refresh`, `/api/auth/forgot-password`, `/api/auth/reset-password`,
`GET /api/waste/categories`, `/uploads/**`, `/actuator/health`, `/actuator/info`, and the
swagger endpoints.

Roles: `USER`, `COLLECTOR`, `ADMIN`. `/api/admin/**` requires `ADMIN`, `/api/collector/**` requires
`COLLECTOR`, everything else requires an authenticated user.

### Errors

All errors share one shape:

```json
{ "status": 400, "error": "Bad Request", "message": "Validation failed",
  "fieldErrors": { "estimatedQuantityKg": "Estimated quantity must be at least 0.1 kg" },
  "timestamp": "2026-09-20T06:12:44.512Z" }
```

`401` unauthenticated, `403` authenticated but not permitted, `409` state conflicts
(e.g. cancelling an accepted pickup), `503` when the AI provider is unavailable or unconfigured.

## Auth

| Method | Path | Notes |
| --- | --- | --- |
| POST | `/api/auth/register` | `{email, password, fullName, phone?}` → `201` + tokens. Creates the profile and a welcome notification. |
| POST | `/api/auth/login` | `{email, password}` → tokens. Records `lastLoginAt`. |
| POST | `/api/auth/refresh` | `{refreshToken}` → rotated tokens. |
| POST | `/api/auth/logout` | `{refreshToken}` → `204`. |
| POST | `/api/auth/forgot-password` | `{email}` → generic message; **in dev mode** also returns `devResetToken` (no email infrastructure exists in this build). |
| POST | `/api/auth/reset-password` | `{token, newPassword}` → `204`. Revokes all refresh tokens for the user. |
| GET | `/api/auth/me` | Current `UserDto`. |

## Waste catalog & scans

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/api/waste/categories` | **Public.** Seeded taxonomy (10 active categories). |
| POST | `/api/waste/analyze` | `multipart/form-data` with `image`. Calls Gemini and returns a structured suggestion. **Nothing is persisted.** Returns `503` with a clear message when AI is unavailable so the client can fall back to manual classification. |
| POST | `/api/waste/scans` | `multipart/form-data`: `categoryId`, optional `detectedItem`, `confidence`, `recyclable`, `hazardous`, `disposalInstruction`, `aiRawResponse`, `image`. Supplying `confidence` marks the scan as `AI`, otherwise `MANUAL`. Images are validated by content (JPG/PNG/WEBP magic bytes, ≤ 8 MB) and the stored extension is derived from the detected type — the filename and the browser-declared content type are advisory and never reject a valid photo. |
| GET | `/api/waste/scans` | Paged, newest first. |
| GET | `/api/waste/scans/{id}` | Owner or admin only. |

## Collection points

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/api/collection-points` | Filters: `material` (category code), `city`, `q`, plus `lat`/`lng`/`radiusKm` (default 100 km) for distance sorting. `distanceKm` is returned when coordinates are supplied. |
| GET | `/api/collection-points/{id}` | Optional `lat`/`lng` for distance. |

## Pickups (resident)

| Method | Path | Notes |
| --- | --- | --- |
| POST | `/api/pickups` | `multipart/form-data`: `categoryId`, `estimatedQuantityKg` (≥ 0.1), `address`, `city`, `pincode?`, `latitude?`, `longitude?`, `pickupDate` (today or later), `timeSlot` (`MORNING`/`AFTERNOON`/`EVENING`), `notes?`, `photo?`. Returns a generated `RL-XXXXXX` code. |
| GET | `/api/pickups` | Optional `status`, paged. Own requests only (admins see all via `/api/admin/pickups`). |
| GET | `/api/pickups/{code}` | Requester, assigned collector, or admin only. |
| PATCH | `/api/pickups/{code}/cancel` | Only while status is `REQUESTED`; otherwise `409`. |

Lifecycle: `REQUESTED → ACCEPTED → SCHEDULED → PICKED_UP → PROCESSING → RECOVERED`, with
`CANCELLED` reachable from `REQUESTED`. Invalid transitions return `409`.

## Collector

Requires role `COLLECTOR` **and** a `VERIFIED` partner record.

| Method | Path | Notes |
| --- | --- | --- |
| POST | `/api/collectors/apply` | `{organizationName, contactPerson, phone, email?, address, city, pincode?, operatingHours?, registrationNumber?, materialCodes[]}` → `PENDING` application. |
| GET | `/api/collectors/me` | Own application, or `404`. |
| GET | `/api/collector/dashboard` | `{availableRequests, activeJobs, completedJobs, todayPickups, totalKgCollected, totalCollections}`. |
| GET | `/api/collector/pickups` | `scope=available` (all `REQUESTED`) or `scope=mine` (assigned to this partner). |
| PATCH | `/api/collector/pickups/{code}/accept` | Claims an available request. |
| PATCH | `/api/collector/pickups/{code}/schedule` | `{scheduledAt}` — must be in the future. |
| PATCH | `/api/collector/pickups/{code}/collect` | `{actualQuantityKg, notes?}` — writes the `collected_waste` record that history and impact both read. One record per pickup. |
| PATCH | `/api/collector/pickups/{code}/status` | `{status}` — `PROCESSING` or `RECOVERED` only. |
| PATCH | `/api/collector/pickups/{code}/release` | Returns an accepted/scheduled request to the pool. |

Every transition notifies the resident.

## History, impact, notifications, profile

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/api/history` | Filters: `material` (validated against the catalog), `status`, `from`, `to` (ISO dates), paged. Returns `totalKg` and `byCategory` alongside entries. Only actually-collected waste appears. |
| GET | `/api/impact` | Totals plus per-material kg and estimated CO₂e. See [IMPACT.md](./IMPACT.md). |
| GET | `/api/notifications` | Paged, newest first. |
| GET | `/api/notifications/unread-count` | `{ "count": n }`. |
| PATCH | `/api/notifications/{id}/read` | `204`. |
| PATCH | `/api/notifications/read-all` | `204`. |
| GET | `/api/profile` | Profile plus linked user. |
| PUT | `/api/profile` | `{fullName, phone?, city?, addressLine?}`. |

## Admin

All paths require role `ADMIN`.

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/api/admin/users` | `q` searches email and profile name; paged. |
| PATCH | `/api/admin/users/{id}/status` | `{status: ACTIVE\|DISABLED}`. |
| GET | `/api/admin/collectors` | `status=PENDING\|VERIFIED\|REJECTED` (omit for all). |
| PATCH | `/api/admin/collectors/{id}/verify` | Grants the account the `COLLECTOR` role. |
| PATCH | `/api/admin/collectors/{id}/reject` | `{reason}` — required, non-blank. |
| GET | `/api/admin/pickups` | Optional `status` filter across all residents. |
| GET | `/api/admin/analytics` | Users, collectors, pickups by status, collected kg by material. |
| POST | `/api/admin/collection-points` | Creates a verified point. |
| PUT / DELETE | `/api/admin/collection-points/{id}` | Update / soft-aware delete. |
| POST | `/api/admin/waste-categories` | Create a category. |
| PUT | `/api/admin/waste-categories/{id}` | Update a category. |
| PATCH | `/api/admin/waste-categories/{id}/active` | `{active: bool}`. |

## First admin account

No default credentials exist anywhere in the codebase. Set `ADMIN_EMAIL` and `ADMIN_PASSWORD` before
the first start and `DataInitializer` creates that account once; the step is skipped afterwards and
skipped entirely if an admin already exists.
