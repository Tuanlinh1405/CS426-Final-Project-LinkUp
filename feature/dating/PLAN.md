# Dating Feature Plan

## Mục tiêu

Hoàn thiện tính năng Dating theo từng giai đoạn. Ưu tiên MVP có thể chạy được một luồng hoàn chỉnh:

```text
Tạo dating profile
    -> xem người phù hợp
    -> Like/Pass
    -> hai người Like nhau
    -> tạo Match
    -> mở Chat
```

Prototype UI hiện có trong `DatingScreens.kt`. Kế hoạch này bổ sung dần model, state, repository, backend API và database logic.

## Phạm vi hiện tại

Đã có:

- Dating Profile screen
- Discover screen
- Nút Like và Pass ở mức UI demo
- Match screen
- Matches screen
- Navigation từ Dating Profile đến Discover, Match và Chat
- Các bảng database: `dating_profiles`, `dating_photos`, `dating_swipes`, `dating_matches`
- API contract được mô tả trong `docs/ARCHITECTURE_API_DATABASE.md`

Chưa hoàn thiện:

- Dữ liệu dating đang hard-code hoặc dùng state cục bộ
- Chưa có model dating riêng
- Chưa có Dating ViewModel
- Chưa lưu profile, Like, Pass và Match thật
- Chưa có Dating API route/service/repository hoàn chỉnh
- Chưa kết nối Android với backend

---

# MVP - Làm trước

## MVP-1. Chốt nghiệp vụ tối thiểu

Các khái niệm cần thống nhất:

- `LookingFor`: `RELATIONSHIP`, `FRIENDSHIP`
- `SwipeDecision`: `LIKE`, `PASS`
- Một user chỉ có một dating profile
- User không thể swipe chính mình
- Candidate đã xử lý không xuất hiện lại trong phiên Discover hiện tại
- Khi hết candidate, hiển thị Empty State và cho phép xem lại candidate đã PASS
- Không reset người đã LIKE, MATCH hoặc BLOCK khi xem lại candidate đã PASS
- Match chỉ được tạo khi hai user cùng Like nhau
- Khi mutual Like xảy ra, cả hai user nhận được thông báo Match
- Một cặp user chỉ có một Match

Chưa cần làm trong MVP:

- AI recommendation
- GPS chính xác
- Swipe gesture bằng kéo thẻ
- Nhiều ảnh nâng cao
- WebSocket realtime

## MVP-2. Model dữ liệu Android

Tạo các model dating riêng, không dùng chuỗi hard-code trong UI:

```kotlin
data class DatingProfile(
    val userId: String,
    val bio: String,
    val interests: List<String>,
    val lookingFor: LookingFor,
    val preferredGender: String?,
    val minAge: Int?,
    val maxAge: Int?
)

data class DatingCandidate(
    val user: User,
    val age: Int,
    val bio: String,
    val interests: List<String>,
    val distanceKm: Double?,
    val photoUrl: String?,
    val likedYou: Boolean = false
)

data class DatingMatch(
    val id: String,
    val user: User,
    val createdAt: String
)

enum class SwipeDecision {
    LIKE,
    PASS
}
```

Có thể đặt model trong `data` nếu được dùng chung giữa nhiều module, hoặc trong `feature/dating` nếu chỉ phục vụ Dating.

## MVP-3. Dating repository

Tạo contract cho repository:

```kotlin
interface DatingRepository {
    suspend fun getProfile(): DatingProfile?
    suspend fun updateProfile(profile: DatingProfile): DatingProfile
    suspend fun getDiscoverCandidates(): List<DatingCandidate>
    suspend fun swipe(targetUserId: String, decision: SwipeDecision): SwipeResult
    suspend fun getMatches(): List<DatingMatch>
}
```

Tạo `FakeDatingRepository` trước để hoàn thiện UI và logic mà chưa phụ thuộc backend.

Fake repository phải xử lý được:

- Danh sách candidate
- Lọc candidate đã Like và candidate đã Pass trong phiên hiện tại
- Lưu Like/Pass trong memory
- Kiểm tra Like hai chiều
- Tạo Match khi mutual Like
- Trả về candidate tiếp theo
- Reset danh sách candidate đã Pass khi user chọn xem lại

## MVP-4. Dating ViewModel và UI state

Tạo:

