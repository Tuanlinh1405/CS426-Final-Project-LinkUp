# PLAN.md — Social Network Android App

## 1. Tổng quan dự án

Xây dựng một ứng dụng mạng xã hội Android lấy Facebook làm khuôn mẫu chính về luồng sử dụng.

### Mục tiêu chính

Ứng dụng cho phép nhiều người dùng:

- Đăng ký, đăng nhập.
- Lướt News Feed.
- Đăng bài viết văn bản, ảnh, video.
- Like, comment, share.
- Xem và đăng Reels.
- Xem và chỉnh sửa trang cá nhân.
- Follow/kết bạn với người dùng khác.
- Chat 1-1 theo thời gian thực.
- Gửi tin nhắn văn bản, ảnh và video.
- Sử dụng trợ lý AI tương tự Meta AI.
- Sử dụng tính năng Dating.
- Nhận thông báo.
- Tìm kiếm người dùng và nội dung.

Ứng dụng được viết bằng **Kotlin trong Android Studio**, hỗ trợ **Android API 24+**.

---

# 2. Công nghệ sử dụng

## Android Frontend

- Kotlin
- Android Studio
- Jetpack Compose
- Material 3
- Navigation Compose
- MVVM
- Hilt
- Retrofit / OkHttp
- Kotlin Coroutines
- StateFlow
- Room Database
- DataStore
- Coil
- Media3 ExoPlayer
- WorkManager
- NotificationManager

## Backend

- Kotlin
- Ktor
- REST API
- WebSocket
- JWT Authentication
- PostgreSQL
- MinIO
- Docker Compose

## Database

### PostgreSQL

Dùng để lưu dữ liệu có cấu trúc:

- User
- Profile
- Post
- Comment
- Like
- Follow
- Reel metadata
- Conversation
- Message metadata
- Notification
- Dating
- AI conversation

### MinIO

Dùng để lưu file:

- Avatar
- Cover
- Ảnh bài viết
- Video bài viết
- Reel
- Thumbnail video
- Ảnh chat
- Video chat
- Ảnh Dating

### Room

Dùng để lưu dữ liệu local trên Android:

- Feed cache
- User cache
- Conversation
- Message
- Reel metadata
- Notification
- Pending message
- Pending upload

### DataStore

Dùng để lưu:

- JWT token
- User ID hiện tại
- Cài đặt app
- Theme
- Notification preference

---

# 3. Kiến trúc tổng thể

```text
Android App
   |
   |-- REST API -----------   |                       |
   |-- WebSocket ----------|---- Ktor Backend
   |                       |         |
   |-- Room Local DB       |         |-- PostgreSQL
                            |         |
                            |         |-- MinIO
                            |
                            |-- AI Provider
```

Frontend áp dụng:

```text
UI
↓
ViewModel
↓
UseCase
↓
Repository
↓
Local Database / REST API / WebSocket
```

---

# 4. Các màn hình chính

## 4.1. Splash Screen

Có:

- Logo ứng dụng.
- Loading.
- Kiểm tra token đăng nhập.
- Điều hướng sang Login hoặc Home.

---

## 4.2. Login Page

Có:

- Email / username.
- Password.
- Nút đăng nhập.
- Nút chuyển sang đăng ký.
- Hiện/ẩn mật khẩu.
- Loading.
- Hiển thị lỗi đăng nhập.

---

## 4.3. Register Page

Có:

- Họ tên.
- Username.
- Email.
- Password.
- Confirm password.
- Ngày sinh.
- Giới tính.
- Chọn avatar.
- Nút đăng ký.
- Validation dữ liệu.

---

# 5. Home / Feed Page

Có:

- Thanh điều hướng trên cùng.
- Logo app.
- Search.
- Notification.
- AI shortcut.
- Ô tạo bài viết.
- Danh sách bài viết.
- Pull to refresh.
- Load thêm khi kéo xuống.

Mỗi bài viết có:

