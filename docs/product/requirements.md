# Yêu cầu sản phẩm

AUTH-01 và nền tảng phân quyền ADMIN-01 đã triển khai ở [giai đoạn 1](../development/phase1/README.md). Hồ sơ và catalog thuộc [giai đoạn 2](../development/phase2/README.md), hội thoại văn bản thuộc [giai đoạn 3](../development/phase3/README.md), phản hồi thuộc [giai đoạn 4](../development/phase4/README.md) và ôn tập/lịch sử/thống kê thuộc [giai đoạn 5](../development/phase5/README.md).

Hai chế độ hội thoại, ghi âm, phát câu AI, thông báo và dọn dữ liệu âm thanh đã triển khai. Kiểm chứng Gemini thật và nghiệm thu browser matrix còn mở. [Giai đoạn 6](../development/phase6/README.md) · [ADR 0009](../decisions/0009-practice-modes.md)

ADMIN-01 có màn hình quản lý tài khoản, danh mục, số liệu tổng hợp và audit V10. [Giai đoạn 7](../development/phase7/README.md)

| Mã | Yêu cầu | Điều kiện nghiệm thu chính |
| --- | --- | --- |
| AUTH-01 | Đăng ký, đăng nhập, đăng xuất và đổi mật khẩu | Email trim/không phân biệt hoa thường và UNIQUE cùng quy tắc; mật khẩu băm; Session JDBC/MySQL idle 30 phút, nhiều thiết bị; đổi mã phiên khi login; logout phiên hiện tại; CSRF; đổi mật khẩu thu hồi phiên cũ; bản đầu chưa xác minh email/quên mật khẩu |
| PROFILE-01 | Hồ sơ học tập | Người học chỉ sửa hồ sơ của mình; chọn A2/B1, mục tiêu và chủ đề quan tâm; thay đổi trình độ không sửa lịch sử buổi cũ |
| SCENARIO-01 | Danh sách tình huống | Lọc theo chủ đề/trình độ; chỉ chọn tình huống đang hoạt động; mỗi tình huống có vai trò và mục tiêu |
| CHAT-01 | Hội thoại văn bản | AI mở đầu đúng vai; tối đa 20 lượt LEARNER được chấp nhận, opening không tính; một lượt đang xử lý/buổi; chống trùng; có gợi ý không thêm lượt/câu vào transcript chính; lỗi AI không hiển thị là thành công |
| VOICE-01 | Luyện nghe và nói theo lượt | Chọn và lưu chế độ theo buổi. Ghi âm là thao tác chính; có thể chủ động mở ô gõ cho một lượt. Bản ghi tối đa 60 giây, kiểm tra server-side, có nghe lại và xác nhận bản chép lời. Audio TTL 1 giờ, transcript nháp 30 phút từ READY theo ADR 0006 |
| PLAYBACK-01 | Nghe câu AI | Phát/dừng, nghe lại và nghe chậm bằng giọng tiếng Anh cục bộ. Chat văn bản hiển thị nội dung ngay. Luyện nghe và nói ẩn câu AI theo từng lượt, có phụ đề theo yêu cầu. Sau buổi, hiển thị toàn bộ hội thoại |
| FEEDBACK-01 | Phản hồi cuối buổi | Tách câu sai với đề xuất tự nhiên hơn; câu gốc phải có trong hội thoại; giải thích tiếng Việt; không chấm phát âm/CEFR; lỗi phân tích có thể thử lại mà không mất hội thoại |
| REVIEW-01 | Lưu và ôn tập bằng thẻ | Chọn lưu từ/mẫu câu; xem nghĩa/ví dụ; đánh dấu cần ôn/đã nhớ và ghi lịch sử ôn FLASHCARD; owner-only; lưu cùng đề xuất không trùng; điền từ/lịch ôn tự động để sau |
| HISTORY-01 | Xem/xóa lịch sử | Phân trang, mới nhất trước; chỉ xem dữ liệu của mình; không trả nội dung khi truy cập chéo; xóa buổi giữ thẻ ôn đã lưu và gỡ nguồn; xóa toàn bộ dữ liệu học tập xóa cả thẻ/lịch sử ôn; thông báo rõ phạm vi |
| STATS-01 | Thống kê hoạt động | Số buổi hoàn thành, lượt người học, ngày luyện và nhóm lỗi lấy từ dữ liệu đã lưu; buổi lỗi không được tính hoàn thành |
| ADMIN-01 | Quản trị | Chỉ admin khóa/mở tài khoản, thêm/sửa/ẩn chủ đề/tình huống; tài khoản khóa không tiếp tục truy cập chức năng bảo vệ; thay đổi tình huống không làm sai lịch sử |
| PRIVACY-01 | Bảo vệ dữ liệu | Thông báo xử lý âm thanh bên ngoài; không lưu lâu dài âm thanh gốc; khóa API chỉ ở server; log không chứa bí mật hoặc hội thoại thô |
| RESILIENCE-01 | Giới hạn và lỗi | Kiểm tra độ dài, kích thước/thời lượng, hạn mức ở backend; lỗi mạng, timeout, quá hạn mức có thông báo; retry có giới hạn |

