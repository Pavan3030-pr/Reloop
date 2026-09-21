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
  system (`frontend/src/styles.css`). Leaflet + OpenStreetMap tiles are used for the
  collection-point map and are lazy-loaded into their own chunk, so the entry bundle stays clean.
  The ♻ brand mark is inline SVG (`components/ReLoopMark.tsx`) and doubles as the favicon.
- **AI:** Google Gemini (`generateContent`) for waste classification, called **only** from the
  backend. The API key never reaches the browser.

## Quick start

Prerequisites: **JDK 21+**, **Node 20+**, **PostgreSQL 14+**.

```bash
# 1. Databases (creates reloop_dev and reloop_test)
./scripts/db-setup.sh

# 2. Local backend environment — creates backend/.env (git-ignored)
cp backend/.env.example backend/.env
printf 'JWT_SECRET=%s\n' "$(openssl rand -base64 48)" >> backend/.env

# 3. Backend on :8080 — migrations run automatically on start
cd backend && ./mvnw spring-boot:run

# 4. Frontend on :5173 (proxies /api and /uploads to :8080)
./scripts/dev-frontend.sh
```

Open <http://localhost:5173>. Set `ADMIN_EMAIL` / `ADMIN_PASSWORD` (see below) before the first
backend start to bootstrap an administrator.

### Health check

```bash
curl http://localhost:8080/actuator/health   # {"status":"UP"}
```

### Configuration

The backend reads everything from the environment — no secrets are committed.

| Variable | Required | Purpose |
| --- | --- | --- |
| `DB_URL` | no | Defaults to `jdbc:postgresql://localhost:5432/reloop_dev` |
| `DB_USERNAME` / `DB_PASSWORD` | no | Default to the OS user (works with trust/peer auth locally) |
| `JWT_SECRET` | **yes** | ≥ 32 characters, HS256 signing key. Locally supplied by `backend/.env` (see above); the app fails fast with instructions if it is missing |
| `GEMINI_API_KEY` | for AI | The only value needed to switch the AI scanner on. Without it, `/api/waste/analyze` returns `503` and the UI falls back to manual classification |
| `GEMINI_MODEL` | no | Defaults to `gemini-3.6-flash`. Must be a model the Gemini API still serves — `gemini-2.0-flash` was shut down on 2026-06-01 |
| `CORS_ALLOWED_ORIGINS` | prod | Comma-separated allow-list |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | first run | Bootstraps the first admin account |
| `RELOOP_DEV_MODE` | no | `true` returns the password-reset token in the API response (there is no mail transport in this build) |
| `STORAGE_LOCAL_DIR` / `STORAGE_PUBLIC_BASE_URL` | no | Local image storage location and public URL prefix |

### Local development: `backend/.env`

The one required value, `JWT_SECRET`, is supplied by a local `backend/.env` file that Spring Boot
imports automatically (`spring.config.import: optional:file:.env`). That means plain
`./mvnw spring-boot:run` works from the backend directory with nothing exported in your shell:

```bash
cd backend
cp .env.example .env                                  # one-time
printf 'JWT_SECRET=%s\n' "$(openssl rand -base64 48)" >> .env   # one-time
./mvnw spring-boot:run                                # http://localhost:8080
```

- `backend/.env.example` is committed and contains placeholders only; `backend/.env` is ignored by
  `.gitignore` and must never be committed.
- Real environment variables still win over `.env`, which is how deployments are configured — see
  `application-prod.yml`. `./scripts/dev-backend.sh` loads `backend/.env` first and only fills in
  what is missing, so it never shadows a value you set yourself.
- `JWT_SECRET` has **no default**. If it is missing or shorter than 32 bytes the application fails
  at startup with instructions, rather than signing tokens with a shared fallback secret.

### Enabling the AI waste scanner (Gemini)

The scanner calls Google Gemini from the backend only; the key is never sent to the browser. To turn
it on:

```bash
# 1. Create a key at https://aistudio.google.com/apikey, then add it locally:
cd backend
printf 'GEMINI_API_KEY=%s\n' 'YOUR_KEY_HERE' >> .env    # backend/.env is git-ignored

# 2. Restart and confirm the startup log line:
./mvnw spring-boot:run
#   "Gemini waste analysis enabled with model gemini-3.6-flash"
```

If the key is absent the log instead reads `Gemini waste analysis is DISABLED`, and `POST
/api/waste/analyze` answers `503` with a clear message — the app never fabricates a classification.

Verify the endpoint directly (any registered user token works):

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"your-password"}' | jq -r .accessToken)

curl -s -X POST http://localhost:8080/api/waste/analyze \
  -H "Authorization: Bearer $TOKEN" \
  -F image=@/path/to/waste-photo.jpg | jq
```

A configured, working key returns `item`, `categoryCode`, `confidence`, `recyclable`, `hazardous`,
`disposalInstruction` and `aiModel`. Override the model only with an ID the API currently serves:
`GEMINI_MODEL=gemini-3.8-flash` — see
[docs/API.md](./docs/API.md#post-apiwasteanalyze) for the response contract and
[the deprecation schedule](https://ai.google.dev/gemini-api/docs/deprecations) before pinning one.

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
  valid structured response, a low-confidence response, a malformed provider payload, a provider
  failure (`503`), the outbound request itself (model in the path, key in a header, inline image
  data, JSON output requested, no deprecated sampling parameters) and image validation.
- **`FullFlowIntegrationTest`** also covers the unconfigured case: with no key, `/api/waste/analyze`
  returns `503` and no fabricated result.
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