- Avatar.
- Tên người đăng.
- Thời gian đăng.
- Nội dung text.
- Ảnh hoặc video.
- Số lượt like.
- Số comment.
- Nút Like.
- Nút Comment.
- Nút Share.
- Menu bài viết.

Trạng thái cần xử lý:

- Loading.
- Empty.
- Error.
- Offline.
- Loading more.

---

# 6. Create Post Page

Có:

- Nội dung bài viết.
- Chọn ảnh.
- Chọn video.
- Preview media.
- Xóa media đã chọn.
- Chọn privacy nếu cần.
- Nút đăng.
- Upload progress.
- Validation file.

Luồng:

```text
Chọn ảnh/video
↓
Upload file lên Backend
↓
Backend lưu file vào MinIO
↓
MinIO trả object key
↓
Backend lưu metadata vào PostgreSQL
↓
Tạo Post
↓
Feed cập nhật
```

---

# 7. Post Detail Page

Có:

- Nội dung đầy đủ bài viết.
- Ảnh/video.
- Like.
- Danh sách comment.
- Nhập comment.
- Reply comment nếu làm thêm.
- Xóa comment của chính mình.

---

# 8. Reels Page

Có:

- Danh sách video dọc.
- Video autoplay.
- Pause/play.
- Avatar người đăng.
- Username.
- Caption.
- Like.
- Comment.
- Share.
- Nút upload Reel.

Sử dụng:

- Media3 ExoPlayer.

Mỗi Reel chỉ lưu metadata trong PostgreSQL.

File video lưu trong MinIO.

Ví dụ:

```text
reels/{userId}/{reelId}/{uuid}.mp4
```

Thumbnail:

```text
reels/{userId}/{reelId}/{uuid}-thumbnail.jpg
```

Database lưu:

- reel_id
- user_id
- video_key
- thumbnail_key
- caption
- duration
- width
- height
- file_size
- created_at

Không lưu video dưới dạng BLOB trong PostgreSQL.

---

# 9. Upload Reel Page

Có:

- Chọn video.
- Preview video.
- Caption.
- Thời lượng.
- Upload progress.
- Nút đăng Reel.
- Báo lỗi nếu file quá lớn hoặc sai định dạng.

---

# 10. Profile Page

Có:

- Cover photo.
- Avatar.
- Họ tên.
- Username.
- Bio.
- Số follower.
- Số following.
- Nút Follow / Unfollow.
- Nút Edit Profile nếu là tài khoản của mình.
- Danh sách bài viết.
- Danh sách Reel.
- Danh sách ảnh.

---

# 11. Edit Profile Page

Có:

- Đổi avatar.
- Đổi cover.
- Sửa tên.
- Sửa bio.
- Sửa thông tin cá nhân.
- Nút Save.

---

# 12. Search Page

Có:

- Search bar.
- Search user.
- Search post.
- Recent search.
- Kết quả tìm kiếm.
- Empty state.
- Loading.
- Error.

API ví dụ:

```text
GET /users/search?q=
GET /posts/search?q=
```

---

# 13. Chat List Page

Có:

- Danh sách conversation.
- Avatar.
- Tên người dùng.
- Tin nhắn gần nhất.
- Thời gian.
- Số tin chưa đọc.
- Search conversation.

---

# 14. Chat Detail Page

Có:

- Avatar.
- Tên người chat.
- Danh sách tin nhắn.
- Tin nhắn gửi.
- Tin nhắn nhận.
- Tin nhắn text.
- Tin nhắn ảnh.
- Tin nhắn video.
- Ô nhập tin nhắn.
- Nút gửi.
- Nút chọn ảnh.
- Nút chọn video.
- Trạng thái gửi.
- Retry khi gửi thất bại.

Chat realtime dùng WebSocket.

---

# 15. Cách lưu tin nhắn

## Tin nhắn văn bản

Lưu trực tiếp trong PostgreSQL:

```text
messages
- id
- conversation_id
- sender_id
- message_type = TEXT
- text_content
- created_at
- read_at
```

