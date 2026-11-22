# Giai đoạn 7 — Quản trị hệ thống

Đăng nhập bằng tài khoản quản trị và chọn **Quản trị**. Với môi trường mới, tạo quản trị viên đầu tiên theo [hướng dẫn bootstrap P1](../phase1/README.md). Flyway áp dụng V10 khi backend khởi động.

- **Tổng quan** hiển thị số tài khoản người học, số tài khoản khóa, chủ đề đang mở, tình huống công khai và buổi luyện hoàn thành. Số liệu AI tính theo từng lần gọi trong 24 giờ gần nhất, gồm kết quả thành công, thất bại và chưa rõ. Tổng lượt gọi bao gồm các lần còn đang xử lý.
- **Tài khoản** cho tìm theo email và xác nhận khóa/mở. Khóa hoặc đổi quyền thu hồi Session hiện có. Tài khoản vừa mở cần đăng nhập lại. Giao diện khóa/mở tài khoản khác giữ nguyên quyền của họ.
- **Nội dung luyện tập** quản lý chủ đề và tình huống trong từng chủ đề. Chọn A2/B1 trước khi công khai. Ẩn chủ đề hoặc tình huống giữ danh mục trong cơ sở dữ liệu, để lịch sử vẫn tham chiếu được. Mỗi buổi tiếp tục sử dụng snapshot tình huống riêng.

Audit được ghi cùng transaction với thay đổi MySQL. Bản ghi không lưu email, mật khẩu, khóa API, prompt, bản chép lời hoặc nội dung biên tập. Java tiếp tục áp dụng kiểm tra Session và role phía server. [Hợp đồng API](../../api/admin.md)

## Kiểm chứng

- 43 kiểm thử đạt trên MySQL 8.4.13 với Gemini giả lập. Flyway áp dụng V10 trên schema rỗng và schema nâng cấp có dữ liệu. Các ca HTTP xác nhận phân quyền, CSRF, cookie cũ bị thu hồi, quyền sở hữu hội thoại, kiểm tra công khai theo chủ đề/trình độ và actor/target của audit.
- Frontend đạt typecheck, lint và build. Playwright tại 390px với API giả lập kiểm tra tổng quan, tìm và khóa/mở tài khoản, cập nhật trình độ, ẩn chủ đề và chặn màn hình quản trị đối với người học.

Phần quản trị của M4 đã kiểm chứng. Nghiệm thu giọng nói trên thiết bị thật vẫn được theo dõi tại [P6.8](../phase6/README.md).
