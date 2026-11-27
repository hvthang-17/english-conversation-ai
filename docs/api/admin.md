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

Audit lưu actor, hành động, loại/ID đối tượng và thời gian. Sự kiện tài khoản ghi thêm quyền/trạng thái trước và sau. Sự kiện catalog chỉ ghi đích thay đổi. Admin có quyền đọc hướng dẫn AI của tình huống để biên tập. Hội thoại riêng vẫn yêu cầu quyền sở hữu buổi.

G10.1 chặn admin tự đổi quyền, tự khóa hoặc tự xóa tài khoản. Để xóa tài khoản admin, một admin khác phải chuyển tài khoản đó thành LEARNER trước. Tài khoản đang chờ xóa không được đổi quyền/trạng thái. Khi thay đổi có hiệu lực, backend thu hồi phiên và ghi audit trong cùng giao dịch.

Profile g6 cung cấp GET `/audit` và GET `/ai-attempts` dưới `/api/v1/admin`, chỉ ADMIN, `Cache-Control: no-store`. Audit hiển thị mã hành động và ID; AI hiển thị tác vụ, model, provider, trạng thái và độ trễ; không trả nội dung hội thoại.

- Không truyền `page`: giữ tương thích phản hồi mảng tối đa 50 mục mới nhất.
- Truyền `page=0&size=12&q=`: trả `{items,page,size,total}`; page từ 0, size 1–50 (mặc định 12). Sắp xếp ID giảm dần, lọc trước khi phân trang và tính total theo cùng bộ lọc.
- `q` tối đa 120 ký tự sau trim, tìm chuỗi con trong metadata (ID, mã tác vụ/hành động, đối tượng/provider, reference/model, actor, trạng thái, thời điểm UTC). Hỗ trợ nhãn tiếng Việt đã ánh xạ trên UI như “Hội thoại”, “Tạo chủ đề”. `%`, `_` và `!` được tìm như ký tự thường.
- Riêng `/ai-attempts` nhận `status=RUNNING|SUCCEEDED|FAILED|UNKNOWN` hoặc bỏ trống. Kết hợp với q bằng điều kiện AND. Trang/size sai trả 400 `INVALID_PAGE`; bộ lọc không hợp lệ trả 400 `INVALID_FILTER`.

Giao diện quản trị dùng nhánh phân trang và cho chọn 12/24/50 mục. Dữ liệu nhật ký có thể thay đổi theo chính sách lưu trữ giữa các lần tải.

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


### Tách thẻ Từ vựng và Mẫu câu

GET `/api/v1/admin/materials/WORDS` nhận thêm query tùy chọn `expressionKind=WORD` hoặc `expressionKind=PHRASE`. Bộ lọc áp dụng trước LIMIT/OFFSET và tổng `total` chỉ tính loại được chọn. Bỏ qua bộ lọc vẫn trả cả hai loại như trước. Giá trị khác hoặc truyền bộ lọc cho loại học liệu khác trả 422 `MATERIAL_INVALID`. Giá trị lọc được bind vào SQL, không ghép trực tiếp đầu vào.

Giao diện chia sáu thẻ con: Bài học, Từ vựng, Mẫu câu, Hội thoại mẫu, Đề nghe, Bài phát âm. Hai thẻ từ/mẫu câu vẫn tạo và sửa qua `/materials/WORDS`, trường `kind` tương ứng WORD/PHRASE. Mỗi thẻ có danh sách và phân trang riêng, về trang đầu khi đổi thẻ. Thẻ tạm khóa khi editor hoặc xác nhận đang mở để không mất dữ liệu nhập.

Triển khai backend hỗ trợ bộ lọc trước hoặc cùng frontend mới. Không đổi schema, không cần migration hay biến môi trường mới; phân quyền Session/ADMIN và CSRF giữ nguyên.

GET /materials/LESSONS/lesson-catalog-options trả danh sách chủ đề và tình huống cho form bài học. Danh sách tình huống gồm tên, chủ đề, trạng thái công khai và cấp độ, còn dữ liệu ghi vẫn dùng topic_id và scenario_id.

