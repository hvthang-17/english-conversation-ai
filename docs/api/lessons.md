# API khám phá bài học G2

Base path `/api/v1`. Các endpoint yêu cầu Session hợp lệ. Thao tác lưu và bỏ dấu yêu cầu CSRF. Backend lấy người dùng từ Session.

| Method và path | Hành vi |
| --- | --- |
| GET `/lessons` | Danh sách bài `ACTIVE` thuộc chủ đề đang hoạt động. Query tùy chọn `q`, `topicId`, `level`, `objectiveCode`, `scenarioId`, `page`, `size`. |
| GET `/lessons/{id}` | Chi tiết bài đang công khai. Bài ẩn hoặc không tồn tại trả `404 LESSON_NOT_FOUND`. |
| GET `/lessons/saved?kind=FAVORITE` | Dấu cá nhân theo từng loại. Hỗ trợ `FAVORITE`, `SAVE_FOR_LATER`, `page`, `size`. |
| PUT `/lessons/{id}/bookmarks/{kind}` | Lưu dấu cho bài công khai. Trả 204 cả khi dấu đã tồn tại. Bài ẩn hoặc không tồn tại trả 404. |
| DELETE `/lessons/{id}/bookmarks/{kind}` | Bỏ dấu của phiên hiện tại. Trả 204 cả khi dấu đã được bỏ. Cho phép bỏ dấu trên bài ẩn. |

Response danh sách có `items`, `page`, `size`, `total`. Mặc định trang 0, kích thước 12, tối đa 50. Kết quả catalog sắp theo thứ tự chủ đề, ID chủ đề rồi ID bài. Dấu cá nhân sắp theo thời điểm lưu giảm dần, phân xử bằng ID bài. Các bộ lọc kết hợp bằng AND. `q` tối đa 120 ký tự, tìm chuỗi con trong tên và tóm tắt, hỗ trợ không dấu và không phân biệt hoa thường. Dấu `%` và `_` được tìm theo nghĩa ký tự.

Mỗi bài công khai có `id`, `available`, `title`, `summary`, `topicId`, `topicName`, `level`, `objectiveCode`, `learningObjective`, `scenarioId`, `scenarioTitle`, `favorite`, `savedForLater`. ID là chuỗi. Hai trường thuộc tình huống là `null` nếu bài chưa gắn tình huống hoặc tình huống không còn công khai. Với dấu của bài ẩn, chỉ có ID, trạng thái khả dụng và các cờ dấu có giá trị.

Profile `g2` cho xem bối cảnh và lựa chọn vai qua [API tình huống](profile-catalog.md). Profile mặc định `g6` cho bắt đầu buổi từ chi tiết tình huống. Quản trị catalog đầy đủ thuộc G10. Khi xóa toàn bộ dữ liệu học tập ở G2, backend xóa cả hai loại dấu và giữ tài khoản, hồ sơ, avatar cùng lịch học.
