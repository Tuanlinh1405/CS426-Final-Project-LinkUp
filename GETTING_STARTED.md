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
./gradlew testDebugUnitTest assembleDebug   # Android
./gradlew :backend:test                     # backend unit test, không cần database
bash scripts/profile-api-smoke.sh           # end-to-end, cần backend đang chạy
```

APK debug: `app/build/outputs/apk/debug/app-debug.apk`.

## Luồng đã tương tác được

- Splash → Login/Register → Feed.
- Feed → Create Post, Post Detail, Like, Comment.
- Bottom navigation → Reels, Dating, Chats, Profile.
- Reels → Upload Reel.
- Profile → Edit Profile → Settings/Logout (đã nối API thật).
- Search, Notifications, AI chat/history.
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

## Tài liệu

- `docs/plan_simplified.md`: plan gốc.
- `docs/ARCHITECTURE_API_DATABASE.md`: REST API, WebSocket, Room, PostgreSQL, MinIO, folder ownership và hướng dẫn chia việc.

Project dùng một `MainActivity` theo kiến trúc Compose. Chỉ integration owner nên 
sửa `app/src/main/.../app/LinkUpApp.kt`; feature owner làm việc trong module của 
mình.
