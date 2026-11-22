# Kế hoạch thực hiện đề tài

## 1. Mục đích, trạng thái và giả định

Đề tài: xây dựng website hỗ trợ luyện giao tiếp tiếng Anh tích hợp Gemini API, dùng React/TypeScript, Java/Spring Boot, MySQL và Session JDBC.

**Trạng thái:** Giai đoạn 0 đã thực hiện phần local/offline. Giai đoạn 1–7 đã triển khai và kiểm thử tự động. Còn nghiệm thu tích hợp giọng nói trên thiết bị thật và Gemini theo P6.8. Giai đoạn 8 chưa bắt đầu. [Báo cáo P6](phase6/README.md) · [Báo cáo P7](phase7/README.md)

Đã xác nhận: **hạn 28/12/2026, hai người, mỗi người 5 giờ/ngày và 5 ngày/tuần, dùng thử khoảng 10 người**. Lịch 20/11–28/12 có 27 ngày thứ Hai–thứ Sáu, tương đương tối đa danh nghĩa 270 giờ-người. Đây là năng lực kế hoạch, không phải giờ đã làm. Ước lượng ban đầu 40 ngày cho một người được thay bằng lịch mục tiêu bên dưới; cần kiểm lại sau mỗi mốc và không cam kết đủ thời gian nếu các điều kiện thay đổi.

### Nền tảng đã có — sử dụng tiếp

- Scaffold frontend/backend, build và kiểm thử health/bảo mật cơ bản.
- MySQL 8.4.13 portable:3307 đã xác minh app login, Unicode tạm, UTC/TLS, restart và Spring Boot JDBC/Flyway/JPA. Giai đoạn 1 bổ sung users/Spring Session JDBC thực tế, được kiểm thử HTTP/MySQL.
- ERD 20 bảng, từ điển trường, workflow và hướng dẫn AI.
- Session JDBC, idle 30 phút, nhiều thiết bị; email chuẩn hóa; 20 lượt LEARNER/buổi; HINT; FLASHCARD trước.

Không làm lại scaffold hoặc coi package placeholder là tính năng hoàn thành. Chi tiết hiện trạng tại [README](../../README.md), [setup](setup.md) và [testing](testing.md).

### Tài liệu sở hữu yêu cầu và quyết định

- [Yêu cầu/tiêu chí nghiệm thu](../product/requirements.md), [luồng người dùng](../product/user-flows.md).
- [Kiến trúc](../architecture/overview.md), [ERD](../architecture/database.md), [dictionary](../architecture/database-dictionary.md), [workflow dữ liệu](../architecture/database-workflows.md).
- [Session](../decisions/0004-session-authentication.md), [quy tắc bản đầu](../decisions/0005-initial-product-rules.md).
- [Retention, thông báo audio, browser matrix](../product/data-and-browser-proposal.md) và audio 60 giây đã chốt theo [ADR 0006](../decisions/0006-phase0-operating-decisions.md); job/UI/server validation chưa triển khai. Gemini hiện chỉ được chuẩn bị offline, không live call.

Kế hoạch sở hữu thứ tự công việc và mốc kiểm tra; không thay thế các tài liệu trên hoặc tự mở rộng phạm vi sản phẩm.

## 2. Lịch tổng quan

| Giai đoạn | Lịch mục tiêu năm 2026 | Công sức dự toán (giờ-người) | Trạng thái |
| --- | --- | --- | --- |
| 0 — yêu cầu/môi trường/probe | 20–24/11 | 20 | Đã làm local/offline; live hoãn |
| 1 — tài khoản/Session | 24–27/11 | 35 | Chưa bắt đầu |
| 2 — hồ sơ/catalog | 30/11–01/12 | 15 | Chưa bắt đầu |
| 3 — hội thoại văn bản | 02–07/12 | 40 | Chưa bắt đầu |
| 4 — phản hồi | 08–10/12 | 25 | Chưa bắt đầu |
| 5 — ôn/lịch sử/thống kê | 11–14/12 | 20 | Chưa bắt đầu |
| 6 — giọng nói | 15–17/12 | 30 | Đã tách hai chế độ, còn nghiệm thu thiết bị thật |
| 7 — quản trị | 18–21/12 | 15 | Đã triển khai và kiểm thử phần quản trị |
| 8 — tổng kiểm/dùng thử/bàn giao kỹ thuật | 22–24/12 | 25 | Chưa bắt đầu |
| Công việc xuyên suốt — báo cáo/review/tích hợp | Trong 20/11–24/12 | 25 | Chưa ghi giờ thực tế |
| Dự phòng/sửa cuối/nộp | 25–28/12, không tính cuối tuần | 20 | Chưa sử dụng |

