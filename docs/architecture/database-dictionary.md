# Từ điển dữ liệu

[ERD 20 bảng](database.md) có DDL qua V1–V10. Quy trình tại [workflows](database-workflows.md). PK: khóa chính; FK: khóa ngoại; UQ: duy nhất; `?`: nullable. Các bảng ứng dụng dùng ID BIGINT có dấu và DATETIME(6) UTC. Hai bảng phiên giữ kiểu của Spring Session.

## Tài khoản và cá nhân hóa

### users

| Trường | Kiểu/ràng buộc | Vai trò |
| --- | --- | --- |
| id | BIGINT PK, tự tăng | Định danh tài khoản; API trả dạng chuỗi |
| email | VARCHAR(254) UQ | ADR 0005/0007: ASCII canonical, strip/lowercase Locale.ROOT; ascii_bin + CHECK lowercase/trim/không whitespace + UNIQUE; không tự loại dấu chấm/phần + |
| password_hash | VARCHAR(255) | Mật khẩu băm, không phải mật khẩu gốc hay mã hóa giải ngược |
| role | VARCHAR(20) | LEARNER hoặc ADMIN; chưa cần bảng quyền động |
| status | VARCHAR(20) | ACTIVE hoặc LOCKED; phải kiểm tra thực tế ở backend/phiên |
| security_version | BIGINT NOT NULL, mặc định 0 | Tăng khi đổi password/role/status; đối chiếu principal để chặn stale session sau revoke (ADR 0007) |
| password_changed_at | DATETIME(6)? | Thời điểm đổi mật khẩu, hỗ trợ chính sách vô hiệu phiên |
| last_login_at | DATETIME(6)? | Lần đăng nhập thành công gần nhất |
| created_at | DATETIME(6) | Tạo tài khoản |
| updated_at | DATETIME(6) | Cập nhật gần nhất |

### learner_profiles

| Trường | Kiểu/ràng buộc | Vai trò |
| --- | --- | --- |
| user_id | BIGINT PK/FK → users | Một hồ sơ/người học; admin không bắt buộc có hồ sơ |
| display_name | VARCHAR(100) | Tên hiển thị |
| selected_level | VARCHAR(2) | A2/B1 tự chọn; không phải kết quả đánh giá CEFR |
| learning_goal | VARCHAR(1000)? | Mục tiêu giao tiếp cá nhân |
| daily_goal_minutes | SMALLINT? | Mục tiêu phút/ngày > 0 nếu có; tùy chọn, chưa có nhắc ôn |
| created_at | DATETIME(6) | Tạo hồ sơ |
| updated_at | DATETIME(6) | Lần chỉnh hồ sơ gần nhất |

### user_topic_interests

| Trường | Kiểu/ràng buộc | Vai trò |
| --- | --- | --- |
| user_id | BIGINT PK/FK → users | Người lựa chọn |
| topic_id | BIGINT PK/FK → topics | Chủ đề quan tâm; PK ghép user_id/topic_id |
| created_at | DATETIME(6) | Lúc chọn; bỏ quan tâm thì xóa hàng |

## Catalog

### topics

| Trường | Kiểu/ràng buộc | Vai trò |
| --- | --- | --- |
| id | BIGINT PK, tự tăng | Định danh chủ đề |
| code | VARCHAR(50) UQ | Mã ổn định, ví dụ TRAVEL |
| name | VARCHAR(150) | Tên hiển thị tiếng Việt |
| description | TEXT? | Giới thiệu |
| is_active | BOOLEAN | Cho phép lựa chọn chủ đề |
| sort_order | INT | Thứ tự, mặc định 0; phân xử bằng id khi trùng |
| created_at | DATETIME(6) | Tạo |
| updated_at | DATETIME(6) | Cập nhật |

### scenarios