## Tin nhắn ảnh

File ảnh:

```text
MinIO:
chat/{conversationId}/{uuid}.jpg
```

PostgreSQL:

```text
messages
- id
- conversation_id
- sender_id
- message_type = IMAGE
- media_id
```

Media table:

```text
media
- id
- owner_id
- storage_key
- mime_type
- file_size
- width
- height
```

## Tin nhắn video

File video:

```text
MinIO:
chat/{conversationId}/{uuid}.mp4
```

Thumbnail:

```text
chat/{conversationId}/{uuid}-thumb.jpg
```

PostgreSQL lưu:

```text
message_type = VIDEO
media_id = ...
```

---

# 16. Realtime Chat Flow

```text
User A gửi tin
↓
Tin được lưu tạm vào Room
↓
WebSocket gửi message event
↓
Backend kiểm tra JWT
↓
Backend lưu message vào PostgreSQL
↓
Backend trả ACK cho User A
↓
Backend gửi realtime event cho User B
↓
User B lưu message vào Room
↓
UI cập nhật
```

Ảnh/video không gửi trực tiếp bằng WebSocket.

Luồng media:

```text
Upload file bằng REST
↓
Nhận mediaId
↓
Gửi WebSocket message chứa mediaId
```

---

# 17. AI Page

Có:

- Danh sách cuộc trò chuyện AI.
- Chat AI.
- Ô nhập prompt.
- Tin nhắn user.
- Tin nhắn AI.
- Loading khi AI trả lời.
- Error nếu AI không hoạt động.
- Lịch sử AI conversation.

Backend:

```text
Android
↓
POST /ai/chat
↓
Ktor Backend
↓
AI Provider
```

Nên có hai chế độ:

```text
AI_MODE=MOCK
AI_MODE=REMOTE
```

Mock mode dùng khi:

- Không có API key.
- Hết quota.
- Không có internet.
- Demo trên máy local.

API key chỉ lưu trên backend.

---

# 18. Dating Page

Có:

- Profile Dating.
- Ảnh.
- Tên.
- Tuổi.
- Bio.
- Sở thích.
- Nút Like.
- Nút Pass.
- Danh sách Match.

Luồng match:

```text
Alice Like Bob
↓
Lưu dating_swipe
↓
Bob Like Alice
↓
Backend phát hiện mutual like
↓
Tạo dating_match
↓
Tạo notification
↓
Hai user thấy Match
```

Có thể tự động tạo conversation sau khi match.

---

# 19. Notification Page

Có:

- Notification like.
- Notification comment.
- Notification follow.
- Notification chat.
- Notification dating match.
- Đánh dấu đã đọc.
- Đánh dấu tất cả đã đọc.

Notification data lưu trong PostgreSQL.

Khi app đang chạy local:

- WebSocket gửi notification event.
- Android dùng NotificationManager để hiển thị system notification.

---

# 20. Settings Page

Có:

- Account.
- Notification settings.
- Theme.
- Logout.
- About.
- Server configuration trong debug mode.
- Xóa cache nếu cần.

---

# 21. PostgreSQL Database

Các bảng chính:

```text
users
refresh_tokens

follows

posts
post_media
post_reactions
comments

reels
reel_reactions
reel_comments

media

conversations
conversation_members
messages

notifications

dating_profiles
dating_photos
dating_swipes
dating_matches

ai_conversations
ai_messages
```

---

# 22. Quan hệ database chính

```text
User
├── Posts
│   ├── Media
│   ├── Likes
│   └── Comments
│
├── Reels
│   └── Media
│
├── Conversations
│   └── Messages
│       └── Media
│
├── Notifications
│
├── Dating Profile
│   ├── Dating Photos
│   ├── Swipes
│   └── Matches
│
└── AI Conversations
    └── AI Messages
```

---

# 23. REST API chính

## Authentication

```text
POST /auth/register
POST /auth/login
POST /auth/logout
GET  /auth/me
```

## User

