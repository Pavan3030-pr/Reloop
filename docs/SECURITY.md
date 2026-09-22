# Security model

This documents what ReLoop actually enforces and how it is verified. It is a description of the
current build, not a compliance claim.

## Authentication

- **JWT access tokens** (HS256, 15-minute TTL) carry the user id and role. Refresh tokens (30 days)
  are stored **hashed** in `refresh_tokens` and rotated on use.
- `JWT_SECRET` has **no default**. A missing or short (< 32 byte) secret fails startup with
  instructions rather than falling back to a shared value.
- Passwords are hashed with BCrypt.
- Sessions are stateless: no server-side session to hijack, and `SecurityConfig` installs JSON
  `401`/`403` handlers so an unauthenticated XHR never receives an HTML login page.
- `RELOOP_DEV_MODE=true` returns the password-reset token in the API response, because this build
  ships no mail transport. It is **off by default** and must never be enabled outside local
  development.

## Authorization

Two layers, both required:

1. **Route level** — `/api/admin/**` ⇒ `ADMIN`, `/api/collector/**` ⇒ `COLLECTOR`, everything else
   authenticated. CORS is restricted to `CORS_ALLOWED_ORIGINS`.
2. **Object level** — the layer that actually protects records. Route guards stop a resident from
   reaching the admin API; they do **not** stop one resident from reading another's pickup, because
   both are authenticated `USER`s. Services therefore resolve ownership or relationship on every
   read and mutation.

| Control | Where |
| --- | --- |
| Read/mutate only your own pickup, scan, notification, profile | `PickupService.assertCanView`, `cancelByUser`, `NotificationService`, `ProfileService` |
| Act only on requests assigned to *your* organisation | `PickupService.assertAssignedCollector` |
| Verified partner required for any collector operation | `CollectorService.verifiedPartnerOf` |
| `COLLECTOR` role granted only by admin verification | `CollectorService.verify` |

## Personal data in the open collector pool

The pool is visible to every verified partner, so it returns a redacted projection
(`PickupSummaryDto`): material, city, requested window and a kilometre-rounded distance. Address,
pincode, precise coordinates, notes, photo and resident name are withheld until a request is assigned
to that collector. Field-level regression cover: `CollectorPoolFilterTest.poolNeverLeaksRequesterDetails`.

## Concurrency and integrity

- `pickup_requests.version` gives optimistic locking on `accept`. Simultaneous accepts leave exactly
  one winner; the loser receives `409` and is **not** told it won. Verified by
  `AuthorizationAuditTest.concurrentAcceptOnlyOneCollectorWins` and re-run against a live server.
- `collected_waste.pickup_request_id` is `UNIQUE`, so a pickup cannot be collected twice even if two
  requests slip past the service.
- CHECK constraints cover status enums, latitude/longitude ranges, confidence `0..1` and
  `quantity_kg > 0`. Weights are additionally validated at the DTO layer
  (`@DecimalMin("0.01")`, `@Digits`), so a negative or absurd weight is rejected at the boundary.
- Impact is computed from `collected_waste` only, so nothing can be double-counted from an estimate.

## File uploads

`ImageStorageService.store` is content-authoritative:

- Sniffs magic bytes — PNG including the mandatory `IHDR` chunk at offset 12, JPEG `FF D8 FF`, WEBP
  `RIFF….WEBP`.
- Derives the stored extension from the **detected** type, never from the client's filename, so a
  crafted name cannot choose the extension written to disk.
- Enforces a size cap and rejects non-images, regardless of the declared MIME type.
- Generates stored filenames server-side; no client-supplied path segments reach the filesystem.

Covered by `ImageStorageServiceTest` (real byte signatures, including a PNG named with no extension
and a text file named `.png`) and by the end-to-end upload step in `FullFlowIntegrationTest`.

## Secrets

- `backend/.env` is git-ignored; `backend/.env.example` contains placeholders only.
- The Gemini key is read server-side and never sent to the browser — the client only ever calls
  `/api/waste/analyze`.
- No secret is logged. Gemini failures log the *reason* (invalid key, retired model) but never the
  key itself.
- **Known local hazard:** `application.yml` sets `spring.config.import: optional:file:.env`, so
  running the backend *or the test suite* from `backend/` loads that file into the process. Tests
  that assert the unconfigured-provider path must therefore pin the provider off explicitly rather
  than relying on the developer's machine having no key —
  see `FullFlowIntegrationTest`'s `@DynamicPropertySource`. Do not let a real credential influence a
  test's outcome.

## Error responses

`GlobalExceptionHandler` maps domain exceptions to a consistent JSON envelope
(`status`, `error`, `message`, optional `fieldErrors`) with meaningful status codes — `400`
malformed, `401` unauthenticated, `403` not yours, `404` absent, `409` illegal transition or lost
race, `503` provider unavailable. Stack traces and SQL errors are never returned to clients.

## Out of scope / not claimed

- No rate limiting on authentication endpoints in this build.
- No email verification flow (the flag exists; no transport is configured).
- No antivirus/AV scanning of uploads beyond format, size and content checks.
- No penetration test has been performed; the controls above are verified by the test suite listed
  in [ARCHITECTURE.md](./ARCHITECTURE.md#testing-shape) and by manual requests during development.
