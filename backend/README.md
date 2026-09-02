# LinkUp backend

Ktor + Exposed + PostgreSQL (Supabase). Packages are split by responsibility so the
backend team can work in parallel. The wider contract lives in
`docs/ARCHITECTURE_API_DATABASE.md`; the canonical schema is
`docs/Database Design/schema.sql`.

```text
src/main/kotlin/com/linkup/
├── config/       # env-backed configuration
├── database/     # Exposed tables, entities, connection + column migrations
├── repository/   # PostgreSQL access
├── routes/       # Ktor REST routes + DTO validation
├── service/      # business rules (JWT, profile validation)
├── storage/      # binary storage behind the MediaStorage interface
└── websocket/    # sessions and events (not started)
```

## Running

From the repository root:

```bash
./gradlew :backend:run
```

Serves on `http://0.0.0.0:8080`. `10.0.2.2:8080` is the same server as seen from the
Android emulator. Configuration is read from `.env` (repo root or `backend/`).

Start the backend **before** launching the app — the app cannot start it.

## Tests

```bash
./gradlew :backend:test                 # JVM unit tests, no database needed
bash scripts/profile-api-smoke.sh       # end-to-end, needs the server running
```

The smoke script registers throwaway users, so point it at a dev database.

## Endpoints

### Auth

| Method | Path | Notes |
| --- | --- | --- |
| `POST` | `/auth/register` | `{email, username, password, fullName?}` → `{user, token}` |
| `POST` | `/auth/login` | `{emailOrUsername, password}` → `{user, token}` |

### Profile

All profile routes require `Authorization: Bearer <jwt>`.

| Method | Path | Notes |
| --- | --- | --- |
| `GET` | `/profile/me` | Own profile, including email and phone |
| `GET` | `/profile/{id}` | `id` is a user UUID **or** a username. Contact details omitted |
| `PATCH` | `/profile/me` | Partial update — see below |
| `POST` | `/profile/me/avatar` | `multipart/form-data`, one image part |
| `POST` | `/profile/me/cover` | `multipart/form-data`, one image part |
| `DELETE` | `/profile/me/avatar` | Clears the avatar and deletes the file |
| `DELETE` | `/profile/me/cover` | Clears the cover and deletes the file |
| `POST` | `/profile/{id}/follow` | → `{isFollowing, followerCount}` |
| `DELETE` | `/profile/{id}/follow` | → `{isFollowing, followerCount}` |

`PATCH /profile/me` takes any subset of `fullName, username, email, phone, bio,
location, website, birthdate, gender`. **Omitting a field leaves it unchanged; sending
an empty string clears it.** `username` and `email` cannot be cleared.

Values are normalised on the way in: usernames and emails are lower-cased, phone
numbers keep only their digits and a leading `+`, websites gain an `https://` scheme,
and gender is upper-cased to one of `MALE`, `FEMALE`, `OTHER`, `PREFER_NOT_TO_SAY`.

Errors use one envelope:

```json
{ "message": "Please check the highlighted field",
  "fieldErrors": { "phone": "Enter a valid phone number, e.g. +84 912 345 678" } }
```

`422` means a field failed validation, `409` means a username or email is taken; both
name the offending field in `fieldErrors` so the client can mark that input.

### Media

Uploads accept JPEG, PNG, WebP and GIF up to 8 MB and are served back from
`GET /media/{key}`.

`LocalMediaStorage` writes to `MEDIA_ROOT` (default `backend/uploads/`, git-ignored),
so avatar and cover uploads work without running MinIO. Everything goes through the
`MediaStorage` interface — swapping in a MinIO adapter means providing another
implementation in `Application.module()`, with no route or repository changes.

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `PORT` / `HOST` | `8080` / `0.0.0.0` | Ktor bind address |
| `DATABASE_URL` | Supabase pooler URI | `postgresql://user:pass@host:port/db` |
| `JWT_SECRET` / `JWT_ISSUER` / `JWT_AUDIENCE` | — | Token signing |
| `MEDIA_ROOT` | `uploads` | Directory for uploaded files |
| `PUBLIC_BASE_URL` | `http://10.0.2.2:$PORT` | Origin written into media URLs |

`PUBLIC_BASE_URL` must be reachable **from the device**. `10.0.2.2` works for the
emulator; on a physical phone, set it to the host machine's LAN address.

## Schema changes

`DatabaseFactory.init()` creates missing tables, then runs the idempotent
`ALTER TABLE ... ADD COLUMN IF NOT EXISTS` statements in `applyColumnMigrations()`.
Add new columns there as well as to the table object. Exposed's automatic column diff
is deliberately not used: it tries to re-add `profiles.id`, which does not exist —
`profiles` is keyed on `user_id`, per `schema.sql`.

Once the schema starts changing shape rather than just growing, move to Flyway or
Liquibase instead.
