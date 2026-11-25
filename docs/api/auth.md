# Hợp đồng API tài khoản — AUTH-01

Tài liệu này ghi hợp đồng áp dụng cho G1. Liên kết xác thực qua email chỉ dùng để đặt lại mật khẩu. [Yêu cầu bản đầu](../product/requirements.md).

Đã triển khai giai đoạn 1. Base path `/api/v1`, JSON, cùng origin qua Vite/reverse proxy. Không dùng JWT hoặc lưu mã phiên ở client storage. ID trong JSON là chuỗi.

## Endpoint

| Method / path | Quyền | Body / kết quả |
| --- | --- | --- |
| GET /auth/csrf | Công khai | 200 `{token, headerName}`; tạo phiên nếu cần, `Cache-Control: no-store` |
| POST /auth/register | Công khai + CSRF | `{email,password}` → 201 User; không tự đăng nhập, chỉ tạo LEARNER |
| POST /auth/login | Công khai + CSRF | `{email,password}` → 200 User; đổi mã phiên, xóa token CSRF cũ, persist SecurityContext JDBC |
| GET /auth/me | Đã đăng nhập | 200 User; 401 nếu hết hạn/khóa/phiên bị thu hồi |
| POST /auth/password | Đã đăng nhập + CSRF | `{currentPassword,newPassword}` → 204; thu hồi tất cả phiên và yêu cầu đăng nhập lại |
| POST /auth/logout | CSRF | 204; kết thúc phiên hiện tại, xóa cookie SESSION; thiết bị khác giữ phiên |
User gồm `id`, `email`, `role` và `status`. Đăng ký tạo tài khoản LEARNER. Các API quản trị tài khoản thuộc G10. [Hợp đồng API quản trị](admin.md).

## Validation và lỗi

- Email: strip khoảng trắng hai đầu, lowercase theo Locale.ROOT; địa chỉ ASCII, tối đa 254 ký tự, local-part tối đa 64, domain có các nhãn hợp lệ và TLD chữ cái 2–63 ký tự. Không hỗ trợ địa chỉ Unicode/quoted local-part trong bản đầu. Không bỏ dấu chấm hoặc phần `+tag`.
- MySQL lưu email đã chuẩn hóa bằng ASCII/ascii_bin, CHECK lowercase/trim/không whitespace và UNIQUE(email). DB từ chối ghi email chưa chuẩn hóa thay vì âm thầm sửa. API chuẩn hóa trước truy vấn/ghi; UNIQUE xử lý cả đăng ký đồng thời.
- Mật khẩu mới 12–128 Java UTF-16 code units; không strip/trim/lowercase, kể cả khoảng trắng đầu/cuối. PBKDF2-HMAC-SHA256 với salt ngẫu nhiên, 310.000 iterations theo encoder đã chọn. Không tự thay thuật toán nếu chưa có kế hoạch chuyển hash.
- Lỗi `{code,message,fieldErrors,requestId}`. 400 validation/password hiện tại sai; 401 thông tin đăng nhập không hợp lệ/phiên kết thúc; 403 CSRF hoặc thiếu quyền; 409 email trùng; 415 Content-Type sai; 500 thông báo chung không lộ stack trace.
- Login sai mật khẩu hoặc tài khoản khóa dùng cùng thông báo chung. Không trả entity JPA.

## Client và vòng đời phiên

1. Trước **mỗi** mutation, GET /auth/csrf và gửi token nguyên trạng trong headerName được trả về. Token trả về là token masked của Spring Security; không đọc cookie HttpOnly.
2. Fetch dùng credentials same-origin. Sau login/logout token cũ không dùng lại; client hiện tại lấy token mới ở mutation tiếp theo. Không tự replay mutation khi nhận 403 hoặc lỗi mạng.
3. SecurityContextRepository lưu context sau SessionAuthenticationStrategy (rotation + CSRF reset). Principal chỉ chứa users.id/security_version, credential null; authority chỉ gồm role.
4. Idle timeout 30 phút, cho nhiều thiết bị. Cleanup JDBC mỗi phút; kiểm tra expiry ở request không chờ cron. Logout hiện tại; đổi mật khẩu/quyền/khóa thu hồi tất cả phiên đích.
5. Mỗi request có principal đối chiếu users.status, security_version và role thực tế. Một phiên cũ được lưu lại do race vẫn bị từ chối ở request tiếp theo; request đang thực thi không được hứa thu hồi ngược thời gian.
6. Đổi mật khẩu/role/status và xóa phiên trong service có transaction, lỗi không được báo thành công. Login khóa hàng tài khoản khi xác thực để tránh đọc password/role cũ giữa các truy vấn.
7. Cookie SESSION: Path=/, HttpOnly, SameSite=Lax; local HTTP không Secure, profile production ép Secure=true. Chưa triển khai CORS khác origin/remember-me.

Ví dụ gọi tiếp từ frontend: lấy `auth/csrf`, POST `auth/login`, GET `auth/me`; giữ cookie bằng trình duyệt. Không chép giá trị cookie/token vào tài liệu hoặc log.

## Khôi phục mật khẩu và xóa tài khoản

Base `/api/v1`. Mutation đều yêu cầu CSRF, kể cả endpoint công khai. Endpoint owner lấy user từ Session. Các giới hạn gửi và thời hạn token được mô tả trong [luồng người dùng](../product/user-flows.md).

| Method / path bổ sung | Quyền, body và kết quả |
| --- | --- |
| POST /auth/password-reset/request | Công khai + CSRF, `{email}`; 202 thông báo chung, không tiết lộ tài khoản |
| POST /auth/password-reset/confirm | Công khai + CSRF, `{token,newPassword}`; 204, thu hồi mọi phiên; lỗi token chung 400 |
| DELETE /auth/account | Session ACTIVE, `{currentPassword,confirmation:"DELETE"}`; 202 sau khi chặn tài khoản, hủy phiên/cookie và nhận job xóa bền vững; 400 mật khẩu/xác nhận sai |

Đăng ký trả 201 và chuyển người dùng sang luồng đăng nhập. Quyền gọi AI và cài nhắc học dựa trên phiên, quyền và trạng thái tài khoản. Profile response có `avatarUrl` nullable. Khi chấp nhận xóa tài khoản, backend thu hồi phiên. Request bảo vệ tiếp theo với cookie cũ trả `AUTHENTICATION_REQUIRED`.

429 có `Retry-After` khi vượt giới hạn. Liên kết dùng URL frontend cấu hình cố định với fragment `#/reset-password`. Frontend chỉ gửi token qua POST khi người dùng xác nhận mật khẩu mới.

Các ca đăng ký, reset một lần, thu hồi phiên và khôi phục qua hộp thư thử được ghi trong [bằng chứng nghiệm thu G1](../development/acceptance/g1-account-acceptance.md).
