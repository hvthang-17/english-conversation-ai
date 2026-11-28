# ADR 0018 — Nhận xét hội thoại V3 theo từng lượt

**Trạng thái.** Đã triển khai trên cấu hình G6 với Flyway V32. Chất lượng ngôn ngữ còn chờ rà trên đầu ra Gemini thật.

- Dùng prompt `practice-feedback-v3.4` cho tác vụ FEEDBACK mới và retry được phép. Đầu vào có câu AI trước lượt, dữ liệu TEXT/VOICE và snapshot vai, bối cảnh, mục tiêu, trình độ. Nếu vượt giới hạn ngữ cảnh, gửi phần đầu liên tiếp còn đủ lượt học và trả độ phủ thực tế. Mỗi lượt được gửi cần có đúng một đánh giá. Bỏ riêng mục từ vựng, lời khuyên từng lượt hoặc bài luyện lại không hợp lệ. Giữ kiểm tra nghiêm với cặp câu, câu sửa và dữ liệu cốt lõi.
- Giữ nhóm lỗi ngôn ngữ tách khỏi mức độ trả lời. Dẫn bằng chứng cho mục tiêu đạt hoặc đạt một phần. Câu AI mơ hồ hoặc lệch vai dẫn đến đánh giá chưa đủ căn cứ. Lỗi lặp đếm theo tin người học khác nhau trong các lỗi được lưu.
- Khi câu hỏi rõ và người học trả lời lệch hoặc thiếu ý, yêu cầu Gemini gợi ý một câu trả lời có chỗ điền cho thông tin còn thiếu. Backend có mẫu cố định cho các trường hợp nhận diện được như giờ tan học, lý do, ví dụ làm việc nhóm và giờ hẹn. Mẫu dự phòng có thể bổ sung khi mở lại nhận xét cũ mà không phát sinh tác vụ AI.
- Yêu cầu phản hồi JSON và truyền schema chi tiết qua chỉ dẫn FEEDBACK. Backend kiểm tra cấu trúc, cặp câu nguồn, trích đoạn, dữ kiện đơn giản và xuất xứ mục từ. Bỏ ví dụ hoặc câu viết lại phát hiện lệch dữ kiện. Các trường hợp phức tạp được rà bằng bộ mẫu tiếng Anh. [Tài liệu Gemini](https://ai.google.dev/gemini-api/docs/structured-output).
- V32 thêm `session_feedback.coaching_json` và `feedback_suggestions.source_json`. V3.3 dùng JSON hiện có để lưu `meaningVerified`. Từ vựng đối chiếu được dùng nghĩa và ví dụ tham chiếu khi lưu thẻ. Mục chưa có căn cứ hiển thị trạng thái chưa đối chiếu và chỉ xem được trong nhận xét. Dữ liệu nhận xét và thẻ đã lưu tiếp tục đọc theo hợp đồng cũ.
- Luyện lại và câu hỏi áp dụng chạy trong giao diện dưới dạng tự đối chiếu tiêu chí. Bản nháp là nội dung cục bộ, không phát sinh tác vụ AI hoặc lượt mới.

[Đặc tả và kế hoạch FB-01–FB-05](../../tasks/prd-conversation-feedback-v3.md) · [Bộ rà 32 lượt](../development/chat-acceptance/feedback-v3-rubric.md).
