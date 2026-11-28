# ADR 0023 — Giữ GET_LOCK trên connection của transaction

Ngày 28/11/2026. Trạng thái: **Được chọn và đã kiểm chứng BR-14**. Cập nhật phương án connection của ADR 0022; giữ GET_LOCK liên instance. [Bằng chứng](../development/backend-refactoring/evidence/br14-admission.md), [số liệu](../development/backend-refactoring/evidence/br14-admission-results.json). BR-15 vẫn mở.

## Căn cứ và giới hạn

[BR-13](../development/backend-refactoring/evidence/br13-admission-measurements.md) đạt tải demo đã đề xuất, nhưng mô hình hai connection gây lỗi khi chín trong mười connection bị chiếm. Giữ khóa liên instance theo schema và timeout hai giây; không thay bằng khóa JVM. Phạm vi vẫn là workload BR-13, chưa phải năng lực production đa host hoặc soak dài.

## Quyết định

AiAdmissionService bắt đầu transaction READ_COMMITTED, dùng DataSourceUtils lấy đúng connection đã được Spring bind cho datasource, rồi GET_LOCK trước callback nghiệp vụ. Đăng ký TransactionSynchronization trước khi thực thi GET_LOCK, đánh dấu ngay khi đã nhận khóa. afterCompletion nhả named lock sau commit/rollback nhưng trước transaction manager trả connection vào pool; nếu release lỗi thì abort connection. Không đóng physical/logical connection trong callback. Cả JpaTransactionManager của ứng dụng và DataSourceTransactionManager của probe phải được kiểm chứng; từ chối nguồn không có binding và lời gọi reserve bên trong transaction có sẵn để tránh nhả khóa trước outer commit.

Transaction chờ GET_LOCK chưa được đọc/ghi dữ liệu nghiệp vụ, chưa giữ khóa owner/session. READ_COMMITTED giữ quota đọc mới sau khi đã có named lock. Tên khóa, timeout, khóa owner → session, check owner/version/replay, claim/attempt, commit → release và provider ngoài transaction được giữ. Callback rollback, GET_LOCK timeout, commit lỗi và process chết phải không làm rò khóa. Chỉ một connection bị mượn cho reservation; pool hết hoàn toàn vẫn có thể từ chối admission, không phải cam kết mọi request luôn được nhận.

Đưa HMAC fingerprint từ đầu vào create/text/hint/explanation/feedback và digest upload WAV ra trước reserve, tái sử dụng cho replay/accept. Khi fingerprint chưa cấu hình, defer về điểm kiểm tra cũ để giữ thứ tự lỗi và replay create; không gọi HMAC với key rỗng trước nhánh replay. Snapshot/catalog, dữ liệu hội thoại, quota, prompt từ trạng thái hiện tại và kiểm tra retry vẫn ở trong vùng khóa: đưa chúng ra ngoài cần giao thức version/revalidation riêng, không được dùng bản đọc cũ để giảm thời gian đo. Fingerprint xác nhận voice có nội dung được xác nhận sau khóa draft nên giữ nguyên vị trí. Validation văn bản, WAV, provider và xử lý sau completion vốn đã nằm ngoài vùng reserve được giữ nguyên. File stage của upload tiếp tục trong reserve để tránh thêm cửa sổ commit tham chiếu tới file chưa tồn tại; thay đổi giao thức file cần task riêng và kiểm thử crash.

Không dùng transaction commit thủ công rồi mới RELEASE_LOCK vì transaction manager có thể trả connection về pool trước lệnh release. Không bind một connection JDBC thủ công để ép JpaTransactionManager dùng lại; dùng resource binding và lifecycle của Spring đã có. Không thêm pool riêng, dependency hoặc migration.

## Điều kiện nghiệm thu

Lặp workload BR-13 một/hai ứng dụng, tuần tự/bốn client; cùng warm-up, số mẫu, quota diagnostic, fake provider và ngưỡng đặt trước. So P50/P95 tổng reservation, giữ khóa, throughput, lỗi và pool; không đặt trước tỷ lệ cải thiện. Xác minh CONNECTION_ID của truy vấn nghiệp vụ trùng IS_USED_LOCK mà không ghi ID vào log/event. Giữ chín connection phải vẫn thực thi SQL và commit với connection thứ mười, pending=0; giữ đủ mười phải từ chối có kiểm soát rồi phục hồi. Hai app không vượt quota; rollback/crash phải nhả cả row/named lock; commit lỗi và release lỗi phải được kiểm chứng.

## Kết quả và phạm vi vận hành

Hai lượt mã cuối đo 384 reservation trên bốn cấu hình workload đều đạt P95 ≤500 ms và throughput burst ≥1 tác vụ/s, không lỗi baseline/pool pending. P95 tổng 24,746–50,953 ms; peak active quan sát 1/10 khi tuần tự và tối đa 3/10 khi bốn client, so với 2/10 và tối đa 5/10 trước thay đổi. Không suy ra mọi cấu hình nhanh hơn: có lượt P95/throughput kém hơn khoảng baseline. Các ca quota qua hai app, timeout, rollback, kill giữa claim, cùng connection nghiệp vụ/named lock và pool pressure đều đạt; giữ chín connection vẫn nhận SQL/commit, giữ đủ mười trả 503 rồi phục hồi. Unit 10/10 kiểm lifecycle/commit/release/abort/binding; hai preparation case bảo toàn replay/HMAC. Clean verify 173 ca, 0 lỗi, 53 skip; 51 ca BR-12 đối chiếu đủ method/class/guard. Chi tiết và giới hạn fixture trong báo cáo.

Chốt dùng phương án này cho phạm vi demo một/hai app, 12 owner/16 session, burst bốn client, pool 10/app như workload đã đo; không tự giảm pool xuống 1 hoặc mở quota. Quota mặc định, timeout GET_LOCK 2 giây và isolation giữ nguyên. Lỗi mở transaction/lấy connection được phân loại 503 ADMISSION_UNAVAILABLE; kể cả lỗi cleanup sau commit, client giữ key cũ và đọc lại trạng thái khi retry. Không tự retry provider trong admission.

Giới hạn vận hành/điều kiện đánh giá lại của ADR 0022 vẫn áp dụng: khi thay DB/mạng, tăng workload nền/người học/instance hoặc thấy P95 reservation >500 ms, pool pending/lỗi bất ngờ, phải đo lại trước tăng quy mô. Timeout pool 1000 ms của fixture chưa là mặc định triển khai. Không cam kết tải production đa host, network partition hoặc soak dài đã nghiệm thu, không tuyên bố bỏ nút thắt serialization toàn schema. Không thay quota mặc định, payload Session, DTO hoặc tích hợp Azure; phân loại lỗi pool 503 đã cập nhật tài liệu API.
