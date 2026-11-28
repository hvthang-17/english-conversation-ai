# ADR 0021 — Kế hoạch thay profile giai đoạn bằng cấu hình tính năng typed

Ngày 28/11/2026. Trạng thái: phương án đã được áp dụng trong BR-11; phần dưới giữ bối cảnh quyết định BR-10. Không đổi profile, endpoint hoặc quyền gọi dịch vụ ngoài trong BR-10.

## Bối cảnh

[Ma trận BR-10](../development/backend-refactoring/profile-matrix.md) ghi annotation, route, Optional và cấu hình thực. Maven có spring-boot.run.profiles=g6, base YAML có spring.profiles.default=bootstrap. Production chỉ đổi cookie Secure, không chọn schema/nhóm tính năng. Biểu thức phủ định khiến profile lạ hoặc production đơn lẻ đủ điều kiện bật một phần hội thoại; tổ hợp stage có thể trùng controller cleanup. Optional tránh lỗi injection nhưng có thể bỏ qua cleanup, không phải cơ chế chứng minh tính năng được bật đầy đủ.

## Phương án typed

BR-11 dùng immutable @ConfigurationProperties(prefix="app.features") với enum Stage { BOOTSTRAP, ACCOUNT, CATALOG, MATERIALS, LISTENING, PRONUNCIATION, FULL } và record FeatureSet. Không thêm dependency chỉ để bind cấu hình; tận dụng Spring Boot hiện có. Stage chọn preset duy nhất, không suy đoán từ key/provider hoặc từ thiếu profile. Base mặc định BOOTSTRAP. Environment triển khai (production) chỉ cấu hình TLS/cookie/storage/secret, tách khỏi Stage.

| Preset | Tính năng nội bộ được đề xuất |
| --- | --- |
| BOOTSTRAP | Health; không persistence/session/provider/worker |
| ACCOUNT | Auth/session/profile/catalog scenario và lifecycle theo hợp đồng g1 |
| CATALOG | ACCOUNT + lesson catalog/bookmark |
| MATERIALS | CATALOG + học liệu/notebook/FLASHCARD |
| LISTENING | MATERIALS + luyện nghe/publication |
| PRONUNCIATION | LISTENING + chấm theo mẫu |
| FULL | PRONUNCIATION + hội thoại/feedback/hint/explanation/voice, spaced review, plans/progress/support/admin theo g6 |

FeatureSet có các trường boolean typed cho persistence, accountLifecycle, lessonCatalog, materials, review, spacedReview, listening, pronunciation, conversation, feedback, plans, progress, support, admin. Bộ validator kiểm tra quan hệ: feedback/voice/help cần conversation; spacedReview cần review; materials/listening/pronunciation cần lessonCatalog; mọi nghiệp vụ có dữ liệu cần persistence + session. Không cho override riêng khiến preset và dependency mâu thuẫn. Nếu cần override sau này phải có ca context/HTTP tương ứng trước mở.

ProviderProperties riêng: Gemini.enabled, ListeningAzure.enabled, PronunciationAzure.enabled, Mail.enabled, Mail.remindersEnabled; mọi enabled mặc định false, không bật khi có key. Khi enabled phải có feature tương ứng, cấu hình/giới hạn hợp lệ; reminders cần mail + schedule, mail cần sender/outbox-key/URL tin cậy. Bật chức năng nội bộ không tự cấp quyền gọi thật; test dùng adapter giả lập. Không gộp hợp đồng Azure với Gemini.

## Tổ hợp cấm và tương thích