```text
GET    /users/{id}
PATCH  /users/me
POST   /users/{id}/follow
DELETE /users/{id}/follow
GET    /users/search?q=
```

## Feed / Post

```text
GET    /feed
POST   /posts
GET    /posts/{id}
DELETE /posts/{id}

POST   /posts/{id}/like
DELETE /posts/{id}/like

GET    /posts/{id}/comments
POST   /posts/{id}/comments
```

## Reels

```text
GET    /reels
POST   /reels
DELETE /reels/{id}
POST   /reels/{id}/like
```

## Media

```text
POST /media/upload
```

## Chat

```text
GET  /conversations
POST /conversations/direct/{userId}
GET  /conversations/{id}/messages
```

## Notifications

```text
GET  /notifications
POST /notifications/{id}/read
```

## Dating

```text
GET  /dating/profile
PUT  /dating/profile
GET  /dating/discover
POST /dating/swipes
GET  /dating/matches
```

## AI

```text
GET  /ai/conversations
POST /ai/conversations
GET  /ai/conversations/{id}/messages
POST /ai/conversations/{id}/messages
```

---

# 24. WebSocket Events

Client gửi:

```text
SEND_MESSAGE
READ_MESSAGE
PING
```

Server gửi:

```text
NEW_MESSAGE
MESSAGE_ACK
NEW_NOTIFICATION
DATING_MATCH
ERROR
```

Có thể thêm:

```text
TYPING_START
TYPING_STOP
USER_ONLINE
USER_OFFLINE
```

---

# 25. Local Storage và Offline

Room cần được dùng để đáp ứng yêu cầu persistent local data.

## Feed

```text
Mở app
↓
Hiển thị dữ liệu Room trước
↓
Gọi API
↓
Nếu thành công → cập nhật Room
↓
Nếu lỗi → giữ cache và hiện Offline
```

## Chat

Tin gửi khi mất mạng:

```text
Save Room
status = PENDING
↓
Kết nối lại
↓
Retry
↓
Server ACK
↓
status = SENT
```

Message status:

```text
PENDING
UPLOADING
SENT
FAILED
READ
```

---

# 26. Authentication và Security

- Password phải hash bằng BCrypt hoặc Argon2.
- JWT dùng để xác thực.
- Không lưu password plaintext.
- Không hard-code JWT secret.
- Không hard-code AI API key.
- Backend phải kiểm tra quyền trước khi edit/delete.
- Validate MIME type media.
- Validate file size.
- Media filename dùng UUID.
- Không tin dữ liệu gửi từ client.

---

# 27. Test nhiều User mà không Deploy

Backend chạy ngay trên laptop.

Dùng:

```text
Docker Compose
├── PostgreSQL
├── MinIO
└── Backend
```

Có thể chạy:

```text
Emulator 1 → Alice
Emulator 2 → Bob
Emulator 3 → Charlie
```

Android Emulator truy cập máy host bằng:

```text
http://10.0.2.2:8080
```

Ví dụ:

```text
API_BASE_URL=http://10.0.2.2:8080/api/v1/
WS_URL=ws://10.0.2.2:8080/ws
```

Nếu dùng điện thoại thật:

- Điện thoại và laptop cùng Wi-Fi.
- Backend bind `0.0.0.0`.
- Android kết nối vào LAN IP của laptop.

Ví dụ:

```text
http://192.168.x.x:8080
```

---

# 28. Các bài test Multi-user

## Feed

```text
Bob đăng bài
↓
Alice refresh Feed
↓
Alice thấy bài của Bob
```

## Like

```text
Alice like bài Bob
↓
Bob nhận notification
```

## Comment

```text
Alice comment
↓
Bob mở Post Detail
↓
Bob thấy comment
```

## Chat

```text
Alice gửi "Hello"
↓
Bob nhận realtime
↓
Bob gửi ảnh
↓
Alice thấy ảnh
```

## Reel

```text
Bob upload Reel
↓
Alice mở Reels
↓
Alice xem được video
```

## Dating

