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

### LinkUp AI configuration

Gemini credentials are server-only. Copy `backend/.env.ai.example` to
`backend/.env.ai`, then set `GEMINI_API_KEY`. `GEMINI_MODEL` defaults to
`gemini-3.6-flash`. The real `.env.ai` file is ignored by Git; never put this key in
the Android app or commit it to the repository.

## Tests

```bash
./gradlew :backend:test                       # JVM unit tests, no database needed
bash scripts/profile-api-smoke.sh             # end-to-end, needs the server running
bash scripts/notifications-api-smoke.sh       # end-to-end, needs the server running
bash scripts/discovery-api-smoke.sh           # end-to-end, needs the server running
bash scripts/friends-api-smoke.sh             # end-to-end, needs the server running
```

The smoke script registers throwaway users, so point it at a dev database.

## Endpoints

### Auth

| Method | Path | Notes |
| --- | --- | --- |
| `POST` | `/auth/register` | `{email, username, password, fullName?}` → `{user, token}` |
| `POST` | `/auth/login` | `{emailOrUsername, password}` → `{user, token}` |

### LinkUp AI

All AI routes require `Authorization: Bearer <jwt>`. Post analysis returns `202`
immediately on a cache miss, then downloads at most two photos concurrently, resizes
them to at most 1600 px, and sends at most 5 MB to Gemini with low thinking effort.
The Android client polls the owned conversation while the job runs. Completed output
is persisted in `ai_conversations` / `ai_messages` and reused from
`ai_analysis_cache` while the post, model and prompt version are unchanged.

| Method | Path | Notes |
| --- | --- | --- |
| `POST` | `/ai/posts/{postId}/analyze` | Create a conversation; `202` means analysis continues in the background |
| `GET` | `/ai/conversations` | List the caller's AI history |
| `POST` | `/ai/conversations` | Create a normal AI conversation |
| `GET` | `/ai/conversations/{id}/messages` | Load one owned conversation |
| `POST` | `/ai/conversations/{id}/messages` | Ask a follow-up question |

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
| `GET` | `/profile/{id}/followers` | Cursor-paged people list |
| `GET` | `/profile/{id}/following` | Cursor-paged people list |
| `POST` | `/profile/{id}/follow` | → `{isFollowing, followerCount}` |
| `DELETE` | `/profile/{id}/follow` | → `{isFollowing, followerCount}` |
| `GET` | `/users/search` | `?q=&cursor=&limit=` — matches username or full name |

Anywhere `{id}` appears it accepts a user UUID, a username, or the literal `me`.
People lists page by username cursor and carry `isMe` / `isFollowing` per row, so a
follow button can render without a second call.

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

### Notifications

All notification routes require `Authorization: Bearer <jwt>` and are scoped to the
caller — someone else's notification is indistinguishable from one that does not exist.

| Method | Path | Notes |
| --- | --- | --- |
| `GET` | `/notifications` | `?cursor=&limit=&filter=all\|unread` → `{items, nextCursor, unreadCount}` |
| `GET` | `/notifications/unread-count` | Just the badge figure |
| `PUT` | `/notifications/{id}/read` | `?read=false` marks it unread again |
| `PUT` | `/notifications/read-all` | → `{affected, unreadCount}` |
| `DELETE` | `/notifications/{id}` | Removes one |
| `DELETE` | `/notifications` | Clears the inbox |

Paging is by cursor, not offset. New rows arrive at the top of this list constantly,
and an offset would silently skip or repeat items as they shift. The cursor pairs the
timestamp with the row id (`<instant>_<uuid>`) so the sort is total even when two
notifications share a timestamp. A malformed cursor restarts from the top rather than
failing the request.

**Who writes notifications.** A notifications table nobody writes to is dead weight,
so the producers are wired in `NotificationWriter` and run inside the same transaction
as the action that caused them:

| Trigger | Type | Behaviour |
| --- | --- | --- |
| `POST /profile/{id}/follow` | `FOLLOW` | Any earlier FOLLOW from the same actor is replaced, so toggling follow cannot pile up duplicates |
| `DELETE /profile/{id}/follow` | — | Withdraws the FOLLOW notification |
| `POST /auth/register` | `SYSTEM` | A welcome notice, so a new account's inbox is never a dead end |

`NotificationType` also defines `LIKE`, `COMMENT`, `MENTION`, `MESSAGE` and
`DATING_MATCH`. Those rows render correctly today; they simply have no producer until
the feed, chat and dating features land. Call `NotificationWriter` from those features
when they do — the client already handles every type, and an unrecognised one degrades
to a readable row rather than disappearing.

### Friends

Friendship is mutual and negotiated; following is one-sided and immediate. Both exist
and are independent — you can follow someone you are not friends with, and vice versa.

| Method | Path | Notes |
| --- | --- | --- |
| `GET` | `/friends` | `?of=` to view someone else's friends |
| `GET` | `/friends/requests/incoming` | Requests waiting on you |
| `GET` | `/friends/requests/outgoing` | Requests you sent |
| `GET` | `/friends/requests/count` | Badge figure |
| `GET` | `/friends/suggestions` | "People you may know", ranked by mutual friends |
| `GET` | `/friends/{id}/state` | → `{status, friendCount, mutualFriendCount, incomingRequestCount}` |
| `POST` | `/friends/{id}/request` | Send a request |
| `DELETE` | `/friends/{id}/request` | Withdraw your request |
| `PUT` | `/friends/{id}/accept` | Accept a request sent to you |
| `PUT` | `/friends/{id}/decline` | Decline a request sent to you |
| `DELETE` | `/friends/{id}` | Unfriend |

`status` is one of `NONE`, `REQUEST_SENT`, `REQUEST_RECEIVED`, `FRIENDS`, always from
the caller's side. A rule violation returns **409** with the reason in `message`.

**One row per pair.** `friendships` holds the request and the friendship in the same
row: it is a request while `status = PENDING` and a friendship once `ACCEPTED`, so the
two can never disagree. Direction is kept because the UI needs it — the requester sees
"Requested", the addressee sees "Confirm".

The awkward cases live in `FriendshipRules`, which is pure and unit tested:

- Requesting someone **who already requested you** accepts their request rather than
  creating a second row.
- Requesting twice is a **no-op**, not an error.
- Only the addressee may accept or decline; only the requester may cancel.
- Declining **deletes** the row, so they may ask again later.

Notifications follow the action inside the same transaction: `FRIEND_REQUEST` on send,
`FRIEND_ACCEPT` on acceptance, and the request notification is **withdrawn** when the
request is cancelled, declined or accepted — a notification never outlives its request.

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
