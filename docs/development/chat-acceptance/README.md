# Kịch bản nghiệm thu hội thoại AI — G6/E2E

## Hồi quy cần chạy ở G6/E2E

Kịch bản này dùng để nghiệm thu trên thiết bị thật. Kết quả hồi quy trên dòng Flyway hiện tại nằm trong [biên bản G6](../acceptance/g6-conversation-acceptance.md). Chạy ca TEXT/VOICE, chọn vai, phụ đề theo lượt, tải lại nháp, gợi ý, giải thích, phản hồi, lưu thẻ và xóa buổi. Dùng provider giả lập cho ca lỗi và ghi riêng kết quả trên MySQL, trình duyệt và thiết bị thật.

## CHAT-C06 — Thiết bị và Gemini thật

| Thiết bị và trình duyệt mục tiêu | Trạng thái | Bằng chứng hiện có |
| --- | --- | --- |
| Chrome Windows | Chờ nghiệm thu toàn luồng | P0 ghi micro thật 8,16 giây, WebM/Opus và WAV PCM 16-bit mono 16 kHz, nghe rõ |
| Edge Windows | Chờ nghiệm thu toàn luồng | Chưa có bằng chứng micro hoặc giọng đọc trên thiết bị |
| Chrome Android | Chờ nghiệm thu toàn luồng | Chưa có bằng chứng micro hoặc giọng đọc trên thiết bị |

[Metadata micro P0](../phase0/evidence/clean-build-chrome-microphone.json)

### Kịch bản trên thiết bị

1. Mở app qua HTTPS hoặc localhost trên thiết bị chạy app. Dùng tài khoản thử. Ghi phiên bản trình duyệt, OS, thiết bị, micro và giọng tiếng Anh cục bộ. Ghi kết nối hoặc quyền micro là nguyên nhân khi một bước bị chặn.
2. Chạy hai mẫu A2/B1 trong [phiếu đánh giá](evaluation.md). Nghe mở đầu với phụ đề đóng, đọc câu hư cấu, nghe lại, gửi audio, xem bản nháp và xác nhận. Nghe phản hồi, dùng **Giải thích câu này**, mở nghĩa tiếng Việt theo yêu cầu rồi kết thúc và tạo nhận xét. Mở lại lịch sử để đối chiếu vai và transcript.
3. Ghi đạt, lỗi hoặc bị chặn theo bước. Định nghĩa một lượt chạy hoàn thành khi qua toàn bộ các bước bắt buộc kể cả mở lại lịch sử. Mẫu số là số lượt thực sự bắt đầu, ghi riêng số lượt bị chặn và lý do. Khi chưa có lượt thật, ghi N=0 và để trống tỷ lệ.
4. Tách độ trễ backend theo **CHAT mở đầu**, **TRANSCRIPTION**, **CHAT sau xác nhận**, **EXPLAIN** và **FEEDBACK**. Ghi thêm thời gian người học nhìn thấy kết quả nếu đo trên UI. Báo số mẫu và từng giá trị khi mẫu nhỏ, median và P95 kèm cỡ mẫu khi tổng kết E2E. Đếm retry là một lần gọi riêng, tránh lưu API key, audio, hoặc nội dung hội thoại cá nhân trong bằng chứng.

Mỗi kịch bản đủ năm bước AI dự kiến tối thiểu 5 lần gọi. Bộ hai tình huống trên cả ba môi trường tương đương tối thiểu 30 lần nếu chạy tất cả bằng provider thật. Tổng lần gọi được giới hạn bởi phạm vi người dùng xác nhận. Nếu trần thấp hơn, ưu tiên hai mẫu khác trình độ và ghi rõ các tổ hợp thiết bị, tác vụ chưa chạy. Các bước phụ đề, phát cục bộ và mở lịch sử không gọi thêm AI.

### Mẫu ghi kết quả mỗi lượt chạy

| Mã mẫu | Thiết bị / OS / trình duyệt | Micro / codec / giọng | Bước cuối đạt | Lỗi hoặc blocker | Độ trễ theo tác vụ (ms) | Số lần gọi |
| --- | --- | --- | --- | --- | --- | --- |
| Chờ kết quả | — | — | — | — | — | — |

Chỉ bắt đầu gọi Gemini khi có model ID, gói API và trần thử rõ ràng. Mẫu thử dùng nội dung hư cấu. Nếu trần cho phép hữu hạn, dừng tại giới hạn và ghi số liệu còn thiếu.

Chưa chạy provider thật hoặc nghiệm thu toàn luồng trên thiết bị vật lý trong bản dựng sạch. Ghi số lần thực tế khi có phạm vi được cấp.

## CHAT-C07 — Đánh giá và bàn giao

[Phiếu chấm và hai mẫu A2/B1](evaluation.md) dùng cho vòng rà ban đầu. Dùng rubric chất lượng, bản ghi độ trễ và usage, kịch bản nhiệm vụ và câu hỏi trải nghiệm cho [đợt nghiệm thu và dùng thử](../full-system-implementation-plan.md#e2e--nghiệm-thu-dùng-thử-và-bàn-giao).

Đã rà nội bộ câu kỳ vọng và tiêu chí cho cả hai kịch bản. Còn chờ người đánh giá độc lập chấm đầu ra thực tế. Chưa có điểm chất lượng để tổng hợp.