```text
Alice like Bob
Bob like Alice
↓
Match
```

---

# 29. Workflow phát triển

## Phase 1 — Setup

- Tạo Git repository.
- Setup Android project.
- Setup Ktor backend.
- Setup PostgreSQL.
- Setup MinIO.
- Setup Docker Compose.
- Android gọi được `/health`.

## Phase 2 — Authentication

- Register API.
- Login API.
- JWT.
- Login/Register UI.
- DataStore token.

## Phase 3 — Profile

- Profile API.
- Edit Profile.
- Avatar upload.
- Follow/unfollow.

## Phase 4 — Feed

- Create Post.
- Feed API.
- Like.
- Comment.
- Image upload.
- Room cache.

## Phase 5 — Reels

- Reel upload.
- Reel API.
- MinIO video.
- Media3 playback.

## Phase 6 — Chat

- Conversation.
- Message DB.
- WebSocket.
- Text message.
- Room chat cache.

## Phase 7 — Chat Media

- Image message.
- Video message.
- Upload progress.
- Retry.

## Phase 8 — Notifications

- Notification DB.
- Realtime event.
- Android system notification.

## Phase 9 — AI

- AI screen.
- AI conversation history.
- Mock provider.
- Remote provider.

## Phase 10 — Dating

- Dating Profile.
- Discover.
- Like/Pass.
- Match.

## Phase 11 — Polish

- Loading.
- Empty state.
- Error.
- Offline.
- Responsive UI.
- Multi-device testing.

## Phase 12 — Submission

- Build APK.
- Report PDF.
- Demo video.
- README.
- Final ZIP.

---

# 30. Priority Feature List

## P0 — Phải hoàn thành

- Login/Register.
- Profile.
- Follow.
- Feed.
- Create Post.
- Like.
- Comment.
- Image upload.
- Reels.
- Chat text.
- Chat image.
- Room.
- Backend.
- PostgreSQL.
- MinIO.
- WebSocket.
- Error/loading/offline state.

## P1 — Nên hoàn thành

- Chat video.
- AI.
- Dating.
- Notification page.
- Share.
- Search.
- Pagination.

## P2 — Nếu còn thời gian

- Story.
- Group chat.
- Voice message.
- Typing indicator.
- Online status.
- Video call.
- Advanced feed recommendation.

---

# 31. Mapping với Grading

## Functional Completeness — 30%

Cần đảm bảo:

- APK cài được.
- Không crash.
- Login/Register chạy.
- Feed chạy.
- Post chạy.
- Reels chạy.
- Profile chạy.
- Chat chạy.
- Dữ liệu persist.
- Nhiều user dùng cùng lúc.

---

## Technical Quality & Source Code — 25%

Thể hiện:

- MVVM.
- Repository.
- UseCase.
- Hilt.
- Room.
- Retrofit.
- Coroutines.
- StateFlow.
- WebSocket.
- JWT.
- PostgreSQL.
- MinIO.
- Docker.
- Lifecycle handling.
- Runtime permission.

---

## UI/UX — 20%

Mỗi page cần có:

- Loading.
- Empty.
- Error.
- Retry.
- Offline nếu có network.
- Navigation rõ ràng.
- UI nhất quán.
- Responsive.
- Không crash khi media lỗi.

---

## Originality & Complexity — 15%

Các điểm nổi bật:

- Feed.
- Reels.
- Chat realtime.
- AI.
- Dating.
- Media storage.
- Offline Room.
- Multi-user local testing.

---

## Report, Presentation & Collaboration — 10%

Cần có:

- Report 10–30 trang.
- Architecture diagram.
- ER diagram.
- Công nghệ.
- Setup.
- Work division.
- Testing.
- Self-assessment.
- Demo video 5–10 phút.
- Tất cả thành viên đều nói.
- Git commit thể hiện đóng góp.

---

# 32. Team Work Division gợi ý

## Thành viên 1

- Android Core.
- Navigation.
- Authentication.
- Feed.
- Room.