Tổng dự toán 270 giờ-người = 225 giờ theo giai đoạn + 25 giờ xuyên suốt + 20 giờ dự phòng. Đây là lịch khá sát, cần ưu tiên bản đầu đã chốt. P0 và P1 giao nhau ngày 24/11 chỉ với phần độc lập (auth có thể bắt đầu khi DB/quy tắc sẵn sàng); không phân công vượt 5 giờ/người/ngày. Live Gemini chưa được cho phép nên P0.4/P0.6 chưa đóng; muốn giữ mốc hội thoại cần xử lý điều kiện này trước 02/12, không tự phát sinh phí để giữ tiến độ.

### Phân công đề xuất cho hai người
- **A:** backend, MySQL/Flyway, Session, tích hợp AI, giới hạn và kiểm thử dữ liệu.
- **B:** frontend, ghi âm/playback, tình huống/khảo sát, E2E và tổng hợp phản hồi.
- Cả hai thống nhất API trước khi nối màn hình, kiểm tra chéo quyền/dữ liệu và cùng demo. Vai A/B chưa gắn tên thực tế, không phải chỉ thị tạo subagent.
- Browser/micro/codec kiểm sớm ở P0; B có thể chuẩn bị UI audio sau khi chat API ổn định, nhưng chỉ đánh dấu P6 hoàn thành khi end-to-end và cleanup đạt. Báo cáo/test chuẩn bị xuyên suốt, không dồn vào 3 ngày P8.

Không tính cuối tuần vào cam kết nguồn lực. Điều kiện hoàn thành quan trọng hơn số ngày; nếu mốc không đạt phải cập nhật tác động/lịch/phạm vi và giữ bằng chứng trung thực.

## 3. Giai đoạn 0 — Hoàn tất yêu cầu và thử nghiệm tích hợp

**Thời gian mục tiêu:** 20–24/11/2026, khoảng 20 giờ-người trong 3 ngày làm việc. **Phụ thuộc:** bộ tài liệu/scaffold hiện có. **Mục tiêu:** loại bỏ sớm điểm chưa rõ về môi trường, AI, âm thanh và phạm vi.

### Công việc

- [x] P0.1 Rà soát yêu cầu và các ADR; xác định thời hạn nộp, nguồn lực, ngân sách API và quy mô dùng thử.
- [x] P0.2 Chốt retention, thông báo âm thanh, browser matrix và thời lượng bản ghi. Không tự áp dụng các con số còn là đề xuất.
- [x] P0.3 Chuẩn bị MySQL local hoặc môi trường phát triển riêng; xác minh kết nối thật, cấu hình secret và quy trình chạy ứng dụng.
- [ ] P0.4 Đã chuẩn bị Java probe/fixture và dry-run 4 case, 8 test đạt. Live hội thoại/chép lời/feedback **hoãn theo yêu cầu chỉ làm offline**, chưa có kết quả provider thật; không tự gọi khi có key.
- [x] P0.5 Smoke audio desktop theo ưu tiên chủ dự án: Chromium tổng hợp và metadata micro Chrome Windows nghe rõ, không upload. Edge/Android/toàn matrix chưa kiểm, cần tiếp ở P6.
- [ ] P0.6 Đã tra cứu tài liệu, có REST prototype và model/SDK candidate. Chưa đo latency/usage/quality/cost provider hoặc chốt model/runtime SDK vì chưa cho live call. Hoàn tất sau khi có yêu cầu mới, quyền sử dụng và evidence thật.
- [x] P0.7 Chuẩn bị khảo sát nhu cầu ngắn, tiêu chí đánh giá AI/trải nghiệm và cách tuyển người dùng thử/người đánh giá tiếng Anh.
- [x] P0.8 Cập nhật ADR, ghi rủi ro và điều chỉnh ước lượng kế hoạch.

### Kết quả bàn giao

Biên bản quyết định còn mở, môi trường MySQL sử dụng được, kết quả bản thử ba tác vụ AI, ghi nhận khả năng audio trên thiết bị và bộ tiêu chí đánh giá ban đầu.

### Điều kiện hoàn thành

Có căn cứ tích hợp Gemini/codec và đủ điều kiện bắt đầu tài khoản. Không còn quyết định chưa rõ ngăn cản bước tiếp theo. Rủi ro chưa giải quyết phải có cách xử lý và thời điểm chốt, không chỉ ghi nhận rồi bỏ qua.

**Đánh giá hiện tại:** MySQL/quy tắc đủ để bắt đầu AUTH-01 khi được giao; chưa đóng toàn bộ P0 vì live Gemini chưa được phép. Các phần còn lại có người chủ trì/mốc xem xét tại [risk register](phase0/risk-register.md).

## 4. Giai đoạn 1 — Database nền tảng và tài khoản Session

