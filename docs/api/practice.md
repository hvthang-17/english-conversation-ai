# API buổi luyện

Các route dùng Session cookie, owner từ principal và CSRF cho POST. ID là chuỗi trong JSON. Tất cả thao tác trả bản nhìn mới của buổi, gồm trạng thái, vai, mục tiêu, level, số lượt, transcript và trạng thái AI hiện hành.

| Method và đường dẫn dưới /api/v1/practice-sessions | Nội dung |
| --- | --- |
| POST / | `scenarioId`, `level`, `clientRequestId`, `mode` tùy chọn. Kiểm tra catalog, chụp snapshot và tạo OPENING số 0. |
| GET /{id} | Đọc buổi và trạng thái tác vụ. Giá trị không thuộc owner trả 404. |
| POST /{id}/turns | `content`, `clientRequestId`. Lưu câu người học cùng lượt trước khi gọi AI. |
| POST /{id}/hints | `clientRequestId`. Trả `hint` tạm thời sau khi xử lý thành công. HINT không tạo turn/message. |
| POST /{id}/operations/{operation}/retry | `acknowledgeUnknown`. Dùng lại tác vụ/lượt hiện có. |
| POST /{id}/operations/{operation}/skip | Bỏ phản hồi FAILED/UNKNOWN. Giữ nguyên câu người học. Khi OPENING lỗi, dùng retry hoặc kết thúc buổi. |
| POST /{id}/finish | Kết thúc khi không còn tác vụ RUNNING. COMPLETED nếu đã có câu người học, còn lại ABANDONED. |

Client dùng lại cùng mã yêu cầu khi gửi lại nội dung đã chuẩn hóa. Mã trùng với dữ liệu khác trả 409. HINT thành công đã trả có thể báo `hintAlreadyConsumed` vì hệ thống không lưu nội dung gợi ý. Khi một tác vụ đang chạy, request giống nhau đọc trạng thái, request bắt đầu tác vụ khác nhận 409. Hạn mức cục bộ trả 429 trước khi gọi provider.

Kết quả provider lỗi được biểu diễn trong `operation.status` và `errorCode`, kể cả khi HTTP đã tiếp nhận thành công. UNKNOWN cần xác nhận trước retry hoặc cho phép bỏ qua phản hồi và tiếp tục. Đọc trạng thái thu hồi lease quá hạn của tác vụ bị bỏ dở. Attempt cũ trả muộn không được ghi đè attempt mới. Hệ thống không tự retry provider.

Mỗi buổi tối đa 20 câu LEARNER được nhận. OPENING, HINT và thử lại không tăng số lượt. Snapshot khóa theo buổi và API chỉ trả các trường dành cho người học. Nội dung gợi ý và dữ liệu phản hồi thô không nằm trong bảng ai_attempts.

## Hai chế độ buổi luyện

Luồng theo [ADR 0009](../decisions/0009-practice-modes.md).

| Thao tác | Hợp đồng |
| --- | --- |
| Tạo buổi | Thêm `mode` nhận `TEXT` hoặc `VOICE`, mặc định `TEXT` khi client cũ bỏ trường. Giá trị khác trả 400. Kiểm tra gửi lại theo mode ban đầu cùng với tình huống và trình độ. |
| Đọc buổi và phản hồi mutation | Thêm `mode` hiện tại. Vẫn trả nội dung AI qua API owner-only để trình duyệt phát giọng cục bộ và mở phụ đề chủ động. |
| `PATCH /{id}/mode` | Body `{"mode":"TEXT"}`. Yêu cầu Session, CSRF và quyền sở hữu. Cho chuyển một chiều từ VOICE khi buổi còn ACTIVE. Gọi lại khi đã TEXT trả trạng thái hiện có. Thao tác chỉ cập nhật mode. |

Chuyển chế độ vẫn giữ các lượt và ID tác vụ. Gửi lại yêu cầu tạo buổi ban đầu sau khi chuyển trả buổi hiện tại. Nhập bằng bàn phím cho một lượt VOICE dùng endpoint gửi câu hiện có. [API ghi âm](voice.md)
