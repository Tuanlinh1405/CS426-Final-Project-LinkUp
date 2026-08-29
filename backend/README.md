# LinkUp backend skeleton

Folder này dành cho Ktor backend. Các package đã được chia theo trách nhiệm để backend team làm song song. Hợp đồng request/response, WebSocket, PostgreSQL, MinIO và quy tắc test nằm tại `docs/ARCHITECTURE_API_DATABASE.md`.

```text
src/main/kotlin/com/linkup/
├── config/       # env, typed configuration
├── database/     # Exposed/JDBI tables, migrations, transaction
├── repository/   # PostgreSQL access
├── routes/       # Ktor REST routes + DTO validation
├── service/      # business rules
├── storage/      # MinIO adapter
└── websocket/    # sessions and events
```

Backend chưa được thêm vào Gradle build hiện tại; mục đích của folder này là giữ sẵn ranh giới ownership, không giả lập rằng server đã hoàn thành.
