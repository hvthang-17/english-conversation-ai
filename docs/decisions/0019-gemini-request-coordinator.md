# ADR 0019 — Điều phối vòng đời tác vụ Gemini

Ngày: 28/11/2026. Trạng thái: Được chọn cho BR-05; bằng chứng triển khai tại [nghiệm thu](../development/backend-refactoring/evidence/br05-ai-request-coordinator.md).

## Bối cảnh

CHAT, HINT, EXPLAIN (giải thích), FEEDBACK và TRANSCRIPTION dùng cùng admission, request/attempt, lease và usage. Chúng có kiểm tra nguồn, điều kiện retry, đầu ra và dữ liệu cần lưu khác nhau. Sau BR-04, persistence đã ở repository nhưng điều phối chung vẫn nằm trong PracticeService.

## Quyết định

AiRequestCoordinator trong module ai quản lý reserve qua AiAdmissionService, kiểm tra quota trước khi dựng prompt, claim/attempt, gọi TextProvider ngoài transaction, fencing completion theo status/attempt, ghi usage/cost và expiry của request/attempt.

Coordinator không phụ thuộc module conversation/feedback/voice/auth và không xác thực người dùng. Callback khóa owner/session và áp điều kiện nghiệp vụ chạy đầu tiên trong transaction completion. Nếu khóa thất bại, callback ghi không chạy. Khi request không còn RUNNING, attempt đã đổi hoặc buổi không còn đủ điều kiện, chỉ đọc view hiện tại. Callback lưu đầu ra riêng cho từng task nằm ở lớp nghiệp vụ, không gọi provider.

Reservation giữ nguyên advisory lock MySQL, timeout và isolation READ_COMMITTED. Claim gồm cập nhật request, tạo attempt rồi callback cập nhật trạng thái task trong cùng transaction. invoke từ chối chạy trong transaction hoặc dispatch replay. Completion mở transaction ngắn, khóa/expire rồi kiểm tra attempt trước callback ghi; provider và validation feedback ngoài transaction như luồng hiện tại. Validation explanation/transcript không gọi mạng và giữ vị trí transaction hiện tại.

Retry limit, acknowledge UNKNOWN, cooldown và ngoại lệ phục hồi validator thuộc chính sách nghiệp vụ từng task. Không áp thêm trần retry chung làm mất khả năng phục hồi feedback đã chốt. Coordinator xử lý quota/usage/state dùng chung, caller chỉ claim sau các kiểm tra retry tương ứng.

Azure pronunciation và Azure TTS giữ adapter/worker và hợp đồng riêng. Không gộp vào coordinator Gemini. Thay cơ chế khóa admission chỉ thực hiện sau BR-13; không suy ra cải thiện tải từ refactor này.

## Hệ quả

Callback có hợp đồng rõ: khóa và kiểm tra quyền trước ghi; không nhận output AI làm quyết định phân quyền; không gọi dịch vụ mạng trong callback transaction. Cần hồi quy cả năm task, stale completion/lease, retry/quota và guard transaction. PracticeService vẫn còn tác vụ riêng để tách ở BR-06, không chuyển toàn bộ sang coordinator. Không có migration hoặc thay đổi API cần thiết.
