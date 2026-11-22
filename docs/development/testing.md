# Kiểm thử và xác minh

Scaffold đã có backend test JUnit/MockMvc trong profile bootstrap, frontend có typecheck/ESLint/build. Các kiểm thử nghiệp vụ, DB, AI và E2E bên dưới là định hướng, chưa được triển khai.

Lệnh tại backend: `.\mvnw.cmd verify` (Unix: `./mvnw verify`). Tại frontend: `npm run typecheck`, `npm run lint`, `npm run build`.

HealthEndpointTest xác minh GET health công khai, đường dẫn nghiệp vụ bị chặn và POST health không được mở theo GET. Test này không xác minh MySQL hoặc Gemini. Chưa có framework unit/E2E frontend trong dependencies.

| Loại thay đổi | Xác minh phù hợp |
| --- | --- |
| Tài liệu/skill | Liên kết nội bộ, đường dẫn, frontmatter skill, tính nhất quán trạng thái |
| Nghiệp vụ backend | JUnit cho tình huống thành công, lỗi và biên có ý nghĩa |
| API/xác thực | Spring Boot Test/MockMvc; truy cập chéo user và role; validation |
| Schema/query | Flyway trên MySQL; ràng buộc, migration và dữ liệu hiện có |
| Gemini | Mock provider; schema sai/rỗng, timeout, 429, chống trùng và giới hạn |
| UI | Build/typecheck/lint nếu có; thử luồng ảnh hưởng, trạng thái lỗi/trống và màn hình nhỏ |
| Luồng đầu cuối | Playwright nếu được cài; dùng spec hiện có trước; không giả định browser MCP có sẵn |

Không gọi Gemini thật trong test mặc định. Live smoke test chỉ dùng credential môi trường được cấp, có giới hạn, không ghi âm cá nhân; kết quả mock không chứng minh chất lượng mô hình thật.

Đo độ trễ riêng cho hội thoại, chép lời và phản hồi; báo median/P95 cùng sample size và điều kiện. Chất lượng AI cần đánh giá bằng bộ hội thoại và người đánh giá, không chỉ kiểm tra JSON hợp lệ.

Khi hoàn thành, ghi lệnh đã chạy và kết quả; nêu lỗi do môi trường hoặc phần chưa chạy. Không viết test chỉ để khớp đúng câu chữ tài liệu hay lặp lại implementation.

## Kết quả xác minh scaffold

- Maven Wrapper verify: build/package thành công, 3 test health/bảo mật đạt, không skipped.
- Frontend: npm install tạo lockfile, typecheck, lint và production build đạt.
- Smoke local: frontend HTTP 200; proxy /api/v1/system/health nhận UP từ backend; route nghiệp vụ qua proxy trả 401.
- Browser: trang hiển thị kết nối thành công, không console error ở luồng thành công; viewport mobile 390px không tràn ngang. Khi dừng backend rồi kiểm tra lại, UI hiển thị lỗi và nút thử lại sử dụng được.
- MySQL/Compose chưa được chạy do môi trường không có Docker; chưa có migration nghiệp vụ hoặc live Gemini để kiểm tra.

Đây là bằng chứng cho scaffold tại lần khởi tạo, không thay thế kiểm thử sau các thay đổi tiếp theo. Các server kiểm tra đã được dừng sau khi xác minh.

## Bổ sung giai đoạn 0 — 20/11/2026

Kết quả sau đây bổ sung cho ghi nhận scaffold cũ; Docker vẫn chưa có nhưng MySQL portable đã được kiểm chứng:

- MySQL 8.4.13 localhost:3307: app login, bảng TEMPORARY Unicode write/read, timezone +00:00 và TLS cipher đạt; stop/start/restart qua script đã đạt. Dữ liệu instance riêng, không sửa service MySQL92.
- Backend default profile (không bootstrap): Hikari/Flyway/JPA khởi tạo qua datasource thật, health UP; Flyway chỉ tạo history table với 0 migration, không chứng minh schema nghiệp vụ/Session.
- experiments/phase0/gemini: 8 unit test đạt, dry-run chuẩn bị 4 case; wrapper từ chối Live. Không có provider request, latency/usage/cost hoặc chất lượng Gemini thật.
- Trang audio độc lập: headless tone capture/decode WebM/Opus đạt; chủ dự án gửi metadata micro Chrome Windows 11.28 giây/nghe rõ/no-upload. Đây là user-reported physical test, chưa phải toàn browser matrix hoặc chép lời provider.
- Khảo sát/rubric/32 case biên soạn đã có, chưa tuyển/thu dữ liệu hoặc đánh giá chuyên gia.

