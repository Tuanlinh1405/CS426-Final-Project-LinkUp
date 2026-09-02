# Reels — UI, API, database và đề xuất theo tương tác

## Phạm vi đã triển khai trong source

- Reels feed vuốt dọc, autoplay video đang hiển thị, chạm để pause/play, mute/unmute, loading/error/retry.
- Player dừng khi mở comment, sang trang khác hoặc app vào background; giải phóng player khi rời video.
- Hiển thị tác giả, avatar (hoặc initials), caption, số like/comment thật. Chạm tác giả để lọc Reels của họ.
- My reels để xem/xóa video của tài khoản hiện tại; có xác nhận trước khi xóa.
- Like/unlike lưu DB; nút bị khóa trong khi gửi để tránh thao tác chồng nhau.
- Comment có phân trang, gửi, retry giữ cùng request ID, xóa comment của mình; ô nhập có xử lý IME/navigation bar.
- Share mở Android share sheet với URL video công khai. Không giả lập bộ đếm share vì không biết người dùng có gửi thật hay không.
- Not interested lưu theo user/reel, loại khỏi feed và giảm affinity với tác giả. API có DELETE hidden để bỏ ẩn; UI quản lý toàn bộ mục đã ẩn chưa triển khai.
- Upload bằng Photo Picker: copy có giới hạn vào cache, preview, chọn cover từ bốn frame, caption, tiến trình, cancel/retry.
- Server kiểm tra MP4/H.264, duration hợp lệ >0, kích thước <=4096 px mỗi cạnh, file <=50 MiB; JPEG cover <=1 MiB/2048 px.
- Reel không có giới hạn thời lượng cứng; giới hạn thực tế là 50 MiB nên video dài cần bitrate thấp hơn.
- Token auth được giữ trong bộ nhớ sau login/register và dùng chung cho các API Reels; logout xóa token.
  Khởi động lại app cần login lại. Các feature khác vẫn còn phần mock của team.

Không yêu cầu chọn tag/chủ đề. Chưa có mô hình phân tích nội dung video, embedding hoặc recommendation theo chủ đề.

## Ranh giới folder để giảm conflict

```text
feature/reels/src/main/java/com/example/linkup/feature/reels/
  feed/ReelsScreen.kt
  player/ReelPlayer.kt, PlaybackTracker.kt
  upload/UploadReelScreen.kt, SelectedVideo.kt
  comments/ReelCommentsSheet.kt
data/src/main/java/com/example/linkup/data/reels/
  ReelModels.kt, ReelApi.kt, ReelRepository.kt
backend/src/main/kotlin/com/linkup/reels/
  ReelModels.kt, ReelRepository.kt, ReelRanker.kt, ReelFeed.kt
  ReelRoutes.kt, ReelMedia.kt, ReelStorage.kt
backend/src/main/resources/db/migrations/001_reels_interactions.sql
```

Phần nối chung được giới hạn ở `LinkUpApp`, `AuthRepository`/`ApiClient`, config media và đăng ký routes trong backend.
Không thêm chức năng Reels vào repository mock chung, không tạo phụ thuộc chéo giữa các feature.

## Điều kiện để chạy trên Supabase của team

### 1. Database owner áp dụng migration Reels

File `backend/src/main/resources/db/migrations/001_reels_interactions.sql` tạo thêm:

| Bảng | Dữ liệu |
| --- | --- |
| reel_assets | object key video/cover, backend storage, file size và duration chính xác theo ms |
| reel_reactions | một like cho mỗi user/reel |
| reel_comments | comment với UUID để chống gửi trùng |
| reel_watch_events | các lần xem và cập nhật thời gian xem tăng dần |
| reel_hidden | những Reel user chọn không quan tâm |

Không đổi schema các bảng feature khác. `reels` vẫn lưu metadata cũ; video bytes không nằm trong PostgreSQL.
Migration bật RLS/thu hồi quyền Data API của `anon`/`authenticated` CHỈ trên năm bảng mới. Ktor dùng kết nối server.
Người phụ trách DB kiểm tra runtime role có quyền cần thiết; không tạo policy public-all hay thay role/mật khẩu trong bước làm Reels.
File là bootstrap cho bảng chưa có, không sửa một bảng cùng tên nhưng schema khác. Cần đối chiếu nếu team đã tạo bảng riêng.

**Không tự chạy migration khi khởi động app/server.** Owner review rồi chạy SQL trong đúng project Supabase.
Sau migration gốc, chạy thêm `003_reels_unbounded_duration.sql` để bỏ giới hạn 60 giây của asset/watch event.

