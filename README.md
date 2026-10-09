# CODEIT CareerOS

Career-readiness SaaS for college students: target-career selection, skill
assessments with gap analysis, 90-day roadmaps, project recommendations, CV
and LinkedIn analysis, an AI Career Assistant, readiness reports, and a
data-driven student dashboard — plus an admin console.

Monolith: **Spring Boot 3 API + Next.js frontend + MySQL only.** No
microservices, no extra infrastructure.

## Technology stack

| Layer    | Choice |
|----------|--------|
| Frontend | Next.js 16, React 19, JavaScript only, Tailwind CSS 4, shadcn-style UI (`components/ui`), axios |
| Backend  | Java 21, Spring Boot 3.5, Spring Security (JWT), Spring Data JPA/Hibernate, Bean Validation, Maven, springdoc OpenAPI |
| Database | MySQL 8 only (H2 in-memory for tests) |
| AI       | Spring AI, configurable provider (OpenAI / Gemini / Ollama / any OpenAI-compatible), 8 approved tools only, offline Smart-Guidance fallback. The AI **never touches MySQL/JPA** — it only calls service-layer tools |
| Storage  | AWS S3 (production, private objects) · local filesystem (development) |
| Docs/PDF | Apache Tika (CV parsing), OpenPDF (report downloads) |

## Repository layout

```
CODEIT-CareerOS/
  careeros-backend/     Spring Boot API (Java 21, Maven)
  careeros-frontend/    Next.js app (JS only)
  docs/                 guides (backup/restore, …)
  docker-compose.prod.yml
  .env.example          production env template (placeholders only)
  run-backend.bat / run-frontend.bat   local Windows runners
```

## Local setup

Prerequisites: JDK 21+, Node 20+, MySQL 8 running on `localhost:3306`.

1. **Database** — create user/db (dev defaults in
   `careeros-backend/src/main/resources/application-dev.yml`):
   `CREATE DATABASE careeros; CREATE USER 'careeros'@'localhost' IDENTIFIED BY 'careeros_dev_pw'; GRANT ALL ON careeros.* TO 'careeros'@'localhost';`
2. **Backend** — `run-backend.bat` (uses the prebuilt jar with profile `dev`),
   or rebuild: `cd careeros-backend && mvn package -DskipTests`
   (needs `JAVA_HOME` → JDK 21+). API: `http://localhost:8080`,
   Swagger: `http://localhost:8080/swagger-ui.html` (dev only).
3. **Frontend** — `run-frontend.bat` (`npm install` once, then `next dev`).
   App: `http://localhost:3000`. Uses `NEXT_PUBLIC_API_URL`
   (`careeros-frontend/.env.local`, default `http://localhost:8080`).

Default dev admin (dev/test profiles only): `admin@careeros.local` /
`Adm1n@ChangeMe` — change immediately; production **requires**
`ADMIN_EMAIL`/`ADMIN_PASSWORD` env vars.

## Environment variables (production)

All in `.env.example` (placeholders only — never commit real values):

| Group | Vars |
|-------|------|
| MySQL | `MYSQL_ROOT_PASSWORD`, `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JPA_DDL_AUTO=validate` |
| Auth  | `JWT_SECRET` (≥32 bytes, fail-fast), `JWT_ACCESS_EXPIRATION_MS`, `JWT_REFRESH_EXPIRATION_MS`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `AUTH_RATE_LIMIT_PER_MINUTE` |
| CORS  | `CORS_ALLOWED_ORIGINS` (comma-separated, never `*` with credentials) |
| AI    | `AI_PROVIDER` (openai/gemini/ollama/custom), `AI_API_KEY`, `AI_MODEL`, `AI_BASE_URL`, `GEMINI_API_KEY`, `GEMINI_MODEL`, iteration/history/timeout/rate-limit guards |
| CV/S3  | `CV_STORAGE=s3`, `CV_S3_BUCKET`, `CV_S3_REGION`, `CV_S3_PREFIX`, `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY` (or IAM role) |
| Frontend | `NEXT_PUBLIC_API_URL` / `FRONTEND_API_URL` (public, build-time) |

AI keys stay **server-side only** — the browser never sees them.
Local AI walkthrough: `careeros-backend/.env.example`.

## Production deployment (Docker)

Easiest — one command on the server (from the repo root):

```bash
./deploy-prod.sh 13.201.83.135
```

It creates `backend.env` (generating secrets on first run), points the
frontend build and backend CORS at your public host, bootstraps the schema
on an empty database only, then builds and health-checks everything.

Manual equivalent:

```powershell
cp .env.example backend.env   # fill in real values (never commit)
docker compose -f docker-compose.prod.yml up -d --build
```

This starts MySQL 8 (persisted `mysql-data` volume), the API
(`SPRING_PROFILES_ACTIVE=prod`, `ddl-auto=validate` — your data is never
auto-migrated), and the frontend standalone server. Health:
`GET /actuator/health` (no details exposed). Swagger is **disabled** in prod.

Without Docker: build the jars/site normally —
`mvn -P? package` with `SPRING_PROFILES_ACTIVE=prod` (+ required env),
frontend `NEXT_PUBLIC_API_URL=https://api.… npm run build && npm start`.

## Testing

- Backend: `cd careeros-backend && mvn test` (H2, `create-drop`; includes
  Sprint 8 auth/ownership suites: anonymous/invalid/expired/wrong-issuer
  tokens, STUDENT→ADMIN 403, logout revocation, refresh-reuse revocation,
  per-student isolation, CV owner scoping).
- Frontend: `npm run lint`, `npm run build`.
- Manual E2E order: register → login → refresh → profile → career →
  assessment → result → gaps/readiness → roadmap → projects → CV
  upload/analysis/download → LinkedIn → AI chat → dashboard/progress/
  reports/settings → admin checks → cross-student isolation.

## Backup & restore

See [`docs/BACKUP_RESTORE.md`](docs/BACKUP_RESTORE.md) — mysqldump/restore,
important tables, and the safe migration process
(dev `ddl-auto:update`, prod `validate` + reviewed ALTERs, backup first).

## Security notes (Sprint 8)

- JWT access (15 min) + rotating refresh tokens (7 d, SHA-256 stored,
  reuse = revoke-all, nightly purge of expired rows).
- Every student API is owner-scoped (`/me`, session-ownership 404s); no
  `userId` path params; ADMIN rule enforced (no admin controllers exist yet).
- Auth endpoints rate-limited per IP (60/min, `AUTH_RATE_LIMIT_PER_MINUTE`,
  off in tests); AI chat has its own 20/min/user limit.
- Passwords BCrypt-hashed; tokens/keys/passwords never logged; error
  responses are generic (`ApiResponse{success,message,errorCode,timestamp}`).
- CORS from env, credentials-safe; frontend security headers (HSTS prod-only,
  `X-Frame-Options: DENY`, `nosniff`); student area requires STUDENT role.
- Uploads: 5 MB cap, extension+MIME allowlist, server-side UUID keys,
  private S3 objects streamed through the backend, owner-only download.
- AI: 8-tool allowlist, max 6 iterations, 120 s timeout, output filtering,
  per-user rate limit, conversation isolation, offline fallback.

## Key routes

Student: `/student/dashboard|careers|assessment|roadmap|projects|cv|
linkedin|ai|reports|progress|profile|settings` · Admin: `/admin/dashboard`.