**Thời gian mục tiêu:** 24–27/11/2026, khoảng 35 giờ-người. **Phụ thuộc:** môi trường MySQL và quy tắc tài khoản đã chốt. **Yêu cầu:** AUTH-01 và phần phân quyền nền tảng ADMIN-01.

**Bảng:** users, SPRING_SESSION, SPRING_SESSION_ATTRIBUTES.

### Công việc

- [x] P1.1 Tạo migration users; thực hiện cùng quy tắc chuẩn hóa/UNIQUE email ở backend và MySQL. Không trim/lowercase mật khẩu.
- [x] P1.2 Thêm Spring Session JDBC tương thích; lấy schema MySQL đúng phiên bản cho Flyway, tắt schema auto-initialize trùng lặp. Kiểm tra migration trên database rỗng.
- [x] P1.3 Backend đăng ký, đăng nhập, lấy user hiện tại, đăng xuất và đổi mật khẩu; PasswordEncoder và validation phù hợp.
- [x] P1.4 Cấu hình idle timeout 30 phút, nhiều thiết bị, chống fixation, persist SecurityContext, CSRF cho React, cookie theo môi trường và lỗi API nhất quán.
- [x] P1.5 Kiểm tra users.status/role ở request bảo vệ; thực hiện thu hồi phiên khi khóa/đổi quyền/mật khẩu. Chưa cần giao diện quản trị đầy đủ.
- [x] P1.6 Tạo quy trình khởi tạo admin đầu tiên có kiểm soát từ môi trường; không password cố định, không reset admin sẵn có hoặc signup ADMIN công khai.
- [x] P1.7 Frontend đăng ký, đăng nhập, đổi mật khẩu; quản lý user hiện tại, điều hướng/protected screens, lỗi input, CSRF và phiên hết hạn. Không lưu mã phiên vào localStorage.
- [x] P1.8 Kiểm thử nghiệp vụ/bảo mật/JDBC và cập nhật hướng dẫn cấu hình. Xác minh build/checks trên thay đổi thực tế.

### Kết quả bàn giao

Luồng đăng ký → login → phiên tồn tại qua request → đổi mật khẩu → logout chạy được từ frontend. Có admin bootstrap và hợp đồng API auth để các module khác dùng.

### Điều kiện hoàn thành — M1

Email khác hoa/thường không tạo trùng; password không dạng rõ; session rotation/persistence/logout/expiry đúng; thiếu/sai CSRF bị xử lý; nhiều thiết bị hoạt động theo quy tắc; user không truy cập quyền admin. Kiểm thử persistence bằng MySQL, không chỉ bootstrap/mock.

## 5. Giai đoạn 2 — Hồ sơ và tình huống luyện tập

**Thời gian mục tiêu:** 30/11–01/12/2026, khoảng 15 giờ-người. **Phụ thuộc:** M1. **Yêu cầu:** PROFILE-01, SCENARIO-01.

**Bảng:** learner_profiles, user_topic_interests, topics, scenarios, scenario_levels.

### Công việc

- [x] P2.1 Migration hồ sơ/catalog, quan hệ và chỉ mục cần dùng; phối hợp tạo profile với quy trình đăng ký từ giai đoạn 1.
- [x] P2.2 API cập nhật tên, A2/B1, mục tiêu, chủ đề quan tâm; kiểm tra ownership.
- [x] P2.3 Chuẩn bị 8 tình huống: giới thiệu bản thân, cuộc sống sinh viên, gọi món, mua sắm, hỏi đường, đặt phòng, sở thích/cuối tuần, phỏng vấn cơ bản.
- [x] P2.4 Viết bối cảnh, vai người học/AI, mục tiêu và level; kiểm tra nội dung, seed catalog theo cách có kiểm soát.
- [x] P2.5 API danh sách/chi tiết theo chủ đề/trình độ; loại tình huống/topic bị ẩn. Không trả ai_instruction qua API người học.
- [x] P2.6 Giao diện hồ sơ, danh sách chủ đề và màn hình chi tiết/chọn bài; trạng thái tải/trống/lỗi và hiển thị trên điện thoại.

### Kết quả bàn giao

Người học thiết lập hồ sơ và chọn được bài đúng trình độ. Bộ tình huống đủ rõ để dùng làm đầu vào AI.

### Điều kiện hoàn thành

Owner-only cho hồ sơ, catalog hoạt động đúng trạng thái/level; không lộ instruction nội bộ; 8 tình huống đã được rà soát. Thay profile/catalog không được định nghĩa lại lịch sử cũ.

## 6. Giai đoạn 3 — Hội thoại văn bản và gợi ý

