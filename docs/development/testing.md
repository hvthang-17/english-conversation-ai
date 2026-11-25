# Kiểm thử và xác minh

Scaffold đã có backend test JUnit/MockMvc trong profile bootstrap, frontend có typecheck/ESLint/build. P0 đã có kiểm thử HTTP, MySQL, frontend và fixture AI. Các ca nghiệp vụ theo nhóm nằm trong kế hoạch hiện tại.

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

## Nghiệm thu cải tiến giao diện

Dùng checklist và quy trình bằng chứng trong [Quy chuẩn cải tiến giao diện](interface-design-standard.md#8-quy-trình-áp-dụng-bắt-buộc) cho mỗi đợt UI. Build thành công không thay thế kiểm tra thị giác, trạng thái, bàn phím, responsive và tương phản. Chỉnh quy chuẩn thuần tài liệu chỉ cần kiểm tra liên kết và tính nhất quán; không cần chạy ứng dụng.

## Kết quả và cách chạy của bản dựng sạch

Xem [bằng chứng P0](phase0/README.md). Chạy `.\scripts\phase0\test-clean-db.ps1` để kiểm tra Flyway, health và ràng buộc MySQL trên schema phát triển mới. Các script kiểm thử dòng migration cũ thuộc đợt trước. Kết quả của từng nhóm G1–G10 được ghi khi chạy các ca trong [kế hoạch hiện tại](full-system-implementation-plan.md).

G1 có bộ test riêng cho dòng migration mới, chạy HTTP/Session JDBC, reset qua outbox, hồ sơ và dọn tài khoản. [Lệnh chạy và bằng chứng nghiệm thu](acceptance/g1-account-acceptance.md).
