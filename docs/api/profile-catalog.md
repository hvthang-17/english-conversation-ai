# API hồ sơ và catalog — PROFILE-01 / SCENARIO-01

Base `/api/v1`, cùng origin và Session/CSRF theo [auth](auth.md). Tất cả endpoint bên dưới yêu cầu tài khoản ACTIVE. ID trong JSON là chuỗi. Chức năng mở buổi hiện dùng [API buổi luyện](practice.md).

## Hồ sơ owner-only

| Method / path | Kết quả |
| --- | --- |
| GET /profile/me | 200 hồ sơ của principal; 404 PROFILE_NOT_FOUND nếu tài khoản chưa có hồ sơ |
| PUT /profile/me | CSRF bắt buộc; thay toàn bộ trường hồ sơ/sở thích hiện tại; 200 hồ sơ đã lưu |

```json
{
  "displayName": "Người học",
  "selectedLevel": "B1",
  "learningGoal": "Giao tiếp khi đi du lịch",
  "dailyGoalMinutes": 20,
  "topicIds": ["5"]
}
```

Response có cùng các trường và `userId` do server xác định. ID chủ đề ví dụ phải lấy từ GET /topics, không hard-code trong client. Không nhận user ID để chọn owner; field userId dư trong JSON không làm thay đổi đối tượng. Không có endpoint sửa hồ sơ người khác, kể cả cho admin.

- displayName bắt buộc, không trắng, tối đa 100 ký tự; strip khi lưu.
- selectedLevel chỉ A2/B1 (tự chọn, không phải đánh giá CEFR).
- learningGoal tối đa 1000 ký tự, nullable/để trống; strip khi lưu.
- dailyGoalMinutes tùy chọn 1–1440, chưa có nhắc học tự động.
- topicIds bắt buộc là mảng, cho phép rỗng; tối đa 20, ID dương hợp lệ, không trùng. Tất cả chủ đề phải đang active. Thiếu/ẩn → 400 INVALID_TOPICS; trùng → 400 DUPLICATE_TOPICS; lỗi định dạng/độ dài → 400.
- PUT khóa hàng users của chính owner và thay interests trong cùng transaction với profile. Hai lần lưu cùng owner được tuần tự hóa; lần lưu sau cùng quyết định dữ liệu, chưa có ETag/version cho UI chỉnh sửa nhiều tab.
- Đăng ký công khai tạo hồ sơ `Người học`/A2/goal null/interests rỗng cùng transaction tạo users. V3 backfill người học có sẵn; không đổi password/role/status. Admin không bắt buộc có profile; có thể PUT /profile/me để tự tạo.
- GET trả sở thích thuộc topic còn active; topic đã ẩn không còn được chọn. Cần tải lại danh sách nếu server báo INVALID_TOPICS.

## Catalog

| Method / path | Kết quả |
| --- | --- |
| GET /topics?level=A2 | Mảng `{id,code,name,description}` của topic active có bài ACTIVE/level khả dụng; level tùy chọn |
| GET /scenarios?level=B1&topicId=5&page=0&size=8 | `{content,page,size,totalElements}`; level/topicId tùy chọn; mặc định page=0,size=12; size 1–50, page 0–100000 |
| GET /scenarios/{id}?level=B1 | Chi tiết khả dụng; level tùy chọn; không có/ẩn/không hỗ trợ level → 404 SCENARIO_NOT_FOUND |

Summary: `{id,code,topicId,topicName,title,description,levels}`. Detail bổ sung `learnerRole,aiRole,communicationGoal,contextDescription`.

Không có `ai_instruction`/`aiInstruction` trong bất kỳ DTO người học nào. Repository chỉ SELECT cột công khai cho projection, không trả entity hoặc instruction rồi trông chờ frontend che đi. Level ngoài A2/B1 → 400 INVALID_LEVEL; phân trang sai → 400 INVALID_PAGINATION. Topic ID hợp lệ nhưng không tồn tại trả danh sách rỗng; ID sai định dạng → 400 INVALID_ID. Sắp xếp ổn định theo topic.sort_order, topic.id, scenario.id. Lấy level links theo batch cho một trang, tránh query mỗi card.

Bài ACTIVE phải thuộc topic active và có ít nhất một scenario_levels; filter level yêu cầu đúng hàng level. DRAFT/ARCHIVED/topic ẩn/không có level không thể chọn qua detail. Không có endpoint chỉnh catalog trong giai đoạn 2.

## Chọn bài và lịch sử
Frontend dùng route `#/profile`, `#/scenarios`, `#/scenarios/{id}?level=B1`. Level mặc định lấy từ hồ sơ; filter bài không sửa level của hồ sơ. Bấm Bắt đầu hội thoại để tạo practice_session ở bài và level đã chọn.

Khi mở buổi, backend kiểm tra lại trạng thái và level rồi lưu snapshot. Nội dung buổi cũ lấy từ snapshot.