| Trường | Kiểu/ràng buộc | Vai trò |
| --- | --- | --- |
| id | BIGINT PK, tự tăng | Định danh tình huống |
| topic_id | BIGINT FK → topics | Nhóm nội dung |
| code | VARCHAR(80) UQ | Mã ổn định, ví dụ RESTAURANT_ORDER |
| title | VARCHAR(200) | Tên bài luyện |
| description | TEXT | Giới thiệu cho người học |
| learner_role | VARCHAR(200) | Vai người học |
| ai_role | VARCHAR(200) | Vai AI |
| communication_goal | TEXT | Mục tiêu cần thực hành |
| context_description | TEXT | Bối cảnh để hội thoại diễn ra |
| ai_instruction | TEXT | Chỉ dẫn phía server; không trả qua API danh sách |
| status | VARCHAR(20) | DRAFT, ACTIVE, ARCHIVED |
| created_by | BIGINT? FK → users | Người tạo; nullable cho dữ liệu seed hoặc actor đã được xóa hợp lệ |
| updated_by | BIGINT? FK → users | Người sửa gần nhất |
| created_at | DATETIME(6) | Tạo |
| updated_at | DATETIME(6) | Cập nhật |

### scenario_levels

| Trường | Kiểu/ràng buộc | Vai trò |
| --- | --- | --- |
| scenario_id | BIGINT PK/FK → scenarios | Tình huống; PK ghép với level |
| level | VARCHAR(2) PK | A2 hoặc B1; ACTIVE phải có ít nhất một hàng |

## Buổi luyện và đầu vào

V5 bổ sung client_request_id/fingerprint cho thao tác mở buổi, attempt_count/lease/retry/error cho ai_requests và cột sinh ảo busy_session_id để khóa tác vụ theo buổi. Trạng thái SKIPPED dùng khi người học bỏ phản hồi nhưng giữ câu LEARNER. V8 thêm cột và FK audio trong messages.

### practice_sessions

| Trường | Kiểu/ràng buộc | Vai trò |
| --- | --- | --- |
| id | BIGINT PK, tự tăng | Định danh buổi |
| user_id | BIGINT FK → users | Chủ sở hữu, kiểm tra server-side |
| scenario_id | BIGINT FK → scenarios | Catalog nguồn; giữ FK bằng RESTRICT/ẩn catalog |
| selected_level | VARCHAR(2) | A2/B1 tại lúc bắt đầu; không đổi theo hồ sơ |
| mode | VARCHAR(5), CHECK TEXT/VOICE | Chế độ hiện tại của buổi. V9 mặc định TEXT cho buổi đã có. |
| initial_mode | VARCHAR(5), CHECK TEXT/VOICE | Chế độ lúc tạo buổi để đối chiếu yêu cầu gửi lại sau khi chuyển sang TEXT. |
| scenario_snapshot | JSON | Bối cảnh/vai trò/mục tiêu/tên tại lúc bắt đầu; không chứa key |
| status | VARCHAR(20) | ACTIVE, COMPLETED, ABANDONED |
| started_at | DATETIME(6) | Bắt đầu |
| ended_at | DATETIME(6)? | Kết thúc bình thường hoặc bỏ dở; không trước started_at |
| last_activity_at | DATETIME(6) | Hoạt động gần nhất, hỗ trợ nhận diện bỏ dở |
| version | BIGINT | Giá trị optimistic lock, khởi tạo 0; không thay khóa giao dịch cấp lượt |

### conversation_turns

| Trường | Kiểu/ràng buộc | Vai trò |
| --- | --- | --- |
| id | BIGINT PK, tự tăng | Định danh lượt |
| session_id | BIGINT FK → practice_sessions | Buổi sở hữu |
| turn_no | INT | Opening 0, lượt người học 1 trở lên; UQ cùng session |
| client_request_id | VARCHAR(64) | ID thao tác từ client; server cấp cho opening; UQ cùng session |
| request_fingerprint | CHAR(64) | HMAC SHA-256 dạng hex của payload chuẩn hóa; ngăn tái sử dụng key khác nội dung |
| kind | VARCHAR(20) | OPENING, LEARNER_TEXT, LEARNER_AUDIO |
| status | VARCHAR(20) | PENDING, RUNNING, SUCCEEDED, FAILED, UNKNOWN |
| ai_request_id | BIGINT? FK → ai_requests, UQ | Tác vụ CHAT xử lý lượt; chưa dispatch có thể NULL |
| processing_expires_at | DATETIME(6)? | Mốc lease cần kiểm tra lại; hết hạn không tự chứng minh provider chưa xử lý |
| created_at | DATETIME(6) | Tiếp nhận lượt |
| completed_at | DATETIME(6)? | Kết thúc xử lý; UNKNOWN có thể chưa có |
| version | BIGINT | Optimistic lock; phối hợp cùng session lock |

