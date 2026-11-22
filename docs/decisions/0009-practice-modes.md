# ADR 0009 — Tách chat văn bản và luyện nghe nói

## Trạng thái

Đã triển khai qua Flyway V9 và giao diện buổi luyện. Luồng ghi âm, xác nhận bản chép lời và phát giọng cục bộ được mô tả trong [báo cáo P6](../development/phase6/README.md).

## Bối cảnh và quyết định

Màn hình hiện tại đặt ô nhắn tin và ghi âm cạnh nhau, đồng thời hiển thị ngay nội dung AI. Người học dễ đọc câu trước khi nghe. Chọn chế độ trên trang chi tiết tình huống, cùng với trình độ, trước khi tạo buổi:

- **Chat văn bản** (`TEXT`) hiển thị câu AI ngay, lấy ô nhập làm thao tác chính và có nút nghe tùy chọn.
- **Luyện nghe và nói** (`VOICE`) mặc định ẩn chữ của mọi câu AI, kể cả câu mở đầu. Mỗi câu có nút nghe, nghe lại, nghe chậm và tùy chọn xem/ẩn phụ đề riêng. Chỉ phát sau thao tác của người học. Ghi âm là cách trả lời chính. Người học có thể mở ô gõ cho một lượt mà không đổi chế độ hoặc mở phụ đề.
- Khi thiếu giọng tiếng Anh cục bộ hoặc phát lỗi, giữ câu AI ẩn và hiển thị lựa chọn xem phụ đề hoặc **Chuyển sang chat văn bản**. Chuyển chế độ cần thao tác rõ ràng và áp dụng một chiều cho phần còn lại của buổi hiện tại. Lịch sử, số lượt và tác vụ AI đang xử lý được giữ nguyên.
- Kết thúc buổi mở toàn bộ hội thoại để đọc và đối chiếu với nhận xét. Câu AI có thể nghe lại nếu thiết bị hỗ trợ.

## Lưu trạng thái và tương thích

V9 thêm `practice_sessions.mode` và `initial_mode`, nhận `TEXT` hoặc `VOICE`. Buổi cũ có cả hai giá trị là `TEXT`. API mặc định `TEXT` khi client cũ bỏ trường mode. Khi chuyển chế độ, `initial_mode` giữ nguyên để đối chiếu yêu cầu tạo buổi gửi lại. Chế độ hiện tại được đọc lại từ server khi tải trang. Trạng thái phụ đề được giữ theo từng tin nhắn trong bộ nhớ màn hình và mặc định đóng sau khi tải lại.

Web Speech cần nội dung câu AI để phát. Vì vậy, chữ có thể nằm trong phản hồi API owner-only và được giữ ngoài cây hiển thị và cây trợ năng cho tới khi người học mở phụ đề. Thông báo câu mới dùng nhãn trung tính, ví dụ **Có câu AI mới**. Giữ cơ chế Gemini, giới hạn lượt, xác nhận bản chép lời và chính sách audio theo P6.

[Luồng người dùng](../product/user-flows.md) · [Hợp đồng API](../api/practice.md#hai-chế-độ-buổi-luyện) · [Kế hoạch triển khai](../development/implementation-plan.md)