```text
feature/dating/
├── DatingScreens.kt
├── DatingViewModel.kt
├── DatingContract.kt
└── DatingRepository.kt
```

State tối thiểu:

```kotlin
data class DatingUiState(
    val profile: DatingProfile? = null,
    val candidates: List<DatingCandidate> = emptyList(),
    val matches: List<DatingMatch> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSwiping: Boolean = false,
    val matchedUser: DatingMatch? = null,
    val error: String? = null
)
```

Screen chỉ nhận state và callback. Không tạo danh sách candidate trực tiếp trong `DatingScreens.kt`.

## MVP-5. Dating Profile

Hoàn thiện các trường:

- Bio
- Interests
- Looking for
- Preferred gender
- Minimum age
- Maximum age
- Nút Save

Luồng:

```text
Mở Dating Profile
    -> load profile
    -> chỉnh sửa
    -> Save
    -> repository lưu dữ liệu
    -> hiển thị trạng thái thành công hoặc lỗi
```

Trong MVP, có thể dùng các lựa chọn cố định cho interests và preferred gender.

## MVP-6. Discover và ưu tiên candidate

Backend hoặc FakeDatingRepository trả candidate theo thứ tự:

1. Người đã Like user hiện tại (`likedYou = true`)
2. Chưa từng Like/Pass
3. Đúng preferred gender
4. Đúng khoảng tuổi
5. Có nhiều interests chung
6. Có profile đầy đủ

Scoring MVP đơn giản:

```text
+30 cho mỗi interest chung
+20 nếu đúng độ tuổi
+20 nếu đúng preferred gender
+10 nếu có bio
+10 nếu có ảnh
+40 nếu candidate đã Like mình
```

Chưa cần machine learning. Mục tiêu là thứ tự ổn định, dễ giải thích và dễ test.

`likedYou` không cần hiển thị trực tiếp cho user trong MVP. Backend chỉ dùng thông tin này để ưu tiên candidate đã Like mình, giúp tăng khả năng hai user sớm Like nhau.

### Hai lớp trải nghiệm khi xem candidate

#### Lớp 1: Summary Card

Đây là giao diện mặc định khi user đang lướt liên tục:

- Hiển thị một ảnh chính hoặc placeholder
- Tên và tuổi
- Khoảng cách nếu có
- Một dòng bio ngắn
- Nút Like và Pass để quyết định nhanh

Mục tiêu là giúp user xem và quyết định trong khoảng 1-2 giây.

#### Lớp 2: Detailed Profile

Khi user muốn tìm hiểu thêm, có thể nhấn vào card hoặc một nút mở chi tiết:

- Mở profile dating của đúng candidate đang hiển thị
- Hiển thị bio đầy đủ
- Hiển thị toàn bộ interests
- Hiển thị các ảnh khác khi dữ liệu đã hỗ trợ nhiều ảnh
- Hiển thị tuổi, khoảng cách và thông tin dating liên quan
- Có Like và Pass ở cuối màn hình để user không phải quay lại Summary Card

MVP có thể triển khai bằng màn hình riêng `CandidateProfileScreen`. Full có thể nâng cấp thành bottom sheet/toàn màn hình có animation và nhiều ảnh.

## MVP-7. Like, Pass và Match

### Pass

```text
User bấm Pass
    -> lưu swipe PASS
    -> ẩn candidate trong phiên Discover hiện tại
    -> hiển thị candidate tiếp theo
```

### Hết candidate

```text
Không còn candidate chưa xử lý
    -> hiển thị Empty State "No more profiles"
    -> user chọn "Review passed profiles"
    -> reset các candidate đã PASS
    -> giữ nguyên LIKE, MATCH và BLOCK
```

### Like không tạo Match

```text
User bấm Like
    -> lưu swipe LIKE
    -> nếu chưa có Like ngược lại
    -> chuyển candidate tiếp theo
```

### Like tạo Match

```text
User bấm Like
    -> lưu swipe LIKE
    -> kiểm tra target đã Like user hiện tại chưa
    -> tạo Match
    -> tạo notification cho cả hai user
    -> mở Match screen
```

Không mở Match screen chỉ vì user bấm Like. Chỉ mở khi kết quả trả về `isMatch = true`.

### Mở Detailed Profile

```text
User nhấn Summary Card
    -> mở CandidateProfileScreen của candidate hiện tại
    -> user xem thông tin chi tiết
    -> có thể Back về Summary Card
    -> hoặc Like/Pass trực tiếp tại Detailed Profile
```

