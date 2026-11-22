# ADR 0004: Xác thực bằng Session

## Trạng thái
Đã chốt theo quyết định của chủ dự án: **Session**, triển khai bằng Spring Security + Spring Session JDBC lưu MySQL. Đã hiện thực giai đoạn 1 theo [ADR 0007](0007-auth-implementation.md); xem [API](../api/auth.md).

## Bối cảnh và lựa chọn
Website React/Vite gọi một backend Spring Boot; có đăng xuất, khóa tài khoản và kiểm tra quyền sở hữu. Session phía server phù hợp phạm vi này. Không triển khai JWT/access token/refresh token song song. Cơ chế xác thực khác trong tương lai cần ADR thay thế.

## Dữ liệu
- users sở hữu tài khoản; Spring Session quản lý SPRING_SESSION và SPRING_SESSION_ATTRIBUTES. Tổng thiết kế: 20 bảng, gồm 18 bảng ứng dụng và 2 bảng hạ tầng phiên.
- Hai bảng giữ tên/kiểu theo schema MySQL của đúng phiên bản Spring Session được Spring Boot quản lý; không tạo JPA entity hoặc tự đổi quy ước thời gian của thư viện.
- PRINCIPAL_NAME lưu chuỗi users.id theo principal ổn định do ứng dụng thiết kế, không dùng email có thể đổi. Đây là liên kết logic/index lookup, **không phải FK vật lý** trong schema upstream. Session trước đăng nhập/CSRF có thể có principal NULL.
- Context phiên chỉ lưu định danh/authority và dữ liệu xác thực tối thiểu cần thiết. Không serialize JPA entity, mật khẩu/hash còn giữ trong principal, API key hoặc lịch sử hội thoại. ATTRIBUTE_BYTES là BLOB của thư viện, không phải kho audio.
- Khi triển khai, Flyway tạo schema từ script MySQL của phiên bản được resolve; tắt cơ chế Spring Session tự initialize schema để tránh hai bên cùng sở hữu DDL. Dependency thực tế resolve 4.1.1; V2 lấy schema trực tiếp từ JAR, có test đối chiếu provenance.

## Luồng và bảo mật
1. React lấy CSRF token theo hợp đồng auth trước khi gửi POST thay đổi trạng thái. Có thể dùng endpoint GET /api/v1/auth/csrf trả token/headerName từ Spring Security; endpoint đã hiện thực theo hợp đồng API auth.
2. Login kiểm tra email/mật khẩu và users.status; xác thực thành công phải áp dụng session authentication strategy chống fixation, đổi mã phiên và lưu SecurityContext bằng repository đúng cấu hình. Không chỉ set SecurityContextHolder trong controller rồi coi đã đăng nhập bền vững.
3. Trình duyệt giữ cookie SESSION dạng opaque, HttpOnly; production HTTPS bật Secure, SameSite=Lax cho triển khai cùng origin đã chọn. Local HTTP có cấu hình dev riêng. Không đặt mã phiên vào localStorage/sessionStorage hoặc URL/log.
4. Frontend/backend dự kiến cùng origin qua Vite proxy local và reverse proxy production. Không cần CORS mở rộng trong phương án này. Nếu tách origin cần thiết kế credentials/CORS/SameSite lại, không dùng wildcard origin cùng credential.
5. Giữ CSRF cho login/logout và thao tác không an toàn. CSRF token khác cookie xác thực, frontend có thể nhận token qua response nhưng không đọc cookie HttpOnly. Làm mới token sau login/logout theo vòng đời Spring Security; không tắt CSRF để sửa lỗi SPA.
6. Mỗi request bảo vệ kiểm tra tài khoản vẫn ACTIVE, role hiện hành và ownership; không chỉ tin role/status cache trong session. Khi khóa user, thu hồi mọi phiên qua principal index và kiểm tra status ở request tiếp theo. Request đã thực thi hoặc tác vụ ngoài đang chạy cần điều phối riêng; không hứa thu hồi ngược thời gian.
7. Logout dùng POST có CSRF, invalidation phiên hiện tại và xóa cookie đúng path. Hết hạn thì request cần auth trả 401 JSON phù hợp, không redirect HTML. Đổi mật khẩu/vai trò thu hồi các phiên cũ; thay đổi và revocation phải có xử lý lỗi rõ.
8. [ADR 0005](0005-initial-product-rules.md) chốt idle timeout 30 phút, nhiều thiết bị, logout phiên hiện tại; không tự bật remember-me. Cleanup hàng hết hạn không thay thế kiểm tra hết hạn ở request. Đã cấu hình và kiểm thử bằng MySQL trong giai đoạn 1.

## Kiểm thử cần có khi triển khai AUTH-01
Login đúng/sai/khóa, persist context qua request kế tiếp, session ID rotation, logout và cookie cũ, hết hạn, CSRF thiếu/sai, token sau login/logout, revoke mọi phiên khi khóa/đổi quyền/mật khẩu, stale principal, anonymous session và owner/role. Kiểm tra JDBC/cleanup/Flyway bằng MySQL; không coi profile bootstrap hoặc mock là bằng chứng persistence.

## Nguồn tham chiếu
- [Spring Session JDBC](https://docs.spring.io/spring-session/reference/configuration/jdbc.html)
- [MySQL schema của Spring Session 4.1.1](https://github.com/spring-projects/spring-session/blob/4.1.1/spring-session-jdbc/src/main/resources/org/springframework/session/jdbc/schema-mysql.sql)
- [Spring Security CSRF và SPA](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)

Tài liệu online có thể trỏ phiên bản mới hơn dự án; dùng đúng dependency/schema được resolve khi viết code.
