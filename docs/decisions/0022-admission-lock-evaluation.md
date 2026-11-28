# ADR 0022 — Đánh giá khóa admission và ngân sách connection

Ngày 28/11/2026. Trạng thái: Baseline/đánh giá BR-13 đã chốt. **Phương án hai connection bên dưới là lịch sử trước BR-14**, được thay bằng cùng connection transaction tại [ADR 0023](0023-admission-transaction-connection.md). GET_LOCK liên instance được giữ; giới hạn tải/điều kiện đánh giá lại vẫn áp dụng. [Báo cáo BR-14](../development/backend-refactoring/evidence/br14-admission.md).

## Bối cảnh

AiAdmissionService dùng monitor theo bean/instance và GET_LOCK theo schema để serialize reservation đến sau commit READ_COMMITTED. Provider chạy sau reserve, ngoài transaction. BR-05/BR-12 kiểm chứng vòng đời và hồi quy; probe hai JVM cũ chỉ tăng bộ đếm, không có pool hay tác vụ thực. [Workload đặt trước](../development/backend-refactoring/evidence/br13-workload.md) xác định tải demo và ngưỡng cần đối chiếu.

## Rủi ro hai connection

Connection A được lấy trực tiếp từ DataSource và giữ advisory lock. TransactionTemplate/JpaTransactionManager lấy connection B để kiểm tra quota, claim và commit. Chỉ B nằm trong transaction Spring; GET_LOCK gắn với MySQL session A, không tự nhả khi B rollback. Vì vậy giữ A qua commit/rollback rồi RELEASE_LOCK trong finally là điều kiện đúng đắn; khi release thất bại phải abort A thay vì trả một physical connection còn khóa vào pool.

Pool=1 không đủ cho mô hình này. Pool=10 cũng có thể bị chiếm hết: chín connection nghiệp vụ khác và A khiến B chờ đến connectionTimeout dù MySQL khỏe. Monitor cùng JVM giảm số reservation đồng thời lấy connection A nhưng không chặn completion, HTTP, worker hoặc các giao dịch khác dùng pool. Hai instance có pool riêng vẫn chia sẻ một named lock, do đó tăng số instance không tự tăng throughput reservation; nó có thể tăng thời gian chờ và tổng số connection đang bị chiếm.

Nếu B chờ quá lâu, A vẫn giữ khóa và instance kia có thể hết GET_LOCK timeout 2 giây. Timeout pool và timeout named lock là hai nguồn lỗi khác nhau; cần đo cả chờ monitor, lấy connection, chờ/giữ khóa và pool pending. Pool timeout không bảo đảm mọi loại lỗi transaction đều được đổi thành ADMISSION_UNAVAILABLE: lỗi mở transaction hiện có thể đi theo handler lỗi 500; cần quyết định riêng nếu muốn đổi hợp đồng.

Kill process trước commit phải làm MySQL rollback B và giải phóng A khi TCP/session đóng. Tốc độ phát hiện process chết cục bộ không chứng minh thời gian phục hồi khi mất mạng/partition. Kill sau commit nhưng trước provider là trường hợp lease/fencing, có kiểm thử riêng ở coordinator; không được coi rollback probe là bằng chứng bao phủ mọi cửa sổ crash.

## Các lựa chọn

1. Giữ khóa hiện tại với vùng reserve ngắn, giám sát pool và giới hạn vận hành có bằng chứng. Không giữ transaction/khóa qua provider. Phù hợp nếu workload mục tiêu đạt và quota đúng trên hai instance.
2. Đưa phần không cần nguyên tử ra ngoài vùng khóa sau khi xác minh snapshot/idempotency. Vẫn phải bảo đảm quota toàn hệ thống nguyên tử.
3. Thiết kế admission dùng cùng connection cho khóa và transaction, hoặc quota/counter được khóa nguyên tử trong DB. Cần phân tích release sau commit, nested transaction, pool failure, schema/index và rollback trước khi triển khai; không coi là thay thế tương đương chỉ qua đổi tên lớp.
4. Khóa JVM đơn lẻ không thay được tính nguyên tử liên instance và bị loại.

## Quyết định sau phép đo

Giữ nguyên thuật toán khóa trong BR-13; đề xuất BR-14 chọn phương án 1 cho phạm vi demo đã đo, kết hợp rà phần việc có thể đưa ra ngoài vùng nguyên tử nếu vẫn giữ snapshot/idempotency. [Báo cáo](../development/backend-refactoring/evidence/br13-admission-measurements.md) và [số liệu](../development/backend-refactoring/evidence/br13-admission-results.json) ghi bốn lượt độc lập, 768 reservation baseline trên một/hai app: P95 tổng reservation 23,166–71,512 ms, pool pending=0, peak active cao nhất 5/10; đủ bốn quota, GET_LOCK timeout, rollback và crash đều đạt. Không đặt mục tiêu cải thiện phần trăm hoặc tuyên bố đã loại bỏ nút thắt.

Phạm vi quyết định: 12 owner/16 session, burst bốn client, pool 10/app và fake provider như workload đã công bố. Quota mặc định giữ nguyên. Không lấy throughput burst làm số tác vụ duy trì vượt RPM. Pool 10 không bảo đảm an toàn dưới mọi tải: ca chiếm chín connection đã tái hiện lỗi transaction và xác minh nhả khóa. Không khuyến nghị pool=1; hai connection là nhu cầu tối thiểu riêng cho một reservation, còn phải dành ngân sách cho completion/session/worker/HTTP. Timeout pool 1000 ms là fixture đo lỗi, chưa áp thành chính sách triển khai.

BR-14 tiếp tục mở để chốt giới hạn vận hành, khả năng giảm vùng khóa và cách phân loại lỗi transaction/pool. Cần đánh giá lại khi thêm workload nền, tăng người học/instance, dùng DB từ xa, hoặc quan sát P95 reservation vượt 500 ms, pool có pending, lỗi ADMISSION_BUSY/transaction ngoài lỗi quota chủ động. Các ngưỡng này là điều kiện đánh giá, chưa phải cơ chế cảnh báo đã triển khai. Nghiệm thu nhiều host, network partition, soak dài và provider thật cần bằng chứng riêng.

Instrumentation JFR chỉ thêm thời gian/outcome, mặc định tắt; monitor theo instance, tên khóa, timeout 2 giây, isolation và thứ tự commit → release không đổi. Không thay API/schema/dependency hoặc gọi provider thật trong phép đo. BR-13 được đóng riêng sau hồi quy mã cuối; ADR này không tự đóng BR-14/BR-15.
