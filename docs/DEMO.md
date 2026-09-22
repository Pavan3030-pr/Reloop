# Three-minute demo

A script for presenting ReLoop live. Every step uses a feature that exists in this build — nothing
here is staged, mocked or edited in the database during the run.

> **No demo seed data ships with this project.** Accounts and pickups are created through the real
> API during the walkthrough, so nothing in the demo can be mistaken for real environmental impact.
> The two accounts below are ordinary registrations; the emails are placeholders, not credentials
> baked into the code.

## Before the room (do this once, 5 minutes)

```bash
# 1. Databases
./scripts/db-setup.sh

# 2. Backend environment — backend/.env is git-ignored
cd backend && cp .env.example .env
printf 'JWT_SECRET=%s\n' "$(openssl rand -base64 48)" >> .env
# Optional, and the one step that makes the AI act real:
#   printf 'GEMINI_API_KEY=%s\n' 'YOUR_KEY' >> .env

# 3. Start it — migrations run automatically, health at /actuator/health
./mvnw spring-boot:run            # http://localhost:8080

# 4. Frontend (another terminal), then open http://localhost:5173
cd .. && ./scripts/dev-frontend.sh
```

Rehearse once and keep the accounts — registration is the slowest part of a live demo:

| Role | Email (example) | Notes |
| --- | --- | --- |
| Resident | `demo.resident@reloop.test` | Ordinary sign-up |
| Collector | `demo.collector@reloop.test` | Must be **verified by an admin** before it can accept work |
| Admin | value of `ADMIN_EMAIL` | Set before the first backend start |

A safe rehearsal trick: point the backend at a throwaway database (`DB_URL=jdbc:postgresql://localhost:5432/reloop_demo`)
so a dry run cannot touch data you care about.

**If `GEMINI_API_KEY` is not set**, say so and show the honest fallback — the scanner returns *"AI
analysis is not configured on this server"* and offers manual classification. Do not claim AI
identification you did not run.

## The script

### 00:00 — The problem
Open the landing page. One sentence, no scrolling: *"Households do not know what their waste
actually is, whether it is recyclable, where it should go, or what happens to it after collection.
ReLoop answers all four — and proves the last one with a real weight."*

Point at the ♻ mark and the hero's circular journey. Do **not** quote platform statistics; there are
none on the page, deliberately.

### 00:20 — AI waste identification
Sign in as the resident → **Scan waste** → choose a photo.

- While it analyses, name the states: the image is validated by content (not filename), then sent
  server-side to Gemini.
- Land the result: item, material category, confidence, recyclable/hazardous, disposal instruction.
- Say the caveat out loud: *"Confidence is a model estimate, not a verdict — and if the AI is
  unavailable, we say so and fall back to manual classification instead of inventing an answer."*
- **Save scan.**

### 00:50 — Collection discovery
**Collection points** → the map and list are the same records, two views.

- Select a point from the list: the map flies to its pin.
- Point out the verified marker and the materials each point accepts, plus distance and hours when
  they are known. *"Verified partners are marked as such; anything unverified is labelled, not
  promoted."*

### 01:10 — Pickup request
**Scan waste → find collection options**, or **Pickups → Request**. Fill in material, quantity,
address, date and time slot. Attach the photo. Submit.

- Read the confirmation: a real pickup code, `RL-XXXXXX`.
- **Do not cancel this one.** (If someone asks: cancellation works while the request is still
  `REQUESTED`, and the API refuses it the moment a collector accepts.)

### 01:30 — Collector workflow
Switch to the verified collector account → **Collector workspace**.

- Show the open pool: material, city, a kilometre-rounded distance — and *no* address, no name, no
  photo. Say it plainly: *"The address is released only after you accept. A pool that every partner
  can browse is not a reason to hand out one household's location."*
- Filter by city, material or distance — server-side, so the options are only what is really waiting.
- **Accept job** → the full address and photo unlock → **Schedule** → confirm the visit time.

Optional 15-second proof, if a second browser is handy: accept the same request as a second
collector simultaneously. One gets the job; the other gets a `409` conflict. *"Two collectors cannot
both be told they won."*

### 02:00 — Actual weight
Still as the collector: **Record collection**. Weigh the material and enter the real kilograms.

- *"This is the number the platform treats as truth. Until this moment, everything was an estimate."*
- Show that the record cannot be written twice for one pickup.

### 02:20 — Processing and recycling
**Move to processing** → **Mark recycled** (or **Mark recovered** — both are terminal outcomes).

- Point at the timeline advancing through the real states.

### 02:40 — Impact and history
Switch back to the resident. Refresh.

- The pickup reads `RECYCLED` and shows the collected weight.
- **History**: the entry, the weight, and the collector's organisation.
- **Impact**: measured kilograms and completed collections, separated from the *estimated* CO₂e —
  open the "how this is calculated" note. *"Measured and estimated are never mixed. If there were no
  data, this page would say so rather than show a number."*
- **Notifications** list the real events: accepted, scheduled, collected, processing, recycled.

### 03:00 — Closing
*"Photograph it, understand it, get it collected by a verified partner, and prove it was recovered —
with the weight, not with a claim. That is ReLoop: a **measured** record of waste actually returning
to circulation instead of a promise that it was recycled."*

## If something fails mid-demo

| Symptom | What to say |
| --- | --- |
| Analyse returns *"AI analysis is not configured"* | Correct behaviour without a key. Use manual classification and state that the app refuses to fabricate a result. |
| Analyse returns *"temporarily unavailable"* | Gemini is unreachable or rate-limited. Same fallback; the log names the cause. |
| Collector workspace is empty | No open requests. The empty state is honest — create one from the resident account first. |
| Impact shows no data | No completed collection yet; the page says so rather than inventing figures. |
| Port 8080 in use | Another backend instance is running: `lsof -nP -iTCP:8080 -sTCP:LISTEN`. |

## What this demo deliberately does **not** do

- No seeded "10,000 kg recycled" banners, no invented testimonials or partner logos.
- No manually edited database rows to move a pickup forward — every state transition is an API call
  made by the actor who is allowed to make it.
