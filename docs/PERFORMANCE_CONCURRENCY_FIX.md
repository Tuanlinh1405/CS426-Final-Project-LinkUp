# Báo cáo hiệu năng backend LinkUp (2026-09-02)

Gồm hai vòng chẩn đoán độc lập:
- **Phần 1** — request bị "serialize": connection starvation ở HikariCP (mục 1–5).
- **Phần 2** — từng request vẫn chậm: số round trip tới Supabase (mục 6–10).

---

# Phần 1: lỗi "request bị serialize"

Triệu chứng người dùng báo: các request tới backend có vẻ bị **serialize** — request sau phải chờ
request trước xong mới chạy, app rất chậm khi vào màn chat.

Kết luận: **không phải** do transaction isolation level `SERIALIZABLE`. Nguyên nhân thật là
**connection starvation ở HikariCP**, bị khuếch đại bởi **N+1 query**, một **retry storm ở client**
và **giới hạn dispatcher của OkHttp**. Bốn thứ này cộng lại tạo ra hiệu ứng giống hệt serialize.

---

## 1. Bằng chứng đã dựa vào

Từ log backend (`gradlew :backend:run`):

```
10:24:08.797 HikariPool-1 - Before cleanup stats (total=3, active=3, idle=0, waiting=2)
10:24:08.797 HikariPool-1 - After cleanup  stats (total=3, active=3, idle=0, waiting=3)
10:24:08.972 [worker-1]  SELECT users... WHERE email='alice@example.com'
10:24:09.179 [worker-4]  SELECT users... WHERE email='alice@example.com'
10:24:09.385 [worker-6]  SELECT users... WHERE email='alice@example.com'
10:24:09.587 [worker-5]  SELECT users... WHERE email='alice@example.com'
...
```

Ba dấu hiệu quyết định:

1. `waiting=2`, `waiting=3` — có request đang **xếp hàng chờ connection**, không phải chờ lock DB.
   Nếu là serialize do isolation, ta sẽ thấy lỗi serialization conflict / retry, không phải `waiting`.
2. Các query giống nhau cách nhau **đúng ~200ms** — bằng một round-trip tới Supabase
   (`aws-0-ap-southeast-2`). Đây là nhịp "một request xong thì nhả connection cho request kế tiếp".
3. Pool bị co lại liên tục: `After cleanup stats (total=5 → total=2)` do `idleTimeout=30000`,
   trong khi mở connection mới tới Supabase pooler mất **~2.7 giây**
   (`10:23:05.981 Starting... → 10:23:08.670 Start completed`).

---

## 2. Các nguyên nhân tìm được

### 2.1 Backend — pool HikariCP quá nhỏ và bị co liên tục
`backend/src/main/kotlin/com/linkup/database/DatabaseFactory.kt`

| Cấu hình | Trước | Vấn đề |
|---|---|---|
| `maximumPoolSize` | 10 | được |
| `minimumIdle` | 2 | pool chỉ duy trì 2 connection sẵn sàng |
| `idleTimeout` | 30 giây | pool tự co về 2 connection chỉ sau 30s không tải |
| dispatcher của `dbQuery` | `Dispatchers.IO` | unlimited thread — hàng trăm coroutine cùng vào, nhưng chỉ ~2–4 connection → `waiting` cao |
| mở connection mới | — | ~2.7s mỗi lần (Supabase pooler) |

Hệ quả: với 3 request DB đồng thời, 1–2 request phải **đợi connection mới** (~2.7s), và mỗi lần
pool co lại rồi nở lại là một cơn sóng chậm. Trong log thấy `waiting` tăng tới 3–4 cùng lúc.

### 2.2 Backend — N+1 query trong `ChatRepository`
`backend/src/main/kotlin/com/linkup/repository/ChatRepository.kt`

- `getMessagesForConversation`: với mỗi message lại gọi `calculateMessageStatusInternal` →
  1–2 query riêng. Mở chat có 50 tin = ~50–100 round-trip, mỗi cái ~200ms → **10–20 giây giữ 1
  connection**, chặn mọi request khác.
- `getConversationsForUser`: gọi `getLastMessageForConversationInternal` (2 query) cho **từng**
  conversation trong danh sách.
- `getParticipantsForConversationInternal`: query `ProfilesTable` 1 lần **cho từng member**.
- `getPendingMessagesForUser`: query `UsersTable` 1 lần **cho từng message** chờ flush.

