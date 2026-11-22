# ADR 0005: Quy tắc sản phẩm phiên bản đầu

## Trạng thái
Đã chốt theo yêu cầu giữ các lựa chọn trong ảnh của chủ dự án. Đây là quy tắc thiết kế, chưa triển khai tính năng hoặc migration. Ba nhóm retention/thông báo âm thanh/trình duyệt sau đó được chấp nhận tại ADR 0006.

## Các quyết định đã chốt

| Nội dung | Quyết định |
| --- | --- |
| Tài khoản | Email/mật khẩu, đổi mật khẩu. Xác minh email và quên mật khẩu để giai đoạn sau, không mặc định là chức năng bản đầu |
| Chuẩn hóa email | Trim khoảng trắng đầu/cuối, không phân biệt hoa/thường khi đăng ký/đăng nhập, UNIQUE cùng quy tắc. Lưu email chuẩn hóa thống nhất; không tự bỏ dấu chấm hoặc phần + theo quy tắc riêng của nhà cung cấp |
| Session | 30 phút không hoạt động theo idle timeout phía server; cho phép nhiều thiết bị; logout chỉ kết thúc phiên hiện tại. Không polling để tự gia hạn phiên khi người dùng không thao tác. Khóa/đổi quyền/mật khẩu vẫn thu hồi phiên cũ theo ADR 0004 |
| Admin đầu tiên | Khởi tạo có kiểm soát, đọc mật khẩu từ môi trường rồi băm; không có mật khẩu mặc định trong Git, không có đăng ký ADMIN công khai. Chạy lại không ghi đè mật khẩu/admin sẵn có; không log secret |
| Buổi luyện | Một lượt đang xử lý/buổi, tối đa 20 lượt người học; AI mở đầu không tính vào 20 |
| Hỗ trợ | Người học có thể yêu cầu gợi ý khi bí câu; sửa lỗi tập trung ở phản hồi cuối buổi |
| Ôn tập | FLASHCARD trước; FILL_BLANK và lịch ôn tự động để giai đoạn sau |

## Chi tiết để tránh hiểu khác nhau
- Mật khẩu không áp dụng trim/lowercase như email. Chuẩn hóa email phải dùng cùng hàm/quy tắc ở đăng ký, login, cập nhật và UNIQUE database. Collation cụ thể chọn khi migration nhưng không được làm sai quy tắc đã chốt.
- 30 phút là idle timeout, không phải tuyệt đối 30 phút kể từ login. Chi tiết last-access của thư viện phải được kiểm thử cùng luồng SPA; không tự thêm remember-me hoặc giới hạn một thiết bị.
- Tạo admin là công cụ/bootstrap được gọi có chủ đích, không endpoint nâng quyền. Khi có admin rồi, không tự reset credential mỗi lần app khởi động; secrets cấu hình chỉ giữ khi thực sự cần vận hành.
- Một câu LEARNER được chấp nhận vào messages tính một lượt; gửi lỗi trước chấp nhận không tính. Retry câu đã lưu không tính thêm. Sau lượt 20 không nhận lượt 21, vẫn cho hoàn tất/retry phản hồi của lượt 20 và kết thúc buổi.
- Gợi ý là tác vụ HINT gắn session, không thêm turn/message vào transcript chính và không tính vào 20 lượt người học. Nội dung hiển thị tạm trên giao diện; không hứa lưu lịch sử gợi ý. HINT vẫn chịu kiểm tra owner, quota API và trạng thái buổi; ngân sách/hạn mức riêng chưa chốt. Không chạy đồng thời với lượt hội thoại đang xử lý trong bản đầu.
- Review schema có thể giữ trường dành cho mở rộng nhưng bản đầu chỉ phát sinh review_mode FLASHCARD. Không coi next_review_at/daily_goal_minutes là bằng chứng có lịch nhắc tự động.

## Quyết định bổ sung sau này
[ADR 0006](0006-phase0-operating-decisions.md) chốt [vòng đời dữ liệu/browser](../product/data-and-browser-proposal.md), audio 60 giây, nhóm hai người và hạn nộp 28/12/2026. Model/provider tier còn cần xác minh; hiện chủ dự án chỉ cho chuẩn bị probe offline và hướng dẫn key, không live call hoặc phát sinh phí.