Chi tiết, nguồn và phần hoãn tại [phase0](phase0/README.md). Không đổi trạng thái nghiệm thu các chức năng nghiệp vụ chỉ từ các thử nghiệm này.

## Giai đoạn 1 — AUTH-01 / nền tảng ADMIN-01

Ngày 20/11/2026: [biên bản P1](phase1/README.md), [hợp đồng API](../api/auth.md). Script `scripts/phase1/test-mysql.ps1` tạo database test MySQL 8.4.13 mới, chạy Flyway V1/V2 và HTTP thật. Bộ kiểm thử có quy tắc email/password, schema Session trùng upstream JAR, CSRF thiếu/sai/cũ, rotation, context JDBC, multi-device/logout/expiry/cleanup, password/access revoke/stale role, signup không cấp ADMIN và bootstrap không reset/promote. Có kiểm tra cookie production Secure qua HTTP local; chưa phải TLS deployment.

Browser smoke qua React: đăng ký/login, restart backend rồi kiểm tra phiên, đổi mật khẩu/login lại/logout; browser storage trống, label đầy đủ, màn hình 390px không tràn ngang và đã xem screenshot. Chỉ Chromium desktop, không suy ra kiểm thử Android/Edge. Expiry JDBC được dịch timestamp để tái lập, không chờ 30 phút.

Kết quả test cuối ở [verification.json](phase1/verification.json). Một lượt kiểm tra bổ sung trước đó lỗi thiếu RAM khi nhiều JVM/Node chạy đồng thời; đã dừng server thử và chạy lại lần lượt với heap giới hạn. Không dùng lượt lỗi đó làm bằng chứng pass.

### Sau chuẩn hóa package backend

Maven clean/test-compile và verify trên database MySQL mới đạt **20/20 test**, gồm 14 HTTP/JDBC. Regression thêm kiểm tra SecurityContext với principal package cũ được deserialize và GET /auth/me vẫn đúng. Không chỉnh Flyway/schema/API/JSON hoặc frontend trong lần refactor này; frontend/browser không chạy lại. Kết quả tại [package-refactor-verification.json](phase1/package-refactor-verification.json), cây file tại [README backend](../../backend/README.md).

### Tự nạp root .env khi chạy Maven

Đã cấu hình spring.config.import với file properties optional từ ../.env và ./.env (file sau ưu tiên nếu cùng tồn tại). Kiểm thử focused Maven verify đạt 7/7: EnvImportTest, AccountRulesTest, HealthEndpointTest. Test config dùng fixture riêng, không truy cập MySQL/Gemini và không in credential; xác minh nạp username, password rỗng, URL có query và ưu tiên override. Không chạy lại nhóm MySQL/browser trong thay đổi cấu hình này. Cách chạy/định dạng file tại [README backend](../../backend/README.md).

## Giai đoạn 2 — PROFILE-01 / SCENARIO-01

Maven verify trên MySQL 8.4.13 riêng đạt **29/29 test**, không skip; script scripts/phase2/test-mysql.ps1 bao gồm fresh schema và upgrade V2→V4 có learner/hash cũ. Kiểm tra owner/CSRF/rollback, validation/interest FK/level CHECK, lọc/ẩn/level/pagination và DTO không lộ instruction. V1/V2 checksum giữ nguyên. Frontend typecheck/lint/build đạt.

Chromium smoke trên 8081/5174/DB test 3307: signup/login, lưu B1/goal/phút/interests, lọc Du lịch, chi tiết/chọn bài, loading/trống/503-retry (503 chỉ giả lập trong browser), kết thúc phiên/401, keyboard và layout 390px. Đã xem screenshot; không suy ra đã thử trên điện thoại thật. Bằng chứng [P2 verification](phase2/verification.json), nội dung [8 bài](../product/starter-catalog-review.md). Không gọi Gemini, chưa tạo snapshot/buổi luyện.
