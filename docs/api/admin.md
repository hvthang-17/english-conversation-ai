# API quản trị

Các route dưới `/api/v1/admin` yêu cầu Session có quyền ADMIN. POST, PUT, PATCH và DELETE yêu cầu CSRF. Thao tác ghi kiểm tra lại tài khoản quản trị còn hiệu lực trước khi cập nhật. ID trong JSON là chuỗi.

| Phương thức và đường dẫn | Nội dung |
| --- | --- |
| GET `/overview` | Số người học, tài khoản khóa, chủ đề đang mở, tình huống công khai, buổi hoàn thành và các lần gọi AI trong 24 giờ gần nhất |
| GET `/users?email=&page=0&size=12` | Tìm chuỗi con email, không phân biệt hoa thường. Mỗi mục có ID, email, vai trò và trạng thái |
| PATCH `/users/{id}/access` | Body có ít nhất một trong hai trường `status` (ACTIVE/LOCKED) và `role` (ADMIN/LEARNER). Trường bỏ qua giữ giá trị hiện tại. |
| GET `/topics?page=0&size=12` | Danh sách cả chủ đề đang mở và đang ẩn |
| POST `/topics`, PUT `/topics/{id}` | Tạo hoặc lưu toàn bộ chủ đề |
| GET `/topics/{topicId}/scenarios?page=0&size=12` | Danh sách tình huống thuộc chủ đề, gồm bản nháp và bản ẩn |
| POST `/topics/{topicId}/scenarios`, PUT `/topics/{topicId}/scenarios/{id}` | Tạo hoặc lưu tình huống, tập trình độ và biến thể vai |
| GET `/topics/{topicId}/scenarios/{id}/roles` | Đọc biến thể vai để biên tập, gồm chỉ dẫn AI nội bộ |

Các danh sách phân trang dạng `{items,page,size,total}`, bắt đầu từ 0, tối đa 50 phần tử/trang. Thay quyền hoặc trạng thái tăng phiên bản bảo mật và thu hồi phiên hiện có. Mở tài khoản cho phép đăng nhập lại. Lặp lại cùng quyền/trạng thái không đổi phiên bản và không ghi thêm audit.

Chủ đề nhận `code`, `name`, `description`, `active`, `sortOrder` từ 0 đến 9999. Tình huống nhận `code`, `title`, `description`, `learnerRole`, `aiRole`, `communicationGoal`, `contextDescription`, `aiInstruction`, `status` và `levels`. Mã dùng chữ ASCII viết hoa, chữ số hoặc dấu gạch dưới, bắt đầu bằng chữ. Mã trùng trả 409.

Để lưu tình huống `ACTIVE`, chủ đề phải đang mở và tập trình độ phải có ít nhất một mục `A2` hoặc `B1`. Trình độ lặp trả 400, điều kiện công khai chưa đủ trả 422. Dùng `ARCHIVED` để ẩn tình huống, hoặc `active=false` để ẩn cả chủ đề trong danh mục người học. Mở lại chủ đề sẽ hiện lại các tình huống vẫn ACTIVE và có trình độ. Tình huống vẫn thuộc chủ đề trên đường dẫn.

Audit V18 lưu actor, hành động, loại/ID đối tượng và thời gian. Sự kiện tài khoản ghi thêm quyền/trạng thái trước và sau. Sự kiện catalog chỉ ghi đích thay đổi. Admin có quyền đọc hướng dẫn AI của tình huống để biên tập. Các API quản trị giới hạn ở tài khoản, catalog và số liệu tổng hợp. Hội thoại riêng vẫn yêu cầu quyền sở hữu buổi.

G10.1 chặn admin tự đổi quyền, tự khóa hoặc tự xóa tài khoản. Để xóa tài khoản admin, một admin khác phải chuyển tài khoản đó thành LEARNER trước. Tài khoản đang chờ xóa không được đổi quyền/trạng thái. Khi thay đổi có hiệu lực, backend thu hồi phiên và ghi audit trong cùng giao dịch.

Profile g6 cung cấp GET /audit và GET /ai-attempts dưới /api/v1/admin. Trả tối đa 50 mục gần nhất, chỉ ADMIN. Audit hiển thị mã hành động và ID; AI hiển thị tác vụ, model, provider, trạng thái và độ trễ.

## Quản lý học liệu G10.2

Các route sau chạy với profile `g6`. `{kind}` nhận `TOPICS`, `LESSONS`, `WORDS`, `DIALOGUES`, `LISTENING` hoặc `PRONUNCIATION`.

