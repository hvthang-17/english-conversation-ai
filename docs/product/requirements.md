# Yêu cầu sản phẩm

G4 dùng Azure Speech Text to Speech F0. G5 chọn Azure Pronunciation Assessment để chấm theo mẫu. G6 dùng Azure UNSCRIPTED en-US mặc định cho lượt VOICE đủ điều kiện khi tính năng được bật. [ADR 0016](../decisions/0016-g6-direct-voice-submission.md) [ADR 0014](../decisions/0014-azure-speech-tts-g4.md) · [ADR 0015](../decisions/0015-azure-pronunciation-assessment-g5.md)

Thiết kế dữ liệu cho 10 nhóm chức năng bản đầu nằm trong [ERD 46 bảng đã chốt](../architecture/database.md#2-erd-logic). Phạm vi học liệu mẫu dùng TTS theo [ADR 0012](../decisions/0012-system-database-scope.md).

Backlog, thứ tự ưu tiên và tiêu chí nghiệm thu của kế hoạch từ đầu nằm trong [kế hoạch 10 nhóm chức năng](../development/full-system-implementation-plan.md). Các mã bên dưới mô tả hành vi và tiêu chí nghiệm thu của sản phẩm.

**Quy tắc sử dụng email cho bản đầu.** Đăng ký không có bước xác minh email riêng. Người học đăng nhập và sử dụng các chức năng theo quyền và trạng thái tài khoản. Khi quên mật khẩu, hệ thống gửi liên kết một lần đến email đã đăng ký để người học đặt lại mật khẩu. Email nhắc học chỉ gửi khi người học chủ động bật. Worker kiểm tra trạng thái tài khoản, phiên bản lịch và hạn gửi trước khi gọi SMTP.

Bằng chứng cho bản dựng sạch được ghi tại [P0](../development/phase0/README.md). Các nhóm nghiệp vụ sẽ được nghiệm thu trên dòng Flyway mới theo kế hoạch hiện tại.

**Khám phá bài học ở G2.** Người học tìm theo tên và tóm tắt, hỗ trợ tiếng Việt không dấu, rồi lọc kết hợp theo chủ đề và cấp độ. Catalog chỉ hiện bài đang công khai thuộc chủ đề đang hoạt động. Khi tình huống gợi ý bị ẩn, bài vẫn đọc được nếu đủ điều kiện công khai. Yêu thích và Học sau là hai dấu độc lập của từng tài khoản. Bài bị ẩn hiển thị trạng thái **Không còn khả dụng** trong danh sách đã lưu và chủ sở hữu vẫn có thể bỏ dấu. [Kế hoạch và bộ bài mẫu G2](../development/full-system-implementation-plan.md#g2--quản-lý-và-khám-phá-nội-dung).

**Học liệu và ôn tập ở G3.** Mỗi bài trong tám bài G2 có ba từ, hai mẫu câu và một hội thoại mẫu 4–6 lượt. TTS cục bộ đọc văn bản tiếng Anh ở 1× hoặc 0,75×, có nghe cả đoạn và nghe lại từng câu. Mặc định hiện lời tiếng Anh, ẩn bản dịch và cho bật/tắt riêng. IPA dùng tra cứu. [Quyết định và tiêu chí G3](../development/full-system-implementation-plan.md#g3--từ-vựng-mẫu-câu-hội-thoại-mẫu-và-ôn-tập).

**Luyện nghe ở G4.** Tám đề A2/B1 có kịch bản mới và audio TTS từ backend. Người học nghe lại tự do ở 1× hoặc 0,75×. Đáp án nháp lưu trên server để tiếp tục trên thiết bị khác. Sau khi nộp, người học xem điểm, lời thoại, bản dịch nếu có, đáp án và giải thích. [Quyết định và tiêu chí G4](../development/full-system-implementation-plan.md#g4--luyện-nghe-phục-vụ-giao-tiếp).

| Mã | Yêu cầu | Điều kiện nghiệm thu chính |
| --- | --- | --- |
| AUTH-01 | Đăng ký, đăng nhập, đăng xuất và đổi mật khẩu | Email trim/không phân biệt hoa thường và UNIQUE cùng quy tắc; mật khẩu băm; Session JDBC/MySQL idle 30 phút, nhiều thiết bị; đổi mã phiên khi login; logout phiên hiện tại; CSRF; đổi mật khẩu thu hồi phiên cũ; quên mật khẩu theo AUTH-03 |
| PROFILE-01 | Hồ sơ học tập | Người học chỉ sửa hồ sơ của mình; chọn A2/B1, mục tiêu và chủ đề quan tâm; thay đổi trình độ không sửa lịch sử buổi cũ |
| SCENARIO-01 | Danh sách tình huống | Lọc theo chủ đề/trình độ; chỉ chọn tình huống đang hoạt động; chọn vai trước khi bắt đầu nếu tình huống có nhiều lựa chọn. Vai, bối cảnh và mục tiêu được chụp theo buổi |
| CHAT-01 | Hội thoại văn bản | AI mở đầu đúng vai; tối đa 20 lượt LEARNER được chấp nhận, opening không tính; một lượt đang xử lý/buổi; chống trùng; có gợi ý không thêm lượt/câu vào transcript chính; lỗi AI không hiển thị là thành công |
| VOICE-01 | Luyện nghe và nói theo lượt | Chọn và lưu chế độ theo buổi. Ghi âm là thao tác chính; có thể chủ động mở ô gõ cho một lượt. Bản ghi tối đa 60 giây, kiểm tra server-side, có nghe lại và gửi trực tiếp. Gemini chép lời sau khi gửi. Học viên xem, sửa và xác nhận bản chép trước khi tạo lượt. Sau xác nhận, Azure nhận audio gốc còn hạn khi tính năng chấm được bật và bản ghi dài tối đa 30 giây. Điểm hiện ở từng lượt và tổng kết. Audio TTL 1 giờ, nháp TTL 30 phút từ READY |
| PLAYBACK-01 | Nghe câu AI | Phát/dừng, nghe lại và nghe chậm bằng giọng tiếng Anh cục bộ. Chat văn bản hiển thị nội dung ngay. Luyện nghe và nói ẩn câu AI theo từng lượt, có phụ đề theo yêu cầu. Sau buổi, hiển thị toàn bộ hội thoại |
| FEEDBACK-01 | Phản hồi cuối buổi | Tách câu sai với đề xuất tự nhiên hơn; câu gốc phải có trong hội thoại; giải thích tiếng Việt; kết quả chấm phát âm hiển thị riêng khi có; lỗi phân tích có thể thử lại mà không mất hội thoại |
| REVIEW-01 | Sổ tay, flashcard và quiz chọn nghĩa | Lưu bản sao từ/mẫu câu, nghĩa, ví dụ tiếng Anh và IPA theo người học và mục nguồn. Bản sao vẫn ôn được sau khi nguồn bị ẩn hoặc xóa. FLASHCARD ghi trạng thái tự đánh dấu. QUICK_QUIZ chọn nghĩa tiếng Việt, chấm phía backend và ghi đúng/sai cùng thời điểm ôn. Gửi lại cùng mã chỉ tạo một lượt. Xóa thẻ dọn cả lượt ôn; xóa dữ liệu học tập dọn cả sổ tay. Lịch ôn tự động và điền từ thuộc các giai đoạn sau. |
| HISTORY-01 | Xem/xóa lịch sử | Phân trang, mới nhất trước; chỉ xem dữ liệu của mình; không trả nội dung khi truy cập chéo; xóa buổi giữ thẻ ôn đã lưu và gỡ nguồn; xóa toàn bộ dữ liệu học tập xóa cả thẻ/lịch sử ôn; thông báo rõ phạm vi |
| STATS-01 | Thống kê hoạt động | Số buổi hoàn thành, lượt người học, ngày luyện và nhóm lỗi lấy từ dữ liệu đã lưu. Buổi mới kết thúc chỉ tính hoàn thành nếu có ít nhất một lượt người học được AI trả lời thành công. Thống kê giữ trạng thái của các buổi đã đóng trước cập nhật |
| ADMIN-01 | Quản trị | Chỉ admin khóa/mở tài khoản, thêm/sửa/ẩn chủ đề/tình huống; tài khoản khóa không tiếp tục truy cập chức năng bảo vệ; thay đổi tình huống không làm sai lịch sử |
| PRIVACY-01 | Bảo vệ dữ liệu | Thông báo xử lý âm thanh bên ngoài; không lưu lâu dài âm thanh gốc; khóa API chỉ ở server; log không chứa bí mật hoặc hội thoại thô |
| RESILIENCE-01 | Giới hạn và lỗi | Kiểm tra độ dài, kích thước/thời lượng, hạn mức ở backend; lỗi mạng, timeout, quá hạn mức có thông báo; retry có giới hạn |

## Quy tắc nghiệp vụ và điều kiện nghiệm thu
- AUTH-01: Session theo ADR 0004, idle 30 phút và quy tắc email theo ADR 0005 đã chốt; kiểm tra status ở request tiếp theo. Collation phải hiện thực đúng trim/case-insensitive. Admin đầu tiên lấy mật khẩu từ môi trường, khởi tạo có kiểm soát; không có ADMIN signup hoặc mật khẩu cố định trong Git.
- HISTORY-01: ADR 0003 giữ thẻ khi xóa buổi; ADR 0006 chốt log 30 ngày/backup hằng ngày giữ 7 ngày. Retention provider phải xác minh theo gói, không hứa cùng mốc local. Không chỉ soft delete để thực hiện xóa nội dung.
- RESILIENCE-01: tối đa 20 lượt và audio 60 giây đã chốt. HINT vẫn tiêu thụ quota API dù không tính lượt; mỗi đợt nghiệm thu thật cần xác nhận model, quota và phạm vi gọi.
- FEEDBACK-01: không có lỗi cũng là kết quả hợp lệ. Tổng kết hiển thị tối đa ba nhóm lỗi lặp trên ít nhất hai câu khác nhau, dùng lại nhận xét đã lưu.
- STATS-01: ngày luyện cần quy ước múi giờ, mặc định thiết kế Asia/Ho_Chi_Minh; thời gian lưu trữ dùng UTC.
- CHAT-01: lượt retry không tạo thêm tin nhắn. Người học có thể yêu cầu giải thích câu AI gần nhất. Kết quả được lưu theo câu và không tính lượt. Khóa trùng khác nội dung bị từ chối. AI trả muộn sau khi xóa buổi không được tạo lại dữ liệu.
- VOICE-01: transcript chưa xác nhận không thành tin nhắn; dọn audio ngoài database phải có trạng thái để thử lại khi thất bại.

Quy tắc nền theo [ADR 0005](../decisions/0005-initial-product-rules.md). [ADR 0006](../decisions/0006-phase0-operating-decisions.md) ghi [chính sách dữ liệu/browser](data-and-browser-proposal.md), audio 60 giây và các giả định của đợt trước. Kế hoạch hiện tại bàn giao đủ 10 nhóm và đánh giá tiến độ theo tiêu chí nghiệm thu. Dùng thử dự kiến khoảng 10 người.

## Ánh xạ yêu cầu sang dữ liệu

Tra đặc tả dữ liệu, ràng buộc và luồng triển khai tại [thiết kế MySQL 46 bảng](../architecture/database.md).

| Yêu cầu | Bảng chính |
| --- | --- |
| AUTH-01 | users, SPRING_SESSION, SPRING_SESSION_ATTRIBUTES; không dùng JWT/refresh token |
| AUTH-03 | account_action_tokens, account_mail_outbox, account_rate_limits |
| PROFILE-02 | learner_profiles.avatar_storage_key; file avatar trong storage riêng |
| SCHEDULE-01 / REMINDER-01 | practice_schedules, practice_schedule_days, practice_reminder_deliveries |
| ACCOUNT-DELETE-01 | users.deletion_requested_at, account_deletion_jobs; dữ liệu owner và file được dọn trước xóa users |
| PROFILE-01 | learner_profiles, user_topic_interests, topics |
| SCENARIO-01 | topics, scenarios, scenario_levels |
| CHAT-01 | practice_sessions, conversation_turns, messages |
| VOICE-01 | audio_submissions, messages; tác vụ TRANSCRIPTION/CHAT riêng |
| PLAYBACK-01 | messages.content; speechSynthesis không yêu cầu bảng audio đầu ra |
| FEEDBACK-01 | session_feedback, feedback_items, feedback_suggestions |
| REVIEW-01 | review_items, review_attempts |
| HISTORY-01 | practice_sessions và dữ liệu con; giữ thẻ đã lưu khi chỉ xóa buổi |
| STATS-01 | Tổng hợp session, messages, feedback và review; chưa có bảng thống kê riêng |
| ADMIN-01 | users, catalog, admin_audit_logs |
| PRIVACY-01 | Ownership, snapshot/log có giới hạn, vòng đời audio và chính sách xóa |
| RESILIENCE-01 | conversation_turns, ai_requests, ai_attempts; quota còn cần điều phối nguyên tử |

## Đánh giá dự kiến
Chuẩn bị 30–50 đoạn hội thoại đúng/sai để đánh giá vai trò, trình độ và sửa lỗi; người đủ năng lực tiếng Anh đánh giá. Dùng thử khoảng 10–15 người để thu phản hồi ban đầu. Đây là quy mô đề xuất, chưa có kết quả thử nghiệm.

## Phạm vi mở rộng — tài khoản và hồ sơ

Phạm vi bản đầu gồm AUTH-03 (khôi phục mật khẩu), PROFILE-02 (ảnh đại diện), SCHEDULE-01 (lịch luyện tập dự kiến), REMINDER-01 (nhắc học qua email) và ACCOUNT-DELETE-01 (xóa tài khoản). AUTH-02 ghi quyết định về xác minh email độc lập. Các phần tái sử dụng từ mã hiện có cần được kiểm tra theo kế hoạch G1 và G8.

Các quy tắc bên dưới áp dụng cho bản đầu. Lịch và avatar nằm trong Hồ sơ, xóa tài khoản nằm trong Tài khoản. Nhắc qua SMTP không tạo buổi hoặc gọi Gemini. [Hợp đồng của mã hiện tại](../api/auth.md).

| Mã | Phạm vi và điều kiện nghiệm thu |
| --- | --- |
| AUTH-02 | Bỏ xác minh email độc lập khỏi bản đầu. Đăng ký xong có thể đăng nhập. Sử dụng AI và cài nhắc học áp dụng theo quyền, trạng thái tài khoản và quy tắc của từng chức năng. |
| AUTH-03 | Quên mật khẩu trả thông báo chung dù email không tồn tại hoặc tài khoản bị khóa. Liên kết một lần cho phép đặt mật khẩu theo quy tắc AUTH-01; thành công thu hồi mọi phiên và mọi token khôi phục còn lại. Người học đăng nhập lại bằng mật khẩu mới. |
| PROFILE-02 | Tải lên, thay và xóa ảnh đại diện của chính mình. JPEG/PNG/WebP tối đa 2 MiB, tối đa 4096 × 4096 pixel; backend kiểm tra chữ ký, giải mã và giới hạn pixel trước khi xử lý, mã hóa lại ảnh tối đa 512 × 512, bỏ metadata. Từ chối SVG/GIF, file hỏng, nội dung giả MIME và ảnh quá giới hạn. Không nhận URL ảnh bên ngoài. |
| SCHEDULE-01 | Trong Hồ sơ, chọn ngày T2–CN, một giờ địa phương chung cho các ngày, múi giờ IANA (mặc định Asia/Ho_Chi_Minh), bật/tắt lịch và riêng email nhắc học. Một lịch/tài khoản; bật lịch cần ít nhất một ngày. Mục tiêu phút dùng daily_goal_minutes hiện có; không bắt buộc để tạo lịch. Đây là lịch dự kiến, không tự tạo buổi hoặc gọi Gemini. |
| REMINDER-01 | Người dùng chủ động bật email; gửi tới địa chỉ của tài khoản ACTIVE và không chờ xóa, khi đến giờ. Kiểm tra lại phiên bản lịch và hạn gửi. Tác vụ chạy khi server hoạt động. Thư chứa đường dẫn tới trang chọn bài và Hồ sơ để tắt nhắc sau đăng nhập. |
| ACCOUNT-DELETE-01 | Trang Tài khoản hiển thị phạm vi mất dữ liệu, yêu cầu mật khẩu hiện tại và xác nhận rõ. Chấp nhận yêu cầu thì chặn truy cập, thu hồi mọi phiên/token, hủy thư đang chờ và vô hiệu lịch ngay. Job xóa có thể thử lại, không báo hoàn tất khi file/provider còn chờ dọn. Xóa tài khoản khác chỉ bằng ID client bị từ chối; không mở API xóa hộ cho admin. |

Chi tiết token, TTL và rate limit nằm trong [luồng người dùng](user-flows.md); SMTP/retry trong [backend](../architecture/backend.md); phạm vi xóa/retention trong [chính sách dữ liệu](data-and-browser-proposal.md). Giới hạn kiểm chứng thực tế được ghi tại testing.md.