### messages

| Trường | Kiểu/ràng buộc | Vai trò |
| --- | --- | --- |
| id | BIGINT PK, tự tăng | Định danh tin nhắn |
| session_id | BIGINT FK → practice_sessions | Hỗ trợ truy vấn/FK cùng buổi; phải khớp turn |
| turn_id | BIGINT FK → conversation_turns | Lượt nguồn; UQ(turn_id, sender) |
| sender | VARCHAR(20) | LEARNER hoặc ASSISTANT; không lưu system instruction như message người dùng |
| content | TEXT | Nội dung cuối được chấp nhận; giới hạn độ dài do API/service |
| input_type | VARCHAR(20) | TEXT, AUDIO, GENERATED |
| audio_submission_id | BIGINT? FK → audio_submissions, UQ | Chỉ LEARNER/AUDIO; dùng FK ghép để khớp session |
| created_at | DATETIME(6) | Lúc chấp nhận/lưu câu; không phải khóa thứ tự duy nhất |

### audio_submissions

| Trường | Kiểu/ràng buộc | Vai trò |
| --- | --- | --- |
| id | BIGINT PK, tự tăng | Lần gửi âm thanh |
| session_id | BIGINT? FK → practice_sessions | Bắt buộc khi sử dụng; chỉ NULL sau xóa buổi khi DELETION_PENDING |
| client_request_id | VARCHAR(64) | Chống trùng upload trong session, độc lập key gửi câu CHAT |
| request_fingerprint | CHAR(64) | HMAC trên dấu vân tay của WAV để đối chiếu lần gửi lại |
| status | VARCHAR(20) | TRANSCRIBING, READY, CONFIRMED, FAILED, EXPIRED, DELETION_PENDING |
| storage_key | CHAR(36)? UQ | UUID file tạm local, giữ tới khi dọn thành công |
| mime_type | VARCHAR(32) | Kiểu file được kiểm tra nội dung, hiện dùng audio/wav |
| size_bytes | INT | Kích thước từ 46 đến 1.920.044 byte |
| duration_ms | INT | Thời lượng > 0 được kiểm tra |
| draft_transcript | TEXT? | Bản chép lời chưa xác nhận; dọn khi không cần/đã xóa buổi |
| ai_request_id | BIGINT FK → ai_requests, UQ | Tác vụ TRANSCRIPTION |
| expires_at | DATETIME(6) | TTL file audio: upload + 1 giờ theo ADR 0006; xóa sớm khi không còn cần, không phải retention provider |
| transcript_expires_at | DATETIME(6)? | Đặt READY + 30 phút cho transcript chưa xác nhận; khác TTL file. Xóa transcript khi xác nhận/hủy/kết thúc sớm hơn |
| confirmed_at | DATETIME(6)? | Người học xác nhận nội dung cuối |
| audio_deleted_at | DATETIME(6)? | Thời điểm file tạm phía ứng dụng đã được dọn |
| created_at | DATETIME(6) | Tiếp nhận |

WAV gửi inline, không có tham chiếu Files API cần theo dõi. Worker đọc DELETION_PENDING kể cả khi session đã bị xóa và chỉ xóa metadata dọn file sau khi xử lý xong. Bản có session NULL đã xóa transcript và nằm ngoài phạm vi API người học.

## Phản hồi

Ba bảng phản hồi đã triển khai bằng V6. session_feedback dùng ai_request_id bắt buộc ngay từ lúc tạo. Trạng thái UNKNOWN cho lần gọi chưa rõ kết quả. Mỗi feedback item dùng hai FK ghép cùng buổi. Các FK dùng RESTRICT để xử lý xóa theo thứ tự ở giai đoạn lịch sử.

### session_feedback