## Quy tắc cần làm rõ trước khi triển khai
- AUTH-01: Session theo ADR 0004, idle 30 phút và quy tắc email theo ADR 0005 đã chốt; kiểm tra status ở request tiếp theo. Collation phải hiện thực đúng trim/case-insensitive. Admin đầu tiên lấy mật khẩu từ môi trường, khởi tạo có kiểm soát; không có ADMIN signup hoặc mật khẩu cố định trong Git.
- HISTORY-01: ADR 0003 giữ thẻ khi xóa buổi; ADR 0006 chốt log 30 ngày/backup hằng ngày giữ 7 ngày. Retention provider phải xác minh theo gói, không hứa cùng mốc local. Không chỉ soft delete để thực hiện xóa nội dung.
- RESILIENCE-01: tối đa 20 lượt và audio 60 giây đã chốt. HINT vẫn tiêu thụ quota API dù không tính lượt; model/quota và ngân sách live còn mở, hiện chưa cho gọi thật.
- FEEDBACK-01: không có lỗi cũng là kết quả hợp lệ; không ép AI tạo lỗi.
- STATS-01: ngày luyện cần quy ước múi giờ, mặc định thiết kế Asia/Ho_Chi_Minh; thời gian lưu trữ dùng UTC.
- CHAT-01: lượt retry không tạo thêm tin nhắn; khóa trùng khác nội dung bị từ chối. AI trả muộn sau khi xóa buổi không được tạo lại dữ liệu.
- VOICE-01: transcript chưa xác nhận không thành tin nhắn; dọn audio ngoài database phải có trạng thái để thử lại khi thất bại.

Quy tắc trong ảnh theo [ADR 0005](../decisions/0005-initial-product-rules.md). [ADR 0006](../decisions/0006-phase0-operating-decisions.md) chốt [chính sách dữ liệu/browser](data-and-browser-proposal.md), audio 60 giây, nhóm 2 người và hạn nộp 28/12/2026. Chính sách chốt chưa đồng nghĩa code đã hoàn thành; dùng thử mục tiêu khoảng 10 người, chưa có kết quả thu thập.

## Ánh xạ yêu cầu sang dữ liệu

users và hai bảng Session đã triển khai; các bảng khác là thiết kế. Danh mục và ràng buộc tại [ERD](../architecture/database.md), trường tại [dictionary](../architecture/database-dictionary.md), luồng tại [workflows](../architecture/database-workflows.md).

| Yêu cầu | Bảng chính |
| --- | --- |
| AUTH-01 | users, SPRING_SESSION, SPRING_SESSION_ATTRIBUTES; không dùng JWT/refresh token |
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
