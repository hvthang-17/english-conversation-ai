# Thiết lập môi trường

## Hiện trạng và công cụ

Đã có frontend/backend, Maven Wrapper, npm lockfile, AUTH-01/Session JDBC, PROFILE-01/SCENARIO-01 và 8 bảng qua Flyway V1–V4. Chưa có hội thoại hoặc Gemini runtime client. Hướng dẫn auth/admin/test tại [giai đoạn 1](phase1/README.md).

- JDK 21 và JAVA_HOME tương ứng; không cần cài Maven global vì có wrapper.
- Node 24.19+ trong nhánh 24 và npm. frontend/.nvmrc ghi bản nền 24.19.0.
- Docker Compose để chạy MySQL hoặc MySQL 8.4 được quản lý riêng.

Giai đoạn 0 đã chuẩn bị **MySQL 8.4.13 portable tại 127.0.0.1:3307**, .env local và script start/verify/stop; backend đã kết nối thật; giai đoạn 1–2 bổ sung 8 bảng tài khoản/Session/hồ sơ/catalog. Xem [MySQL phase0](phase0/mysql.md). Không sửa dịch vụ MySQL 9.2 trên máy; hướng dẫn Compose bên dưới vẫn là phương án cho máy có Docker.

Máy thiết lập có Node 24.19.0 trong nvm nhưng chưa kích hoạt mặc định; cần chọn bản Node tương ứng bằng công cụ quản lý phiên bản của bạn trước khi dùng npm/VSCode tasks. Việc xác minh dùng PATH chỉ trong tiến trình, không đổi cấu hình global.

## Chạy scaffold không cần MySQL

Terminal 1, từ gốc repository:

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=bootstrap"
```

Terminal 2, từ gốc repository:

```powershell
cd frontend
npm ci
npm run dev
```

Mở http://127.0.0.1:5173. Vite proxy /api tới backend 8080. GET /api/v1/system/health công khai; mọi đường dẫn khác bị chặn. Profile bootstrap chỉ kiểm tra scaffold HTTP, không có database/AI và không dùng production.

## Chạy với MySQL

Tại gốc repository, sao chép .env.example thành .env nếu chưa có, đặt MYSQL_PASSWORD và MYSQL_ROOT_PASSWORD riêng cho local. Không commit .env.

```powershell
docker compose --env-file .env -f infrastructure/compose.yaml up -d mysql
```

Compose chỉ chạy MySQL, bind localhost và giữ dữ liệu trong named volume. Đổi mật khẩu trong .env sau khi volume đã khởi tạo không tự đổi mật khẩu tài khoản MySQL; phải thực hiện thay đổi tại database, không tự xóa volume chứa dữ liệu.

Trong terminal backend, export cấu hình rồi chạy không có profile bootstrap:

```powershell
$env:SPRING_DATASOURCE_URL = 'jdbc:mysql://localhost:3306/english_conversation?connectionTimeZone=UTC'
$env:SPRING_DATASOURCE_USERNAME = 'english_app'
$env:SPRING_DATASOURCE_PASSWORD = '<giá trị MYSQL_PASSWORD đã chọn>'
.\mvnw.cmd spring-boot:run
```

Nếu đổi MYSQL_DATABASE, MYSQL_USER hoặc MYSQL_PORT, đổi biến datasource tương ứng. Backend tự đọc root .env khi chạy từ backend/; Compose chỉ dùng các biến MYSQL_* của file đó. JPA dùng validate, Flyway bật; default profile áp V1 users/V2 Session/V3 hồ sơ-catalog/V4 seed tám bài. Các bảng hội thoại/ôn tập chưa triển khai.

ERD gồm 20 bảng (18 ứng dụng + 2 Spring Session JDBC) tại [database](../architecture/database.md), với [dictionary](../architecture/database-dictionary.md) và [workflow](../architecture/database-workflows.md). Session theo ADR 0004/0007 đã chạy JDBC 4.1.1, V2 lấy schema từ JAR, auto-initialize=never. 8 bảng được triển khai; không chạy DDL 12 bảng còn lại theo phỏng đoán.

## Cấu hình frontend và AI

Frontend mặc định VITE_API_BASE_URL=/api/v1 và DEV_API_TARGET=http://localhost:8080; có thể dùng frontend/.env.local từ [mẫu](../../frontend/.env.example). VITE_* là công khai; không chứa key. Preview 4173 không có dev proxy; production cần reverse proxy hoặc URL API cùng chính sách CORS phù hợp.

Các biến GEMINI_* trong mẫu gốc dành cho bước tích hợp sau, chưa được scaffold sử dụng. Khi triển khai cần HTTPS, secret thực, backup và retention.

[Hướng dẫn lấy key và probe phase0](phase0/gemini.md) đã có; hiện chủ dự án chỉ cho chuẩn bị offline, không gọi Gemini thật. Có key không tự cấp quyền gọi hoặc bật billing. [Trang kiểm tra micro](phase0/audio.md) chạy local, không upload.

## Kiểm tra và VSCode

Từ backend: `.\mvnw.cmd verify`. Từ frontend: `npm run typecheck`, `npm run lint`, `npm run build`. macOS/Linux dùng `./mvnw` hoặc `sh ./mvnw`.

VSCode có tasks cho backend verify/bootstrap và frontend dev/build, cùng danh sách extension gợi ý. Các tasks dùng Java/Node trong PATH của terminal VSCode; không tự cài extension hoặc đổi môi trường máy.

Xem [backend](../../backend/README.md), [frontend](../../frontend/README.md), [ADR 0002](../decisions/0002-application-scaffold.md) và [testing](testing.md).

## Skill repo-local

Nội dung chính tại agents/skills/. Máy hiện tại có junction .agents/skills tới đó, gitignore để tránh commit đường dẫn tuyệt đối.

Sau khi clone trên Windows, chạy từ gốc repository nếu đường dẫn khám phá chưa tồn tại:

```powershell
$skillTarget = (Resolve-Path -LiteralPath 'agents/skills').Path
New-Item -ItemType Directory -Path '.agents' -Force | Out-Null
if (-not (Test-Path -LiteralPath '.agents/skills')) {
    New-Item -ItemType Junction -Path '.agents/skills' -Target $skillTarget
}
```

Nếu đã tồn tại, kiểm tra trước; không ghi đè hoặc xóa skill có sẵn. Có thể yêu cầu agent đọc trực tiếp agents/skills/<skill-name>/SKILL.md thay vì tạo junction. Không cần cài global.