| Trường | Kiểu/ràng buộc | Vai trò |
| --- | --- | --- |
| id | BIGINT PK, tự tăng | Định danh phản hồi |
| session_id | BIGINT FK → practice_sessions, UQ | Tối đa một bản tổng hợp/buổi |
| status | VARCHAR(20) | PENDING, RUNNING, SUCCEEDED, FAILED |
| summary | TEXT? | Nhận xét chung; chưa có khi đang tạo |
| strengths | JSON? | Mảng điểm làm tốt đã validate |
| goal_assessment | TEXT? | Nhận xét mục tiêu tình huống; không phải điểm CEFR |
| next_practice | TEXT? | Gợi ý luyện tiếp |
| ai_request_id | BIGINT? FK → ai_requests, UQ | Tác vụ FEEDBACK; retry dùng cùng tác vụ, thêm attempt |
| generated_at | DATETIME(6)? | Hoàn tất kết quả hợp lệ |
| created_at | DATETIME(6) | Tạo bản ghi xử lý |
| updated_at | DATETIME(6) | Cập nhật gần nhất |

### feedback_items

| Trường | Kiểu/ràng buộc | Vai trò |
| --- | --- | --- |
| id | BIGINT PK, tự tăng | Từng mục sửa/gợi ý |
| feedback_id | BIGINT FK → session_feedback | Bản tổng hợp sở hữu |
| session_id | BIGINT | Dùng trong hai FK ghép cùng feedback và source message |
| source_message_id | BIGINT FK → messages | Câu LEARNER nguồn phải cùng session |
| item_type | VARCHAR(20) | ERROR hoặc IMPROVEMENT; không đếm improvement là lỗi |
| category | VARCHAR(20) | GRAMMAR, VOCABULARY, EXPRESSION |
| rule_code | VARCHAR(80)? | Mã allowlist của backend cho nhóm lỗi chi tiết; AI không tự đặt taxonomy |
| original_text | TEXT | Đoạn trích phải kiểm tra có trong câu nguồn |
| suggested_text | TEXT | Câu/đoạn sửa hoặc diễn đạt tốt hơn |
| explanation_vi | TEXT | Giải thích cho người học |
| sort_order | INT | Thứ tự hiển thị |

### feedback_suggestions

| Trường | Kiểu/ràng buộc | Vai trò |
| --- | --- | --- |
| id | BIGINT PK, tự tăng | Định danh đề xuất ôn |
| feedback_id | BIGINT FK → session_feedback | Nguồn đề xuất |
| item_type | VARCHAR(20) | VOCABULARY hoặc PHRASE |
| expression | TEXT | Từ/cụm từ/mẫu câu nên học |
| meaning_vi | TEXT | Nghĩa/diễn giải tiếng Việt |
| example_en | TEXT? | Ví dụ tiếng Anh |
| sort_order | INT | Thứ tự hiển thị |

## Ôn tập

V7 triển khai thư viện độc lập với nguồn. Cột client_request_id trong review_attempts chống ghi trùng theo thẻ và giữ kết quả đổi trạng thái nhất quán. Xóa thẻ CASCADE attempt. Xóa suggestion SET NULL liên kết thẻ.

### review_items

| Trường | Kiểu/ràng buộc | Vai trò |
| --- | --- | --- |
| id | BIGINT PK, tự tăng | Nội dung người học đã lưu |
| user_id | BIGINT FK → users | Chủ sở hữu; không lấy quyền từ source_suggestion_id |
| source_suggestion_id | BIGINT? FK → feedback_suggestions | Nguồn tham khảo; SET NULL khi xóa lịch sử; UQ cùng user khi có giá trị |
| item_type | VARCHAR(20) | VOCABULARY hoặc PHRASE |
| expression | TEXT | Bản sao nội dung để độc lập nguồn |
| meaning_vi | TEXT | Bản sao nghĩa tiếng Việt |
| example_en | TEXT? | Bản sao ví dụ |
| status | VARCHAR(20) | NEEDS_REVIEW hoặc REMEMBERED |
| last_reviewed_at | DATETIME(6)? | Lần ôn mới nhất |
| next_review_at | DATETIME(6)? | Mốc ôn tiếp theo nếu áp dụng quy tắc đơn giản |
| created_at | DATETIME(6) | Người học lưu |
| updated_at | DATETIME(6) | Cập nhật nội dung/trạng thái |