### 2.3 Client — OkHttp chặn thread bằng `runBlocking`
`data/src/main/java/com/example/linkup/data/remote/interceptor/AuthInterceptor.kt`

Interceptor gọi `runBlocking { dataStore.getStoredToken() }` cho **mọi request** (trừ login/register).
Đọc DataStore là I/O tuần tự hoá trong process; nhiều request đồng thời → mỗi cái phải chờ lượt
đọc của mình qua một coroutine mới, trên thread OkHttp bị khoá. Đây là nguồn thứ hai tạo cảm giác
"serialize".

### 2.4 Client — retry đệ quy vô hạn
`data/src/main/java/com/example/linkup/data/repository/ChatRepositoryImpl.kt`

`refreshConversations()` khi fail lại `scope.launch { delay(2500); refreshConversations() }` —
đệ quy không giới hạn. Nếu backend chậm (như trên), nhiều chuỗi retry tồn tại song song, tự nướng
thêm request vào đống đang tắc.

### 2.5 Client — OkHttp mặc định 5 request/domain
`data/src/main/java/com/example/linkup/data/di/NetworkModule.kt`

OkHttp mặc định `maxRequestsPerHost=5`. Connection WebSocket chat chiếm 1 slot vĩnh viễn → chỉ còn
4 slot cho toàn bộ REST. Trong app chat, chỉ vài request đồng thời là đã đầy, các request sau xếp
hàng — giống hệt serialize.

---

## 3. Những gì đã sửa

### 3.1 `DatabaseFactory.kt` (backend)
```kotlin
private const val POOL_SIZE = 10

private val dbDispatcher: CoroutineDispatcher =
    Executors.newFixedThreadPool(POOL_SIZE).asCoroutineDispatcher()
```
- `minimumIdle = 10` — giữ đủ connection nóng, không còn co/giãn pool.
- `idleTimeout = 600000` — không dọn pool trong lúc app chỉ dùng vài request.
- `dbQuery` chạy trên dispatcher **giới hạn đúng bằng pool size** → coroutine không thể vào nhiều
  hơn số connection → hết oversubscription, hết `waiting`.

### 3.2 `ChatRepository.kt` (backend) — gỡ N+1
- `getMessagesForConversation`: đọc receipt của **toàn bộ** message trong 1 query, tính status bằng
  `calculateMessageStatusBulk(...)` (không còn query lẻ từng message).
- `getConversationsForUser`: thêm `buildLastMessagesMap(...)` — 1 query `max(createdAt)` theo nhóm +
  1 query receipt cho tất cả conversation (thay 2 query × số conversation).
- `getParticipantsForConversationInternal`: `leftJoin` thẳng `ProfilesTable` (thay 1 query/member).
- `getPendingMessagesForUser`: `innerJoin UsersTable` lấy `full_name` trực tiếp (thay 1 query/message).

### 3.3 `AuthTokenDataStore.kt` + `AuthInterceptor.kt` (Android)
- Thêm bộ đệm trong bộ nhớ: `cachedToken` (@Volatile) + `peekToken()` + `isTokenLoaded()`,
  cập nhật mỗi khi DataStore emit / save / clear.
- Interceptor chỉ đọc DataStore **một lần duy nhất** khi bộ đệm chưa được nạp (lần đầu sau khi
  process khởi động); mọi request sau đọc từ bộ nhớ, không `runBlocking`.

### 3.4 `ChatRepositoryImpl.kt` (Android) — chặn retry storm
- `scheduleConversationsRetry()` dùng đúng **một `retryJob`**; nếu một retry đang chờ thì không
  launch thêm. Hết đệ quy vô hạn, hết chồng chuỗi retry song song.

### 3.5 `NetworkModule.kt` (Android) — nới OkHttp dispatcher
```kotlin
val dispatcher = Dispatcher().apply {
    maxRequests = 64
    maxRequestsPerHost = 32
}
...
.pingInterval(20, TimeUnit.SECONDS)
```
- Giới hạn/host từ 5 lên 32 → connection WebSocket không còn "ăn" slot REST.
- `pingInterval` giữ WebSocket sống khi qua mạng di động/nat.

---

## 4. Còn nhận định quan trọng (chưa sửa)

