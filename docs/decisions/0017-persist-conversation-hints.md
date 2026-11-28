# ADR 0017 — Khôi phục gợi ý hội thoại theo câu AI

**Trạng thái:** Đã triển khai từ Flyway V30.

## Bối cảnh

Trước V30, HINT trả nội dung một lần qua phản hồi HTTP. Nếu mất kết nối sau khi Gemini trả lời, gửi lại cùng mã có thể nhận `hintAlreadyConsumed`. Người học có thể phải dùng thêm hạn mức để lấy một gợi ý khác.

## Quyết định

- Lưu một gợi ý thành công cho mỗi câu AI trong `assistant_hints`. Dùng khóa duy nhất theo buổi và câu, FK ghép xác nhận câu thuộc buổi, và liên kết duy nhất tới tác vụ HINT. Bảng tác vụ và lần gọi tiếp tục giữ trạng thái, metadata kỹ thuật và usage.
- Chỉ bắt đầu gợi ý cho câu AI gần nhất của buổi `ACTIVE` thuộc chủ phiên. Hai yêu cầu đồng thời cho cùng câu dùng chung tác vụ. Cùng mã và cùng câu trả nội dung hoặc trạng thái hiện có. Dùng lại mã đã tạo tác vụ cho câu khác trong buổi trả HTTP 409.
- Lưu nội dung đã xác thực và trạng thái `SUCCEEDED` trong cùng giao dịch. Dùng lại tác vụ cho retry có giới hạn. Nếu lần trước có kết quả chưa rõ, yêu cầu xác nhận trước khi gọi lại. Kết quả đến muộn phải kiểm tra buổi còn tồn tại và lần gọi vẫn có quyền ghi.
- Khi mở buổi hoặc nhận câu mới, frontend đọc gợi ý theo ID câu. Theo dõi tác vụ đang xử lý, phục hồi nội dung đã lưu và bỏ qua phản hồi của câu cũ khi giao diện đã sang câu mới. Gợi ý nằm ngoài transcript, số lượt và ngữ cảnh CHAT. Trong VOICE, mở gợi ý giữ nguyên trạng thái phụ đề.
- Dọn gợi ý khi xóa buổi, xóa toàn bộ dữ liệu học tập hoặc xóa tài khoản. Dữ liệu HINT cũ chỉ còn metadata. Cho học viên xin gợi ý với mã mới cho câu AI hiện tại của buổi còn mở.

Quyết định này thay phần hiển thị tạm của [ADR 0005](0005-initial-product-rules.md). Hợp đồng đích nằm trong [API buổi luyện](../api/practice.md#khôi-phục-gợi-ý-theo-câu-ai). Ca nghiệm thu nằm trong [kế hoạch G6](../development/full-system-implementation-plan.md#g6--luyện-hội-thoại-với-ai).
