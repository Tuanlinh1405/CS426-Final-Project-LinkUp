# Search và comment replies

## Search

- `GET /search?q=&type=all|people|posts|reels&cursor=&limit=` yêu cầu JWT.
- Từ khóa dài 2–100 ký tự; UI debounce 350 ms.
- Tab Top trả tối đa 5 kết quả mỗi loại. Các tab cụ thể trả mặc định 20, tối đa 30 và phân trang bằng cursor offset.
- Người dùng được xếp hạng theo username khớp chính xác/prefix, sau đó follower count.
- Post/Reel tìm theo nội dung hoặc caption và tên/username tác giả; Post riêng tư không bị lộ, Reel đã chọn Not interested bị loại.
- Ảnh và video Supabase dùng signed URL trực tiếp; chạm Post mở đúng Post Detail, chạm Reel đặt Reel đó ở trang đầu.

Search hiện dùng `ILIKE` có escape wildcard, phù hợp dữ liệu demo. Khi dữ liệu lớn nên thêm PostgreSQL full-text search hoặc `pg_trgm`, ranking và search history riêng.

## Comment replies

Migration `004_search_and_comment_replies.sql` thêm `parent_comment_id` nullable cho `comments` và `reel_comments`.
Comment cũ không thay đổi. App hỗ trợ một cấp hiển thị giống Facebook; bấm Reply trên một reply vẫn gửi vào comment gốc.
Server kiểm tra comment cha thuộc đúng Post/Reel và phải là comment gốc. Xóa comment gốc cascade reply; xóa reply không ảnh hưởng comment gốc.

Migration `005_comment_reactions.sql` thêm reaction idempotent cho comment Post và Reel. Mỗi user chỉ có tối đa một tim trên mỗi comment; API trả `likeCount` và `liked`. UI cập nhật optimistic rồi rollback nếu request lỗi.

Tim Reel cũng cập nhật optimistic. Client giữ nguyên signed media URL trong response reaction để Media3 không tạo lại player và buffer video. Gửi/xóa comment cập nhật count tại chỗ, không gọi lại toàn bộ Reel/Post.

Áp dụng migration sau khi database owner review:

```powershell
.\gradlew.bat :backend:searchRepliesMigration --args="--confirm"
.\gradlew.bat :backend:commentReactionsMigration --args="--confirm"
```

Sau đó khởi động lại backend và Run app từ Android Studio.