1. `UserRepository.validateUser` dùng `or` trên `email`/`username`. Cả hai cột đều có unique index
   (Exposed `.uniqueIndex()`), nên query này vẫn chạy được; tốc độ đủ. Nếu muốn tối ưu tiếp, tách
   thành 2 lần lookup — nhưng đây **không phải** nguồn gây serialize.
2. `DatabaseFactory.init()` mỗi lần khởi động chạy `SchemaUtils.create(...)` — ở môi trường production
   nên thay bằng migration nghiêm túc (Flyway/Liquibase). Ngoài phạm vi request này.
3. `DbQuery` giờ dùng `Executors.newFixedThreadPool(10).asCoroutineDispatcher()` — thread pool cần
   được đóng khi app shutdown nếu về sau có lifecycle hook. Hiện ổn vì backend chạy đến khi tắt.

---

## 5. Kiểm chứng (đã chạy thật)

| Kiểm tra | Trước | Sau |
|---|---|---|
| Build `:backend:compileKotlin`, `:app:compileDebugKotlin` | — | BUILD SUCCESSFUL |
| Khởi động backend | pool: `minIdle=2` | log: `maximumPoolSize=10, minimumIdle=10` |
| 8 request login đồng thời (vòng `curl &`) | query cách nhau ~200ms, kéo dài ~2s | **8 query cùng mili giây**; mỗi request ~1.3s (giới hạn bởi BCrypt, không phải hàng đợi) |

Lưu ý: phần Android (3.3–3.5) đã được biên dịch nhưng chưa được chạy thử trên emulator/thiết bị
trong phiên này — cần kiểm thử thủ công trên app.

---

# Phần 2: tối ưu tốc độ truy vấn (2026-09-02, sau khi hết `waiting`)

Sau khi gỡ xong connection starvation, request không còn xếp hàng nhưng **từng request vẫn chậm**
(vào màn chat ~1.2s, login ~1.5s). Vòng 2 đo lại từ đầu và tìm ra nguyên nhân khác hẳn vòng 1:
không phải hàng đợi, mà là **số lần đi lại (round trip) tới Supabase**.

## 6. Đo lường: round trip là toàn bộ chi phí

Đo từ máy dev (Việt Nam) tới `aws-0-ap-southeast-2.pooler.supabase.com`:

| Phép đo | Kết quả |
|---|---|
| 1 hop DB (`SELECT 1`) | trung bình **203ms**, tốt nhất 185ms |
| 7 query tuần tự | **1638ms** — thuần RTT, không phải chi phí xử lý |
| Mở connection mới (TCP+TLS+auth) | **1.5–2.4 giây** |
| Cùng 1 câu `SELECT`: qua `dbQuery` của Exposed | **364ms** |
| Cùng 1 câu `SELECT`: JDBC auto-commit | **153ms** |
| `BCrypt.checkpw` cost-10 | 105–130ms (không đáng kể so với RTT) |

Công cụ đo: vòng lặp `SELECT 1` để lấy RTT nền, và `probe_server.py` (HTTP server giả có tham số
`?ms=` để trả chậm theo yêu cầu) để tách chi phí mạng client↔backend ra khỏi chi phí backend↔DB.

**Kết luận định hướng:** tối ưu ở đây nghĩa là **giảm số câu lệnh**, không phải giảm số dòng đọc.
Một câu lệnh chạm 4 bảng luôn nhanh hơn 4 câu lệnh dù mỗi câu đọc rất ít.

## 7. Các phát hiện

### 7.1 `BEGIN`/`COMMIT` của Exposed cộng thêm 2 round trip mỗi lần đọc

`dbQuery` dùng `newSuspendedTransaction`, nên **mọi** lần đọc đều là 3 hop:
`BEGIN` → `SELECT` → `COMMIT` ≈ 510ms, trong khi một câu đọc thuần chỉ cần 1 hop ≈ 170ms.
Đặt `isAutoCommit = true` ở HikariCP **không giải quyết được** — Exposed vẫn tự mở transaction.

### 7.2 `/conversations` tốn 7 câu lệnh

Sau vòng 1 endpoint này đã hết N+1 theo từng conversation, nhưng vẫn còn ~7 câu lệnh
(danh sách conversation, participants, last message, receipt của last message, unread count, …)
≈ **1.2s thuần chờ mạng**, dù dữ liệu chỉ vài chục dòng.

