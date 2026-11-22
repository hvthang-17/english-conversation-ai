# Backend Java / Spring Boot

Backend dùng Java 21 / Spring Boot 4.1.2 với Maven Wrapper. Profile bootstrap tắt persistence, profile mặc định kết nối MySQL. GET /api/v1/system/health kiểm tra process HTTP.

## Module và phân lớp
Base package vn.englishconversation, chia auth, user, scenario, conversation, ai, feedback, review, statistics, admin; system sở hữu HTTP health, config sở hữu cấu hình, common cho phần xuyên suốt dùng chung. Module chưa có tính năng chỉ ghi trách nhiệm trong package-info.java.

Module có chức năng thực được tổ chức theo `controller/`, `service/`, `repository/`, `entity/`, `dto/` phù hợp trách nhiệm. Auth có thêm `security/` (principal/filter) và `bootstrap/` (admin đầu tiên). Exception/handler chung ở `common/exception`, hợp đồng lỗi ở `common/dto`. Không tạo các thư mục trống hoặc ServiceImpl chỉ để đủ cây. Cây file hiện tại nằm trong [README backend](../../backend/README.md).

Trong mỗi module: Controller → Service → Repository. Entity dùng lưu trữ, DTO dùng hợp đồng HTTP. Không trả entity JPA trực tiếp. Dùng Bean Validation và xử lý exception tập trung.

## Xác thực và dữ liệu
Đã chốt Session cookie + Spring Security + Spring Session JDBC/MySQL theo [ADR 0004](../decisions/0004-session-authentication.md); đã hiện thực dependency/schema/API theo [ADR 0007](../decisions/0007-auth-implementation.md). Không tạo JWT/refresh token song song. Kiểm tra role, trạng thái tài khoản và owner ở server, không tin userId client. PasswordEncoder băm mật khẩu; không mã hóa có thể giải ngược.

Thiết kế auth dùng principal users.id ổn định, cookie HttpOnly/Secure khi HTTPS/SameSite phù hợp cùng origin, session ID rotation khi login và persist SecurityContext đúng repository. Giữ CSRF cho SPA, làm mới token theo vòng đời login/logout. ADR 0005 chốt idle 30 phút, nhiều thiết bị, logout phiên hiện tại; khóa tài khoản/đổi mật khẩu hoặc quyền thu hồi phiên cũ và kiểm tra trạng thái ở request tiếp theo. Hai bảng phiên do thư viện quản lý, schema qua Flyway, không JPA entity; runtime đã triển khai auth và security_version để chặn phiên cũ sau revocation.

Runtime công khai GET health/CSRF, POST register/login có CSRF; route auth bảo vệ và /admin/** yêu cầu ADMIN; denyAll phần chưa triển khai. Không form/basic login hoặc tài khoản sinh tự động. Error DTO/handler tập trung đã có; xem [API](../api/auth.md). Profile bootstrap chỉ có health.

Truy vấn danh sách phải phân trang, giới hạn size và thứ tự ổn định. Lỗi dùng cấu trúc thống nhất gồm code, message, fieldErrors khi có và requestId; không trả stack trace/bí mật.

## Hội thoại và giao dịch

P3 triển khai API buổi luyện, snapshot, lượt văn bản và HINT. Xem [hợp đồng API](../api/practice.md) và [cấu hình Gemini](../development/phase3/README.md).
Lưu yêu cầu với định danh chống trùng và trạng thái trong transaction ngắn; gọi Gemini ngoài transaction; lưu kết quả bằng transaction ngắn tiếp theo. Không giữ khóa DB hoặc connection chờ dịch vụ ngoài.

Mỗi buổi chỉ xử lý một lượt đang chờ theo thiết kế ban đầu; cần kiểm soát cạnh tranh ở backend, không chỉ disable nút. Retry phải phân biệt yêu cầu đã hoàn tất, đang chạy và thất bại. Chính sách lưu tin nhắn chờ phải được thiết kế cùng CHAT-01.

## Kiểm thử
JUnit cho nghiệp vụ; Spring Boot Test/MockMvc cho HTTP và bảo mật; kiểm thử dữ liệu với MySQL tương thích khi có migration/query. Không gọi Gemini thật trong test mặc định.

## Quản trị

Spring Security giới hạn `/api/v1/admin/**` cho ADMIN. Giao dịch ghi kiểm tra lại tài khoản thực hiện, thu hồi Session khi thay khóa/quyền, và ghi audit V10 trong cùng giao dịch MySQL. Cập nhật trình độ thuộc cùng giao dịch với lưu tình huống. Catalog và hồ sơ học tập vẫn đọc theo trạng thái công khai. Chi tiết buổi luyện tiếp tục kiểm tra owner. [API quản trị](../api/admin.md)

## Tương thích package và Session JDBC

Refactor ngày 20/11/2026 chuyển principal mới sang auth.security.AccountPrincipal. Record ở auth.AccountPrincipal được giữ như cầu nối deserialize (`readResolve`, serialVersionUID 0) vì tên class là một phần dữ liệu SecurityContext serialized. Phiên cũ được đọc thành principal mới, không cần migration DB hay logout hàng loạt. Login mới không dùng type deprecated. Có regression test ghi context với class cũ vào ATTRIBUTE_BYTES và GET /auth/me qua HTTP/MySQL thật.

Chỉ bỏ bridge sau khi xác minh không còn dữ liệu phiên dùng class cũ; request chỉ đọc có thể chưa rewrite attribute nên không mặc định đủ 30 phút sau deploy. Bản cũ chưa biết principal mới không đọc được phiên mới khi rollback; cần quy trình revoke phiên hoặc bản tương thích trước rollback. Tên field JSON/API và Flyway V1/V2 không đổi trong refactor này.

## Hồ sơ và catalog (P2)

profile/ theo controller/service/repository/entity/dto; learner_profiles dùng JPA. scenario/ theo controller/service/repository/dto; catalog đọc bằng NamedParameterJdbcTemplate với projection công khai, không tạo entity chỉ để đọc. interests được thay qua JDBC trong transaction của ProfileService, dùng chung datasource/transaction với JPA; có test rollback và ownership. Signup gọi ProfileService trong transaction tạo user.

V3 tạo năm bảng/FK/index/CHECK, backfill learner cũ; V4 seed 6 topic/8 scenario/16 level links một lần. Catalog API không SELECT ai_instruction. Mở buổi/chụp snapshot và nạp instruction cho Gemini thuộc P3, không được suy ra đã triển khai từ catalog hiện tại. Hợp đồng [API P2](../api/profile-catalog.md).
