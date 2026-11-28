# API buổi luyện

Các route dùng Session cookie, owner từ principal và CSRF cho POST. ID là chuỗi trong JSON. Bản đọc buổi gồm trạng thái, vai, mục tiêu, level, số lượt, transcript và trạng thái AI hiện hành. API gợi ý và giải thích trả DTO riêng.

| Method và đường dẫn dưới /api/v1/practice-sessions | Nội dung |
| --- | --- |
| POST / | `scenarioId`, `level`, `clientRequestId`, `mode` và `roleCode` tùy chọn. Kiểm tra catalog, chụp snapshot vai được chọn và tạo OPENING số 0. |
| GET /{id} | Đọc buổi và trạng thái tác vụ. Giá trị không thuộc owner trả 404. |
| POST /{id}/turns | `content`, `clientRequestId`. Lưu câu người học cùng lượt trước khi gọi AI. |
| POST /{id}/hints | Nhận `messageId` và `clientRequestId`. Trả trạng thái và gợi ý theo câu. |
| GET /{id}/messages/{messageId}/hint | Đọc gợi ý đã lưu hoặc trạng thái theo câu AI. |
| GET /{id}/messages/{messageId}/explanation | Đọc trạng thái hoặc giải thích đã lưu cho câu AI thuộc buổi. GET không gọi Gemini. |
| POST /{id}/messages/{messageId}/explanation | JSON `{clientRequestId,retry?,acknowledgeUnknown?}`. Tạo hoặc thử lại giải thích cho câu AI gần nhất của buổi đang luyện. |
| POST /{id}/operations/{operation}/retry | `acknowledgeUnknown`. Dùng lại tác vụ/lượt hiện có. |
| POST /{id}/operations/{operation}/skip | Bỏ phản hồi FAILED/UNKNOWN. Giữ nguyên câu người học. Khi OPENING lỗi, dùng retry hoặc kết thúc buổi. |
| POST /{id}/finish | Kết thúc khi không còn tác vụ PENDING/RUNNING. COMPLETED khi có ít nhất một lượt người học được AI trả lời thành công, còn lại ABANDONED. |

Quy tắc kết thúc áp dụng khi buổi chuyển từ ACTIVE sang trạng thái cuối. Các buổi đã đóng giữ nguyên trạng thái và cách tính thống kê trước đó. Câu đã gửi và các phản hồi hiện có vẫn xem được trong lịch sử. Tạo nhận xét và đếm số buổi hoàn thành dùng trạng thái COMPLETED. Lượt người học đã được lưu vẫn tính vào số lượt hoạt động.

Client dùng lại cùng mã yêu cầu khi gửi lại nội dung đã chuẩn hóa. Mã trùng với dữ liệu khác trả 409. Khi một tác vụ đang chạy, request giống nhau đọc trạng thái, request bắt đầu tác vụ khác nhận 409. Hạn mức cục bộ trả 429 trước khi gọi provider.

Buổi trả `roleCode` cùng vai người học, vai AI, bối cảnh và mục tiêu trong snapshot. Bỏ `roleCode` khi tạo sẽ dùng `DEFAULT`. Mã đúng định dạng nhưng không thuộc tình huống trả 404 `ROLE_NOT_AVAILABLE`. Đổi vai với cùng mã tạo buổi trả 409. Để luyện vai khác, bắt đầu một buổi mới.

Kết quả provider lỗi được biểu diễn trong `operation.status` và `errorCode`, kể cả khi HTTP đã tiếp nhận thành công. UNKNOWN cần xác nhận trước retry hoặc cho phép bỏ qua phản hồi và tiếp tục. Đọc trạng thái thu hồi lease quá hạn của tác vụ bị bỏ dở. Attempt cũ trả muộn không được ghi đè attempt mới. Hệ thống không tự retry provider.

Mỗi buổi tối đa 20 câu LEARNER được nhận. OPENING, HINT và thử lại không tăng số lượt. Snapshot khóa theo buổi và API chỉ trả các trường dành cho người học. Nội dung gợi ý và dữ liệu phản hồi thô không nằm trong bảng ai_attempts.

Admission trả `503 ADMISSION_BUSY` khi chờ khóa toàn hệ thống quá hạn; `503 ADMISSION_UNAVAILABLE` khi không thể mở transaction/lấy connection hoặc bảo đảm cleanup khóa. BR-14 chuẩn hóa cả lỗi mở transaction do pool cạn về ADMISSION_UNAVAILABLE. Khi thử lại, giữ nguyên clientRequestId và nội dung; không coi riêng 503 là bằng chứng transaction trước chưa commit. Đọc lại trạng thái/tác vụ và giữ quy tắc retry/acknowledgeUnknown hiện có; server không tự gọi provider lại vì lỗi cleanup. Không thay cấu trúc DTO hoặc quy tắc quota/idempotency.

## Hai chế độ buổi luyện

