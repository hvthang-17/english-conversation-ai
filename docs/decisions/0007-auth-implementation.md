# ADR 0007: Hiện thực tài khoản và thu hồi Session

## Trạng thái
Đã hiện thực ngày 20/11/2026 cho giai đoạn 1, kế thừa [ADR 0004](0004-session-authentication.md) và [ADR 0005](0005-initial-product-rules.md). Không thay lựa chọn Session/JDBC.

## Quyết định
- Spring Boot 4.1.2 quản lý Spring Session JDBC **4.1.1**; V2 sao nguyên schema MySQL từ JAR đã resolve, chỉ thêm dòng provenance. Spring Session initialize-schema=never, Flyway sở hữu V1 users và V2 hai bảng session.
- Email chỉ nhận ASCII với domain chuẩn trong bản đầu; normalize strip/Locale.ROOT lowercase, không đổi `+`/dấu chấm. CHECK + UNIQUE trên dữ liệu canonical/ascii_bin để không gộp dấu hoặc phụ thuộc collation không rõ. Chi tiết [API](../api/auth.md).
- Password mới 12–128 ký tự Java, giữ nguyên nội dung. Dùng Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8 (PBKDF2-HMAC-SHA256, 310.000 iterations, salt 16 bytes, hash 256 bits); không thêm thư viện crypto ngoài JDK. PBKDF2 phục vụ phạm vi hiện tại, đo lại tải/hash khi tăng quy mô.
- Bổ sung users.security_version (BIGINT, mặc định 0). Tăng khi đổi mật khẩu/quyền/trạng thái, xóa phiên bằng principal index, đồng thời kiểm tra version/status/role mỗi request để chặn stale/concurrent session save. Không tăng số bảng.
- Principal serializable chỉ id/security_version; Authentication không giữ credential/hash/entity. Login áp dụng ChangeSessionIdAuthenticationStrategy + CsrfAuthenticationStrategy và save context tường minh.
- SPA lấy masked CSRF token qua endpoint, dùng HttpSessionCsrfTokenRepository; không dùng localStorage, không cần cookie token JavaScript hoặc CORS wildcard.
- Bootstrap admin chỉ chạy khi bật từ môi trường. MySQL GET_LOCK bao quanh transaction tạo admin đầu tiên; giữ lock tới sau commit. Không promote tài khoản learner trùng email, không reset admin hiện có (kể cả LOCKED). Quy trình bootstrap cần pool có ít nhất hai connection; mặc định Hikari đáp ứng. Tắt cờ/xóa secret bootstrap sau khởi tạo.
- Chưa tạo admin_audit_logs/catalog trong giai đoạn này; API thay quyền/trạng thái là nền tảng ADMIN-01. Giao diện quản trị/audit được làm ở giai đoạn tương ứng.

## Kiểm chứng và giới hạn
Xem [Kế hoạch G1](../development/full-system-implementation-plan.md#g1--tài-khoản-và-hồ-sơ-học-viên). Test JDBC dùng database local mới, HTTP thật; không dùng H2 hoặc bootstrap để chứng minh Session. Cookie production được kiểm tra thuộc tính qua HTTP local, chưa phải kiểm thử TLS/reverse proxy production. Chưa có email verification/reset password, rate limit hoặc tính năng học tập; không gọi Gemini.

## Nguồn
- [Spring Session JDBC](https://docs.spring.io/spring-session/reference/configuration/jdbc.html)
- [Schema MySQL 4.1.1](https://github.com/spring-projects/spring-session/blob/4.1.1/spring-session-jdbc/src/main/resources/org/springframework/session/jdbc/schema-mysql.sql), upstream Apache-2.0; phần SQL được giữ nguyên trong V2.
- [Spring Security context persistence/session](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html)
- [Spring Security CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
