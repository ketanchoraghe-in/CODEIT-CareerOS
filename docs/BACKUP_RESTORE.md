# MySQL Backup, Restore & Migration — CODEIT CareerOS

Sprint 8 rule, repeated: **never delete or reset production data.**
`application-prod.yml` uses `ddl-auto: validate` — the app will refuse to
start against an unexpected schema instead of altering your data.

## What to back up

- The whole `careeros` database (single `mysqldump` covers everything).
- Most critical tables: `users`, `student_profiles`, `refresh_tokens`,
  `assessment_attempts`, `assessment_answers`, `skill_scores`,
  `roadmap_item_progress`, `project_progress`, `cv_documents`,
  `linkedin_profiles`, `ai_chat_sessions`, `ai_chat_messages`.
- Master/catalog tables (re-seedable, but back up anyway): `skills`,
  `careers`, `career_skills`, `assessment_tests`, `assessment_questions`,
  `question_options`, `roadmap_phases`, `roadmap_items`, `projects`,
  `project_skills`.
- CV binaries live in **S3** (prod) — covered by bucket versioning/
  replication, not by mysqldump. Local dev files live under
  `./data/` (back up the folder if needed).

## Backup (run on the DB host)

```powershell
# PowerShell — timestamped full backup
$stamp = Get-Date -Format "yyyyMMdd-HHmm"
mysqldump -h localhost -u root -p --single-transaction --routines `
  careeros > "careeros-backup-$stamp.sql"
```

```bash
# bash — same thing
stamp=$(date +%Y%m%d-%H%M)
mysqldump -h localhost -u root -p --single-transaction --routines \
  careeros > "careeros-backup-${stamp}.sql"
```

Automate daily via Task Scheduler (Windows) or cron (Linux), keep 14+
copies, and periodically restore one to a scratch database to prove it works.

## Restore

```powershell
mysql -h localhost -u root -p careeros < careeros-backup-20260101-0300.sql
```

Then start the backend with `SPRING_PROFILES_ACTIVE=prod` — `validate`
confirms the schema matches before serving traffic.

## Safe migration process (no Flyway in this project — deliberate, Sprint 8
kept the architecture unchanged)

1. Develop the entity change against **dev** (`ddl-auto: update` applies it
   to your local DB automatically — never point dev at production).
2. Capture the exact DDL Hibernate used (dev log, `format_sql: true`).
3. **Back up production** (above).
4. Review the DDL by hand: only additive/compatible changes
   (`ADD COLUMN NULL`, `CREATE INDEX`). Destructive changes
   (`DROP`, type narrowing) need a written data-migration plan first.
5. Apply the reviewed DDL to production with `mysql < migration.sql`.
6. Deploy the backend; `validate` passes → done. If it fails, the app
   stays down **instead of corrupting data** — restore the backup and retry.

## Docker Compose volumes

Production data lives in the `mysql-data` volume. Back up the database with
`mysqldump` (above, via `docker exec` against the `mysql` service), not by
copying volume files while MySQL runs:

```powershell
docker compose -f docker-compose.prod.yml exec mysql mysqldump -u root -p careeros > careeros-backup.sql
```