## MVP-8. Backend API tối thiểu

Triển khai các endpoint:

```text
GET  /dating/profile
PUT  /dating/profile
GET  /dating/discover
POST /dating/swipes
GET  /dating/matches
```

Tổ chức backend:

```text
backend/src/main/kotlin/com/linkup/
├── routes/DatingRoutes.kt
├── service/DatingService.kt
├── repository/DatingRepository.kt
└── model/dating/DatingDtos.kt
```

### `GET /dating/profile`

Trả dating profile của user hiện tại.

### `PUT /dating/profile`

Tạo mới hoặc cập nhật profile. Request tối thiểu:

```json
{
  "bio": "...",
  "interests": ["Travel", "Coffee"],
  "lookingFor": "RELATIONSHIP",
  "preferredGender": "ANY",
  "minAge": 20,
  "maxAge": 30
}
```

### `GET /dating/discover`

Backend cần:

- Loại user hiện tại
- Loại candidate đã swipe
- Lọc theo profile preference
- Tính compatibility score
- Sắp xếp giảm dần theo score
- Trả danh sách candidate

### `POST /dating/swipes`

Request:

```json
{
  "targetUserId": "uuid",
  "decision": "LIKE"
}
```

Xử lý trong transaction:

```text
Validate target
    -> lưu swipe
    -> kiểm tra reverse LIKE
    -> nếu mutual LIKE: tạo match và notification cho cả hai user
    -> trả isMatch
```

Response nên có:

```json
{
  "decision": "LIKE",
  "isMatch": true,
  "match": {
    "id": "uuid",
    "userId": "uuid"
  }
}
```

## MVP-9. Matches và Chat

- Matches screen lấy dữ liệu từ `GET /dating/matches`
- Match screen hiển thị đúng user vừa match
- `Chat Now` mở hoặc tạo conversation với user đó
- Không dùng danh sách match hard-code
- Có thể dùng chat repository hiện tại trong MVP nếu conversation đã hỗ trợ gửi tin nhắn
- Khi Match được tạo, lưu notification cho cả hai user
- Khi mở app hoặc Notifications screen, tải notification từ backend/database để user offline vẫn thấy Match

### Notification MVP

MVP chỉ cần notification bền vững trong database, chưa cần WebSocket:

```text
Mutual Like
    -> tạo Match
    -> tạo notification cho user A và user B
    -> user mở app/Notifications
    -> GET /notifications
    -> hiển thị "You have a new Match"
```

User vừa bấm Like có thể mở Match screen ngay dựa trên `isMatch = true`. User còn lại biết về Match thông qua notification khi mở app.

## MVP-10. Test và Definition of Done

### Unit test

- Profile được tạo và cập nhật
- Looking for được lưu đúng
- Candidate đã xử lý không xuất hiện lại trong phiên hiện tại
- Khi hết candidate, Empty State được hiển thị
- Reset candidate đã PASS không reset LIKE, MATCH hoặc BLOCK
- Pass không tạo Match
- Like một chiều không tạo Match
- Like hai chiều tạo Match
- Không tạo Match trùng
- Không thể swipe chính mình
- Candidate được sắp xếp đúng score

### UI test

- Mở Dating Profile
- Chọn Looking for
- Chọn interests
- Save profile
- Mở Discover
- Nhấn Summary Card mở đúng Candidate Profile
- Like/Pass hoạt động từ Candidate Profile
- Pass chuyển candidate
- Like không mutual chuyển candidate
- Like mutual mở Match
- Chat Now mở đúng cuộc trò chuyện

### MVP hoàn thành khi

- Có thể tạo và lưu dating profile
- Có thể xem candidate động
- Summary Card và Detailed Profile hiển thị đúng cùng một candidate
- Có thể mở profile candidate từ Summary Card
- Có thể Like/Pass
- Candidate đã xử lý không xuất hiện lại trong phiên Discover hiện tại
- Hết candidate hiển thị Empty State
- Có thể xem lại candidate đã PASS
- Mutual Like tạo đúng một Match
- Candidate đã Like mình được ưu tiên trong Discover
- Cả hai user nhận được notification khi Match được tạo
- User offline vẫn thấy Match sau khi mở lại app
- Matches screen hiển thị dữ liệu thật từ repository/API
- Có thể mở Chat từ Match
- Các test nghiệp vụ chính chạy pass
- `./gradlew.bat testDebugUnitTest assembleDebug` chạy thành công

