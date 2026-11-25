# ADR 0005: Quy tắc sản phẩm phiên bản đầu

Quyết định này áp dụng cho G1. Email là định danh đăng nhập. Liên kết xác thực qua email chỉ dùng để đặt lại mật khẩu. [Yêu cầu áp dụng](../product/requirements.md).

## Trạng thái
Đã chốt theo yêu cầu giữ các lựa chọn trong ảnh của chủ dự án. Đây là quy tắc thiết kế, chưa triển khai tính năng hoặc migration. Ba nhóm retention/thông báo âm thanh/trình duyệt sau đó được chấp nhận tại ADR 0006.

## Các quyết định đã chốt

| Nội dung | Quyết định |
| --- | --- |
| Tài khoản | Đăng ký bằng email/mật khẩu, đăng nhập ngay sau đăng ký, đổi mật khẩu khi đã đăng nhập và khôi phục bằng liên kết email khi quên mật khẩu |
| Chuẩn hóa email | Trim khoảng trắng đầu/cuối, không phân biệt hoa/thường khi đăng ký/đăng nhập, UNIQUE cùng quy tắc. Lưu email chuẩn hóa thống nhất; không tự bỏ dấu chấm hoặc phần + theo quy tắc riêng của nhà cung cấp |
| Session | 30 phút không hoạt động theo idle timeout phía server; cho phép nhiều thiết bị; logout chỉ kết thúc phiên hiện tại. Không polling để tự gia hạn phiên khi người dùng không thao tác. Khóa/đổi quyền/mật khẩu vẫn thu hồi phiên cũ theo ADR 0004 |
| Admin đầu tiên | Khởi tạo có kiểm soát, đọc mật khẩu từ môi trường rồi băm; không có mật khẩu mặc định trong Git, không có đăng ký ADMIN công khai. Chạy lại không ghi đè mật khẩu/admin sẵn có; không log secret |
| Buổi luyện | Một lượt đang xử lý/buổi, tối đa 20 lượt người học; AI mở đầu không tính vào 20 |
| Hỗ trợ | Người học có thể yêu cầu gợi ý khi bí câu; sửa lỗi tập trung ở phản hồi cuối buổi |
| Ôn tập | G3 triển khai `FLASHCARD` trước, rồi thêm `QUICK_QUIZ` chọn nghĩa tiếng Việt của từ hoặc mẫu câu tiếng Anh. Điền từ và lịch ôn tự động thuộc các giai đoạn sau. |

## Chi tiết để tránh hiểu khác nhau
- Mật khẩu không áp dụng trim/lowercase như email. Chuẩn hóa email phải dùng cùng hàm/quy tắc ở đăng ký, login, cập nhật và UNIQUE database. Collation cụ thể chọn khi migration nhưng không được làm sai quy tắc đã chốt.
- 30 phút là idle timeout, không phải tuyệt đối 30 phút kể từ login. Chi tiết last-access của thư viện phải được kiểm thử cùng luồng SPA; không tự thêm remember-me hoặc giới hạn một thiết bị.
- Tạo admin là công cụ/bootstrap được gọi có chủ đích, không endpoint nâng quyền. Khi có admin rồi, không tự reset credential mỗi lần app khởi động; secrets cấu hình chỉ giữ khi thực sự cần vận hành.
- Một câu LEARNER được chấp nhận vào messages tính một lượt; gửi lỗi trước chấp nhận không tính. Retry câu đã lưu không tính thêm. Sau lượt 20 không nhận lượt 21, vẫn cho hoàn tất/retry phản hồi của lượt 20 và kết thúc buổi.
- Gợi ý là tác vụ HINT gắn session, không thêm turn/message vào transcript chính và không tính vào 20 lượt người học. Nội dung hiển thị tạm trên giao diện; không hứa lưu lịch sử gợi ý. HINT vẫn chịu kiểm tra owner, quota API và trạng thái buổi; ngân sách/hạn mức riêng chưa chốt. Không chạy đồng thời với lượt hội thoại đang xử lý trong bản đầu.
- Flashcard ghi lượt ôn và cập nhật trạng thái thẻ theo lựa chọn **Cần ôn / Đã nhớ**. Quiz do backend chấm, ghi đúng/sai và thời điểm ôn, giữ trạng thái do người học chọn. Cả hai luồng chống ghi trùng khi gửi lại. Khi thiếu lựa chọn quiz có nghĩa phân biệt rõ, người học có thể dùng flashcard. G3 để `next_review_at` trống. [Kế hoạch G3](../development/full-system-implementation-plan.md#g3--từ-vựng-mẫu-câu-hội-thoại-mẫu-và-ôn-tập).

## Quyết định bổ sung sau này
[ADR 0006](0006-phase0-operating-decisions.md) chốt [vòng đời dữ liệu/browser](../product/data-and-browser-proposal.md), audio 60 giây, nhóm hai người và hạn nộp 28/12/2026. Model/provider tier còn cần xác minh; hiện chủ dự án chỉ cho chuẩn bị probe offline và hướng dẫn key, không live call hoặc phát sinh phí.

## Quy tắc tài khoản áp dụng cho G1

Bỏ xác minh email độc lập khỏi đăng ký, đăng nhập và trang Tài khoản. Truy cập AI và cài đặt nhắc học dựa vào phiên, quyền và trạng thái tài khoản. Người học có thể chủ động bật email nhắc học theo phạm vi G8.

Khi quên mật khẩu, gửi liên kết chứa token dùng một lần đến email đã đăng ký. Token lưu dưới dạng băm và hết hạn sau 30 phút. Phản hồi yêu cầu gửi liên kết dùng thông báo chung. Sau khi đặt lại mật khẩu, thu hồi toàn bộ phiên cùng token đặt lại còn lại và yêu cầu đăng nhập lại. Giữ Session cookie, CSRF và phân quyền backend.

Lưu một lịch tuần/tài khoản theo giờ địa phương và múi giờ IANA; mục tiêu phút không nhân bản. Worker SMTP chạy server-side với delivery duy nhất, lease/retry có giới hạn, không transaction chờ mạng và không hứa exactly-once. Mail mặc định tắt; outbox chứa token phải mã hóa và dọn payload. Không tự chọn nhà cung cấp hoặc gửi email thật trong kiểm thử.

Avatar là file xử lý lại trong storage do server kiểm soát, không URL client tùy ý. Xóa tài khoản là quy trình chặn truy cập ngay, thu hồi phiên rồi dọn dữ liệu/file có tracking và retry; bảo toàn catalog/audit dùng chung và yêu cầu xóa qua restore backup.

G1.3 đã gỡ luồng xác minh riêng khỏi mã tái sử dụng và kiểm tra khôi phục mật khẩu qua Mailpit. [Bằng chứng G1](../development/acceptance/g1-account-acceptance.md).
