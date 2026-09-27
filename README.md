# Style Communicator

Practice written communication in a chosen **style** — pick a persona (a CEO, a negotiator, a character you describe), get put into a realistic situation, write your response, and get AI-scored feedback across six communication dimensions.

**Stack:** Spring Boot 3.4 (Java 17) + PostgreSQL · Next.js 15 (React 19) · Pluggable LLM via [OpenRouter](https://openrouter.ai)

---

## Quick start — Docker

```bash
git clone https://github.com/shreyAmritkar/engish style-communicator && cd style-communicator

# 1. Configure the backend — note: the file goes in backend/, not the repo root
cp backend/.env.example backend/.env
echo "OPENROUTER_API_KEY=sk-or-..." >> backend/.env

# 2. Start Postgres + the API
docker compose up --build
```

| Service | URL |
|---|---|
| API | http://localhost:8080 |
| Health check | http://localhost:8080/api/health |
| **Interactive API docs** | http://localhost:8080/swagger-ui.html |
| Postgres | localhost:5432 (`postgres` / `postgres`, db `stylecommunicator`) |

Flyway runs all schema migrations and seeds three preset styles automatically on first boot — nothing manual required. The container runs with `SPRING_PROFILES_ACTIVE=dev` (verbose logging, full error detail); the only thing that changes between `dev` and `prod` is verbosity and a couple of access rules — both always talk to the same real Postgres.

### Frontend (run with Node — not containerized)

```bash
cd frontend
npm install
npm run dev
```

App: http://localhost:3000 (already configured to hit `http://localhost:8080` via `.env`)

### Stopping / resetting

```bash
docker compose down          # stop containers, keep data
docker compose down -v       # stop containers, wipe the Postgres volume
```

---

## What this project actually does

A user picks or describes a **communication style**. The app drops them into a generated real-world **situation** that fits that style, they write a response, and the backend scores it — then, only on request, offers a rewrite and a coaching tip. AI calls only happen where they add value: scoring uses a cheap/fast model, rewrites use a better model on demand, and grammar checking and progress math are pure code (zero AI cost, zero latency).

### 1. Style extraction & deduplication

```
POST /api/styles/from-description  { name, description }
        │
        ▼
StyleEngineService.extractFromDescription()
        │  LLM (FAST tier) turns free text into a structured "style DNA":
        │  vocabulary tier, sentence structure, emotional range, power dynamic,
        │  formality (1-10), key/avoid patterns, sample phrases
        ▼
CosineSimilarityUtil — hybrid dedup so near-duplicate styles collapse into one
        │  30% structural enums + 45% pattern/phrase Jaccard + 25% lexical fingerprint
        │  (keeps "CEO" and "meme streamer" apart even though both are DOMINANT/formal)
        ▼
if similar profile exists → return it (no duplicate row)
else → compress prompt → save → cache (60min TTL)
```

### 2. Practice session lifecycle

```
POST /api/sessions/start            → resolves CEFR difficulty (A1-B2) from the
                                       user's current level; picks a matching
                                       situation + required word + emotional
                                       context from the situation bank

POST /api/sessions/{id}/submit      → IntentValidationService rejects empty/
                                       off-topic replies before spending an LLM
                                       call → AnalysisRouter scores 0-100 on
                                       confidence, tone, persuasion, emotional
                                       control, professionalism, style_match
                                       (relative to the chosen style, not
                                       generic politeness) → GrammarCheckerService
                                       runs regex checks in parallel (no AI
                                       cost) → ProgressTracker updates EMA
                                       averages and recalculates level

POST /api/sessions/{id}/rewrites        → on-demand only, QUALITY-tier LLM call
POST /api/sessions/{id}/coaching-tip    → on-demand only, cached once generated
```

**Situation bank loading strategy** (admin-controlled, `GET`/`POST /api/admin/settings/situation-loading`): `ON_DEMAND` (default) replenishes a bucket reactively only once a real request finds it running low. `BACKGROUND` runs a scheduled sweep every 15 minutes that proactively tops up every bucket ahead of time — a genuine latency-vs-LLM-cost trade-off, not a cosmetic toggle.

### 3. Progress tracking (no AI)

`ProgressTracker` is pure math — EMA (0.7 old / 0.3 new) per dimension, and a weighted average over the last 10 sessions to set the 1–5 level that drives the next session's difficulty. (Note: an earlier version also did trend/variance-based "habit flag" detection; that was simplified out in favor of the plain average above — worth knowing if you're asked to explain the history of this file, since `habitFlags` still exists on the model but isn't populated anymore.)

### 4. Rival leaderboard

`RivalLeaderboardService` computes, per request, which other users share your strongest dimension and ranks you against them. This is a deliberate simplification from an earlier Redis-backed, nightly-precomputed design — fine at the scale this project runs at (in-memory, one query), but the honest trade-off is it re-scans all users on every leaderboard view rather than reading a precomputed cache, so it wouldn't scale as-is to a large user base.

### 5. Auth & access control

- Email/password, BCrypt-hashed, JWT issued on register/login as an **HttpOnly cookie** (`sc_token`) — never returned in the JSON body.
- `JwtAuthFilter` validates the cookie on every request **and** checks the account is still active on every request — an admin deactivating a user takes effect immediately, not just at the user's next login.
- Route rules (`SecurityConfig`): auth endpoints and the style library are public; `/api/admin/**` and `/actuator/**` require `ADMIN`; `/swagger-ui.html` is open in `dev`, `ADMIN`-only in `prod`.
- `RateLimitFilter` caps session starts/submits at **10/user/day** (in-memory).

### 6. Admin panel

User management (list, promote/demote, activate/deactivate, delete — single and bulk), platform stats, and the situation-loading toggle above. Bulk endpoints report partial success/failure per item rather than failing the whole batch.

### 7. LLM layer

`LlmClient` is a provider-agnostic interface; `OpenRouterLlmClient` is the only implementation, so swapping the underlying model is a config change, not a code change. An AOP aspect (`LlmCallLoggingAspect`) logs latency and outcome for every LLM call without ever logging the prompt or response content itself.

| Tier | Used for | Config key |
|---|---|---|
| `FAST` | style extraction, scoring, coaching tips, situation generation | `LLM_FAST_MODEL` |
| `QUALITY` | on-demand rewrites | `LLM_QUALITY_MODEL` |

On failure, requests retry with backoff and fall through a configured fallback list; if every model fails, callers degrade gracefully (e.g. style extraction falls back to sane defaults) rather than failing the request outright.

---

## Observability

- **`/swagger-ui.html`** — interactive API docs (OpenAPI 3), grouped by feature area. Auth in the docs works exactly like the app: call `POST /api/auth/login` via "Try it out" first, then every subsequent call in the same tab is authenticated via the cookie automatically.
- **`/actuator/health`, `/actuator/metrics`, `/actuator/prometheus`** — ADMIN-only. Only these three are exposed (not the full Actuator surface — `env`, `beans`, `heapdump` etc. stay closed, since this API handles user data and an LLM key).
- **Structured logging** — `logback-spring.xml` with real `dev`/`prod` profiles: `dev` shows SQL and full error detail on console; `prod` logs to stdout only (the container runs as a non-root user with no writable log directory, and Render's filesystem is ephemeral anyway, so a log *file* in prod would be pointless even if it worked). Bound SQL parameter values (which would include user emails and full practice-session text) are never logged in either profile.

## Environment variables (backend)

| Variable | Required | Default | Notes |
|---|---|---|---|
| `OPENROUTER_API_KEY` | yes | — | one key, pick any model by id |
| `DATABASE_URL` / `DB_USER` / `DB_PASSWORD` | yes (docker-compose sets it) | — | JDBC form |
| `LLM_FAST_MODEL` / `LLM_QUALITY_MODEL` | no | see `application.yml` | change anytime, restart backend |
| `JWT_SECRET` | recommended in prod | dev placeholder | `openssl rand -base64 48` |
| `COOKIE_SECURE` | prod only | `false` | set `true` behind HTTPS |
| `CORS_ORIGINS` | yes for deployed frontend | `http://localhost:3000` | comma-separated |
| `SPRING_PROFILES_ACTIVE` | set by `docker-compose.yml`/`render.yaml` | none | `dev` locally, `prod` on Render |

## API summary

Full, current, always-in-sync detail lives at `/swagger-ui.html` — this is just the map.

| Group | Endpoints |
|---|---|
| **Auth** | `POST /api/auth/register`, `/login`, `/logout` · `GET /api/auth/me` |
| **Styles** | `GET /api/styles/library`, `/{id}` · `POST /api/styles/from-description` |
| **Sessions** | `POST /api/sessions/start`, `/{id}/submit`, `/{id}/rewrites`, `/{id}/coaching-tip` · `GET /{id}` |
| **Progress** | `GET /api/progress/me`, `/{userId}` |
| **Leaderboard** | `GET /api/leaderboard/rivals` |
| **Admin** | Users (list/get/role/active/delete, bulk promote/demote/delete), `/stats`, `/health`, situation-loading settings — all `ADMIN` |
| **Health** | `GET /api/health` (public, used by Render) |

## Project layout

```
backend/   Spring Boot API — controllers → services → repositories (JPA) → Postgres
           llm/        provider-agnostic LLM client (OpenRouter)
           aop/        LLM call logging aspect
           service/    style engine, session, analysis, progress, leaderboard, situation bank
           security/   JWT issuing + per-request active-account check
           db/migration/   Flyway SQL, versioned (V1-V10)
frontend/  Next.js app router — practice, library, progress, rivals, admin pages
           lib/api.ts  typed fetch client, cookie-based auth
docker-compose.yml   Postgres + backend for local dev
render.yaml           backend deploy (Render, Docker, prod profile)
vercel.json            frontend deploy (Vercel) + API proxy rewrite
```

## Deployment

- **Backend → Render**: `render.yaml` builds `backend/Dockerfile`, runs with `SPRING_PROFILES_ACTIVE=prod`, and expects `DATABASE_URL`, `OPENROUTER_API_KEY`, `CORS_ORIGINS` as dashboard secrets.
- **Frontend → Vercel**: set `NEXT_PUBLIC_API_URL` to the Render backend URL; `vercel.json` proxies `/api/*` to it.

## Tests

```bash
cd backend && mvn test
```

Unit tests cover grammar checks, intent validation, progress math, situation source routing and the new background-sweep scheduling, JWT signing and the per-request active-account check, and the cosine similarity dedup util. There's no integration-test suite (e.g. Testcontainers against a real Postgres) yet — the honest next thing to add if this needs to prove itself further.

## Known trade-offs (worth knowing before someone asks)

- **Rival leaderboard** is computed synchronously per request rather than precomputed/cached — simple and correct at demo scale, wouldn't scale to a large user base as-is.
- **Habit-flag detection** (trend/variance-based coaching flags) was simplified out of `ProgressTracker` in favor of a plain weighted average; the field still exists on the model but is always empty now.
- **No integration tests** — unit-level coverage only.
- **No pagination** on list endpoints (e.g. `GET /api/admin/users`) — fine at hundreds of rows, would need it before thousands.
- **No DB indexes** beyond what's needed for the FK/unique constraints already in the schema — invisible at small scale, worth adding (`style_profile.source`, etc.) before real growth.
