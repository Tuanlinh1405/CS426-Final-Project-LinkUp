# LinkUp — chạy và kiểm tra prototype

Prototype Jetpack Compose dựa trên `Design UI/LinkUp.png`. Đăng ký/đăng nhập hiện gọi
backend Ktor thật; các màn social còn dùng mock data. Backend kết nối PostgreSQL
trên Supabase. Xem `backend/README.md` để dùng `backend/.env` kết nối database có sẵn và chạy server.

## Chạy project

1. Mở project bằng Android Studio và sync Gradle.
2. Chọn emulator API 24 trở lên rồi Run cấu hình `app`.
3. Setup và chạy backend theo `backend/README.md`, sau đó đăng ký tài khoản rồi đăng nhập.
   Tài khoản demo điền sẵn không tự được tạo trong database.

Kiểm tra bằng command:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

APK debug: `app/build/outputs/apk/debug/app-debug.apk`.

## Luồng đã tương tác được

- Splash → Login/Register → Feed.
- Feed → Create Post, Post Detail, Like, Comment.
- Bottom navigation → Reels, Dating, Chats, Profile.
- Reels → Upload Reel.
- Profile → Edit Profile → Settings/Logout.
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

## Tài liệu

- `docs/plan_simplified.md`: plan gốc.
- `backend/README.md`: hướng dẫn setup Supabase, env, kiểm tra kết nối và chạy backend.
- `docs/REELS_IMPLEMENTATION.md`: tính năng Reels, migration riêng, media storage, recommendation và checklist test.
- `docs/ARCHITECTURE_API_DATABASE.md`: REST API, WebSocket, Room, PostgreSQL, MinIO, folder ownership và hướng dẫn chia việc.

Project dùng một `MainActivity` theo kiến trúc Compose. Chỉ integration owner nên 
sửa `app/src/main/.../app/LinkUpApp.kt`; feature owner làm việc trong module của 
mình.
