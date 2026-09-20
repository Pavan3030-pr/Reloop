# Scripts

Local development helpers. All scripts are safe to re-run.

| Script | Purpose |
| --- | --- |
| `db-setup.sh` | Creates the `reloop_dev` and `reloop_test` PostgreSQL databases (idempotent) |
| `dev-backend.sh` | Starts Spring Boot on `:8080`, generating a local `JWT_SECRET` and bootstrapping an admin |
| `dev-frontend.sh` | Installs dependencies if needed and starts Vite on `:5173` with the API proxy |

```bash
./scripts/db-setup.sh
./scripts/dev-backend.sh     # terminal 1
./scripts/dev-frontend.sh    # terminal 2
```

Environment overrides are passed straight through, for example:

```bash
GEMINI_API_KEY=your-key ADMIN_EMAIL=you@example.com ADMIN_PASSWORD='strong-password' \
  ./scripts/dev-backend.sh
```

Run the test suites:

```bash
cd backend  && ./mvnw test        # requires reloop_test (see db-setup.sh)
cd frontend && npm run build      # typecheck + production build
```