Backend kiểm tra suggestion thuộc người học trước khi sao chép. Nhiều review item không có source được phép tồn tại; không đặt UNIQUE(expression) toàn hệ thống. Chưa bổ sung endpoint tự tạo thẻ ngoài phạm vi yêu cầu.

### review_attempts

| Trường | Kiểu/ràng buộc | Vai trò |
| --- | --- | --- |
| id | BIGINT PK, tự tăng | Một lần ôn |
| review_item_id | BIGINT FK → review_items | Thẻ được ôn; user suy ra qua thẻ |
| review_mode | VARCHAR(20) | Bản đầu chỉ FLASHCARD theo ADR 0005; FILL_BLANK dành cho giai đoạn sau, không coi đã có UI |
| result | VARCHAR(20) | REMEMBERED hoặc NEEDS_REVIEW |
| reviewed_at | DATETIME(6) | Thời điểm ôn; không bắt buộc giữ câu trả lời thô |

## Vận hành

### ai_requests

| Trường | Kiểu/ràng buộc | Vai trò |
| --- | --- | --- |
| id | BIGINT PK, tự tăng | Tác vụ nghiệp vụ logic, không phải một HTTP attempt |
| user_id | BIGINT FK → users | Người phát sinh usage; xóa tài khoản cần chính sách riêng |
| session_id | BIGINT? FK → practice_sessions | Bối cảnh; SET NULL khi xóa buổi, không giữ nội dung trong log |
| task_type | VARCHAR(20) | CHAT, TRANSCRIPTION, FEEDBACK, HINT; HINT không tạo thêm turn/message |
| operation_key | VARCHAR(128) | Khóa do server tạo/chuẩn hóa ổn định; UQ cùng user và task |
| status | VARCHAR(20) | PENDING, RUNNING, SUCCEEDED, FAILED, UNKNOWN |
| prompt_version | VARCHAR(80) | Phiên bản template/hướng dẫn, không phải prompt có transcript |
| created_at | DATETIME(6) | Tiếp nhận tác vụ |
| finished_at | DATETIME(6)? | Kết thúc tác vụ |

Đối tượng nghiệp vụ giữ FK ngược tới ai_request. Nhờ đó log không cần FK đa hình hoặc chứa bản sao nội dung. Kiểm tra task/owner/session khớp ở backend; UNKNOWN của attempt phải được xử lý trước khi kết luận hoặc retry tác vụ.

Riêng HINT gắn session, chỉ trả nội dung gợi ý tạm cho frontend, không có FK kết quả tới turn/audio/feedback và không lưu response vào log. Tác vụ này vẫn cần idempotency/hạn mức API; không tính vào 20 lượt LEARNER.

### ai_attempts

| Trường | Kiểu/ràng buộc | Vai trò |
| --- | --- | --- |
| id | BIGINT PK, tự tăng | Lần gọi provider |
| ai_request_id | BIGINT FK → ai_requests | Tác vụ sở hữu |
| attempt_no | INT | Tăng từ 1, UQ cùng ai_request |
| model_name | VARCHAR(150) | Model thực tế, không suy từ config hiện tại sau này |
| status | VARCHAR(20) | RUNNING, SUCCEEDED, FAILED, UNKNOWN |
| provider_request_id | VARCHAR(255)? | ID provider nếu API có, không giả định provider idempotency |
| input_tokens | BIGINT? | Token đầu vào được báo; chưa biết để NULL |
| output_tokens | BIGINT? | Token đầu ra được báo; chưa biết để NULL |
| latency_ms | BIGINT? | Thời gian của attempt, không phải tổng thời gian người học |
| estimated_cost | DECIMAL(18,8)? | Ước tính USD theo quy ước thiết kế; không phải hóa đơn và không gán 0 khi chưa biết |
| pricing_reference | VARCHAR(255)? | Revision/thời điểm/bảng giá dùng tính USD; cần đủ căn cứ hoặc để cost NULL |
| error_code | VARCHAR(100)? | Mã lỗi an toàn; không chứa response/prompt thô |
| started_at | DATETIME(6) | Bắt đầu gọi |
| finished_at | DATETIME(6)? | Kết thúc hoặc timeout |

