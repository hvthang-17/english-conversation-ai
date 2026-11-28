# API thư viện, lịch sử và thống kê

Các route dưới `/api/v1/learning` xác thực bằng Session. Backend xác định owner từ principal. POST và DELETE yêu cầu CSRF. Định danh trả về trong JSON là chuỗi. Buổi hoặc thẻ của người khác trả 404.

| Method và route | Kết quả |
| --- | --- |
| GET /cards?status=NEEDS_REVIEW&page=0&size=12 | Trang thẻ có `items,page,size,total`. Status tùy chọn, nhận NEEDS_REVIEW hoặc REMEMBERED. |
| POST /cards/from-suggestion/{suggestion} | Sao chép nội dung và `partOfSpeech` (nếu có) từ nhận xét SUCCEEDED của owner. Gửi lại trả cùng thẻ và trạng thái hiện tại. Mục có `meaningVerified=false` trả HTTP 409 với mã `SUGGESTION_MEANING_UNVERIFIED`. |
| POST /cards/{id}/attempts | Body `{"result":"REMEMBERED","clientRequestId":"review-request-01"}`. Ghi một attempt FLASHCARD và cập nhật thẻ. |
| GET /history?page=0&size=12 | Lịch sử mới nhất trước, gồm cả buổi có thể tiếp tục. Cùng định dạng phân trang với thẻ. |
| DELETE /history/{id} | 204. Xóa buổi, transcript và nhận xét. Thẻ đã lưu giữ bản sao và mất liên kết nguồn. |
| DELETE /data | 204. Xóa toàn bộ buổi, transcript, nhận xét, thẻ và attempt của owner. |
| GET /stats | Số buổi hoàn thành, tin nhắn LEARNER, ngày luyện, tối đa 30 ngày có luyện gần nhất, chủ đề, số tin nhắn nguồn khác nhau có lỗi theo nhóm và tối đa 5 bài gợi ý. |

Danh sách nhận size từ 1 đến 50. Cùng mã attempt và kết quả được xử lý idempotent. Cùng mã và kết quả khác trả 409. Chi tiết lịch sử dùng lại GET practice-sessions/{id} và GET practice-sessions/{id}/feedback. Khi xin xóa toàn bộ, giao diện yêu cầu xác nhận riêng trước khi gọi DELETE.

Key attempt được dùng chung theo từng thẻ giữa `/cards/{id}/attempts` và `/notebook/{id}/attempts`. FLASHCARD cùng key/result qua hai API chỉ ghi một lượt; mỗi API giữ response riêng như trước. Key đã thuộc SPACED_REVIEW hoặc QUICK_QUIZ không được replay như FLASHCARD qua cards và trả 409 `REQUEST_ID_REUSED`. Hàng FLASHCARD cũ thiếu fingerprint vẫn replay được khi result trùng; không đổi dữ liệu legacy. Tự đánh dấu không khởi tạo hoặc thay đổi lịch spaced. [API sổ tay và ôn tập](lesson-materials.md).

## Gợi ý học tập G7.1

GET `/api/v1/learning/recommendations` yêu cầu phiên đăng nhập. Query `focus` nhận `LISTENING`, `PRONUNCIATION`, `CONVERSATION` hoặc `REVIEW`. Bỏ qua `focus` để dùng xếp hạng chung. Khi triển khai kế hoạch G7.3, màn hình có thể truyền trọng tâm từ kế hoạch.

Mỗi kết quả gồm `type` (`LESSON` hoặc `REVIEW_ITEM`), `id` dạng chuỗi, `title`, `level`, `topic`, `reasonCode` và `reason`. Thẻ có `level` và `topic` rỗng (NULL). API trả tối đa 12 mục.

