# ReLoop

**Give every piece of waste a better destination.**

ReLoop is a waste-management and circular-economy platform. A resident photographs an item, gets an
AI-suggested recycling stream, finds a verified collection point, books a pickup, and follows that
pickup until a collector weighs the material and it is recovered. Only *actually* weighed waste
feeds the recycling history and impact figures.

```
resident ──▶ scan / AI classification ──▶ collection point or pickup request
                                              │
                        verified collector ◀──┘
                                  │
              accept → schedule → weigh & collect → processing → recovered
                                  │
                                  ▼
                    recycling history + impact (kg, kg CO₂e)
```

## Repository layout

| Path | Contents |
| --- | --- |
| `backend/` | Spring Boot 3.5 service (Java 21) — REST API, JWT auth, JPA/Flyway, Gemini integration |
| `frontend/` | React 19 + TypeScript + Vite web client (light climate-tech design system) |
| `database/` | Notes on the schema and how migrations are managed |
| `docs/` | [API reference](./docs/API.md) and [impact methodology](./docs/IMPACT.md) |
| `scripts/` | Local setup and run helpers |

## Stack

- **Backend:** Spring Boot 3.5.6, Java 21, Spring Web/Security/Data JPA/Validation/Actuator,
  Flyway, PostgreSQL, JJWT, springdoc-openapi, Lombok.
- **Frontend:** React 19, TypeScript, Vite 7, React Router 7. No UI kit — a purpose-built CSS design
  system (`frontend/src/styles.css`).
- **AI:** Google Gemini (`generateContent`) for waste classification, called **only** from the
  backend. The API key never reaches the browser.

## Quick start

Prerequisites: **JDK 21+**, **Node 20+**, **PostgreSQL 14+**.

```bash
# 1. Databases (creates reloop_dev and reloop_test)
./scripts/db-setup.sh

# 2. Backend on :8080 — migrations run automatically on start
./scripts/dev-backend.sh

# 3. Frontend on :5173 (proxies /api and /uploads to :8080)
./scripts/dev-frontend.sh
```

Open <http://localhost:5173>. Set `ADMIN_EMAIL` / `ADMIN_PASSWORD` (see below) before the first
backend start to bootstrap an administrator.

### Configuration

The backend reads everything from the environment — no secrets are committed.

| Variable | Required | Purpose |
| --- | --- | --- |
| `DB_URL` | no | Defaults to `jdbc:postgresql://localhost:5432/reloop_dev` |
| `DB_USERNAME` / `DB_PASSWORD` | no | Default to the OS user (works with trust/peer auth locally) |
| `JWT_SECRET` | **yes** | ≥ 32 characters, HS256 signing key |
| `GEMINI_API_KEY` | for AI | Without it, `/api/waste/analyze` returns `503` and the UI falls back to manual classification |
| `GEMINI_MODEL` | no | Defaults to `gemini-2.0-flash` |
| `CORS_ALLOWED_ORIGINS` | prod | Comma-separated allow-list |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | first run | Bootstraps the first admin account |
| `RELOOP_DEV_MODE` | no | `true` returns the password-reset token in the API response (there is no mail transport in this build) |
| `STORAGE_LOCAL_DIR` / `STORAGE_PUBLIC_BASE_URL` | no | Local image storage location and public URL prefix |

## Testing

The backend suite runs against a real PostgreSQL database (`reloop_test`) and the real HTTP stack —
no mocked success paths.

```bash
cd backend && ./mvnw test      # 39 tests
cd frontend && npm run build   # typecheck + production build
cd frontend && npm run dev     # dev server
```

What the suite covers:

- **`FullFlowIntegrationTest`** — the complete journey over real HTTP: register → login → scan →
  pickup → collector application → admin verification → accept → schedule → record actual weight →
  processing → recovered → notifications → history → impact, plus password reset, profile updates,
  role authorization and rejection of invalid state transitions.
- **`AiAnalysisWithStubProviderTest`** — the real analysis path (controller → service → Gemini
  client → response parsing) against a deterministic local stub at the network boundary, covering a
  valid structured response, a low-confidence response, a malformed provider payload and a provider
  failure (`503`).
- **`GeminiServiceTest`**, **`JwtServiceTest`**, **`GeoUtilsTest`** — unit coverage of response
  parsing, token issue/parse/expiry and distance maths.

## Design decisions worth knowing

- **The AI never invents data.** If Gemini is unconfigured or failing, the endpoint returns `503`
  with a human-readable reason and the client offers manual classification. There is no canned
  fallback "prediction".
- **Only measured weight counts.** History and impact read `collected_waste`, written when a
  collector records the actual weighed quantity. Estimated pickup weights never contribute.
- **CO₂e is labelled as an estimate** everywhere it appears, with the coefficients and their
  limitations documented in [docs/IMPACT.md](./docs/IMPACT.md).
- **Collectors are vetted.** A collector can only act after an administrator verifies the
  application; verification is what grants the `COLLECTOR` role.
- **Images are validated by content, not filename** — magic-byte sniffing plus size and type checks.

## Status

Implemented and verified end to end: authentication (JWT + rotating refresh tokens, password reset),
waste scanning with AI-assisted classification and manual fallback, collection-point directory with
distance search, the full pickup lifecycle, collector application/verification and workspace,
notifications, recycling history, impact reporting, and an admin console (directory, catalog,
analytics).