**Thời gian mục tiêu:** 02–07/12/2026, khoảng 40 giờ-người. **Phụ thuộc:** giai đoạn 2 và kết quả bản thử Gemini được phép thực hiện. **Yêu cầu:** CHAT-01, RESILIENCE-01.

**Bảng:** practice_sessions, ai_requests, ai_attempts, conversation_turns, messages.

### Công việc

- [x] P3.1 Tạo migration theo thứ tự FK; chưa tạo FK tới audio_submissions chưa tồn tại. Bổ sung liên kết audio bằng migration khi thực hiện giai đoạn 6.
- [x] P3.2 Tạo buổi với snapshot/level, lượt OPENING và câu mở đầu đúng vai; owner/status validation.
- [x] P3.3 Backend gửi câu LEARNER, gọi Gemini, kiểm tra/lưu câu AI; giới hạn context và input/output.
- [x] P3.4 Điều phối một lượt xử lý/buổi, khóa/cấp số lượt, idempotency/fingerprint; tối đa 20 câu LEARNER được chấp nhận, không đếm opening/retry/HINT.
- [x] P3.5 Tác vụ HINT gắn session, trả gợi ý tạm; không thêm transcript chính hoặc chạy đồng thời lượt đang xử lý.
- [x] P3.6 Hạn mức nguyên tử trước gọi, timeout/retry có giới hạn, UNKNOWN và cách phục hồi; không giữ DB transaction chờ provider.
- [x] P3.7 Ghi ai_requests/ai_attempts, model, prompt version, latency/usage và cost ước tính khi đủ dữ liệu; không log nội dung thô/secret.
- [x] P3.8 Frontend hội thoại, mục tiêu/vai/trình độ, số lượt, đang gửi/đang chờ/lỗi, thử lại, gợi ý và kết thúc.
- [x] P3.9 Test concurrency, gửi lại, timeout, ownership, hạn mức; live smoke test có giới hạn trong phạm vi được cấp.

### Kết quả bàn giao

Chọn tình huống → AI mở đầu → trao đổi văn bản → nhận gợi ý nếu cần → kết thúc buổi. Có thống kê kỹ thuật ban đầu cho lời gọi AI.

### Điều kiện hoàn thành — M2

Không nhân đôi lượt/tin nhắn khi retry; không nhận lượt 21; AI lỗi không làm mất câu đã lưu; không truy cập buổi người khác; có bằng chứng hội thoại đúng vai trên tình huống mẫu. Mock test không được báo là bằng chứng chất lượng model thật.

## 7. Giai đoạn 4 — Phản hồi sau buổi luyện

**Thời gian mục tiêu:** 08–10/12/2026, khoảng 25 giờ-người. **Phụ thuộc:** M2. **Yêu cầu:** FEEDBACK-01.

**Bảng:** session_feedback, feedback_items, feedback_suggestions; sử dụng ai_requests/ai_attempts.

### Công việc

- [x] P4.1 Migration phản hồi và FK nguồn cùng buổi; một bản tổng hợp/buổi.
- [x] P4.2 Thiết kế prompt/schema gồm summary, strengths, goal assessment, corrections, từ/mẫu câu và gợi ý tiếp.
- [x] P4.3 Validate đầu ra, đối chiếu message nguồn/LEARNER và đoạn trích; phân biệt ERROR/IMPROVEMENT, kiểm soát rule_code, cho phép không có lỗi.
- [x] P4.4 Tách trạng thái session hoàn thành với phản hồi; retry bản thất bại idempotently, không ghi đè bản thành công tùy tiện.
- [x] P4.5 Frontend trình bày nhận xét, câu gốc/câu sửa/giải thích; trạng thái đang tạo và thử lại.
- [x] P4.6 Đánh giá sơ bộ bằng hội thoại đúng/sai; ghi lỗi, tinh chỉnh prompt có version và giữ mẫu phục vụ đánh giá cuối.

### Kết quả bàn giao

Kết thúc buổi → nhận xét chung, điểm tốt, mục sửa và nội dung nên ôn. Có bằng chứng ban đầu về tính hợp lý của phản hồi.

### Điều kiện hoàn thành

Không lưu kết quả sai cấu trúc/nguồn; kết quả chi tiết và trạng thái thành công nhất quán; phân tích lỗi không mất hội thoại; không chấm phát âm/CEFR. Có rà soát ngôn ngữ phù hợp, không chỉ kiểm tra JSON.

## 8. Giai đoạn 5 — Ôn tập, lịch sử và thống kê

**Thời gian mục tiêu:** 11–14/12/2026, khoảng 20 giờ-người. **Phụ thuộc:** giai đoạn 4. **Yêu cầu:** REVIEW-01, HISTORY-01, STATS-01.

