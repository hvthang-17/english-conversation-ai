# API giọng nói theo lượt

Các đường dẫn dưới đây dùng tiền tố `/api/v1/practice-sessions/{id}/audio`. Người gọi phải sở hữu buổi luyện. POST và DELETE dùng Session cookie và CSRF như [API tài khoản](auth.md). Giới hạn buổi 20 lượt chỉ tăng khi bản chép lời được xác nhận thành công vào buổi luyện.

| Phương thức | Đường dẫn tương đối | Hợp đồng |
| --- | --- | --- |
| POST | gốc | Multipart với `file`, `clientRequestId` (8–64 ký tự chữ/số, gạch ngang hoặc gạch dưới), `retry` tùy chọn. Trả về bản chép lời hoặc trạng thái xử lý |
| GET | `/{audioId}` | Đọc trạng thái của bản ghi khi lần gửi trước còn xử lý |
| POST | `/{audioId}/confirm` | JSON `{"content":"I would like soup, please."}`. Kiểm tra 1–1000 ký tự và giới hạn đầu vào đã cấu hình. Trả về buổi luyện cập nhật theo [API hội thoại](practice.md) |
| DELETE | `/{audioId}` | Hủy nháp hoặc tác vụ đã thất bại và trả HTTP 204. Tác vụ đang xử lý hoặc bản đã xác nhận trả HTTP 409 |

Định dạng upload là `audio/wav`, RIFF WAV chuẩn 44 byte, PCM tuyến tính 16-bit little-endian, mono 16 kHz, tối đa 60 giây và 1.920.044 byte kể cả header. Máy chủ đối chiếu header với kích thước thực, từ chối codec sai, file rỗng và PCM toàn số 0. Nội dung vượt dung lượng trả 413.

Phản hồi gồm `id` dạng chuỗi, `status` (`TRANSCRIBING`, `READY`, `CONFIRMED`, `FAILED`, `EXPIRED`), `transcript`, `expiresAt` theo UTC, `durationMillis`, `errorCode`, `retryable`, `unknown` và `retryAt`. Transcript chỉ có trong trạng thái READY và còn hạn. Đường dẫn file và dữ liệu audio thuộc máy chủ.

Gửi lại cùng key và cùng file đọc trạng thái hiện có. Gửi file khác với key cũ trả 409. Với lỗi có thể thử lại, người học gửi lại cùng file/key và đặt `retry=true`. Hạn mức chung, thời gian chờ và số lần thử tối đa vẫn áp dụng. Với `unknown=true`, giao diện giải thích khả năng lần gọi trước đã dùng hạn mức và yêu cầu người học chọn gọi lại.

Sau khi xác nhận, việc gửi lại cùng bản ghi và cùng nội dung trả trạng thái hội thoại hiện có. Thay nội dung sau khi đã xác nhận trả 409. Nếu Gemini lỗi ở bước CHAT, câu LEARNER đã chấp nhận vẫn còn trong hội thoại và dùng nút thử lại phản hồi hiện có.
