# ADR 0009 — Tách chat văn bản và luyện nghe nói

## Trạng thái

Quyết định chọn chế độ còn hiệu lực. Dòng Flyway hiện tại tạo mode và initial_mode ở V16. Luồng ghi âm, xác nhận bản chép lời và phát giọng cục bộ được mô tả trong [Kịch bản nghiệm thu hội thoại](../development/chat-acceptance/README.md).

## Bối cảnh và quyết định

Để người học nghe trước khi đọc, chọn chế độ trên trang chi tiết tình huống, cùng với trình độ, trước khi tạo buổi:

- **Chat văn bản** (`TEXT`) hiển thị câu AI ngay, lấy ô nhập làm thao tác chính và có nút nghe tùy chọn.
- **Luyện nghe và nói** (`VOICE`) mặc định ẩn chữ của mọi câu AI, kể cả câu mở đầu. Mỗi câu có nút nghe, nghe lại, nghe chậm và tùy chọn xem/ẩn phụ đề riêng. Chỉ phát sau thao tác của người học. Ghi âm là cách trả lời chính. Người học có thể mở ô gõ cho một lượt mà không đổi chế độ hoặc mở phụ đề.
- Khi thiếu giọng tiếng Anh cục bộ hoặc phát lỗi, cho mở phụ đề theo từng câu. Chế độ được chọn trước buổi; nhập bàn phím trong VOICE chỉ thay cách trả lời của lượt hiện tại.
- Kết thúc buổi mở toàn bộ hội thoại để đọc và đối chiếu với nhận xét. Câu AI có thể nghe lại nếu thiết bị hỗ trợ.

## Lưu trạng thái và tương thích

V16 tạo `practice_sessions.mode` và `initial_mode`, nhận `TEXT` hoặc `VOICE`. Buổi cũ có cả hai giá trị là `TEXT`. API mặc định `TEXT` khi client cũ bỏ trường mode. Khi chuyển chế độ, `initial_mode` giữ nguyên để đối chiếu yêu cầu tạo buổi gửi lại. Chế độ hiện tại được đọc lại từ server khi tải trang. Trạng thái phụ đề được giữ theo từng tin nhắn trong bộ nhớ màn hình và mặc định đóng sau khi tải lại.

Web Speech cần nội dung câu AI để phát. Vì vậy, chữ có thể nằm trong phản hồi API owner-only và được giữ ngoài cây hiển thị và cây trợ năng cho tới khi người học mở phụ đề. Thông báo câu mới dùng nhãn trung tính, ví dụ **Có câu AI mới**. Giữ cơ chế Gemini, giới hạn lượt, xác nhận bản chép lời và chính sách audio theo [ADR 0016](0016-g6-direct-voice-submission.md).

[Luồng người dùng](../product/user-flows.md) · [Hợp đồng API](../api/practice.md#hai-chế-độ-buổi-luyện) · [Kế hoạch hội thoại AI](../development/full-system-implementation-plan.md#g6--luyện-hội-thoại-với-ai)
