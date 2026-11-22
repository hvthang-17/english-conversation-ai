# Hướng dẫn backend

Phạm vi: backend/. Tuân thủ hướng dẫn gốc và đọc [thiết kế backend](../docs/architecture/backend.md).

Java 21, Spring Boot 4.1.2; base package vn.englishconversation. Có Maven Wrapper và endpoint GET /api/v1/system/health. Chạy tại backend: `./mvnw verify` (Windows: `.\mvnw.cmd verify`). Chạy không DB: `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=bootstrap"`. Xem README.md trong thư mục này.

- Java + Spring Boot + Maven; MySQL qua Spring Data JPA, migration bằng Flyway.
- Package theo nghiệp vụ; constructor injection; DTO riêng; Bean Validation; lỗi tập trung.
- Kiểm tra role và owner tại server, có kiểm thử truy cập chéo dữ liệu.
- Không giữ transaction khi gọi Gemini; bảo vệ thứ tự và chống trùng lượt.
- Không trả entity, secret hoặc stack trace cho client.
- Đây là Spring Boot 4: dùng starter webmvc và đúng package auto-configuration/test của phiên bản này; không sao chép import Boot 3 theo trí nhớ.
- Công khai GET health/CSRF và POST register/login; mutation vẫn kiểm tra CSRF. Auth bảo vệ tài khoản, GET catalog và GET/PUT profile/me; /admin/** yêu cầu ADMIN; route chưa triển khai denyAll. Không có tài khoản mặc định.
- AUTH-01 đã triển khai theo ADR 0004/0007: Session JDBC 4.1.1, Flyway V1 users/V2 Session, explicit context save và CSRF. Không thêm JWT/refresh token. Thay đổi bảo mật cần kiểm thử MySQL qua scripts/phase2/test-mysql.ps1 (toàn bộ auth/profile/catalog và upgrade), không chỉ profile bootstrap.
- Profile bootstrap loại bỏ persistence, chỉ dùng kiểm tra scaffold local; không coi là bằng chứng MySQL/Flyway đã chạy.
- Mock Gemini trong test mặc định; dùng MySQL khi xác minh query/migration đặc thù.
- Tham khảo skill implement-backend-feature; thêm change-database/integrate-gemini khi cần.
