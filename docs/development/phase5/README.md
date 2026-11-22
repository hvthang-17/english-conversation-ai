# Giai đoạn 5 — Ôn tập, lịch sử và thống kê

Khởi động lại backend để Flyway áp dụng V7. Sau khi tạo nhận xét, bấm **Lưu thẻ ôn tập** bên dưới từ hoặc mẫu câu. Mở **Ôn tập**, xem đáp án và đánh dấu **Cần ôn** hoặc **Đã nhớ**. Mỗi thao tác hợp lệ ghi một attempt FLASHCARD. Điền từ và lịch ôn tự động dành cho phiên bản sau.

Mục **Lịch sử** phân trang theo thời điểm bắt đầu và ID, mới nhất trước. Mở chi tiết để đọc transcript, nhận xét hoặc tiếp tục buổi còn ACTIVE. Giao diện giải thích phạm vi và yêu cầu xác nhận trước khi xóa.

| Thao tác | Dữ liệu được xử lý |
| --- | --- |
| Xóa một buổi | Xóa phản hồi chi tiết, gợi ý, tổng hợp, tin nhắn, lượt và buổi. FK đặt nguồn thẻ thành NULL, giữ bản sao và lịch sử ôn của thẻ. |
| Xóa toàn bộ dữ liệu học tập | Xóa mọi buổi của owner, thẻ và các attempt. Tài khoản, hồ sơ và chủ đề quan tâm tiếp tục dùng được. |

Transaction xóa khóa hàng tài khoản và buổi để phối hợp với admission AI. Metadata AI còn được giữ theo chính sách vận hành với session_id đã gỡ. Attempt đang chạy chuyển UNKNOWN khi bị xóa. Callback phải lấy khóa và tìm được buổi trước khi lưu, nên kết quả về sau bị bỏ. P5 áp dụng cho dữ liệu văn bản. Đường dọn audio sẽ nối ở P6.

**Thống kê** dùng dữ liệu đang lưu. Buổi hoàn thành lấy trạng thái COMPLETED, lượt người học lấy message LEARNER và nhóm lỗi lấy item ERROR của bản nhận xét thành công. Ngày luyện nhóm từ UTC sang giờ Việt Nam UTC+7, tương ứng Asia/Ho_Chi_Minh trong phạm vi dữ liệu ứng dụng. Topic của buổi lấy từ catalog. Gợi ý lọc bài hoạt động đúng level, ưu tiên chủ đề quan tâm rồi bài có ít lần hoàn thành hơn. Đây là số liệu hoạt động, không dùng để kết luận năng lực dài hạn.

[Hợp đồng API](../../api/learning.md).

## Kiểm chứng

- Maven verify trên MySQL 8.4.13 đạt 38/38, 0 skip. Flyway áp V7 trên schema mới và nâng cấp từ V2. Test kiểm tra truy cập chéo, lưu trùng, ghi attempt idempotent, nguồn bị xóa, callback muộn, giữ thẻ khi xóa một buổi, xóa toàn bộ theo owner. Kiểm tra HTTP với Session và từ chối DELETE thiếu CSRF.
- Thống kê được kiểm tra sau timeout, retry, replay, HINT, xóa và mốc qua nửa đêm giờ Việt Nam. Chỉ correction ERROR được đếm trong nhóm lỗi. Gợi ý có lọc B1 và ưu tiên chủ đề quan tâm trong fixture.
- Frontend qua typecheck, lint và build. Playwright ở 390px dùng API giả lập kiểm tra lưu thẻ, mở đáp án bằng bàn phím, đánh dấu đã nhớ, xác nhận cả hai phạm vi xóa, giữ thẻ sau xóa buổi và thống kê trống sau xóa. Không tràn ngang. Luồng tích hợp Gemini live chưa được kiểm thử ở giai đoạn này.
