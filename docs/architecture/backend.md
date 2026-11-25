# Backend Java / Spring Boot

Backend dùng Java 21 / Spring Boot 4.1.2 với Maven Wrapper. Profile bootstrap tắt persistence, profile mặc định kết nối MySQL. GET /api/v1/system/health kiểm tra process HTTP.

## Module và phân lớp
Base package vn.englishconversation, chia auth, user, scenario, conversation, ai, feedback, review, statistics, admin; system sở hữu HTTP health, config sở hữu cấu hình, common cho phần xuyên suốt dùng chung. Module chưa có tính năng chỉ ghi trách nhiệm trong package-info.java.

Module có chức năng thực được tổ chức theo `controller/`, `service/`, `repository/`, `entity/`, `dto/` tùy trách nhiệm. Xem [quy ước đặt file backend](../development/coding-conventions.md#cấu-trúc-backend-spring-boot) và [cây mã nguồn hiện tại](../../backend/README.md).

Trong mỗi module: Controller → Service → Repository. Entity dùng lưu trữ, DTO dùng hợp đồng HTTP. Không trả entity JPA trực tiếp. Dùng Bean Validation và xử lý exception tập trung.

| Package | Trách nhiệm |
| --- | --- |
| `auth/controller`, `auth/service`, `auth/job` | API tài khoản, khôi phục mật khẩu, giới hạn request và tác vụ dọn tài khoản. |
| `profile/controller`, `profile/service` | Hồ sơ, ảnh đại diện và lịch luyện tập của người học. |
| `mail/provider`, `mail/job` | Gateway/adapter SMTP và worker xử lý hàng đợi email tài khoản, lời nhắc luyện tập. |
| `voice/dto`, `voice/service` | Hợp đồng âm thanh hội thoại, kiểm tra WAV và lưu/dọn âm thanh tạm. |
| `common/repository` | `JdbcStore` cung cấp thao tác thực thi SQL dùng chung. SQL nghiệp vụ thuộc lớp sử dụng nó. |

Các lớp xác thực dùng để đọc Session cũ tiếp tục ở package đã ghi trong phần tương thích bên dưới.

## Xác thực và dữ liệu
Đã chốt Session cookie + Spring Security + Spring Session JDBC/MySQL theo [ADR 0004](../decisions/0004-session-authentication.md); đã hiện thực dependency/schema/API theo [ADR 0007](../decisions/0007-auth-implementation.md). Không tạo JWT/refresh token song song. Kiểm tra role, trạng thái tài khoản và owner ở server, không tin userId client. PasswordEncoder băm mật khẩu; không mã hóa có thể giải ngược.

Thiết kế auth dùng principal users.id ổn định, cookie HttpOnly/Secure khi HTTPS/SameSite phù hợp cùng origin, session ID rotation khi login và persist SecurityContext đúng repository. Giữ CSRF cho SPA, làm mới token theo vòng đời login/logout. ADR 0005 chốt idle 30 phút, nhiều thiết bị, logout phiên hiện tại; khóa tài khoản/đổi mật khẩu hoặc quyền thu hồi phiên cũ và kiểm tra trạng thái ở request tiếp theo. Hai bảng phiên do thư viện quản lý, schema qua Flyway, không JPA entity; runtime đã triển khai auth và security_version để chặn phiên cũ sau revocation.

Runtime công khai GET health/CSRF, POST register/login có CSRF; route auth bảo vệ và /admin/** yêu cầu ADMIN; denyAll phần chưa triển khai. Không form/basic login hoặc tài khoản sinh tự động. Error DTO/handler tập trung đã có; xem [API](../api/auth.md). Profile bootstrap chỉ có health.

Truy vấn danh sách phải phân trang, giới hạn size và thứ tự ổn định. Lỗi dùng cấu trúc thống nhất gồm code, message, fieldErrors khi có và requestId; không trả stack trace/bí mật.

## Hội thoại và giao dịch

P3 triển khai API buổi luyện, snapshot, lượt văn bản và HINT. Xem [hợp đồng API](../api/practice.md) và [Kế hoạch G6](../development/full-system-implementation-plan.md#g6--luyện-hội-thoại-với-ai).
Lưu yêu cầu với định danh chống trùng và trạng thái trong transaction ngắn; gọi Gemini ngoài transaction; lưu kết quả bằng transaction ngắn tiếp theo. Không giữ khóa DB hoặc connection chờ dịch vụ ngoài.

Mỗi buổi chỉ xử lý một lượt đang chờ theo thiết kế ban đầu; cần kiểm soát cạnh tranh ở backend, không chỉ disable nút. Retry phải phân biệt yêu cầu đã hoàn tất, đang chạy và thất bại. Chính sách lưu tin nhắn chờ phải được thiết kế cùng CHAT-01.

## Kiểm thử
JUnit cho nghiệp vụ; Spring Boot Test/MockMvc cho HTTP và bảo mật; kiểm thử dữ liệu với MySQL tương thích khi có migration/query. Không gọi Gemini thật trong test mặc định.

## Quản trị

Spring Security giới hạn `/api/v1/admin/**` cho ADMIN. Giao dịch ghi kiểm tra lại tài khoản thực hiện, thu hồi Session khi thay khóa/quyền, và ghi audit V18 trong cùng giao dịch MySQL. Cập nhật trình độ và biến thể vai thuộc cùng giao dịch với lưu tình huống. Chỉ API quản trị đọc chỉ dẫn AI để biên tập. Buổi luyện giữ chỉ dẫn trong snapshot để tiếp tục sau khi catalog thay đổi. Catalog và hồ sơ học tập vẫn đọc theo trạng thái công khai. Chi tiết buổi luyện tiếp tục kiểm tra owner. [API quản trị](../api/admin.md)

## Tương thích package và Session JDBC

Refactor ngày 20/11/2026 chuyển principal mới sang auth.security.AccountPrincipal. Record ở auth.AccountPrincipal được giữ như cầu nối deserialize (`readResolve`, serialVersionUID 0) vì tên class là một phần dữ liệu SecurityContext serialized. Phiên cũ được đọc thành principal mới, không cần migration DB hay logout hàng loạt. Login mới không dùng type deprecated. Có regression test ghi context với class cũ vào ATTRIBUTE_BYTES và GET /auth/me qua HTTP/MySQL thật.

Chỉ bỏ bridge sau khi xác minh không còn dữ liệu phiên dùng class cũ; request chỉ đọc có thể chưa rewrite attribute nên không mặc định đủ 30 phút sau deploy. Bản cũ chưa biết principal mới không đọc được phiên mới khi rollback; cần quy trình revoke phiên hoặc bản tương thích trước rollback. Tên field JSON/API và Flyway V1/V2 không đổi trong refactor này.

## Hồ sơ và catalog (P2)

profile/ theo controller/service/repository/entity/dto; learner_profiles dùng JPA. scenario/ theo controller/service/repository/dto; catalog đọc bằng NamedParameterJdbcTemplate với projection công khai, không tạo entity chỉ để đọc. interests được thay qua JDBC trong transaction của ProfileService, dùng chung datasource/transaction với JPA; có test rollback và ownership. Signup gọi ProfileService trong transaction tạo user.

V3 tạo năm bảng/FK/index/CHECK, backfill learner cũ; V4 seed 6 topic/8 scenario/16 level links một lần. Catalog API không SELECT ai_instruction. Mở buổi/chụp snapshot và nạp instruction cho Gemini thuộc P3, không được suy ra đã triển khai từ catalog hiện tại. Hợp đồng [API P2](../api/profile-catalog.md).

## Mở rộng tài khoản bổ sung

G1 dùng token một lần cho khôi phục mật khẩu, mail adapter SMTP và outbox mã hóa. Lịch và worker nhắc học dùng lease/retry. Avatar được kiểm tra trước khi lưu và job xóa quản lý việc dọn dữ liệu. `MAIL_ENABLED=false` là mặc định. Gọi SMTP, storage và provider ngoài transaction. [Quy tắc sản phẩm](../decisions/0005-initial-product-rules.md) · [Dữ liệu và giao dịch](database.md#3-từ-điển-46-bảng-và-dữ-liệu-mẫu) · [Hợp đồng API](../api/auth.md).

### SMTP và tác vụ nền bổ sung

| Môi trường | Quy tắc gửi email |
| --- | --- |
| Test tự động | Dùng adapter giả lập hoặc SMTP bắt thư nội bộ. |
| Local / staging | Dùng Mailpit nội bộ để kiểm tra liên kết, nội dung và retry. Chỉ dùng dữ liệu thử và tắt chuyển tiếp/relay ra ngoài. [Cấu hình Mailpit](https://mailpit.axllent.org/docs/configuration/) |
| Production | SMTP thật chỉ bật khi có cấu hình sender, secret và TLS hợp lệ, cùng quyền gửi của môi trường triển khai. Mặc định tắt khi chưa cấu hình. |

Trong thiết kế bản đầu, backend dùng trạng thái tài khoản và quyền truy cập cho AI và nhắc học. Liên kết email dùng để chứng minh quyền truy cập hộp thư trong luồng đặt lại mật khẩu.

Backend dùng SMTP qua Spring Boot Mail/JavaMailSender, phiên bản do BOM Spring Boot 4.1.2 quản lý (Apache-2.0). Cấu hình môi trường: host/port/username/password, TLS, from, public frontend URL tin cậy; không lấy URL từ Host header. SMTP mặc định tắt; thiếu cấu hình thì không báo đã gửi. Chọn nhà cung cấp và kiểm tra quota/chi phí trước bật thực tế; không gửi email thật trong kiểm thử mặc định.

Scheduler tạo delivery duy nhất theo (schedule_id, scheduled_for UTC); worker claim bằng transaction ngắn và lease, gọi SMTP ngoài transaction, cập nhật kết quả có kiểm tra claim/version. Kiểm tra lại trạng thái tài khoản, lịch và hạn gửi ngay trước gửi. Không giữ connection/khóa DB khi chờ SMTP. Khi sửa/tắt/xóa lịch, không hứa thu hồi email đã được SMTP nhận hoặc đang gửi.

Lời nhắc hết hạn sau 30 phút kể từ giờ dự kiến; thử tối đa 3 lần (sau 1 và 5 phút), không vượt hạn. Khi restart chỉ xử lý lần còn trong cửa sổ, bỏ lần cũ. Lease hết hạn sau crash cần đối soát; SMTP có thể nhận thư trước khi ứng dụng ghi SENT, nên có khả năng trùng khi kết quả không rõ, không cam kết exactly-once. SENT nghĩa SMTP chấp nhận, không bảo đảm inbox. Lưu mã lỗi phân loại, không lưu nguyên văn phản hồi chứa dữ liệu cá nhân.

Mail đặt lại mật khẩu cần payload chứa token gốc trong thời gian gửi: dùng outbox mã hóa bằng khóa môi trường tách DB, không lưu token rõ. Xóa payload sau thành công/hết hạn/hủy, giới hạn retry theo TTL token. Khóa không nằm trong Git/log/backup DB; cần hướng dẫn rotation trước vận hành. Job phải kiểm tra token còn hiệu lực trước gửi. Không dùng bảng token băm làm nguồn dựng lại liên kết.
