# ReLoop

**Give every piece of waste a better destination.**

ReLoop is a waste-management and circular-economy platform. A resident photographs an item, gets an
AI-suggested recycling stream, finds a verified collection point, books a pickup, and follows it until
a collector weighs the material and it is recovered. Only *actually* weighed waste feeds the history
and impact figures.

```
resident → scan / AI classification → collection point or pickup request
        → verified collector: accept → schedule → weigh & collect → processing → recovered
        → recycling history + impact (kg, kg CO₂e)
```

## Stack

- **Backend:** Spring Boot 3.5 (Java 21), JWT auth, Spring Data JPA + Flyway, PostgreSQL, Gemini.
- **Frontend:** React 19 + TypeScript + Vite, React Router, Leaflet for the collection-point map.
- **AI:** Google Gemini, called only from the backend — the API key never reaches the browser.

## Quick start

### Docker (one command)

```bash
cp .env.example .env   # set DB_PASSWORD and JWT_SECRET (openssl rand -base64 48)
docker compose up --build
```

Open <http://localhost:8081>. Add `GEMINI_API_KEY` to `.env` to enable AI scanning, and
`ADMIN_EMAIL` / `ADMIN_PASSWORD` to create the first administrator.

### Local (JDK 21+, Node 20+, PostgreSQL 14+)

```bash
./scripts/db-setup.sh                                    # creates reloop_dev and reloop_test
cp backend/.env.example backend/.env
printf 'JWT_SECRET=%s\n' "$(openssl rand -base64 48)" >> backend/.env
cd backend && ./mvnw spring-boot:run                     # :8080, migrations run on start
./scripts/dev-frontend.sh                                # :5173, proxies /api and /uploads
```

## Configuration

The backend reads everything from the environment; no secrets are committed.

| Variable | Required | Purpose |
| --- | --- | --- |
| `JWT_SECRET` | yes | HS256 signing key, ≥ 32 characters. The app fails fast without it |
| `GEMINI_API_KEY` | for AI | Enables the scanner; without it `/api/waste/analyze` returns `503` and the UI falls back to manual classification |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | no | Default to a local `reloop_dev` database |
| `CORS_ALLOWED_ORIGINS` | prod | Comma-separated allow-list |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | first run | Bootstraps the first administrator |
| `RELOOP_TIMEZONE` | no | Calendar zone for history dates and the collector's "today" (default `Asia/Kolkata`) |
| `RELOOP_RATE_LIMIT_AUTH_PER_MINUTE` | no | Per-IP attempts on the auth endpoints per minute (default 30) |
| `STORAGE_LOCAL_DIR`, `STORAGE_PUBLIC_BASE_URL` | no | Image storage location and public URL prefix |

## Testing

```bash
cd backend && ./mvnw test      # 90 tests against real PostgreSQL and real HTTP
cd frontend && npm run build   # typecheck + production build
```

## Docs

- [API reference](docs/API.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Security model](docs/SECURITY.md)
- [Impact methodology](docs/IMPACT.md)
- [Three-minute demo script](docs/DEMO.md)
- [Pitch deck — Team TechGaint](docs/submission/ReLoop-Pitch-Deck-TechGaint.pptx)
- SANKALP submission: [pitch deck](docs/submission/ReLoop-Sankalp-Pitch.pptx), [video script](docs/submission/VIDEO_SCRIPT.md)
