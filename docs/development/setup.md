# Thiết lập môi trường

## SMTP thử nghiệm bằng Mailpit

Khi backend và Mailpit chạy trên cùng máy, dùng `MAIL_ENABLED=true`, `MAIL_HOST=127.0.0.1`, `MAIL_PORT=1025` và `MAIL_STARTTLS=false` cho kết nối loopback local mặc định. Đặt `MAIL_FROM` bằng địa chỉ thử, `APP_PUBLIC_URL` bằng URL frontend local và cấp `MAIL_OUTBOX_KEY` qua môi trường. Mở `http://127.0.0.1:8025` để xem thư thử. Nếu chạy trong container hoặc staging, dùng tên máy của Mailpit trong mạng nội bộ và cấu hình TLS phù hợp. Tắt chuyển tiếp và relay ở môi trường thử. [Cấu hình Mailpit](https://mailpit.axllent.org/docs/configuration/)

Production mặc định tắt gửi thư. Chỉ bật SMTP thật khi môi trường được cấp quyền, cấu hình sender, secret và TLS đã được xác minh.

## Hiện trạng và công cụ

Lệnh Maven `spring-boot:run` mặc định chọn profile `g6`, chạy Flyway tới V19. Dùng profile `bootstrap` khi cần kiểm tra health độc lập với MySQL. [Bằng chứng P0](phase0/README.md).

- JDK 21 và JAVA_HOME tương ứng; không cần cài Maven global vì có wrapper.
- Node 24.19+ trong nhánh 24 và npm. frontend/.nvmrc ghi bản nền 24.19.0.
- Docker Compose để chạy MySQL hoặc MySQL 8.4 được quản lý riêng.

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

### Đích MySQL cho bản dựng sạch

Profile `p0-db` trỏ tới MySQL `127.0.0.1:3306/english_conversation`. Ngày 25/11/2026 đã xác nhận V1 chạy trên đích này và restart giữ đúng lịch sử Flyway. [Quyết định đích MySQL](../decisions/0012-system-database-scope.md#đích-mysql-phát-triển-cho-p02) · [Kết quả kiểm tra](phase0/mysql.md).

1. Kết nối đúng host và cổng. Kiểm tra `SELECT @@hostname, @@port, @@server_uuid, DATABASE();`, danh sách bảng và lịch sử Flyway.
2. Chỉ chạy dòng mới khi schema rỗng hoặc đã có đúng lịch sử migration mới. Nếu còn bảng và lịch sử cũ, xác nhận riêng đích, phạm vi và cách xử lý dữ liệu trước khi dọn. Flyway vẫn kiểm tra checksum và tắt `clean` cùng `baseline-on-migrate`.
3. Sau khi schema sẵn sàng, khởi động backend và kiểm tra `english_conversation.flyway_schema_history` ghi `V1__users_and_learner_profiles.sql`. Khởi động lại để xác nhận Flyway không áp lại V1.

Để chạy bản dựng P0 có MySQL, cấp `P0_DB_USERNAME` và `P0_DB_PASSWORD` cho tài khoản phát triển có quyền trên `english_conversation`. Tại `backend/`, chạy `./mvnw.cmd clean spring-boot:run "-Dspring-boot.run.profiles=bootstrap,p0-db"`. Hai profile theo thứ tự này chạy Flyway từ `db/migration` và phục vụ health. Khi schema đã sẵn sàng, kiểm thử từ thư mục gốc bằng `./scripts/phase0/test-clean-db.ps1`.

Có thể dùng Compose để cấp MySQL local. Kiểm tra biến cấu hình đích trước khi chạy và tránh khởi động trùng cổng với MySQL đang dùng.

## Cấu hình frontend và AI

### Chạy tài khoản, hồ sơ và catalog

Tạo schema MySQL phát triển rỗng lần đầu, hoặc dùng `english_conversation` đã áp V1 của dòng mới. Đặt `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` và `SPRING_DATASOURCE_PASSWORD` trong `.env` hoặc môi trường. Tại `backend/`, chạy:

```powershell
mvn spring-boot:run
```

Flyway áp V11–V19 khi schema có V10. Từ V15, Flyway áp V16–V17 cho luyện hội thoại. Chạy `npm run dev` tại `frontend/`, mở URL Vite rồi vào chi tiết bài cho học liệu G3 và đề luyện nghe G4. Vào **Tình huống** để luyện hội thoại hoặc **Luyện phát âm** để đọc theo mẫu. Xem [cấu hình và nghiệm thu G5](acceptance/g5-pronunciation-acceptance.md). Dùng schema riêng khi tái lập `g1`, `g2` hoặc `g3`. Để thử liên kết khôi phục, dùng [cấu hình Mailpit](#smtp-thử-nghiệm-bằng-mailpit) và đặt `APP_PUBLIC_URL` trùng URL frontend đang mở. Tùy chọn nhắc học được lưu từ G1. Worker gửi nhắc thuộc G8. [API học liệu và sổ tay](../api/lesson-materials.md).

Đề nghe mới ở trạng thái nháp. Để gọi Azure F0 khi công khai, điền `AZURE_SPEECH_KEY`, `AZURE_SPEECH_REGION`, bật `LISTENING_AZURE_ENABLED=true` rồi chạy lại backend. Đăng nhập bằng ADMIN, mở **Luyện nghe → Công khai đề nghe** và xác nhận từng đề. Giữ `LISTENING_AUDIO_DIRECTORY` ở ổ lưu trữ riêng của backend. [API luyện nghe và công khai đề](../api/listening.md).

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
