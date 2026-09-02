# LinkUp backend — kết nối database có sẵn để test

Luồng hiện tại: Android → Ktor REST API → Exposed/JDBC → PostgreSQL trên Supabase.
Supabase chỉ host database; backend Ktor phải chạy để Android gọi được API.

## 1. Cấu hình local

Thông tin kết nối được đọc từ `backend/.env`, do người phụ trách backend/database cung cấp.
File này được Git bỏ qua; không cần đổi mật khẩu hay cấu hình lại Supabase để chạy thử.
Giữ nguyên các giá trị đang được team sử dụng.

Contributor chưa có file env có thể tạo bản local từ mẫu, sau đó điền cấu hình được cung cấp:

```powershell
if (-not (Test-Path -LiteralPath backend/.env)) {
    Copy-Item -LiteralPath backend/.env.example -Destination backend/.env
}
```

- `DATABASE_URL`: URI PostgreSQL, định dạng `postgresql://USER:PASSWORD@HOST:PORT/DATABASE`.
  Mỗi dòng env phải là `KEY=VALUE`; backend tự chuyển URI sang JDBC và truyền user/password riêng.
  Ký tự đặc biệt trong password phải percent-encode nếu có, ví dụ `@` → `%40`, `#` → `%23`.
- `JWT_SECRET`, `JWT_ISSUER`, `JWT_AUDIENCE`: giữ các giá trị backend owner cung cấp để chạy auth.
- `PORT`, `LINKUP_SERVER_HOST`: mặc định `8080` và `0.0.0.0`.
- `MINIO_*`: giữ nguyên cho bước triển khai media sau; hiện chưa cần để kiểm tra database.

Backend tìm file env khi chạy từ project root hoặc từ thư mục `backend`.
Khi chạy JAR ở thư mục khác, đặt `LINKUP_ENV_FILE` trỏ tới đường dẫn tuyệt đối của file.
Biến môi trường hệ điều hành có ưu tiên cao hơn file; restart backend sau khi thay đổi cấu hình.
Kết nối từ xa mặc định dùng SSL (`sslmode=require`), không cần thay đổi setting Supabase cho bước này.

## 2. Test và chạy

Chạy từ thư mục gốc project:

```powershell
# Unit test: không cần env, không kết nối Supabase
.\gradlew.bat :backend:test

# Kiểm tra kết nối và tên bảng/cột: chỉ đọc, không sửa database
.\gradlew.bat :backend:dbCheck

# Chạy server Ktor với database có sẵn
.\gradlew.bat :backend:run
```

Gradle dùng daemon JDK 21; backend dùng toolchain JDK 17, có thể được Gradle tải tự động khi có mạng.
Build/unit test không cần secret. `dbCheck`/`run` mới cần cấu hình runtime.

Kết quả mong đợi của `dbCheck`:

```text
Database connection: OK (read-only check).
Schema columns: OK (20 tables).
```

Lệnh chỉ chạy `SELECT 1` và đọc metadata tên bảng/cột, không đọc dữ liệu người dùng.
Không kiểm toán kiểu dữ liệu, index, constraint, RLS hay quyền của từng API.
Exit code 1: lỗi cấu hình/kết nối; exit code 2: thiếu bảng/cột hoặc role không thấy chúng.
Nếu schema không khớp hoặc bị từ chối quyền, báo người phụ trách database; không tự chạy SQL sửa database chung.

Backend khởi động chỉ kiểm tra kết nối, không tự tạo/sửa bảng. Các API nghiệp vụ vẫn có thể ghi dữ liệu:
ví dụ đăng ký tài khoản thử sẽ thêm user. Hãy dùng dữ liệu test đã thống nhất với team.

Server mặc định ở `http://localhost:8080/`. Hiện có `POST /auth/register` và `POST /auth/login`.
Android đang dùng `http://10.0.2.2:8080/` cho emulator. Với điện thoại thật, dùng địa chỉ LAN của máy
chạy Ktor (cùng mạng) hoặc HTTPS của server đã deploy, không dùng URL database làm base URL Android.

## 3. Ranh giới công việc

Phần local chỉ đọc cấu hình được cung cấp, kết nối và chạy thử chức năng.
Đổi mật khẩu, quản lý role/quyền, Data API/RLS, tạo bảng và migration thuộc người phụ trách database.
Không cần tạo lại database hoặc chạy lại schema khi database chung đã có đủ bảng.

Code mapping `profiles.user_id` khớp với bảng đang có; đây chỉ là ánh xạ phía Kotlin, không chạy DDL.
Reels đã có API, upload/media và các tương tác trong source. Cần migration riêng và storage hoạt động
để chạy trên database chung; xem `docs/REELS_IMPLEMENTATION.md`. Không tự chạy migration lúc server khởi động.
