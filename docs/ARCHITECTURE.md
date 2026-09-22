# Architecture

ReLoop is a three-tier application. The browser never talks to PostgreSQL, and it never talks to
Google directly.

```
┌──────────────────────────┐        ┌──────────────────────────┐        ┌──────────────────┐
│  React 19 + TypeScript   │  /api  │  Spring Boot 3.5 (Java 21)│  JDBC  │   PostgreSQL 14+ │
│  Vite dev server :5173   │ ─────▶ │  REST API on :8080        │ ─────▶ │  reloop_dev      │
│  (proxies /api,/uploads) │        │  JWT auth · JPA · Flyway  │        │  reloop_test     │
└──────────────────────────┘        └───────────┬──────────────┘        └──────────────────┘
                                                │
                     ┌──────────────────────────┼──────────────────────────┐
                     ▼                          ▼                          ▼
          Google Gemini API          local image storage           OSM tile server
          (generateContent)          (./data/uploads)              (Leaflet, browser-side)
          server-side key only
```

| Layer | Where | Notes |
| --- | --- | --- |
| Web client | `frontend/` | React 19, TypeScript, Vite 7, React Router 7. No UI kit: a purpose-built CSS design system in `frontend/src/styles.css`. |
| API | `backend/src/main/java/app/reloop/controller` | Thin controllers — validation and delegation only. |
| Domain logic | `.../service` | All business rules, transactions and authorization checks live here. |
| Persistence | `.../repository`, `.../entity` | Spring Data JPA. `ddl-auto: validate` — Flyway owns the schema. |
| Schema | `backend/src/main/resources/db/migration` | `V1__init.sql`, `V2__…`, `V3__recycled_status_and_concurrency_guards.sql`. Applied migrations are never edited. |
| AI | `.../integration/GeminiService` | The only outbound dependency. Wrapped so failure is a `503`, never a fabricated answer. |
| Files | `.../service/ImageStorageService` | Local disk for this build; content-sniffed, size-capped. |

## Request path

`Browser → Vite proxy → Controller (@Valid) → Service (@Transactional, authorization) →
Repository (JPA) → PostgreSQL`, with `GlobalExceptionHandler` translating domain exceptions into the
JSON error envelope documented in [API.md](./API.md).

## Roles and authorization

Authorization is enforced in two layers, and the second layer is the one that matters.

1. **Route level** (`SecurityConfig`): `/api/admin/**` requires `ADMIN`, `/api/collector/**` requires
   `COLLECTOR`, everything else is authenticated. Stateless JWT, JSON `401`/`403` handlers, CORS
   restricted to configured origins.
2. **Object level** (services): the role that opens the door is not the role that grants access to a
   *record*. Every read and mutation resolves the acting user's relationship to the row:

| Rule | Enforced in |
| --- | --- |
| A resident reads/mutates only their own pickups, scans, notifications and profile | `PickupService.assertCanView`, `cancelByUser`, `NotificationService`, `ProfileService` |
| An assigned collector reads the full record; anyone else gets `403` | `PickupService.assertCanView` |
| A collector acts only on requests assigned to *their own organisation* | `PickupService.assertAssignedCollector` |
| Only a **VERIFIED** partner gets collector access at all | `CollectorService.verifiedPartnerOf` |
| Admin-only operations | `SecurityConfig` + role check in `AdminService` |

Note that the `COLLECTOR` role is granted **only on admin verification** (`CollectorService.verify`),
so an unverified applicant is still `USER` and cannot reach the collector routes at all.

## Pickup state machine

Server-authoritative. The client may only *request* a transition; `PickupService` decides.

```
REQUESTED ──accept──▶ ACCEPTED ──schedule──▶ SCHEDULED ──collect──▶ PICKED_UP
    │                    │                       │                        │
    │                    └─────release──────────▶ (back to REQUESTED)     │
    │                                                                     ▼
 cancelByUser                                                        PROCESSING
    │                                                                    │
    ▼                                            ┌───────────────┬───────┘
 CANCELLED                                       ▼               ▼
                                              RECOVERED       RECYCLED
                                              (terminal)      (terminal)
```