- `REVIEW_NEEDED`. Tối đa bốn thẻ đang Cần ôn của chủ phiên, ưu tiên thẻ chưa ôn hoặc ôn lâu hơn.
- `SKILL_GAP`. Hai lượt nghe đã nộp gần nhất của cùng một đề đang công khai đều dưới 60%, hoặc cùng mã lỗi hội thoại xuất hiện ở ít nhất hai tin nhắn khác nhau trong một chủ đề. Tín hiệu hội thoại ưu tiên bài trong chủ đề đó có tình huống gợi ý còn mở.
- `FOCUS_MATCH`, `TOPIC_INTEREST` và `LEVEL_MATCH`. Các bài khả dụng ở cấp độ hồ sơ được ưu tiên theo tín hiệu điểm yếu, trọng tâm, sở thích và ID. Trọng tâm được đối chiếu với học liệu hoặc tình huống khả dụng của bài.

G7.1 đọc dữ liệu hiện có. G7.3 ưu tiên bài chưa hoàn thành trong lộ trình trong cùng nhóm tín hiệu điểm yếu. API gợi ý phục vụ màn Hôm nay ở G7.4.

## Gợi ý độ khó G7.2

GET `/api/v1/learning/recommendations/difficulty` trả `{ "suggestion": null }` nếu chưa đủ bằng chứng. Khi đủ, `suggestion` chứa `currentLevel`, `suggestedLevel`, `sampleSize` và `threshold`.

Chỉ tính lượt đã nộp của đề và bài đang khả dụng, đúng trình độ hồ sơ. Dùng lượt mới nhất của mỗi bài để các lần làm lại hoặc các phiên bản đề của cùng bài chỉ góp một mẫu. Trong mỗi nhóm cùng phiên bản chấm và cùng số câu của từng dạng, lấy ba mẫu mới nhất. Cả ba lượt A2 đạt từ 80% thì gợi ý thử B1. Cả ba lượt B1 dưới 60% thì gợi ý ôn A2. Hai ngưỡng cấu hình qua `app.learning.difficulty.promotion-percent` và `app.learning.difficulty.remediation-percent`.

Màn Hồ sơ học tập cho giữ mức hiện tại trong lần mở trang hoặc chọn mức được gợi ý, rồi lưu hồ sơ để xác nhận. API gợi ý chỉ đọc dữ liệu. Bộ đề A2 mẫu hiện có cơ cấu dạng câu hỏi khác nhau. Cần công bố đủ ba đề A2 tương đương để có thể nhận gợi ý thử B1 từ dữ liệu luyện thật.

## Kế hoạch học G7.3

Các đường dẫn bên dưới bắt đầu bằng `/api/v1/learning/plans`. Chỉ học viên được mở kế hoạch của chính mình. Mutation dùng CSRF theo cơ chế Session hiện có.

| Phương thức | Dữ liệu và hành vi |
| --- | --- |
| GET đường dẫn gốc | Trả tối đa 20 kế hoạch mỗi trang, mới nhất trước. Query `page` bắt đầu từ 0. |
| GET `/{id}` | Trả `plan` và `items`. Kế hoạch lưu trữ vẫn xem được. |
| POST đường dẫn gốc | Nhận `settings` gồm `goal`, `focusSkill` tùy chọn, `startDate` và `days` bằng 1 hoặc 7. Khi thay kế hoạch đang mở, gửi thêm `replaceId` và `version` của kế hoạch cũ. |
| PUT `/{id}` | Nhận `settings` và `version`. Khoảng ngày mới phải chứa các mục đã xếp lịch. |
| POST `/{id}/items` | Nhận `version`, `dueDate`, `position` từ 1, và `source` gồm `type` là `LESSON` hoặc `REVIEW_ITEM` cùng `id`. Gửi `recommended: true` để lấy mã lý do từ gợi ý backend đang cung cấp. |
| PUT `/{id}/items/{item}` | Đổi ngày, vị trí hoặc nguồn của mục đang chờ. Gửi `version`, `dueDate` và `position`. Bỏ `source` để giữ nguồn cũ. |
| POST `/{id}/items/{item}/finish` | Gửi `version` và `result` bằng `COMPLETED` hoặc `SKIPPED`. |

