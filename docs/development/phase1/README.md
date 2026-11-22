# Giai đoạn 1 — Database nền tảng và tài khoản Session

## Phạm vi và kết quả
Thực hiện ngày **20/11/2026**, sớm hơn cửa sổ mục tiêu 24–27/11. Ước lượng 35 giờ-người là kế hoạch, không phải thời gian công đã đo. AUTH-01 và phần phân quyền tài khoản nền tảng của ADMIN-01 đã triển khai; không mở rộng sang catalog/audit UI hoặc Gemini.

| Đầu việc | Kết quả |
| --- | --- |
| P1.1 | V1 users, email canonical ASCII + CHECK/UNIQUE, PBKDF2 hash, password không trim |
| P1.2 | Starter Session JDBC 4.1.1, V2 schema nguyên từ JAR, auto-initialize tắt; Flyway chạy trên MySQL 8.4.13 rỗng |
| P1.3 | Register/login/me/logout/password, DTO validation, lỗi JSON thống nhất |
| P1.4 | Idle 30 phút, nhiều thiết bị, rotation/explicit context save, CSRF, cookie local/production |
| P1.5 | Kiểm tra status/version/role mỗi request; admin PATCH access, revoke khi khóa/đổi quyền/password |
| P1.6 | Bootstrap admin từ môi trường, lock qua commit, không reset hoặc promote tài khoản có sẵn |
| P1.7 | Form React đăng ký/login/password, current user, điều hướng theo hash, xử lý 401/403/input/network; không lưu phiên trong browser storage |
| P1.8 | Kiểm thử MySQL/HTTP, frontend checks, browser smoke và hợp đồng/vận hành đồng bộ |

Chỉ **3 bảng runtime**: users, SPRING_SESSION, SPRING_SESSION_ATTRIBUTES, ngoài flyway_schema_history. 17 bảng còn lại trong ERD chưa triển khai.

## Chạy ứng dụng
Từ root, chuẩn bị .env theo [setup](../setup.md); Backend tự đọc root .env khi chạy từ backend/.

```powershell
# MySQL portable đã chuẩn bị ở giai đoạn 0
.\scripts\phase0\start-mysql.ps1
# Chạy backend default profile với biến từ .env
.\scripts\phase0\run-backend.ps1
# Terminal thứ hai
cd frontend
npm run dev
```

Mở http://127.0.0.1:5173/#/register. Backend mặc định 8080, Vite proxy /api. Không dùng profile bootstrap khi thử auth. Với Docker/máy khác, cấu hình datasource tương ứng; script portable chỉ dành instance local phase0.

## Khởi tạo admin đầu tiên
1. Dừng backend đang chạy để tránh giữ cờ/secret ở process cũ.
2. Trong root .env **local ignored**, đặt ADMIN_BOOTSTRAP_ENABLED=true, ADMIN_BOOTSTRAP_EMAIL là địa chỉ ASCII của người quản trị và ADMIN_BOOTSTRAP_PASSWORD là mật khẩu riêng ngẫu nhiên tối thiểu 12 ký tự. Không đưa mật khẩu vào CLI history, chat hoặc Git.
3. Chạy script backend default profile, kiểm tra startup thành công, đăng nhập qua frontend với email/password đã chọn. Có thể GET /auth/me để thấy role ADMIN.
4. Dừng process, đặt ADMIN_BOOTSTRAP_ENABLED=false và xóa giá trị ADMIN_BOOTSTRAP_PASSWORD; chạy lại. Production dùng secret manager/env tương ứng và profile production/HTTPS.

Nếu đã có một ADMIN thì bootstrap không thay đổi gì. Nếu chưa có ADMIN nhưng email thuộc một LEARNER, startup bootstrap thất bại và không sửa tài khoản. Admin LOCKED không bị tự mở/reset. Không có tài khoản/mật khẩu admin mặc định trong Git. Pool mặc định đủ connection cho lock và transaction bootstrap; không hạ xuống 1 trong lần khởi tạo.

## Kiểm thử tái lập

```powershell
# Java 21 trong JAVA_HOME; MySQL local đang chạy
.\scripts\phase1\test-mysql.ps1
cd frontend
npm run typecheck
npm run lint
npm run build
```

Script test tạo **database mới** phase1_auth_<UTC timestamp>, cấp quyền app local và chạy Maven verify; không drop/reset database đang có. Cần credential root local từ phase0/mysql-admin.cnf. Test từ chối URL ngoài namespace loopback test. Dữ liệu thử là fixture tổng hợp; database giữ lại để kiểm tra, không phải dữ liệu sản phẩm. Maven verify thường không có PHASE1_MYSQL_TEST sẽ bỏ qua nhóm MySQL; không nhầm skipped với pass.

Kết quả cuối: **19/19 test đạt, 0 failure/error/skipped**, trong đó 13 test HTTP/JDBC với MySQL; frontend typecheck/lint/build đạt. Bằng chứng hiện tại ở [verification.json](verification.json). Báo cáo Maven chi tiết tái tạo tại backend/target/surefire-reports; log/screenshot local dưới .runtime/phase1, không chứa secret trong bản bàn giao.

## Điều kiện M1 và giới hạn
Email khác hoa/thường không tạo trùng, DB từ chối email không canonical, đăng ký đồng thời chỉ một bản ghi; mật khẩu hash và giữ khoảng trắng; Session JDBC rotation/persistence/logout/expiry/cleanup; CSRF thiếu/sai/cũ bị từ chối; nhiều thiết bị; quyền admin và revocation đều có test thật. Browser đã thực hiện register→login→backend restart→me→password→login lại→logout và kiểm tra màn hình 390px.

Cookie Secure được assert ở profile production trên HTTP local; chưa triển khai HTTPS/reverse proxy hoặc load test. Expiry được kiểm tra bằng dịch mốc thời gian hàng JDBC, không chờ 30 phút thực. Không khẳng định đã kiểm thử Android/Edge hoặc hoàn thành toàn bộ ADMIN-01. Gemini vẫn 0 live call, P0.4/P0.6 chưa đóng.

Hợp đồng module khác dùng: [API auth](../../api/auth.md), [ADR 0007](../../decisions/0007-auth-implementation.md).

Các server backend/Vite dùng kiểm thử đã dừng sau xác minh. Tài khoản example.test tạo riêng cho smoke browser đã được dọn; không có tài khoản admin mặc định. Database test riêng được giữ lại.

## Chuẩn hóa package backend sau M1

Đã chuyển 10 class vào các lớp theo nghiệp vụ, tách 6 DTO thành file riêng và cập nhật toàn bộ import/test/config. DTO response tài khoản dùng chung là user.dto.UserResponse; JSON/API và migration giữ nguyên. Phiên cũ được hỗ trợ bằng bridge auth.AccountPrincipal → auth.security.AccountPrincipal. Kết quả sau refactor: **20/20 test đạt, 0 failure/error/skipped**, gồm 14 kiểm thử HTTP/MySQL. Bằng chứng nằm ở [package-refactor-verification.json](package-refactor-verification.json); xem [cây source hiện tại](../../../backend/README.md).

Sau khi chuyển package, chạy Maven clean trước verify để loại bytecode cũ, tránh Spring scan đồng thời class cũ/mới. Khi dùng script MySQL: `.\backend\mvnw.cmd -f backend/pom.xml clean` rồi `.\scripts\phase1\test-mysql.ps1` từ root; JAVA_HOME cần Java 21.
