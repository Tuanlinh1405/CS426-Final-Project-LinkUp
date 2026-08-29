# Feature modules

Mỗi folder con là một Android library module độc lập:

- `:feature:auth` — splash, login, register.
- `:feature:feed` — feed, create post, post detail.
- `:feature:reels` — reels và upload.
- `:feature:profile` — profile và edit profile.
- `:feature:chat` — conversation list và chat detail.
- `:feature:ai` — AI chat và history.
- `:feature:dating` — dating profile, discover, match.
- `:feature:more` — search, notification, settings.

Feature module chỉ phụ thuộc `:core` và `:data`, không phụ thuộc feature khác. Điều hướng ra ngoài module dùng callback để `:app` xử lý. Quy tắc này giúp các thành viên làm song song mà không tạo dependency vòng hoặc sửa source của nhau.

Ví dụ kiểm tra riêng Feed:

```powershell
.\gradlew.bat :feature:feed:assembleDebug
```