| Phương thức và đường dẫn dưới `/api/v1/admin` | Nội dung |
| --- | --- |
| GET `/materials/{kind}?page=0&size=12` | Danh sách phân trang, kèm toàn bộ trường biên tập và trạng thái. |
| POST `/materials/{kind}` | Lưu bản nháp và trả dữ liệu đã lưu với HTTP 201. |
| PUT `/materials/{kind}/{id}` | Thay toàn bộ trường biên tập của bản nháp. Chủ đề đã phát hành cho sửa tên, mô tả và thứ tự, giữ mã và trạng thái. |
| POST `/materials/{kind}/{id}/publish` | Công bố nội dung hợp lệ. Điều kiện công bố gồm chủ đề và bài cha đang mở khi có liên kết. |
| POST `/listening-exercises/{id}/publish` | Công bố đề nghe qua luồng tạo MP3. Một bài có tối đa một đề đang công khai. |
| POST `/materials/{kind}/{id}/archive` | Ẩn học liệu đã công bố, giữ phiên bản và tham chiếu. |
| DELETE `/materials/{kind}/{id}` | Xóa bản nháp chưa được tham chiếu. |

Body của POST tạo nháp và PUT dùng tên trường theo đặc tả database. Các cột JSON nhận mảng JSON. `lines_json` gồm `line_no`, `speaker_label`, `text_en`, `text_vi`, `voice_key`. `script_segments_json` dùng cùng cấu trúc nhưng bỏ `text_vi`, và `transcript_en` phải khớp các lượt nói. Phiên bản đề giữ chính sách `AFTER_SUBMIT`. Nội dung sai trả 422, tham chiếu đang dùng hoặc thao tác sai trạng thái trả 409.

Với nội dung đã công bố hoặc đã ẩn, dùng **Sao chép thành bản nháp** để biên tập phiên bản mới. Khi thay nội dung thường, admin ẩn phiên bản cũ bằng thao tác riêng. Với đề nghe, phát hành phiên bản mới tự ẩn đề trước đó của cùng bài và giữ tệp cho các lượt cũ. Nút **Nghe thử** dùng giọng tiếng Anh cục bộ của trình duyệt.

## Biến thể vai G10.3

Mỗi biến thể trong `roleVariants` nhận `code`, `learnerRole`, `aiRole`, `communicationGoal`, `contextDescription` và `aiInstruction`. Biến thể dùng chung tập trình độ của tình huống. Mã dùng chữ hoa ASCII, chữ số và dấu gạch dưới, bắt đầu bằng chữ, tối đa 32 ký tự. Mỗi tình huống nhận tối đa 12 biến thể. Mã `DEFAULT` dành cho vai mặc định. Backend từ chối mã trùng, cặp vai trùng sau chuẩn hóa hoa/thường và khoảng trắng, và ký tự điều khiển trong chỉ dẫn AI. Điều kiện công bố vẫn yêu cầu chủ đề mở và có ít nhất một trình độ.

PUT lưu biến thể trong cùng giao dịch với tình huống, trình độ và audit. Bỏ qua `roleVariants` khi chỉ cập nhật tình huống hoặc dùng `[]` để gỡ toàn bộ biến thể. API người học chỉ trả mã, vai, bối cảnh và mục tiêu được phép hiển thị. Buổi đã mở tiếp tục dùng snapshot vai, bối cảnh, mục tiêu và chỉ dẫn AI lúc tạo buổi. Sửa hoặc gỡ biến thể có hiệu lực với buổi mới.

Trong editor, admin rà vai của AI, cách mở lời, độ khó theo A2/B1 và mục tiêu giao tiếp trước khi lưu công khai. Xác nhận trong giao diện ghi nhận thao tác rà trên form. Kiểm thử với fixture xác minh cách chọn chỉ dẫn, còn chất lượng lời thoại cần rà riêng với Gemini thật.

Trong giao diện ADMIN, vào **Chủ đề** để thêm, sửa, công bố, ẩn hoặc xóa chủ đề nháp. Chủ đề đã phát hành giữ mã ổn định và được ẩn để bảo toàn lịch sử. Xóa chủ đề nháp ghi audit với mã TOPIC_UPDATED và metadata operation=DELETE.

Trong **Tình huống**, admin có thể xóa bản nháp qua DELETE /topics/{topicId}/scenarios/{id} hoặc ẩn tình huống đang công khai qua POST /topics/{topicId}/scenarios/{id}/archive. Xóa bị chặn nếu có bài học hoặc buổi luyện tham chiếu. Thao tác ẩn giữ nguyên trình độ, biến thể và snapshot lịch sử. Nhật ký xóa dùng SCENARIO_UPDATED với metadata operation=DELETE.

POST /topics/{topicId}/scenarios/{id}/restore công khai lại tình huống đã ẩn khi chủ đề đang mở, có trình độ và cấu hình vai hợp lệ. Thao tác giữ nguyên nội dung và snapshot buổi cũ.
