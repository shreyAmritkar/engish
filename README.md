# Style Communicator

Full-stack app for practicing written communication in a chosen style (Spring Boot + Next.js + pluggable LLM).

## Features

- **Style extraction** — Describe a style or paste dialogue; Gemini Flash extracts communication DNA (JSON).
- **Hybrid style deduplication** — Structural enums + pattern/phrase Jaccard + lexical fingerprint (CEO ≠ meme even if both DOMINANT).
- **Practice sessions** — Situation bank, required word, emotional context; Flash scoring without rewrites.
- **On-demand Pro** — Rewrites and coaching tip only when the user clicks.
- **Progress (no AI)** — EMA averages, linear-regression habit flags, weighted level-ups.
- **Rate limit** — 10 session starts/submits per user per day (in-memory).
- **Grammar** — Rule-based checks (no AI cost).
- **Pluggable LLM** — Gemini or [OpenRouter](https://openrouter.ai); swap models via env (no code change).

## LLM configuration

**Recommended: OpenRouter only** — one `OPENROUTER_API_KEY`, pick any vendor by model id (including Gemini). No separate Google API key.

| Tier | Used for | Default (via OpenRouter) |
|------|----------|---------------------------|
| `FAST` | Extraction, scoring, coaching tip | `google/gemini-2.0-flash-001` |
| `QUALITY` | Rewrites (on demand) | `google/gemini-2.0-flash-001` |

```env
LLM_PROVIDER=openrouter
OPENROUTER_API_KEY=sk-or-...
LLM_FAST_MODEL=google/gemini-2.0-flash-001
LLM_QUALITY_MODEL=google/gemini-2.0-flash-001
```

Change model anytime (restart backend):

```env
LLM_FAST_MODEL=google/gemini-2.5-flash-preview
```

On **429 rate limit**, the app retries with backoff and tries `llm.fallback-models` from `application.yml`.

**Avoid** free `:free` models for demos if you need reliability — e.g. `deepseek/deepseek-v4-flash:free` is often upstream rate-limited. Use paid/credit models or [BYOK](https://openrouter.ai/settings/integrations).

**Legacy:** direct Gemini API — `LLM_PROVIDER=gemini` + `GEMINI_API_KEY` (only if you skip OpenRouter).

## Quick start (local)

### Prerequisites

- Java 17+, Maven 3.9+
- Node 20+, npm
- PostgreSQL 15+
- [Gemini API key](https://aistudio.google.com/apikey)

### Database

```sql
CREATE DATABASE stylecommunicator;
```

### Backend

```bash
cd backend
cp .env.example .env   # set OPENROUTER_API_KEY and DB credentials
mvn spring-boot:run
```

API: `http://localhost:8080` — health: `GET /api/health`

### Frontend

```bash
cd frontend
cp .env.example .env.local
npm install
npm run dev
```

App: `http://localhost:3000`

User ID is stored in `localStorage` as a UUID (no auth for MVP).

## Deploy

### Railway (backend)

1. New project → deploy `backend/` (or monorepo with root directory `backend`).
2. Add PostgreSQL plugin; set `DATABASE_URL` to JDBC form, e.g.  
   `jdbc:postgresql://HOST:5432/railway?user=...&password=...`
3. Env: `GEMINI_API_KEY` (or OpenRouter vars above), `CORS_ORIGINS=https://your-app.vercel.app`
4. Build: `mvn -DskipTests package` — start: `java -jar target/backend-0.1.0.jar`

### Vercel (frontend)

1. Import repo, root directory `frontend`.
2. Env: `NEXT_PUBLIC_API_URL=https://your-railway-app.up.railway.app`

## API overview

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/styles/library?source=COMMUNITY,PRESET` | List styles |
| POST | `/api/styles/from-description` | Extract style |
| POST | `/api/styles/community/submit` | Community card (pending) |
| POST | `/api/sessions/start` | Start practice |
| POST | `/api/sessions/{id}/submit` | Score response |
| POST | `/api/sessions/{id}/rewrites` | Pro rewrites (on demand) |
| POST | `/api/sessions/{id}/coaching-tip` | Coaching tip |
| GET | `/api/progress/{userId}` | Progress + history |

Header: `X-User-Id: <uuid>` for rate limiting and rewrites.

## Preset styles

Flyway seed inserts: **Assertive CEO**, **Empathetic Listener**, **Direct Negotiator**.

## Cost notes (≈50 users)

- **FAST** tier for extraction + scoring (~250 calls/day).
- **QUALITY** tier only for rewrites on click.
- OpenRouter free models (e.g. `deepseek/deepseek-v4-flash:free`) can replace FAST tier at $0.
- In-memory style prompt cache (60 min TTL).