- Đề xuất fail-fast khi bootstrap ghép stage nghiệp vụ, nhiều g1–g6 đồng thời, stage và FeatureSet mâu thuẫn, provider enabled nhưng feature/credential/quota thiếu, production không có stage rõ hoặc dùng bootstrap. Quy tắc này chưa tồn tại trong runtime BR-10.
- Giữ các lệnh spring-boot:run -Dspring-boot.run.profiles=g1…g6 bằng bridge stage tương ứng; Maven mặc định g6 vẫn map FULL, JAR không đối số vẫn BOOTSTRAP. --spring.profiles.active=g6,production vẫn được hỗ trợ. Không dùng thuộc tính Maven làm giá trị mặc định runtime của JAR.
- Khi có cả app.features.stage rõ và profile legacy: chỉ chấp nhận cùng preset, khác nhau báo lỗi khởi động. Profile lạ không tự chọn FULL. SPRING_PROFILES_DEFAULT/ACTIVE vẫn là cơ chế Spring; phải kiểm tra và không suy diễn rằng bỏ CLI profile đã xóa cấu hình môi trường.
- Security dùng cùng FeatureSet với controller/worker. Route chưa bật phải deny trước matcher rộng; route không biết vẫn denyAll. Quyền owner/role/CSRF hiện có giữ nguyên. Chỉ có một handler DELETE learning/data cho preset đang chọn, vẫn gọi cleanup đầy đủ; Optional chỉ dành collaborator thực sự tùy chọn, không được im lặng bỏ qua module có dữ liệu cần dọn.
- BR-11 phải chốt schema cho mỗi preset. Trước mắt giữ target legacy 5/8/10/12/15/33 cho các lệnh giai đoạn, không tự nâng schema. Các mapper dùng cột mới trên stage cũ cần kiểm thử/sửa tương thích hoặc thông báo cấu hình không hỗ trợ rõ ràng. Không dùng matrix bean để tuyên bố CRUD stage cũ hoạt động.

## Trình tự áp dụng

BR-10 chỉ kiểm kê/đề xuất. BR-11 thêm typed binding/validator và bridge, chạy context fail-fast/supported combinations với fixture trước thay annotation theo từng module, rồi đối chiếu security, worker và cleanup. BR-12 tổ chức test theo module. Mỗi bước giữ lệnh cũ, migration/schema rõ và provider tắt mặc định. Không đánh dấu BR-11 hoàn tất từ ADR này.

Ngoại lệ công cụ cần giữ khi áp validator: bootstrap,p0-db phục vụ kiểm tra P0 có guard schema/port/marker riêng, không phải stage nghiệp vụ. Không cấm cấu hình này bằng quy tắc “một stage”; tiếp tục giữ guard PhaseZeroDatabaseConfig và kiểm thử riêng trước thay thế. BR-10 không chạy quy trình P0 vào database đang dùng.


## Áp dụng trong BR-11

Đã triển khai FeatureProperties.Stage có binding typed, FeatureSet immutable suy ra các nhóm feature theo preset và FeatureEnabled/FeatureCondition tại config. Các nhóm CONVERSATION/FEEDBACK/PLANS/PROGRESS/SUPPORT/ADMIN/LEARNING/SPACED_REVIEW/IN_APP_REMINDERS hiện đi cùng FULL; không có override từng boolean. YAML stage vẫn là adapter datasource/Flyway; app.features.stage phải khớp bootstrap/g1–g6, chưa hỗ trợ property stage đơn lẻ thay adapter schema. Đây là giới hạn có chủ ý để giữ lệnh/target cũ, không tự nâng schema.

ACCOUNT giữ auth/profile/topics; CATALOG thêm scenario/lesson/bookmark đúng endpoint giai đoạn hiện có. Foundation controller gọi service; FULL dependency bắt buộc, Optional chỉ còn khi preset thực sự thiếu module. Security/capability metadata dùng chung FeatureSet. Provider property hiện hữu giữ nguyên tên và thêm validator tập trung; disabled không cần key, không tự gộp Azure/Gemini. Bootstrap tắt provider rõ để tránh cờ developer .env bật ngoài stage.

[Bằng chứng BR-11](../development/backend-refactoring/evidence/br11-feature-configuration.md) phân biệt context/schema đã chạy, JAR default có guard và các lệnh startup còn chờ; không coi các tổ hợp chỉ có fixture là đã triển khai production.