**Bảng bổ sung:** review_items, review_attempts; truy vấn dữ liệu luyện tập đã có.

### Công việc

- [x] P5.1 Migration ôn tập; lưu bản sao từ/mẫu câu từ suggestion thuộc owner, không trùng cùng nguồn.
- [x] P5.2 Giao diện FLASHCARD và trạng thái cần ôn/đã nhớ; ghi review_attempts, cập nhật thẻ nhất quán. Chưa triển khai điền từ/lịch ôn tự động.
- [x] P5.3 Danh sách lịch sử phân trang, xem hội thoại/phản hồi và trạng thái trống/lỗi.
- [x] P5.4 Xóa một buổi nhưng giữ thẻ đã lưu, gỡ nguồn; xóa toàn bộ dữ liệu học tập gồm cả thẻ/lịch sử ôn.
- [x] P5.5 Xử lý thứ tự FK, gỡ liên kết log và chặn worker muộn tái tạo dữ liệu; đường dọn audio sẽ bổ sung ở giai đoạn 6.
- [x] P5.6 Thống kê số buổi hoàn thành, lượt LEARNER, ngày luyện theo múi giờ, chủ đề và nhóm lỗi; gợi ý đơn giản theo level/sở thích/lịch sử.
- [x] P5.7 Test ownership, save trùng, nguồn đã xóa, thống kê sau retry/xóa; giải thích tác động xóa trên UI.

### Kết quả bàn giao

Chu trình văn bản hoàn chỉnh: luyện → phản hồi → lưu nội dung → ôn tập → theo dõi hoạt động.

### Điều kiện hoàn thành — M3

Thẻ độc lập nguồn, dữ liệu owner-only, xóa đúng phạm vi; thống kê không đếm request API/retry thành lượt học. Không tuyên bố thống kê chứng minh cải thiện năng lực dài hạn.

## 9. Giai đoạn 6 — Giọng nói theo lượt và nghe câu AI

**Thời gian mục tiêu:** 15–17/12/2026, khoảng 30 giờ-người. **Phụ thuộc:** M3, bản thử audio P0 và chính sách đã chốt; chuẩn bị UI độc lập có thể làm sớm nhưng không thay nghiệm thu tích hợp. **Yêu cầu:** VOICE-01, PLAYBACK-01, PRIVACY-01.

**Bảng bổ sung:** audio_submissions; ALTER messages để gắn audio và FK cùng session. Điều chỉnh timestamp/expiry nếu chính sách được chấp nhận cần hai TTL độc lập.

### Công việc

- [x] P6.1 Chốt hợp đồng audio, thông báo, retention và browser matrix. Kiểm tra migration V8.
- [x] P6.2 Frontend xin quyền micro, ghi/dừng/nghe lại/hủy, hiển thị thời lượng và feature detection. Chỉ upload khi người học bấm gửi.
- [x] P6.3 Backend kiểm tra MIME/codec/dung lượng/thời lượng, lưu tạm, chống upload trùng và tạo TRANSCRIPTION. Đã kiểm bằng fixture provider.
- [x] P6.4 Xem/sửa/xác nhận transcript. Mỗi audio tạo tối đa một message/turn và một tác vụ CHAT.
- [x] P6.5 Phát/dừng câu AI với giọng tiếng Anh cục bộ. Text dùng được khi thiếu playback.
- [x] P6.6 Cleanup audio/transcript theo chính sách, giữ DELETION_PENDING để dọn khi xóa buổi. Retry lỗi provider có giới hạn.
- [x] P6.7 Kiểm thử tự động quyền micro, hủy/thoát, mất mạng, audio rỗng, codec không hợp lệ, gửi trùng và lỗi provider bằng dữ liệu giả lập.
- [ ] P6.8 Test desktop và Android thật theo browser matrix đã chấp nhận; ghi browser/OS/thiết bị. Nếu Safari/iPhone bắt buộc phải đưa vào bản thử sớm, không đợi đến cuối.

P6.8 và kiểm chứng TRANSCRIPTION với Gemini thật còn mở. Xem [bằng chứng, phạm vi và quy trình nghiệm thu](phase6/README.md).

### Bổ sung trước nghiệm thu — tách hai chế độ