Backend tuần tự hóa thao tác ghi theo tài khoản. Bản cũ nhận HTTP 409. Mỗi kế hoạch hỗ trợ 100 mục. Múi giờ lấy từ lịch luyện trong hồ sơ khi tạo, mặc định `Asia/Ho_Chi_Minh` khi chưa có lịch. Dùng `available=false` để hiển thị **Không còn khả dụng**. Mục mất nguồn vẫn giữ tên và trạng thái. Có thể thay nguồn của mục đang chờ. Xóa toàn bộ dữ liệu học tập dọn kế hoạch lẫn mục; hồ sơ tiếp tục tồn tại.

## Tiến độ và thông báo G8

Các đường dẫn dưới đây bắt đầu từ `/api/v1/learning/progress`, thuộc phiên học viên hiện tại. Các lệnh ghi dùng CSRF.

| Đường dẫn | Dữ liệu |
| --- | --- |
| GET đường dẫn gốc | Tổng thời lượng, phút/ngày, mục tiêu, chuỗi ngày, số bài/nhiệm vụ, điểm, huy hiệu, tiến độ chủ đề, lỗi lặp, 20 mốc hoàn thành bài gần nhất và tối đa 100 kết quả gần nhất cho mỗi kỹ năng. `level` nhận A2 hoặc B1, mặc định theo hồ sơ. |
| GET hoặc POST `/lessons/{id}` | GET đọc mốc hoàn thành và khả năng đánh dấu thủ công. POST ghi mốc cho bài chỉ có học liệu mẫu. Lượt nộp nghe và kết quả phát âm của bài ghi mốc qua luồng nghiệp vụ tương ứng kể từ khi triển khai. |
| POST `/heartbeat` | Nhận UUID `eventId` và `streamId`, cùng `activity` là LESSON, LISTENING, PRONUNCIATION, CHAT hoặc REVIEW. Server tính từ giờ nhận. Sự kiện đầu hoặc sau hơn 45 giây chỉ mở mốc mới. Tín hiệu hợp lệ ghi tối đa 30 giây. |
| GET `/notifications?page=0` | Trả tối đa 20 thông báo mỗi trang, gồm kết quả hoặc nhắc học. |
| PUT `/notifications/{id}/read` | Giữ thời điểm đọc lần đầu. ID thuộc người khác trả 404. |

Thời lượng hiển thị là ước lượng từ hoạt động hợp lệ. Trình duyệt theo dõi tương tác trong vùng luyện tập và mở luồng mới khi người học quay lại sau khoảng không hoạt động. Thời gian qua nửa đêm dùng múi giờ hồ sơ. Ba ngày đủ 60 giây kích hoạt huy hiệu qua heartbeat. SMTP nhắc học mặc định tắt. Bật `MAIL_REMINDERS_ENABLED` và cấu hình Mailpit để thử email loopback. Lỗi kết nối SMTP xác định trước khi gửi được thử lại có giới hạn. Một lần gửi mà worker mất lease hoặc gặp lỗi không xác định được kết quả được ghi `UNKNOWN` và giữ lại để truy vết.

## Tương thích sau BR-09

Route, DTO và quy tắc owner của learning/history và learning/stats giữ nguyên. Lịch sử sắp started_at giảm dần, sau đó id giảm dần; trang vượt tổng trả items rỗng, tổng vẫn là tổng của owner. Thống kê tiếp tục dùng Asia/Ho_Chi_Minh, đếm tin nhắn LEARNER theo hợp đồng cũ. Buổi chỉ được đánh dấu COMPLETED khi có trao đổi thành công; chỉ mở đầu hoặc chỉ gửi lỗi là ABANDONED. Xóa buổi giữ bản sao thẻ và gỡ nguồn, xóa toàn bộ dọn cả thẻ/lộ trình/tiến độ và dữ liệu nghe/phát âm. Backend conversation sở hữu xóa buổi; learning điều phối xóa toàn bộ trong cùng transaction.
