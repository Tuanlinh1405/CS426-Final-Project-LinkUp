# Feed — UI, REST API, database và Supabase Storage

## Phạm vi đã triển khai

- Feed đọc bài thật từ PostgreSQL, phân trang cursor 15 bài và tự tải trang kế khi cuộn gần cuối.
- Feed trả URL Supabase Storage ký ngắn hạn ngay trong page response để bỏ một lượt gọi backend cho mỗi ảnh; `GET /media/{id}` vẫn là URL chia sẻ/fallback ổn định.
- Android cache ảnh theo `mediaId` (không theo URL ký thay đổi) và preload ảnh của ba bài gần vị trí đang cuộn.
- Đăng bài public có nội dung hoặc tối đa 4 ảnh; app thu ảnh về JPEG, cạnh dài tối đa 2048 px trước khi upload.
- Like/unlike idempotent, chi tiết bài, comment phân trang, gửi/xóa comment của mình, xóa bài của mình và share qua Android share sheet.
- Like/comment của người khác tạo row `notifications` cho chủ bài. Tự tương tác không tạo notification.
- Loading, empty, lỗi/retry, tiến trình upload và giao diện tránh bàn phím/navigation bar đã được xử lý.

## Folder ownership

```text
feature/feed/src/main/.../feed/       UI Feed, Create Post, Post Detail, xử lý ảnh
data/src/main/.../feed/               DTO, Retrofit API, repository
backend/src/main/kotlin/.../posts/    route, SQL repository, validation
backend/src/test/kotlin/.../posts/    kiểm thử API end-to-end với database/storage tạm
```

## REST contract

Tất cả endpoint `/posts` yêu cầu JWT. `GET /media/{id}` là public để ảnh chia sẻ và Coil tải được.

| Method | Endpoint | Chức năng |
|---|---|---|
| GET | `/posts?cursor=&limit=` | Feed mới nhất, mặc định 15 và tối đa 30 bài |
| POST | `/posts` | Multipart `id`, `content`, tối đa 4 part `media` |
| GET / DELETE | `/posts/{id}` | Chi tiết / xóa bài của chính mình |
| PUT / DELETE | `/posts/{id}/reaction` | Like / unlike idempotent |
| GET / POST | `/posts/{id}/comments?cursor=` | Đọc / thêm comment `{id,content}` |
| DELETE | `/posts/{id}/comments/{commentId}` | Xóa comment của chính mình |
| GET | `/media/{id}` | Đọc ảnh bằng metadata `media.storage_key` |

Client tự sinh UUID cho post/comment và giữ nguyên UUID khi retry để tránh tạo bản ghi trùng.

## Database và Storage

Feed sử dụng schema hiện có: `posts`, `media`, `post_media`, `post_reactions`, `comments`, `notifications`, `users`, `profiles`. Không cần chạy migration mới.

Storage dùng cùng cấu hình Supabase S3 với Reels trong `backend/.env.storage`:

```text
REELS_STORAGE=supabase
SUPABASE_STORAGE_BUCKET=linkup-media
SUPABASE_STORAGE_S3_ENDPOINT=...
SUPABASE_STORAGE_S3_REGION=...
SUPABASE_STORAGE_S3_ACCESS_KEY_ID=...
SUPABASE_STORAGE_S3_SECRET_ACCESS_KEY=...
```

Ảnh mới được lưu theo key `posts/{userId}/{postId}/{mediaId}.jpg`. Không đặt access/secret key trong Android app.

## Chạy và kiểm tra

Chạy backend rồi Run app trong Android Studio. Sau khi đăng nhập, Feed sẽ đọc trực tiếp dữ liệu seed hiện có.

```powershell
.\gradlew.bat :backend:test :data:testDebugUnitTest :feature:feed:testDebugUnitTest :app:assembleDebug
```