Nếu hỗ trợ tiền tệ khác phải thêm currency và sửa quy ước; không trộn đơn vị trong một cột. Token không âm khi có, attempt_no > 0. UNKNOWN có thể vẫn phát sinh chi phí; tách thời gian chờ user khỏi latency provider.

### admin_audit_logs

| Trường | Kiểu/ràng buộc | Vai trò |
| --- | --- | --- |
| id | BIGINT PK, tự tăng | Sự kiện quản trị |
| actor_user_id | BIGINT? FK → users | Người thực hiện; nullable khi có chính sách xóa actor phù hợp |
| action | VARCHAR(80) | USER_ACCESS_UPDATED, TOPIC_CREATED, TOPIC_UPDATED, SCENARIO_CREATED hoặc SCENARIO_UPDATED |
| target_type | VARCHAR(40) | Loại đối tượng bị tác động |
| target_id | BIGINT | ID đối tượng lịch sử; không phải FK đa hình tới nhiều bảng |
| safe_metadata | JSON? | Dữ liệu truy vết đã lọc, không chứa hội thoại/mật khẩu/key/PII không cần thiết |
| created_at | DATETIME(6) | Lúc thao tác thành công |

V10 ghi audit cùng transaction thay đổi. Các trường quyền/trạng thái trước và sau chỉ áp dụng cho USER_ACCESS_UPDATED. Tình huống và chủ đề chỉ ghi hành động và ID.

## Hạ tầng Spring Session JDBC

Theo [ADR 0004](../decisions/0004-session-authentication.md), giữ schema MySQL của đúng phiên bản thư viện; không viết JPA entity cho hai bảng này. Dưới đây đối chiếu schema 4.1.1 được BOM Spring Boot hiện tại quản lý, chưa cài/chạy trong ứng dụng. Không áp dụng quy ước snake_case, ID Long và DATETIME của bảng nghiệp vụ vào đây.

### SPRING_SESSION

| Trường | Kiểu/ràng buộc | Vai trò |
| --- | --- | --- |
| PRIMARY_ID | CHAR(36) PK | Khóa nội bộ bất biến để attributes tham chiếu |
| SESSION_ID | CHAR(36) UQ | Định danh phiên có thể thay khi chống fixation; cookie opaque do thư viện ánh xạ, không tự coi giá trị cookie là raw DB ID |
| CREATION_TIME | BIGINT | Thời điểm tạo, epoch milliseconds |
| LAST_ACCESS_TIME | BIGINT | Truy cập gần nhất, epoch milliseconds |
| MAX_INACTIVE_INTERVAL | INT | Khoảng bất hoạt tối đa, đơn vị giây theo ngữ nghĩa thư viện |
| EXPIRY_TIME | BIGINT | Mốc hết hạn, epoch milliseconds; có index phục vụ cleanup |
| PRINCIPAL_NAME | VARCHAR(100)? | Lookup người dùng theo chuỗi users.id trong thiết kế principal; không FK, NULL được phép cho anonymous session |

### SPRING_SESSION_ATTRIBUTES

| Trường | Kiểu/ràng buộc | Vai trò |
| --- | --- | --- |
| SESSION_PRIMARY_ID | CHAR(36) PK/FK | Tham chiếu SPRING_SESSION.PRIMARY_ID, ON DELETE CASCADE |
| ATTRIBUTE_NAME | VARCHAR(200) PK | Tên thuộc tính; PK ghép SESSION_PRIMARY_ID/ATTRIBUTE_NAME |
| ATTRIBUTE_BYTES | BLOB | Giá trị thuộc tính được thư viện serialize, gồm context xác thực/CSRF khi cấu hình cần; không lưu entity JPA, credentials, key hoặc hội thoại |

Đổi SESSION_ID không đổi khóa liên kết attributes là PRIMARY_ID. Không log ID phiên hoặc nội dung BLOB. Logout hết phiên đăng nhập không xóa practice_sessions; đây là hai khái niệm session khác nhau.