```powershell
.\gradlew.bat :backend:reelsDurationMigration --args="--confirm"
```
Kiểm tra chỉ đọc:

```powershell
.\gradlew.bat :backend:dbCheck --args="--reels"
```

Lệnh kiểm tra 20 bảng cũ + 5 bảng Reels, không tạo/sửa bảng và không đọc dữ liệu người dùng.

### 2. Có nơi lưu video

Storage hỗ trợ `supabase`, `minio` hoặc `local`. Với Supabase, copy `backend/.env.storage.example`
thành `backend/.env.storage`, giữ bucket private và điền S3 endpoint, region, access key và secret key từ Dashboard.
Các S3 key chỉ nằm ở backend vì chúng có quyền rộng và bỏ qua Storage RLS.

Mặc định cũ là `REELS_STORAGE=minio`, dùng các biến `MINIO_*` đã có trong `backend/.env`.
MinIO phải đang chạy và bucket do owner tạo sẵn; backend không tự tạo bucket.
Video được backend proxy qua HTTP Range, vì vậy điện thoại không cần truy cập trực tiếp `localhost:9000` của MinIO.

Để test một máy khi chưa có MinIO, có thể chủ động chọn trong file env local:

```dotenv
REELS_STORAGE=local
```

Chế độ này lưu video vào `.reels-media` cạnh file env (mặc định `backend/.reels-media`), Git bỏ qua.
PostgreSQL vẫn là Supabase, không thay database bằng mock. Không tự chuyển sang local khi MinIO lỗi.
Local storage chỉ phù hợp một server development; mất folder hoặc dùng server khác sẽ mất khả năng đọc các video local đó.
Mỗi bản ghi asset giữ loại storage đã sử dụng, nên đổi mode upload không đổi kiểu lưu của video cũ.

### 3. Chạy backend và Android

```powershell
.\gradlew.bat :backend:run
.\gradlew.bat :app:assembleDebug
```

Emulator mặc định gọi `http://10.0.2.2:8080/`. Điện thoại thật cần backend LAN/HTTPS có thể truy cập:

```powershell
# Thay địa chỉ ví dụ bằng IP LAN thật của máy chạy backend.
.\gradlew.bat :app:assembleDebug "-Plinkup.apiBaseUrl=http://192.168.1.10:8080/"
```

`linkup.apiBaseUrl` được dùng chung cho auth/Reels/media/share. Dấu `/` cuối URL là bắt buộc.
HTTP chỉ được bật trong debug manifest. Release cần base URL HTTPS của server đã deploy.
Đăng nhập bằng tài khoản thật trước khi upload/like/comment; không dùng `FakeLinkUpRepository.currentUser()` cho Reels.

## REST API thực tế

| Method | Endpoint | Hành vi |
| --- | --- | --- |
| GET | /reels?cursor=&authorId= | feed có snapshot; authorId để lọc tác giả/My reels |
| POST | /reels | multipart id, caption, video, thumbnail tùy chọn; trả metadata đã đăng |
| GET / DELETE | /reels/{id} | chi tiết / xóa Reel của mình |
| PUT / DELETE | /reels/{id}/reaction | like / unlike idempotent |
| GET / POST | /reels/{id}/comments | đọc phân trang / thêm comment {id,content} |
| DELETE | /reels/{id}/comments/{commentId} | chỉ chủ comment được xóa |
| PUT / DELETE | /reels/{id}/hidden | ẩn / bỏ ẩn |
| POST | /reels/{id}/events | {id,watchedMs,reason} |
| GET / HEAD | /reels/{id}/video | video công khai, hỗ trợ một byte range và 416 |
| GET | /reels/{id}/thumbnail | JPEG cover công khai |

Các API trừ media cần bearer JWT. User ID lấy từ JWT, không tin user ID do client gửi trong body.
Upload dùng một request multipart riêng cho Reels ở đợt này; `/media/upload` dùng chung cho Post/Chat vẫn là contract tương lai.
Upload retry dùng cùng id để không tạo hai Reel. Comment retry cũng dùng cùng id.
Delete Reel xóa row và cascade tương tác/asset metadata, sau đó xóa object trong storage.
Nếu storage lỗi đúng lúc xóa, có thể còn object không được tham chiếu; cần job dọn orphan khi đưa lên production.

## Recommendation đợt 1: chỉ tương tác