### 7.3 `/login` tốn 3 hop cho 1 lần tra bảng `users`

`validateUser` chạy trong transaction → `BEGIN`/`SELECT`/`COMMIT`, rồi BCrypt chạy **trong khi vẫn
đang giữ connection**.

### 7.4 Pooler thả connection nguội, mà mở lại rất đắt

Supabase pooler đóng connection rỗi; mỗi lần Hikari phải mở lại mất 1.5–2.4s — đúng cảm giác
"lần đầu bấm login thì rất lâu, sau đó thì nhanh".

## 8. Những gì đã sửa

### 8.1 `DatabaseFactory.kt` — thêm đường đọc raw không transaction

```kotlin
suspend fun <T> rawRead(block: (java.sql.Connection) -> T): T =
    kotlinx.coroutines.withContext(dbDispatcher) {
        dataSource.connection.use { conn ->
            conn.autoCommit = true
            block(conn)
        }
    }
```

- Bỏ `BEGIN`/`COMMIT` cho các lần đọc → 1 hop thay vì 3.
- Vẫn chạy trên `dbDispatcher` giới hạn = pool size như `dbQuery`.
- **Chỉ dùng cho đọc 1 câu lệnh.** Mọi thao tác ghi (register, send message, mark read…) vẫn đi
  `dbQuery` để giữ tính atomicity của transaction.
- Thêm `keepaliveTime = 120000` (Hikari gửi probe trước khi pooler thả connection).

### 8.2 `ChatRepository.getConversationsForUser` — 7 câu lệnh → 1 câu

Viết lại hoàn toàn thành **một** SQL (hằng số `CONVERSATION_LIST_SQL`, cuối file) dùng:
`JOIN` members/users/profiles + `LEFT JOIN LATERAL` cho last message kèm status +
correlated subquery cho unread count. `rawRead` cho toàn bộ, `readConversationRows` +
`groupBy` họp thành `ConversationResponse`. Đo được **136–162ms end-to-end** (trước ~1.2s).

### 8.3 `UserRepository.validateUser` — /login 3 hop → 1 hop

- Tra `users` bằng `rawRead` (1 câu `SELECT … WHERE email = ? OR username = ? LIMIT 1`,
  auto-commit) → 236–272ms (trước ~500ms+).
- **BCrypt chạy sau khi nhả connection** — không giữ connection trong lúc checkpw.
- Đổi trả về `AuthUser` (class plain) thay vì `UserEntity` detached — entity tạo ngoài
  transaction sẽ vỡ lazy-load.
- Giữ `dbQuery` (wrapper transaction) cho `registerUser` — vì có thao tác ghi.

### 8.4 Client — cache danh sách hội thoại trên thiết bị (`ConversationCacheDataStore`)

`ChatRepositoryImpl` lưu danh sách hội thoại vừa tải về vào DataStore; khi vào lại màn chat,
hiển thị ngay bản cache rồi mới tải mạng ở nền → hết cảnh "màn trắng đợi network".

## 9. Kiểm chứng (đã đo thật)

| Điểm đo | Trước | Sau |
|---|---|---|
| `/conversations` (end-to-end) | ~1.2s (7 query) | **136–162ms** (1 câu) |
| `/login` | ~500ms+ (transaction 3 hop) | **236–272ms** (1 câu auto-commit, BCrypt ngoài connection) |
| Đã chạy `:backend:compileKotlin` sau mọi thay đổi → BUILD SUCCESSFUL |

---

## 10. Nhận định còn lại

1. `SchemaUtils.create(...)` vẫn chạy mỗi lần khởi động backend — nên thay bằng migration
   (Flyway/Liquibase) nếu đưa production.
2. `ProfilesTable` vẫn giữ tên cột `user_id` (sửa từ `UUIDTable("profiles", "user_id")`) — SQL
   `CONVERSATION_LIST_SQL` join `profiles pr ON pr.user_id = u.id` theo đúng cột đó.
3. `idx_messages_conversation` đã có từ trước và phục vụ `getLastMessageForConversationInternal`
   (nơi còn lại dùng `dbQuery` cho consistency của ghi/tạo hội thoại).
4. Client (8.4) đã biên dịch nhưng chưa chạy thử trên emulator trong phiên này.

