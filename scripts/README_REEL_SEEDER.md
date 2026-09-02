# Seed dữ liệu Reels từ video được phép tái sử dụng

Script `seed_reels_from_youtube.py` dùng YouTube Data API để tìm video có metadata
Creative Commons theo các chủ đề football, gaming, music, technology và travel/food.
Video được encode lại thành MP4/H.264 540x960, giữ toàn bộ thời lượng và kiểm tra dưới 50 MiB.
Seeder mặc định chọn nguồn từ 3 phút trở lên và tự giảm bitrate cho video dài.
Để bộ test không mất quá lâu khi encode, mặc định chỉ seed 3–10 phút; truyền
`--max-duration 0` nếu muốn seeder chấp nhận mọi thời lượng. App/backend không có trần này.

Script **không upload thẳng vào Supabase Storage**. Nó gọi `POST /reels` của backend để backend:

1. kiểm tra video/thumbnail;
2. upload object vào bucket Supabase đang cấu hình;
3. ghi `reels` và `reel_assets` vào PostgreSQL trong cùng luồng nghiệp vụ.

## Chuẩn bị một lần

1. Tạo YouTube Data API v3 key trong Google Cloud và chỉ cấp quyền cần thiết cho key.
   Có thể lưu key vào file local `scripts/.env.reel-seeder` theo dạng
   `YOUTUBE_API_KEY=...`; file này đã được Git bỏ qua.
   Nếu terminal hiện tại chưa nhận FFmpeg vừa cài, có thể thêm `FFMPEG_DIR=...\\bin`
   vào cùng file; terminal mới thường không cần dòng này.
2. Cài dependency:

   ```powershell
   python -m pip install -r scripts/requirements-reel-seeder.txt
   winget install Gyan.FFmpeg
   ```

3. Mở terminal mới rồi kiểm tra:

   ```powershell
   python scripts/seed_reels_from_youtube.py --check
   ```

4. Đảm bảo `backend/.env.storage` có `REELS_STORAGE=supabase`, bucket đã được tạo,
   migration Reels đã chạy và backend đang chạy ở cổng 8080.

## Chuẩn bị 16 video

Không ghi API key vào source. Chỉ đặt cho terminal hiện tại:

```powershell
$env:YOUTUBE_API_KEY = "YOUR_YOUTUBE_DATA_API_KEY"
python scripts/seed_reels_from_youtube.py --count 16 --acknowledge-rights
```

Kết quả nằm trong `.reel-seed-long/prepared`; nguồn, tác giả, giấy phép và trạng thái nằm trong
`.reel-seed-long/manifest.json`. Caption dùng để upload còn được xuất riêng ở
`captions.csv` và `captions.txt`. Mở manifest, kiểm tra từng video và đổi `reviewed` thành `true`
cho các video được phép dùng. Metadata Creative Commons trên YouTube không đảm bảo người
upload thật sự sở hữu mọi hình ảnh hoặc âm thanh trong video, đặc biệt với âm nhạc.

## Upload vào Supabase Storage và database

Khởi động backend, sau đó chạy:

```powershell
.\gradlew.bat :backend:run
```

Ở terminal khác:

```powershell
python scripts/seed_reels_from_youtube.py --upload-only --acknowledge-rights
```

Script sẽ hỏi tài khoản LinkUp; mọi Reel được tạo dưới tài khoản đó. Có thể dùng
`LINKUP_TOKEN`, `LINKUP_EMAIL_OR_USERNAME` và `LINKUP_PASSWORD` trong biến môi trường,
nhưng không đưa chúng vào Git. Để seed nhanh khi đã tự kiểm tra toàn bộ nguồn:

```powershell
python scripts/seed_reels_from_youtube.py --upload-only --upload-unreviewed --acknowledge-rights
```

Nếu backend chạy trên địa chỉ khác, thêm `--backend-url`, ví dụ:

```powershell
python scripts/seed_reels_from_youtube.py --upload-only --backend-url http://192.168.1.10:8080 --acknowledge-rights
```

Script dùng cùng `reel_id` khi retry nên request đã upload thành công sẽ không tạo Reel trùng.
