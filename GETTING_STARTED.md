# LinkUp — chạy và kiểm tra prototype

Prototype Jetpack Compose dựa trên `Design UI/LinkUp.png`. Phần lớn màn hình vẫn chạy
bằng mock data; **Auth và Profile đã nối thật với backend Ktor + Supabase**.

## Chạy project

Backend phải chạy **trước**, vì app không tự khởi động được nó.

1. Terminal 1 — chạy backend:
   ```bash
   ./gradlew :backend:run
   ```
   Đợi dòng `Responding at http://0.0.0.0:8080`.
2. Mở project bằng Android Studio và sync Gradle.
3. Chọn emulator API 24 trở lên rồi Run cấu hình `app`.
4. Đăng ký một tài khoản mới ở màn Register, hoặc đăng nhập nếu đã có.

App gọi backend qua `http://10.0.2.2:8080/` — đó là địa chỉ máy host nhìn từ emulator.
Với máy thật, đổi `provideBaseUrl()` trong `data/di/NetworkModule.kt` và
`PUBLIC_BASE_URL` trong `.env` sang IP LAN của máy.

Kiểm tra bằng command:

```bash
./gradlew testDebugUnitTest assembleDebug     # Android
./gradlew :backend:test                       # backend unit test, không cần database
bash scripts/profile-api-smoke.sh             # end-to-end, cần backend đang chạy
bash scripts/notifications-api-smoke.sh       # end-to-end, cần backend đang chạy
bash scripts/discovery-api-smoke.sh           # end-to-end, cần backend đang chạy
bash scripts/friends-api-smoke.sh             # end-to-end, cần backend đang chạy
```

APK debug: `app/build/outputs/apk/debug/app-debug.apk`.

## Luồng đã tương tác được

- Splash → Login/Register → Feed.
- Feed → Create Post, Post Detail, Like, Comment.
- Bottom navigation → Reels, Dating, Chats, Profile.
- Reels → Upload Reel.
- Profile → Edit Profile → Settings/Logout (đã nối API thật).
- Search người dùng (đã nối API thật), AI chat/history.
- Notifications (đã nối API thật).
- Chat list → Chat detail → gửi tin nhắn mock.
- Dating profile → Discover → Match → Chat/Matches.

## Multi-module

```text
LinkUp/
├── app/            # APK, MainActivity và composition root
├── core/           # navigation, design system, theme
├── data/           # model và repository contract
└── feature/
    ├── auth/
    ├── feed/
    ├── reels/
    ├── profile/
    ├── chat/
    ├── ai/
    ├── dating/
    └── more/
```

Mỗi folder trên là Gradle module riêng và có `build.gradle.kts`. Có thể test một 
module bằng ví dụ `./gradlew :data:testDebugUnitTest` hoặc build riêng 
`./gradlew :feature:feed:assembleDebug`.

## Profile (đã hoàn thiện)

Vào từ tab **Profile** ở bottom navigation, hoặc từ avatar trên Feed.

- Xem profile: ảnh bìa, avatar, tên, bio, địa điểm, link, ngày tham gia, số
  posts/followers/following.
- **Edit profile**: đổi ảnh đại diện và ảnh bìa (Android Photo Picker, không cần xin
  quyền), sửa họ tên, username, bio, email, số điện thoại, địa điểm, website, ngày
  sinh, giới tính.
- Ảnh được thu nhỏ và xoay đúng chiều EXIF ngay trên máy trước khi upload.
- Validate tại chỗ khi gõ, cộng thêm validate ở server; lỗi hiện ngay dưới đúng ô nhập.
- Xem profile người khác: email và số điện thoại được ẩn, có nút Follow/Following.

Backend tương ứng: xem bảng endpoint trong `backend/README.md`.

## Notifications (đã hoàn thiện)

Vào từ icon chuông trên thanh top bar của Feed. Chuông có badge đếm số chưa đọc.

- Danh sách nhóm theo **New / This week / Earlier**, lọc **All / Unread**.
- Mỗi dòng có avatar kèm badge loại thông báo, câu mô tả in đậm tên người thực hiện,
  thời gian tương đối ("5m", "3h", "2d"), chấm tím nếu chưa đọc.
- Menu `⋯` trên từng dòng: đánh dấu đã đọc/chưa đọc, xoá. Menu ở header: đánh dấu tất
  cả đã đọc, xoá tất cả (có hộp thoại xác nhận).
- Cuộn tới cuối tự tải trang tiếp theo (cursor pagination).
- Mọi thao tác cập nhật giao diện ngay lập tức và tự hoàn tác nếu server báo lỗi.
- Bấm vào thông báo FOLLOW sẽ mở trang cá nhân của người đó.

Thông báo được sinh ra thật, không phải mock: **follow một người sẽ tạo thông báo cho
họ**, bỏ follow thì rút lại, và tài khoản mới đăng ký nhận một thông báo chào mừng.

## Tìm người & danh sách theo dõi (mới)

- **Search** (icon ⌕ trên Feed): gõ tên hoặc username, kết quả thật từ database, có
  nút Follow ngay trong danh sách, bấm vào một người để mở trang cá nhân của họ.
- **Followers / Following**: bấm vào số Followers hoặc Following trên trang cá nhân
  (của mình hoặc của người khác) để mở danh sách, có thể follow/unfollow ngay tại đó.
- Cả hai đều cuộn tới đâu tải tới đó.

## Kết bạn (mới)

Vào từ icon ☺ trên top bar của Feed (có badge số lời mời đang chờ), hoặc bấm vào số
**Friends** trên trang cá nhân.

- Ba tab: **Friends**, **Requests** (kèm số), **Suggestions** ("người bạn có thể biết",
  xếp theo số bạn chung).
- Trên trang cá nhân người khác, nút đổi theo quan hệ: **Add friend** → **Requested**
  → **Confirm / Delete** → **Friends ▾** (menu có Unfriend).
- Hiện **số bạn chung** và nhãn **Follows you**.
- Kết bạn và follow là hai thứ độc lập: có thể follow mà không cần kết bạn.
- Gửi lời mời sẽ tạo thông báo cho đối phương; huỷ/từ chối/chấp nhận thì thông báo đó
  cũng được rút lại, không để lại thông báo "mồ côi".
- Nếu hai người cùng gửi lời mời cho nhau thì tự động thành bạn, không tạo hai dòng.

## Đăng nhập & đăng xuất

- App **tự đăng nhập lại** nếu token còn hạn: mở app là vào thẳng Feed. Token hết hạn
  sẽ tự bị xoá và quay về màn Login.
- **Đăng xuất** xoá token thật và xoá toàn bộ dữ liệu đang cache (profile, thông báo,
  search), nên tài khoản sau không nhìn thấy dữ liệu của tài khoản trước.

## Tài liệu

- `docs/plan_simplified.md`: plan gốc.
- `docs/ARCHITECTURE_API_DATABASE.md`: REST API, WebSocket, Room, PostgreSQL, MinIO, folder ownership và hướng dẫn chia việc.

Project dùng một `MainActivity` theo kiến trúc Compose. Chỉ integration owner nên 
sửa `app/src/main/.../app/LinkUpApp.kt`; feature owner làm việc trong module của 
mình.
