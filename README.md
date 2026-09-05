# LinkUp

<p align="center">
  <strong>A full-stack social networking application for Android</strong><br>
  Feed, Reels, real-time chat, social graph, Dating, and AI in a modular architecture.
</p>

<p align="center">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.1.10-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Android" src="https://img.shields.io/badge/Android-API%2024%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white">
  <img alt="Ktor" src="https://img.shields.io/badge/Backend-Ktor%203.1.1-087CFA?logo=ktor&logoColor=white">
  <img alt="PostgreSQL" src="https://img.shields.io/badge/Database-PostgreSQL-4169E1?logo=postgresql&logoColor=white">
</p>

## Project information

| Item | Details |
|---|---|
| Project title | **LinkUp — A Full-Stack Social Networking Application for Android** |
| Project type | University coursework project |
| Demo video | **[Add the public demo video URL here before submission]** |
| Build instructions | See [Build and run instructions](#build-and-run-instructions) |
| Test credentials | See [Test accounts](#test-accounts) |

## Team members

| Student ID | Full name |
|---|---|
| 24125079 | Tran Dinh Quoc Thang |
| 24125057 | Tran Dang Le Huy |
| 24125066 | Bui Trong Tuan Linh |
| 24125054 | Do Manh Cuong |
| 24125065 | Vo Quoc Linh |

## Test accounts

LinkUp requires authentication. The following standard seed accounts are reserved for coursework
evaluation and the two-device real-time demonstration. They contain demonstration data only and
must not be reused for personal or production accounts.

| Device | Email | Password | Suggested use |
|---|---|---|---|
| Device A | `linkup.seed.01@example.com` | `Seed!J07M_jweSCYaOFIDkv4I_EOQ` | Feed, Reels, comments, sharing, and sending Chat messages |
| Device B | `linkup.seed.02@example.com` | `Seed!QtIKNxowTmcRO_NF0LmYDbCD` | Receiving Chat messages, typing, presence, and read-receipt verification |

The shared development backend and Supabase project must be available for these accounts to work.
If the test database is reseeded or replaced, update this table before submission. Infrastructure
credentials such as database passwords, JWT secrets, Supabase S3 keys, and Gemini API keys are
deliberately excluded from this README.

LinkUp is an academic project that recreates the core experience of a modern social networking
platform. The Android client is built with Jetpack Compose, while a Ktor backend exposes REST APIs
and WebSocket communication. PostgreSQL on Supabase stores relational data, Supabase Storage hosts
Reel videos, and Google Gemini powers multimodal post analysis and AI conversations.

The project focuses on three engineering goals: verifiable end-to-end data flows, responsive user
interactions through optimistic updates and media caching, and a modular structure that allows
multiple contributors to work in parallel with minimal merge conflicts.

> **Project status:** functional prototype for coursework and demonstrations. It has not been
> hardened for production use. Review [Current limitations](#current-limitations) before reusing it.

---

## Features

| Area | Capabilities |
|---|---|
| Authentication | Registration, email/username login, JWT authentication, and DataStore session restoration |
| Feed | Cursor pagination, text posts, up to four images per post, image caching, and preloading |
| Interactions | Likes, comments, one-level replies, comment reactions, and optimistic updates |
| Reels | Upload, autoplay, pause/mute, seek, double-tap ±10 seconds, cache/preload, and hide |
| Recommendation | Reel ranking based on interaction, recency, quality, follows, and viewing history |
| Profiles | Private/public views, profile editing, avatar/cover media, followers, and following |
| Friends | Request, accept, decline, cancel, unfriend, and mutual-friend suggestions |
| Chat | Direct/group conversations, images, typing, presence, read receipts, and WebSocket reconnect |
| Sharing | Share Posts and Reels to Chat using rich previews and deep navigation |
| Search | Search People, Posts, and Reels with debounce and cursor pagination |
| Notifications | Inbox, unread count, filtering, read state, deletion, and clear-all actions |
| Dating | Dating profile, discovery, Like/Pass, compatibility scoring, and mutual matches |
| LinkUp AI | Analyze post captions/images, ask follow-up questions, and reopen conversation history |


## System architecture

```mermaid
flowchart LR
    subgraph Android[Android Client]
      APP[app\ncomposition root]
      FEATURE[feature:*\nCompose UI + state]
      CORE[core\ndesign system + navigation]
      DATA[data\nrepositories + network + cache]
      APP --> FEATURE
      APP --> CORE
      FEATURE --> CORE
      FEATURE --> DATA
      APP --> DATA
    end

    DATA -->|REST + JWT| API[Ktor Backend]
    DATA <-->|WebSocket + JWT| API
    API -->|Exposed / JDBC| DB[(Supabase PostgreSQL)]
    API -->|S3-compatible API| STORAGE[(Supabase Storage)]
    API -->|HTTPS| AI[Google Gemini]
    API -->|/media| LOCAL[(Local media)]
```

The Android application never connects directly to PostgreSQL and never contains a database
password, S3 secret, or Gemini API key. The backend is the only trust boundary responsible for
authentication, authorization, business rules, persistence, and third-party integrations.

### Standard data flow

```text
Compose Screen
    → ViewModel / UI state
    → Repository in :data
    → Retrofit or WebSocket
    → Ktor route
    → Backend repository/transaction
    → PostgreSQL and/or Storage
```

The server remains the source of truth for all mutations. Likes, comments, and reactions update the
interface optimistically, then reconcile with the canonical API response. Chat messages use a
temporary client ID and server acknowledgement so an optimistic bubble can be replaced without
creating duplicates.

## Multi-module structure

```text
LinkUp/
├── app/                    # APK, MainActivity, composition root, and app navigation
├── core/                   # Theme, icons, shared components, motion, and AppRoute
├── data/                   # DTOs, Retrofit, repositories, DataStore, and WebSocket client
├── feature/
│   ├── auth/               # Splash, Login, Register, and session handling
│   ├── feed/               # Feed, Create Post, Post Detail, comments, and replies
│   ├── reels/              # Player, feed, cache/preload, upload, and comments
│   ├── profile/            # Profile, editing, follows, friends, and people lists
│   ├── chat/               # Conversation list/detail, groups, and share-to-chat
│   ├── ai/                 # AI Chat, post analysis, and conversation history
│   ├── dating/             # Dating profile, discovery, candidate, and match flows
│   └── more/               # Search, Notifications, and Settings
├── backend/                # Ktor, REST/WS, database, storage, and AI integration
├── docs/                   # Architecture, database, technical report, and demo guide
├── scripts/                # Seed and smoke-test utilities
└── Design UI/              # Interface design references
```

Feature modules do not depend directly on one another. Cross-feature navigation is expressed as
callbacks and composed in `:app`, allowing contributors to own separate modules without repeatedly
editing the same integration files.

## Technology stack

### Android

- Kotlin 2.1.10, Jetpack Compose, and Material 3
- Hilt with KSP for dependency injection
- Retrofit, OkHttp, and Kotlinx Serialization
- Preferences DataStore for authentication state and conversation cache
- Coil for images and AndroidX Media3/ExoPlayer for video playback and disk caching
- Kotlin Coroutines and Flow for asynchronous state management

### Backend and infrastructure

- Ktor 3.1.1 running on Netty
- JWT authentication and BCrypt password hashing
- Exposed/JDBC, PostgreSQL JDBC driver, and HikariCP
- Supabase PostgreSQL for relational application data
- Supabase Storage through its S3-compatible API for Reel assets
- Google Gemini for multimodal post analysis and chatbot conversations
- Local media adapter for avatars, cover photos, and Chat images in the demo environment

## Prerequisites

| Component | Requirement |
|---|---|
| JDK | JDK 17 for Gradle and the backend toolchain |
| Android Studio | A release compatible with Android Gradle Plugin 9.2.1 |
| Android SDK | Compile SDK 36, Target SDK 36, Min SDK 24 |
| Database | A Supabase/PostgreSQL project with the base schema and migrations applied |
| Storage | A Supabase Storage bucket with S3 access credentials for Reels |
| AI | A Gemini API key when running LinkUp AI |

The Gradle Wrapper downloads the required Gradle version, so contributors do not need a global
Gradle installation.

## Build and run instructions

### 1. Clone and open the project

```powershell
git clone https://github.com/Tuanlinh1405/CS426-Final-Project-LinkUp.git
cd CS426-Final-Project-LinkUp
```

Open the **repository root** in Android Studio. Do not open the `app/` directory as a standalone
project.

### 2. Configure the backend

Create local configuration files from the committed templates. Real configuration files are
ignored by Git and must never be committed:

```powershell
Copy-Item backend/.env.example backend/.env
Copy-Item backend/.env.storage.example backend/.env.storage
Copy-Item backend/.env.ai.example backend/.env.ai
```

Configure `backend/.env`:

```dotenv
PORT=8080
LINKUP_SERVER_HOST=0.0.0.0
DATABASE_URL=postgresql://USER:PASSWORD@POOLER_HOST:5432/postgres?sslmode=require
JWT_SECRET=replace-with-a-long-random-secret
JWT_ISSUER=http://localhost:8080
JWT_AUDIENCE=linkup

# Required when another device must load avatar, cover, or Chat media from this backend.
PUBLIC_BASE_URL=http://YOUR_LAN_IP:8080
```

Configure `backend/.env.storage`:

```dotenv
REELS_STORAGE=supabase
SUPABASE_STORAGE_S3_ENDPOINT=https://PROJECT_REF.storage.supabase.co/storage/v1/s3
SUPABASE_STORAGE_S3_REGION=PROJECT_REGION
SUPABASE_STORAGE_S3_ACCESS_KEY_ID=replace-me
SUPABASE_STORAGE_S3_SECRET_ACCESS_KEY=replace-me
SUPABASE_STORAGE_BUCKET=linkup-media
```

Configure `backend/.env.ai`:

```dotenv
GEMINI_API_KEY=replace-with-google-ai-studio-key
GEMINI_MODEL=gemini-3.6-flash
```

Do not use a Supabase anon key as `JWT_SECRET`. If the database password contains URI-reserved
characters such as `@`, `#`, `%`, or `:`, percent-encode the password before adding it to
`DATABASE_URL`.

### 3. Prepare the database

The base schema is located at
[`docs/Database Design/schema.sql`](docs/Database%20Design/schema.sql).

Additional migrations live in
[`backend/src/main/resources/db/migrations`](backend/src/main/resources/db/migrations) and must be
applied in order from `001` through `005`:

| Version | Purpose |
|---|---|
| 001 | Reel assets, reactions, comments, watch events, hidden state, and indexes |
| 002 | Supabase Storage backend support |
| 003 | Removes the 60-second Reel ceiling while retaining valid duration constraints |
| 004 | Search support and one-level comment replies |
| 005 | Comment reactions for Posts and Reels |

Apply `001` and `002` through the Supabase SQL Editor. The repository provides idempotent Gradle
tasks for `003`–`005`:

```powershell
.\gradlew.bat :backend:reelsDurationMigration --args="--confirm"
.\gradlew.bat :backend:searchRepliesMigration --args="--confirm"
.\gradlew.bat :backend:commentReactionsMigration --args="--confirm"
```

Verify database and Storage connectivity after configuration:

```powershell
.\gradlew.bat :backend:dbCheck
.\gradlew.bat :backend:storageCheck
```

`storageCheck` creates, reads, and deletes one temporary object. It does not modify application
media.

### 4. Run the backend

From the repository root:

```powershell
.\gradlew.bat :backend:run
```

The backend is ready when the log contains:

```text
Responding at http://0.0.0.0:8080
```

Open `http://localhost:8080/` on the backend host. The expected response is:

```text
LinkUp Backend is running!
```

### 5. Configure the Android API URL

Create or update `local.properties` in the repository root. This file belongs to the local machine
and is excluded from version control.

**Android Emulator on the same machine as the backend:**

```properties
linkup.apiBaseUrl=http\://10.0.2.2\:8080/
```

**Physical Android device on the same Wi-Fi network:**

```properties
linkup.apiBaseUrl=http\://192.168.1.100\:8080/
```

Replace `192.168.1.100` with the backend machine's actual LAN IPv4 address. The URL must end with
`/`. Sync and rebuild the project after changing it because the value is compiled into
`BuildConfig`.

### 6. Build and run the Android application

```powershell
.\gradlew.bat :app:assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Alternatively, select the `app` run configuration and an emulator or connected device in Android
Studio, then click **Run**.

> On Windows, the correct command is `.\gradlew.bat`. `.\gradlew\.bat` is invalid because
> `gradlew.bat` is a file in the repository root, not a file inside a `gradlew` directory.

## Running on two devices

Use two devices to demonstrate real-time Chat and cross-account data flows:

1. Run exactly one backend instance with `LINKUP_SERVER_HOST=0.0.0.0`.
2. Connect the backend host and both Android devices to the same Wi-Fi network.
3. Use the host LAN address for both `linkup.apiBaseUrl` and `PUBLIC_BASE_URL`.
4. Allow Java or TCP port `8080` through Windows Firewall on Private networks.
5. Install the same APK on both devices and sign in with different accounts.
6. Visit `http://<LAN-IP>:8080/` in each device browser before opening the application.

Chat messages, typing state, presence, receipts, and deletion events are delivered through
WebSocket. Feed interactions, friendships, and in-app notifications are persisted on the server,
but the other device may need to refresh. The current build does not provide FCM/system push.

## Testing and code quality

### Android/JVM unit tests

```powershell
.\gradlew.bat testDebugUnitTest
```

### Backend tests

```powershell
.\gradlew.bat :backend:test
```

### Debug APK build

```powershell
.\gradlew.bat :app:assembleDebug
```

### Static analysis

```powershell
.\gradlew.bat ktlintCheck detekt
```

Backend tests use H2 and Ktor test hosts where appropriate and do not require a production database
connection. Smoke tests under `scripts/` require a running backend and may create test records in
the configured database; only run them against a development environment.

## Performance and concurrency

- Feed uses cursor pagination, nearby-image preloading, and first-page comment prefetching.
- Coil cache keys use stable media IDs instead of short-lived signed URLs.
- Reels warm the media cache at application startup, prioritize the visible video, and only prepare
  the required neighboring players.
- Watch events are best effort and never block a swipe gesture.
- Likes, comments, and reactions use optimistic updates and server reconciliation.
- OkHttp increases per-host concurrency so a persistent WebSocket does not starve REST calls.
- AI post analysis returns `202 Accepted` on a cache miss and processes images in the background
  with bounded concurrency.
- Chat reconnects with exponential backoff and provides a REST fallback for text messages.

See [Performance & Concurrency](docs/PERFORMANCE_CONCURRENCY_FIX.md) for the implementation details.

## Configuration security

- Never commit `.env`, `local.properties`, keystores, API keys, database passwords, or S3 secrets.
- Secrets belong exclusively to the backend; Android only receives the public API base URL.
- User passwords are hashed with BCrypt and protected endpoints require JWT authentication.
- The server validates upload MIME types, size limits, and resource ownership.
- Database mutations are scoped to the authenticated user and the applicable business rules.
- Revoke and rotate any credential that has appeared in Git history, screenshots, or public chat.

Only `.env.example`, `.env.storage.example`, and `.env.ai.example` may be committed, and they must
contain placeholders rather than live credentials.

## Current limitations

- There is no refresh-token or server-side token revocation flow; logout primarily clears the
  client session.
- There is no FCM integration or operating-system push notification service.
- There is no complete offline mode or Room database; only authentication data, conversation lists,
  and media caches are persisted locally.
- Avatars, cover images, and Chat images still use backend-local storage in the demo environment;
  Reel assets use Supabase Storage.
- Reel recommendation is an interaction-based heuristic, not a topic-aware machine learning model.
- AI analysis is not a specialist solver. For example, a chessboard image is not validated by a
  chess engine.
- Schema changes use reviewed SQL and explicit Gradle tasks, but Flyway/Liquibase and an automated
  migration gate have not been integrated.
- Production concerns such as rate limiting, token rotation, metrics, alerting, and a deployment
  pipeline remain outside the current scope.

## Contributing

1. Create a focused branch for each feature or fix and keep changes inside the owning module when
   possible.
2. Do not introduce direct dependencies between `feature:*` modules. Coordinate cross-feature
   navigation and callbacks in `:app`.
3. Do not modify the database schema only through the dashboard. Commit a versioned migration for
   every schema change.
4. Do not commit generated media, seed output, build output, or local secret files.
5. Before opening a pull request, run at minimum:

   ```powershell
   .\gradlew.bat :app:assembleDebug :backend:test
   ```

6. Document changed modules, API/schema/environment changes, migrations, and manual verification
   steps in the pull request.
7. Include before/after screenshots for UI changes and test real-time changes with two accounts.

## Documentation

| Document | Description |
|---|---|
| [Architecture & API](docs/ARCHITECTURE_API_DATABASE.md) | Module boundaries, REST, WebSocket, and database contracts |
| [Database Schema](docs/Database%20Design/schema.sql) | Canonical PostgreSQL base schema |
| [Backend Guide](backend/README.md) | Backend configuration and endpoint reference |
| [Feed Implementation](docs/FEED_IMPLEMENTATION.md) | Feed/Post media, interactions, and caching |
| [Reels Implementation](docs/REELS_IMPLEMENTATION.md) | Reel storage, upload, recommendation, and migrations |
| [Search & Comment Replies](docs/SEARCH_AND_COMMENT_REPLIES.md) | Search, replies, and comment reactions |
| [Performance & Concurrency](docs/PERFORMANCE_CONCURRENCY_FIX.md) | Caching, preloading, request concurrency, and real-time tuning |

## Usage scope

LinkUp was developed for coursework and academic evaluation. This repository does not currently
declare a software license for redistribution or commercial use. Contact the project authors before
reusing it outside the original academic context.
