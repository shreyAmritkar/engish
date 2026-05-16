# Style Communicator

Full-stack app for practicing written communication in a chosen style (Spring Boot + Next.js + Gemini).

## Features

- **Style extraction** — Describe a style or paste dialogue; Gemini Flash extracts communication DNA (JSON).
- **Cosine deduplication** — Similar profiles (>0.85) reuse existing rows instead of duplicating.
- **Practice sessions** — Situation bank, required word, emotional context; Flash scoring without rewrites.
- **On-demand Pro** — Rewrites and coaching tip only when the user clicks.
- **Progress (no AI)** — EMA averages, linear-regression habit flags, weighted level-ups.
- **Rate limit** — 10 session starts/submits per user per day (in-memory).
- **Grammar** — Rule-based checks (no AI cost).

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
cp .env.example .env   # set GEMINI_API_KEY and DB credentials
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
3. Env: `GEMINI_API_KEY`, `CORS_ORIGINS=https://your-app.vercel.app`
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

- Flash for extraction + scoring (~250 calls/day ≈ $0.05/day with compressed prompts).
- Pro only for rewrites/coaching on click.
- In-memory style prompt cache (60 min TTL).