| Transition | Allowed from | Actor | Rejected |
| --- | --- | --- | --- |
| `→ ACCEPTED` | `REQUESTED` unassigned | verified collector | `409` if taken |
| `→ SCHEDULED` | `ACCEPTED` | assigned collector | must be a future instant |
| `→ PICKED_UP` | `ACCEPTED`, `SCHEDULED` | assigned collector | writes `collected_waste` exactly once |
| `→ PROCESSING` | `PICKED_UP` | assigned collector or admin | |
| `→ RECOVERED` / `→ RECYCLED` | `PROCESSING` | assigned collector or admin | both terminal |
| `→ CANCELLED` | `REQUESTED` | owning resident | never after acceptance |
| `→ REQUESTED` (release) | `ACCEPTED`, `SCHEDULED` | assigned collector | clears assignment |

Anything else is a `409` with the rejected transition named. `actualQuantityKg` can only be written
by the collect step, and the database enforces one `collected_waste` row per `pickup_request`.

### Concurrency

`accept` is a read-check-write, so two collectors can reach it at the same instant. `pickup_requests`
carries a `version` column; the loser's `UPDATE` matches no rows and the transaction fails with an
optimistic-lock conflict, surfaced as `409`. The resident is notified once. This is covered by
`AuthorizationAuditTest.concurrentAcceptOnlyOneCollectorWins`.

## Privacy model

A household's address, pincode, precise coordinates, free-text notes and photo are personal data, and
the open collector pool is visible to every verified partner. Therefore:

- `GET /api/collector/pickups?scope=available` returns `PickupSummaryDto` — material, city, requested
  window and a **1 km-rounded** distance. No address, no coordinates, no notes, no photo, no name.
- Full detail (`PickupDto`) unlocks per request only once it is assigned to that collector.
- The collection-point map plots verified partner locations, which are business addresses.

The rounding is deliberate: it is enough to judge whether a trip is worth making, and too coarse to
locate a home.

## AI integration

`GeminiService` builds the `generateContent` request server-side (inline base64 image, model in the
path, key in `x-goog-api-key`, `response_mime_type: application/json`, no deprecated sampling
parameters), then parses and validates the reply: category codes are normalised against the seeded
catalogue, unknown codes fall back to `OTHER`, confidence is clamped to `0..1` and flagged below
`0.5`. Every failure mode — unconfigured, HTTP error, malformed payload, missing item — becomes a
`503` with a human-readable message. A scan the resident confirms is stored with `source = AI`;
manual classification stores `source = MANUAL`.

## Images

`ImageStorageService.store` sniffs magic bytes (PNG with its mandatory `IHDR` chunk, JPEG, WEBP) and
derives the stored extension from the **detected** content. Size limits, path-traversal protection
and non-image rejection are enforced regardless of the uploaded filename or declared MIME type.

## Impact

History and impact read `collected_waste` only — never a scan, never an estimated pickup weight. See
[IMPACT.md](./IMPACT.md) for the measured/estimated split and the CO₂e coefficients.

## Testing shape

| Suite | What it proves |
| --- | --- |
| `FullFlowIntegrationTest` | The whole journey over real HTTP against real PostgreSQL, including role authorization, invalid transitions and the unconfigured-AI `503`. |
| `AiAnalysisWithStubProviderTest` | The real analysis path against a local stub **at the network boundary only** — valid, low-confidence, malformed, provider-failure and image-validation cases. |
| `AuthorizationAuditTest` | Concurrency, redaction, cross-object access and mutation between residents and between collectors. |
| `CollectorPoolFilterTest` | Pool filtering correctness and that filtering never widens what is exposed. |
| `ImageStorageServiceTest`, `GeminiServiceTest`, `JwtServiceTest`, `GeoUtilsTest` | Unit coverage of content sniffing, response parsing, tokens and distance maths. |