## Thành viên 2

- Backend.
- PostgreSQL.
- Auth API.
- Post API.
- User API.
- Docker.

## Thành viên 3

- Reels.
- Media.
- MinIO.
- Video playback.
- Upload.

## Thành viên 4

- Chat.
- WebSocket.
- Notification.
- AI.
- Dating.

Mỗi thành viên cần có commit riêng và trình bày phần mình làm trong video.

---

# 33. Report Structure

```text
1. Project Overview
2. Problem and Target Users
3. Functional Requirements
4. UI/UX
5. Application Architecture
6. Frontend Architecture
7. Backend Architecture
8. Database Design
9. Media Storage
10. REST API
11. WebSocket
12. Main Features
13. Offline Storage
14. Security
15. Testing
16. Multi-user Testing
17. Work Division
18. Installation
19. Self-assessment
20. Limitations
21. Future Work
22. Conclusion
```

---

# 34. Cấu trúc source

```text
src/
├── android/
│   └── app/
│
├── backend/
│
├── docker-compose.yml
├── .env.example
└── README.md
```

Android:

```text
app/
├── core/
├── data/
├── domain/
└── feature/
    ├── auth/
    ├── feed/
    ├── reels/
    ├── profile/
    ├── search/
    ├── chat/
    ├── ai/
    ├── dating/
    ├── notification/
    └── settings/
```

---

# 35. Final Submission Structure

```text
<ID1>_<ID2>_<ID3>/
├── README.md
├── src/
│   ├── android/
│   ├── backend/
│   └── ...
├── apk/
│   └── app-release.apk
├── report/
│   └── report.pdf
└── video/
    └── demo-link.txt
```

Không đưa vào ZIP:

```text
build/
.gradle/
.idea/
node_modules/
.env
*.keystore
```

---

# 36. Checklist cuối

- [ ] APK chạy trên API 24+.
- [ ] Login/Register hoạt động.
- [ ] 2–3 tài khoản test được cùng lúc.
- [ ] Feed hoạt động.
- [ ] Create Post hoạt động.
- [ ] Like hoạt động.
- [ ] Comment hoạt động.
- [ ] Profile hoạt động.
- [ ] Reels hoạt động.
- [ ] Upload video hoạt động.
- [ ] Chat realtime hoạt động.
- [ ] Chat text hoạt động.
- [ ] Chat ảnh hoạt động.
- [ ] Chat video nếu có.
- [ ] AI hoạt động hoặc có mock fallback.
- [ ] Dating hoạt động.
- [ ] Notification hoạt động.
- [ ] Room lưu local.
- [ ] Offline không làm app crash.
- [ ] Loading state đầy đủ.
- [ ] Empty state đầy đủ.
- [ ] Error state đầy đủ.
- [ ] Backend chạy local bằng Docker.
- [ ] PostgreSQL lưu structured data.
- [ ] MinIO lưu ảnh/video.
- [ ] Không có secret trong source.
- [ ] Report 10–30 trang.
- [ ] Demo video 5–10 phút.
- [ ] Tất cả thành viên đều trình bày.
- [ ] README đầy đủ.
- [ ] ZIP đúng cấu trúc yêu cầu.

---

# 37. Mục tiêu bản final

Bản final nên demo được luồng sau:

```text
Alice đăng nhập
↓
Alice xem Feed
↓
Bob đăng bài có ảnh
↓
Alice thấy bài Bob
↓
Alice Like + Comment
↓
Bob nhận Notification
↓
Bob upload Reel
↓
Alice xem Reel
↓
Alice Chat với Bob
↓
Gửi text + ảnh
↓
Bob nhận realtime
↓
Alice dùng AI
↓
Alice và Bob dùng Dating
↓
Mutual Like
↓
Match
```

Nếu luồng này chạy ổn định trên ít nhất hai emulator hoặc thiết bị, project đã đáp ứng tốt các tiêu chí functional, technical quality, UI/UX, complexity và multi-user testing.
