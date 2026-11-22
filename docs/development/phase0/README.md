# Giai đoạn 0 — Kết quả thực hiện và phần còn lại

## Trạng thái
Đã thực hiện các phần độc lập ngày **20/11/2026**; chưa đóng toàn bộ giai đoạn vì thử Gemini thật/đo provider được chủ dự án chủ động hoãn. Không coi dry-run, mẫu tổng hợp hoặc tài liệu online là kết quả live.

**Có đủ căn cứ kỹ thuật để bắt đầu tài khoản/Session khi được giao giai đoạn 1:** MySQL thật đã chạy, quy tắc auth/retention rõ. Việc chọn model và đánh giá AI thật vẫn cần hoàn tất trước khi tích hợp hội thoại.

## Theo dõi P0.1–P0.8

| Đầu việc | Trạng thái | Bằng chứng/kết quả | Phần còn lại |
| --- | --- | --- | --- |
| P0.1 | Hoàn tất quyết định đầu vào | Hạn 28/12/2026, 2 người × 5 giờ/ngày × 5 ngày/tuần, dùng thử khoảng 10; hiện chỉ chuẩn bị offline, không có quyền chi phí API | Phê duyệt phạm vi live mới nếu muốn đo Gemini |
| P0.2 | Đã chốt | [ADR 0006](../../decisions/0006-phase0-operating-decisions.md): audio 1 giờ, nháp 30 phút, AI log 30 ngày, backup/ngày giữ 7 ngày, cleanup 5 phút, notice/browser, audio 60 giây | Các job/UI/server validation chưa triển khai, cần làm theo giai đoạn |
| P0.3 | Hoàn tất smoke local | [MySQL](mysql.md): 8.4.13:3307, app login, Unicode tạm, UTC/TLS, restart; Spring Boot default profile/Hikari/Flyway/JPA thành công | Chưa có schema nghiệp vụ/Session hoặc migration test |
| P0.4 | Chuẩn bị offline, live hoãn theo yêu cầu | [Probe Java](../../../experiments/phase0/gemini/), 8 test đạt, dry-run 4 case, WAV SAPI tổng hợp và manifest | 0 Gemini request, chưa có câu trả lời/chép lời/phản hồi thật |
| P0.5 | Đạt smoke desktop sớm trong phạm vi đã điều chỉnh | [Audio](audio.md), [metadata chủ dự án gửi](evidence/desktop-audio.json): WebM/Opus, 11.28 giây, nghe rõ, không upload; riêng headless có tone capture/decode | Edge, Android, stable matrix, noise/60 giây và chép lời provider chưa thử |
| P0.6 | Tra cứu và chuẩn bị, chưa đủ chốt model/live metrics | [Gemini/key](gemini.md): Java REST prototype, SDK candidate, model candidate, timeout/output limit thăm dò; usage/cost/latency provider chưa có | Chỉ quyết định sau live test được cho phép; không giả định free quota hoặc giá bằng 0 |
| P0.7 | Hoàn tất khâu chuẩn bị | [Khảo sát](../../research/needs-survey.md), [AI rubric](../../research/ai-evaluation.md), [dùng thử](../../research/usability-test.md), 32 fixture tổng hợp | Chưa phát hành khảo sát/tuyển người/thu dữ liệu hoặc chuyên gia xác nhận gold set |
| P0.8 | Hoàn tất cập nhật hiện tại | ADR 0006, [kế hoạch lịch thực tế](../implementation-plan.md), [risk register](risk-register.md), tài liệu/fixtures/scripts | Tiếp tục cập nhật khi có live evidence hoặc điều kiện thay đổi |

6 đầu việc hoàn tất theo phạm vi hiện tại; P0.4/P0.6 không đánh dấu hoàn tất. Mốc M1–M5 nghiệp vụ chưa đạt.

## Artifact và giới hạn
- MySQL riêng trong .runtime, root .env và option file chứa secret đều local/ignored; không đụng dịch vụ MySQL92 của máy.
- Flyway chỉ có flyway_schema_history, không có SQL/entity 20 bảng. Không tuyên bố đã triển khai persistence nghiệp vụ hoặc auth.
- Bộ thử Gemini độc lập, không thêm dependency provider vào backend; wrapper từ chối Live theo yêu cầu hiện tại. Metadata dry-run không chứa raw prompt/response/audio/key.
- Trang audio static local không upload; browser báo cáo khả năng codec không chứng minh provider chấp nhận. Bằng chứng micro thật do chủ dự án thực hiện, agent chỉ kiểm tra metadata gửi lại.
- Fixture nghiên cứu là nội dung biên soạn, không có số liệu người dùng hoặc kết quả AI giả.

## Bằng chứng local
Các file .runtime/phase0/mysql-verification.json, gemini-dry-run.json, synthetic-audio.json và browser-audio-headless.json là artifact của máy hiện tại, không cần commit. Test report dưới experiments/phase0/gemini/target/surefire-reports được tái tạo bằng script. Bản sao metadata đã loại secret được lưu cùng tài liệu: [MySQL smoke](evidence/mysql-smoke.json), [Gemini offline](evidence/gemini-offline.json), [tone tổng hợp trên trình duyệt](evidence/browser-tone.json) và [micro thật do chủ dự án kiểm thử](evidence/desktop-audio.json). Các file ghi rõ nguồn bằng chứng; không chứa bản ghi âm.

Ngày/giờ của evidence lưu UTC; lịch thực hiện theo Asia/Ho_Chi_Minh. Không công bố credential hoặc raw server log chưa che dữ liệu.

## Điểm dừng và bước tiếp
Hiện chủ dự án yêu cầu **chưa gọi API**, nên không thử key/quota/model. Có thể chuẩn bị/triển khai AUTH-01 sau khi được giao; trước CHAT-01 cần yêu cầu live mới, model access, hạn mức/ngân sách và kết quả đo thật. Hướng dẫn lấy key đã có, key không tự cấp quyền gọi.

## Cập nhật sau giai đoạn 1

Ngày 20/11/2026, [giai đoạn 1](../phase1/README.md) đã triển khai users, Session JDBC và tài khoản. Các mô tả “chưa có bảng nghiệp vụ/auth” phía trên là ảnh chụp kết quả riêng của P0; hiện trạng runtime đã thay đổi ở P1. P0.4/P0.6 Gemini live vẫn chưa hoàn tất và chưa được gọi.