Chọn tối đa 500 Reel gần đây chưa bị user ẩn. Tính điểm phía server:

```text
score = 0.35 * creatorAffinity
      + 0.25 * smoothedCompletionQuality
      + 0.20 * freshness
      + 0.20 * isFollowingCreator
      - 0.45 * alreadyWatched
```

- Affinity với tác giả: mỗi Reel đã like +3; có comment +1 (không tăng theo số comment spam);
  xem >=80% +1; lướt qua dưới 2s -1 nếu không có lần xem tích cực hơn; mỗi Reel chọn Not interested -3.
  Tổng /12 rồi giới hạn [-1,1].
- Quality = (completed + 2)/(validViews + 4); tối đa một đóng góp mỗi viewer/reel/ngày.
- Freshness = exp(-ageDays/7). Các trọng số là heuristic để demo và cần đánh giá bằng dữ liệu thật.
- Đã xem bị hạ điểm, không cấm vĩnh viễn. Không quan tâm thì bị loại khỏi feed của user đó.
- Cứ năm vị trí dành một vị trí khám phá video mới chưa xem; tránh ba Reel liên tiếp cùng tác giả nếu có lựa chọn khác.
- User mới nhận video mới/chất lượng chung; sở thích tác giả được cập nhật qua tương tác, không cần chọn chủ đề.
- Đây chưa phải collaborative filtering giữa người dùng hay mô hình học máy; mức cá nhân hóa hiện tại chủ yếu theo tác giả.

Player gửi START khi thực sự phát, heartbeat mỗi khoảng 5s và bản cập nhật khi pause/background/swipe.
Client loại thời gian buffering/background; server giữ watchedMs tăng dần, giới hạn theo elapsed time và tối đa ba vòng video.
UUID phiên xem ngăn retry bị tính thành view mới, và server ràng buộc phiên với user/reel.
Hàng đợi client có giới hạn/retry ngắn, chưa persist offline: process chết hoặc mất mạng lâu có thể mất một phần event.

Feed snapshot giữ thứ tự 30 phút, ràng buộc với user/author filter và giới hạn 500 snapshot trong bộ nhớ server.
Tương tác mới ảnh hưởng lần refresh tiếp theo, không sắp xếp lại các trang đang lướt.
Server restart/cursor hết hạn trả 410 và UI cho Refresh. Chưa hỗ trợ nhiều backend instance cùng chia sẻ snapshot.

## Kiểm thử

```powershell
.\gradlew.bat :backend:test :data:testDebugUnitTest :feature:reels:testDebugUnitTest :app:assembleDebug
```

- Backend test dùng H2 PostgreSQL mode và video H.264 được tạo trong test, không chạm Supabase.
- Kiểm tra upload, stream Range/HEAD, request sai, auth, ownership, cascade delete, idempotency, comment/feed cursor,
  watch session, hide/unhide và các nguyên tắc xếp hạng.
- Data test dùng HTTP mock; auth test không còn gửi đăng ký thật hoặc in token trong unit test.
- Player tracker test kiểm tra pause/background/gap và giới hạn vòng lặp.
- H2 không thay thế integration test PostgreSQL thật, RLS hoặc MinIO thật. Chỉ chạy những test đó khi owner cho phép và storage sẵn sàng.

Checklist trên điện thoại (chưa thể xác nhận nếu không có thiết bị/emulator):

1. Login Alice; upload clip MP4/H.264 ngắn, chọn cover và caption; nhìn thấy Reel trong My reels sau khi quay lại.
2. Login Bob; vuốt nhiều Reel, chỉ nghe một video; pause/mute; home/background rồi quay lại không có audio chạy ngầm.
3. Like/unlike rồi refresh/restart/login lại: trạng thái và count đúng.
4. Mở comment, bật bàn phím, nhập dài/gửi/xóa: composer và comment mới không bị bàn phím/home bar che.
5. Comment pagination không trùng, share mở share sheet, chạm tác giả lọc đúng người.
6. Hide rồi refresh: Reel không còn trong feed; Bob không thể xóa Reel Alice qua API.
7. Alice xóa Reel: biến mất khỏi My reels/feed và URL media trả 404.
8. Tắt mạng giữa upload/like/comment: UI báo lỗi, retry không tạo bản ghi trùng; giới hạn duration/file size có thông báo.
9. Thử navigation bằng gesture và ba nút trên màn hình nhỏ; upload caption phải cuộn được khi bàn phím mở.