- [x] Thêm migration lưu mode, mặc định TEXT cho buổi đã có. Cập nhật API tạo/đọc/chuyển chế độ và kiểm tra gửi trùng theo mode tạo ban đầu.
- [x] Thêm lựa chọn chế độ ở chi tiết tình huống. Trong VOICE, hiển thị thẻ nghe trước, nghe lại/nghe chậm và phụ đề riêng từng câu. Kiểm tra câu mở đầu, câu mới, tải lại trang và thông báo trong cây trợ năng.
- [x] Dùng lại ghi âm và xác nhận bản chép lời. Bổ sung nhập bằng bàn phím cho một lượt, xử lý thiếu giọng và cho chuyển hẳn sang TEXT theo yêu cầu. Mở toàn bộ hội thoại khi kết thúc.
- [x] Kiểm tra luồng TEXT và dữ liệu cũ, VOICE giữ mode sau tải lại, câu AI mới vẫn ẩn sau khi mở phụ đề câu trước, thao tác gõ thay thế giữ phụ đề đóng, và hội thoại/số lượt nhất quán sau khi chuyển chế độ. Kiểm thử trình duyệt dùng API và giọng đọc giả lập.

[Quyết định thiết kế](../decisions/0009-practice-modes.md) · [Luồng người dùng](../product/user-flows.md) · [Hợp đồng API](../api/practice.md#hai-chế-độ-buổi-luyện)

### Kết quả bàn giao

Ghi âm → nghe lại → gửi → sửa/xác nhận transcript → AI trả lời → nghe câu AI; có phương án nhập văn bản và cơ chế dọn có thể kiểm chứng.

### Điều kiện hoàn thành

Không dùng transcript chưa xác nhận làm câu đã gửi, không rò file tạm, micro dừng đúng vòng đời, cleanup có kiểm tra. Có bằng chứng audio trên thiết bị thật; Playwright/mock không thay thế kiểm thử micro/codec thực tế.

## 10. Giai đoạn 7 — Quản trị hệ thống

**Thời gian mục tiêu:** 18–21/12/2026, khoảng 15 giờ-người. **Phụ thuộc:** auth/catalog ổn định và các chức năng chính đã tích hợp. **Yêu cầu:** ADMIN-01.

**Bảng bổ sung:** admin_audit_logs; sử dụng users và catalog.

### Công việc

- [x] P7.1 Migration audit và API chỉ cấp quyền ADMIN. Hội thoại cá nhân giữ ràng buộc owner.
- [x] P7.2 Giao diện tìm tài khoản, khóa/mở và thu hồi Session.
- [x] P7.3 Thêm/sửa/ẩn chủ đề, tình huống và trình độ. Kiểm tra ACTIVE và giữ snapshot lịch sử.
- [x] P7.4 Số liệu vận hành tổng hợp và audit tối thiểu cùng giao dịch MySQL.
- [x] P7.5 Kiểm tra quyền truy cập, cookie của tài khoản bị khóa, snapshot cũ và actor/target audit. [Báo cáo P7](phase7/README.md)

### Kết quả bàn giao

Admin quản lý tài khoản/nội dung qua ứng dụng, không cần sửa trực tiếp database. Giao diện quản trị có trạng thái và validation.

### Điều kiện hoàn thành — M4

Quyền admin được thực thi server-side; khóa có hiệu lực ở request bảo vệ tiếp theo; audit không chứa secret/nội dung cá nhân không cần thiết; catalog sửa không làm sai lịch sử. Các chức năng bản đầu đã tích hợp, còn đánh giá và bàn giao.

## 11. Giai đoạn 8 — Kiểm thử tổng thể, dùng thử và bàn giao

**Thời gian mục tiêu:** 22–24/12/2026, khoảng 25 giờ-người; khảo sát/báo cáo/recruitment chuẩn bị trước. **Phụ thuộc:** M4 và môi trường dùng thử đủ điều kiện. **Yêu cầu:** toàn bộ luồng bắt buộc.

### Công việc

- [ ] P8.1 Kiểm thử đầu cuối từ đăng ký đến ôn tập; truy cập chéo owner/role, session/CSRF, retry/concurrency, quota và dữ liệu sau xóa.
- [ ] P8.2 Xác minh migration trên DB rỗng và đường nâng cấp có dữ liệu; FK cùng buổi, cleanup thất bại/chạy lại, worker muộn và provider lỗi.
- [ ] P8.3 Đánh giá khoảng 30–50 đoạn hội thoại mẫu với người đủ năng lực tiếng Anh: đúng vai, phù hợp trình độ, sửa lỗi hợp lý, giải thích hữu ích. Quy mô cần xác nhận theo nguồn lực.
- [ ] P8.4 Đo median/P95 độ trễ từng tác vụ, sample size/điều kiện, usage và cost ước tính theo phiên bản giá; không dùng mock để kết luận hiệu năng/provider thật.
- [ ] P8.5 Tổ chức dùng thử theo nhiệm vụ, định hướng 10–15 người nếu có nguồn lực; thu độ dễ dùng, ích lợi phản hồi và khó khăn. Sửa lỗi cản luồng và ghi các giới hạn mẫu nhỏ.
- [ ] P8.6 Chuẩn bị HTTPS, secret, cookie/proxy, backup/restore, hạn mức, monitoring và cleanup theo chính sách được chốt; xác minh trước triển khai công khai.
- [ ] P8.7 Triển khai trong phạm vi được giao; không tự xuất bản/chi tiền chỉ vì kế hoạch nhắc đến. Kiểm tra lại luồng trên môi trường đích.
- [ ] P8.8 Hoàn thiện hướng dẫn dùng/cài/triển khai, mã nguồn/migration/catalog mẫu, kết quả kiểm thử và hạn chế.
- [ ] P8.9 Hoàn thiện báo cáo, hình/sơ đồ đúng implementation, slide và kịch bản demo; tổng kết đạt/chưa đạt từng yêu cầu.

### Kết quả bàn giao

Phiên bản trình diễn được, môi trường có thể tái lập, bằng chứng kiểm thử/đánh giá/dùng thử, hướng dẫn vận hành và bộ tài liệu đồ án.

### Điều kiện hoàn thành — M5

Các yêu cầu bắt buộc đạt tiêu chí nghiệm thu; không còn lỗi đã biết gây rò dữ liệu hoặc chặn luồng chính. Phần chưa làm/không kiểm chứng phải nêu rõ. Cài lại theo hướng dẫn được, backup phục hồi theo phạm vi đã chọn, demo có kịch bản và phương án xử lý lỗi. Không kết luận hiệu quả học dài hạn từ đợt dùng thử ngắn.

## 12. Mốc kiểm soát và bằng chứng

| Mốc | Hạn tương đối | Bằng chứng tối thiểu | Trạng thái |
| --- | --- | --- | --- |
| M1 | 27/11/2026 | Demo auth + kết quả test Session/MySQL/bảo mật | Đã đạt local 20/11/2026; xem [P1](phase1/README.md) |
| M2 | 07/12/2026 | Một buổi chat thật + test chống trùng/giới hạn + usage | API/MySQL và 4 mẫu Gemini live đã đạt; còn demo toàn luồng website với provider live |
| M3 | 14/12/2026 | Luyện → feedback → lưu/ôn → lịch sử/thống kê/xóa | Đạt điều kiện nghiệp vụ local với fixture; demo toàn luồng với Gemini live còn mở |
| M4 | 21/12/2026 | Demo audio trên thiết bị thật và quản trị có phân quyền | Quản trị đã kiểm chứng, còn demo thiết bị thật |
| M5 | 24/12/2026, hoàn thiện/nộp trước 28/12 | Nghiệm thu yêu cầu, dùng thử, hướng dẫn, báo cáo và demo | Chưa đạt |

Mỗi lần cập nhật tiến độ ghi ngày thực tế, đầu việc hoàn tất, lệnh/test/kết quả hoặc artifact liên quan, phần chưa chạy và vướng mắc. Tick checkbox khi đã có bằng chứng tương ứng; không đánh dấu mốc đạt chỉ vì hết thời lượng hoặc có file/màn hình.

## 13. Quy trình trong từng đầu việc

1. Đọc mã yêu cầu, kiến trúc và hướng dẫn phạm vi; xác định điều kiện hoàn thành.
2. Thiết kế API/dữ liệu cần dùng, xử lý lỗi/owner/role và cập nhật tài liệu khi có thay đổi.
3. Tạo migration/backend/kiểm thử phù hợp, rồi nối frontend vào API thật; có thể xen kẽ theo lát cắt chức năng, không dồn tích hợp cuối.
4. Chạy checks của phần bị ảnh hưởng và xác minh thành công/lỗi/trống. Kiểm thử từ đầu mỗi giai đoạn, không để toàn bộ tới giai đoạn 8.
5. Dùng pr-review cho thay đổi đáng kể theo project profile, sửa finding đã xác nhận và kiểm tra lại. Không coi review là thay thế test.
6. Demo, cập nhật trạng thái/bằng chứng và đánh giá phụ thuộc trước giai đoạn tiếp theo.

Skill chọn theo [Agent tooling](agents.md); lệnh thực tế theo [setup](setup.md), mức kiểm thử theo [testing](testing.md). Kế hoạch không tự tạo quyền commit/push/deploy, gửi lời mời hoặc dùng API có phí.

## 14. Báo cáo và đánh giá thực hiện song song

| Thời điểm | Tài liệu/bằng chứng chuẩn bị |
| --- | --- |
| Giai đoạn 0–2 | Tổng quan, khảo sát, mục tiêu/phạm vi, yêu cầu, kiến trúc, ERD và giao diện dự kiến |
| Giai đoạn 3–4 | Mô tả hội thoại/AI, prompt version, trường hợp mẫu và kết quả phản hồi |
| Giai đoạn 5–7 | Các chức năng còn lại, ảnh giao diện thực tế, test case và lỗi đã sửa |
| Giai đoạn 8 | Tổng hợp đánh giá, hạn chế/hướng phát triển, hướng dẫn, slide và demo |

Chuẩn bị bộ hội thoại/biểu mẫu và nguồn người dùng thử từ đầu, không chờ đến ngày nghiệm thu mới tìm người. Không đưa dữ liệu cá nhân/secret vào ảnh, báo cáo hoặc fixture.

## 15. Dự phòng và điều chỉnh

- Model/audio: thử ở giai đoạn 0; nếu không phù hợp, đổi phương án trong phạm vi đã chốt và cập nhật ADR trước khi phụ thuộc sâu.
- Chi phí: chốt ngân sách/quota trước live call; không dùng toàn bộ bài test tự động để gọi provider thật.
- Thiếu thời gian: ưu tiên hoàn thiện các luồng bắt buộc, không thêm quên mật khẩu, điền từ, lịch ôn tự động, realtime voice hoặc tính năng mở rộng. Không tự bỏ giọng nói/quản trị khỏi phạm vi bắt buộc mà không thống nhất lại.
- Dùng 20 giờ-người dự phòng ngày làm việc 25 và 28/12 cho lỗi tích hợp/sửa cuối/nộp; nếu vượt, ghi rõ phần ảnh hưởng và quyết định phạm vi, không mặc định cả nhóm làm cuối tuần.
- Hạ tầng/quyền truy cập chưa có không được che bằng dữ liệu giả hoặc đánh dấu pass; tiếp tục phần độc lập và báo phần thực sự bị chặn.

## 16. Nhật ký tiến độ

Chưa có giai đoạn nghiệp vụ nào hoàn thành. P0 đã có bằng chứng trong [báo cáo riêng](phase0/README.md); P0.4/P0.6 live hoãn theo yêu cầu. Không coi công sức dự toán là giờ đã ghi nhận.

| Ngày thực tế | Đầu việc | Trạng thái/kết quả | Bằng chứng | Vướng mắc/bước tiếp |
| --- | --- | --- | --- | --- |
| 20/11/2026 | P0.1/2/3/5/7/8 | Hoàn tất trong phạm vi ghi ở báo cáo | [Bằng chứng P0](phase0/README.md) | P0.4/P0.6 live hoãn; không có kết quả API |

P0 bắt đầu theo yêu cầu ngày 20/11/2026; lịch phần còn lại là mục tiêu đến hạn 28/12 đã xác nhận. Cập nhật lại sau từng mốc theo bằng chứng và nguồn lực thực tế.

### Cập nhật thực hiện 20/11/2026 — M1

P1.1–P1.8 đã hoàn tất: migration users/Session, auth API/React, bootstrap admin và phân quyền tài khoản nền tảng. Kết quả MySQL/HTTP/browser/checks tại [biên bản P1](phase1/README.md). Ước lượng 35 giờ-người giữ làm baseline, không suy ra thời gian đã dùng. ADMIN-01 catalog/audit còn lại và toàn bộ tính năng học tập chưa triển khai; P0 Gemini live vẫn hoãn.

### Cập nhật thực hiện 20/11/2026 — Giai đoạn 2

P2.1–P2.6 hoàn tất; [biên bản P2](phase2/README.md), [API](../api/profile-catalog.md), [rà soát 8 bài](../product/starter-catalog-review.md). Ước lượng 15 giờ-người là baseline, không phải giờ đã đo. Chưa đạt M2 (hội thoại thật), chưa mở P3 hoặc gọi Gemini.

### Cập nhật ngày 22/11/2026 — Giai đoạn 3

P3.1–P3.9 đã triển khai. Luồng HTTP chạy trên MySQL với provider giả lập; bốn lời gọi Gemini Free tier qua smoke riêng cho kết quả đúng vai trên mẫu nhà hàng. Demo trình duyệt từ đầu tới cuối với provider live chưa thực hiện. [Biên bản P3](phase3/README.md).

### Cập nhật ngày 22/11/2026 — Giai đoạn 4

P4.1–P4.6 đã thực hiện. Migration, API và UI đã được kiểm chứng bằng MySQL và fixture. Mẫu đúng/sai đã được rà soát ngôn ngữ offline. Chưa có phản hồi Gemini live cho P4 và chưa chốt M3. [Biên bản P4](phase4/README.md).

### Cập nhật ngày 22/11/2026 — Giai đoạn 5

Đã thực hiện P5.1–P5.7. MySQL và kiểm tra trình duyệt xác nhận hành vi xóa và nguồn thẻ theo đúng phạm vi, thống kê đếm dữ liệu người học đã lưu. [Biên bản P5](phase5/README.md).