Luồng theo [ADR 0009](../decisions/0009-practice-modes.md).

| Thao tác | Hợp đồng |
| --- | --- |
| Tạo buổi | `mode` nhận `TEXT` hoặc `VOICE`, mặc định `TEXT` khi client cũ bỏ trường. Giá trị khác trả 400. Kiểm tra gửi lại theo mode ban đầu, mã vai, tình huống và trình độ. |
| Đọc buổi và phản hồi mutation | Thêm `mode` hiện tại. Vẫn trả nội dung AI qua API owner-only để trình duyệt phát giọng cục bộ và mở phụ đề chủ động. |
| `PATCH /{id}/mode` | Body `{"mode":"TEXT"}`. Yêu cầu Session, CSRF và quyền sở hữu. Cho chuyển một chiều từ VOICE khi buổi còn ACTIVE. Gọi lại khi đã TEXT trả trạng thái hiện có. Thao tác chỉ cập nhật mode. |

Giao diện chọn chế độ trước khi bắt đầu buổi và dùng endpoint gửi câu cho lượt gõ thay trong VOICE. PATCH được giữ cho tương thích API; khi được gọi, thao tác giữ các lượt và ID tác vụ. Gửi lại yêu cầu tạo ban đầu trả buổi hiện tại. [API ghi âm](voice.md)

## Khôi phục gợi ý theo câu AI

Hợp đồng theo [ADR 0017](../decisions/0017-persist-conversation-hints.md) có từ V30. Triển khai frontend cùng backend hỗ trợ `messageId`.

| Phương thức | Hành vi sau bản sửa |
| --- | --- |
| `POST /{id}/hints` | Nhận `{messageId, clientRequestId}`. Bắt đầu tác vụ cho câu AI mới nhất của buổi đang luyện hoặc trả lại nội dung/trạng thái đã có. |
| `GET /{id}/messages/{messageId}/hint` | Đọc gợi ý hoặc trạng thái của câu AI thuộc buổi của chủ phiên. Trả `NOT_STARTED` nếu chưa có tác vụ. GET không gọi Gemini. |
| `POST /{id}/operations/{operation}/retry` | Thử lại tác vụ HINT hiện có nếu còn đủ điều kiện và câu vẫn là câu AI gần nhất. Giữ quy tắc `acknowledgeUnknown`. |

DTO gợi ý gồm `messageId`, `operationId`, `status`, `hint`, `errorCode`, `retryable` và `retryNotBefore`. Bản đọc buổi và phản hồi thử lại cũng đính kèm DTO này trong trường `hint` cho câu AI gần nhất. Nội dung chỉ có sau khi kết quả hợp lệ được lưu cùng trạng thái `SUCCEEDED`. ID không thuộc buổi, tin người học hoặc buổi thuộc tài khoản khác trả 404. Tạo mới cho câu cũ, tạo lúc buổi đã đóng hoặc dùng lại mã đã tạo tác vụ cho câu khác trả 409. Gửi trùng cùng câu đang xử lý trả trạng thái hiện có. Hai tab cùng xin gợi ý cho một câu dùng chung kết quả và chỉ gọi Gemini một lần.

## Giải thích câu AI

Mỗi câu AI có tối đa một bản giải thích. GET trả `NOT_STARTED` khi chưa có tác vụ. Gửi trùng trả trạng thái hiện có. Một mã đã dùng để tạo giải thích cho câu khác trong cùng buổi trả 409. Câu không thuộc buổi, câu của người học hoặc buổi thuộc người khác trả 404. Tạo hoặc gọi lại sau khi buổi đóng hay sau khi đã có câu AI mới bị từ chối.

Phản hồi gồm `messageId`, `status`, `errorCode`, `retryable`, `retryNotBefore`, `simpleEnglish` và `meaningVi`. Hai phần nội dung chỉ có khi kết quả được xác thực và lưu thành công. Khi kết quả lần trước chưa rõ, gửi `retry=true` cùng `acknowledgeUnknown=true` để xác nhận gọi lại. EXPLAIN dùng hạn mức chung và giải phóng khóa của buổi sau timeout. Tác vụ không tăng số lượt người học, nội dung không nằm trong transcript. Giao diện ẩn nghĩa tiếng Việt đến khi người học mở.

CHAT và HINT dùng phần hội thoại gần nhất vừa với CHAT_CONTEXT_LIMIT, tính cả chỉ dẫn hệ thống. Transcript đầy đủ vẫn được lưu. Nếu ngay cả chỉ dẫn và tin mới nhất vượt giới hạn, API trả CONTEXT_LIMIT.

Trong chi tiết buổi, tin người học có lượt chấm thành công trả pronunciation.recognizedText và pronunciation.words khi Azure có dữ liệu. Mỗi từ có điểm, mã nhận xét và danh sách âm vị IPA khi có. Dữ liệu lấy từ kết quả đã lưu và mở trong mục Xem chi tiết phát âm.