Danh mục liên kết cũng trả danh sách bài học theo chủ đề để chọn khi biên tập từ vựng, mẫu câu, hội thoại mẫu, đề nghe và bài phát âm. API ghi nhận lesson_id của bài đã chọn.

## Phiếu hỗ trợ G10.4

Admin xem hàng đợi, nhận hoặc phân công lại cho admin đang hoạt động. Quyền phản hồi và chuyển trạng thái thuộc người được phân công hiện tại. [Hợp đồng hàng đợi và hỗ trợ](support.md#hàng-đợi-quản-trị-g104).

## Thông báo G10.5

| API dưới `/announcements` | Hành vi |
| --- | --- |
| `GET ?page=0&size=12` | Danh sách phân trang có trạng thái hiệu lực, phiên bản và số người còn trong danh sách nhận đã lưu. |
| `POST` | Tạo nháp với `{draft,clientRequestId}`. `draft` có `title`, `body`, `audienceRole`, `audienceLevel` và `expiresAt`. Cùng người tạo, UUID và nội dung trả bản đã tạo. |
| `PUT /{id}` | Sửa nháp với `{draft,version}`. |
| `POST /preview` | Nhận trực tiếp `draft`, trả nội dung nháp và số người đủ điều kiện lúc xem trước. |
| `POST /{id}/publish` | Nhận `version`, công bố và lưu người nhận trong cùng giao dịch với audit. Gửi lại bản đã công bố giữ danh sách đã lưu. |
| `POST /{id}/archive` | Ẩn bản đã công bố khỏi hộp thư. |

Tiêu đề tối đa 200 ký tự, nội dung tối đa 1.000 ký tự. Người nhận là tài khoản đang hoạt động, quyền `LEARNER` hoặc `ADMIN`. Chỉ nhóm `LEARNER` có thể lọc `A2` hoặc `B1`. Thay đổi trình độ sau công bố giữ danh sách nhận ban đầu. `expiresAt` là mốc ISO 8601 tùy chọn. Tại thời điểm hết hạn, admin thấy `EXPIRED` và hộp thư ngừng hiển thị. Khi cần chỉnh nội dung đã công bố, tạo bản nháp mới.

## Thống kê và nhật ký G10.6

`GET /metrics?days=7` nhận số nguyên từ 1 đến 90, mặc định 7. Phản hồi gồm khoảng thời gian UTC và các số liệu sau. Hai API phân trang `/audit` và `/ai-attempts` nhận cùng tham số `days` để lọc theo kỳ. Bỏ tham số giữ lịch sử theo bộ lọc hiện có. Giao diện cung cấp các kỳ 24 giờ, 7 ngày, 30 ngày và 90 ngày.

- Tài khoản, chủ đề và tình huống là số hiện tại. Đăng ký và hoạt động học tập tính theo mốc ghi nhận trong kỳ.
- API trả tử số và mẫu số hoàn thành hội thoại. Giao diện lấy số buổi `COMPLETED` chia cho số buổi `COMPLETED` cộng `ABANDONED` có thời điểm kết thúc trong kỳ. Mẫu số bằng 0 hiển thị **Chưa có dữ liệu**. Lượt học lấy từ tin người học, đề nghe đã nộp, lần chấm bài phát âm thành công và lượt ôn đã lưu.
- Nội dung được sử dụng gồm bài có mốc hoàn thành, tình huống có buổi bắt đầu, đề nghe đã nộp và bài phát âm có lượt chấm hợp lệ.
- Nhật ký AI nhóm theo tác vụ và nhà cung cấp. Tổng hợp trạng thái, retry, lỗi quota có ghi nhận, độ trễ trung bình và số mẫu, token và số mẫu có usage. Hạn mức hiển thị lấy từ cấu hình ứng dụng. Dữ liệu tổng hợp chỉ bao phủ các lần gọi có ghi `ai_attempts`. Azure TTS công bố đề nghe hiện dùng luồng riêng.

Các API thống kê và nhật ký chỉ trả số tổng hợp hoặc metadata kỹ thuật. Audit phiếu và thông báo lưu loại sự kiện, người thực hiện, ID đối tượng và thời gian.