---

# Full - Làm sau MVP

## FULL-1. Dating profile nâng cao

- Upload nhiều ảnh dating
- Sắp xếp ảnh
- Xóa/thay ảnh
- Avatar và ảnh preview thật
- Thêm ngày sinh và tự động tính tuổi
- Thêm pronouns, occupation, education và location
- Preview profile trước khi lưu
- Validation đầy đủ cho bio, tuổi và sở thích

## FULL-2. Recommendation nâng cao

- Khoảng cách dựa trên GPS
- Haversine distance
- Filter theo khoảng cách tối đa
- Cooldown cho candidate đã PASS, ví dụ 7 ngày
- Ưu tiên hoạt động gần đây
- Ưu tiên profile mới
- Điều chỉnh trọng số dựa trên hành vi Like/Pass
- Recommendation service riêng
- Có thể tích hợp AI sau khi có đủ dữ liệu

Không đưa AI vào MVP vì khó kiểm thử và chưa cần thiết để chứng minh luồng Dating.

## FULL-3. Swipe gesture và trải nghiệm UI

- Kéo card sang trái/phải
- Animation khi Like/Pass
- Undo Pass gần nhất
- Hiển thị số lượng candidate còn lại
- Bottom sheet hoặc full-screen transition cho Detailed Profile
- Hiển thị nhiều ảnh và cho phép vuốt ảnh trong Detailed Profile
- Loading state
- Error state và Retry
- Offline state
- Accessibility content description cho nút Like/Pass

## FULL-4. Notification và realtime

- Đánh dấu notification đã đọc
- WebSocket event `DATING_MATCH`
- Đồng bộ Match khi user đăng nhập trên thiết bị khác
- Push notification
- Notification badge và deep link trực tiếp đến Match

## FULL-5. Conversation sau Match

- Tự động tạo conversation khi Match
- Chỉ cho phép chat nếu đã Match
- Hiển thị thời gian Match
- Unmatch
- Block user
- Report user
- Xử lý trạng thái conversation bị đóng

## FULL-6. Bảo mật và kiểm soát dữ liệu

- Kiểm tra quyền truy cập profile
- Không trả thông tin riêng tư không cần thiết
- Rate limit Like/Pass
- Chống spam request
- Soft delete dating profile
- Ẩn profile tạm thời
- Chặn người dùng khỏi Discover
- Audit các hành động Like/Pass/Match

## FULL-7. Database production

- Migration versioning thay vì tạo table tự động
- Index cho `dating_swipes.swiper_id`
- Index cho `dating_swipes.target_id`
- Index cho profile filtering
- Constraint chuẩn hóa thứ tự user trong `dating_matches`
- Đảm bảo transaction và idempotency
- Tối ưu cursor pagination cho Discover

## FULL-8. Test đầy đủ

- Backend integration test với PostgreSQL
- Test transaction khi tạo Match
- Test concurrent Like từ hai user
- Test idempotency khi retry request
- Test API authorization
- Compose UI test với loading/error/empty state
- Multi-user test với hai account thật
- Test upload ảnh và quyền truy cập media

---

# Thứ tự thực hiện đề xuất

```text
1. Tạo model dating
2. Tạo FakeDatingRepository
3. Tạo DatingContract và DatingViewModel
4. Tách dữ liệu hard-code khỏi DatingScreens.kt
5. Hoàn thiện Dating Profile
6. Hoàn thiện Discover với Like/Pass và Empty State
7. Thêm chức năng xem lại candidate đã PASS
8. Viết test logic mutual Match và reset PASS
9. Thêm notification database cho Match
10. Hoàn thiện Matches và Chat
11. Triển khai backend Dating API
12. Kết nối Android với backend
13. Kiểm tra với hai user
14. Làm các hạng mục FULL
```

# Nguyên tắc triển khai

- Hoàn thành và test MVP trước khi làm Full.
- Giữ UI, business logic và data access tách biệt.
- Backend là nguồn dữ liệu chính khi kết nối thật.
- Android không kết nối trực tiếp PostgreSQL.
- Không commit mật khẩu hoặc token trong các file account/local config.
- Mỗi thay đổi nên chạy test module liên quan trước, sau đó mới chạy full build.
