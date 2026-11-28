# API giọng nói theo lượt

Các đường dẫn dưới đây dùng tiền tố `/api/v1/practice-sessions/{id}/audio`. Người gọi phải sở hữu buổi luyện. POST và DELETE dùng Session cookie và CSRF như [API tài khoản](auth.md). Giới hạn buổi 20 lượt chỉ tăng khi bản chép lời được xác nhận thành công vào buổi luyện.

| Phương thức | Đường dẫn tương đối | Hợp đồng |
| --- | --- | --- |
| POST | gốc | Multipart với `file`, `clientRequestId` (8–64 ký tự chữ/số, gạch ngang hoặc gạch dưới), `retry` tùy chọn. Trả về bản chép lời hoặc trạng thái xử lý |
| GET | gốc | Khôi phục bản chưa xác nhận mới nhất còn hiệu lực của buổi VOICE đang luyện. Trả HTTP 200 với `TRANSCRIBING` hoặc `READY`, hoặc HTTP 204 khi không có bản phù hợp |
| GET | `/{audioId}` | Đọc trạng thái của bản ghi khi lần gửi trước còn xử lý |
| POST | `/{audioId}/confirm` | JSON `{"content":"I would like soup, please.","assessPronunciation":true}`. Kiểm tra 1–1000 ký tự và giới hạn đầu vào đã cấu hình. Trả về buổi luyện cập nhật theo [API hội thoại](practice.md) |
| DELETE | `/{audioId}` | Hủy nháp hoặc tác vụ đã thất bại và trả HTTP 204. Tác vụ đang xử lý hoặc bản đã xác nhận trả HTTP 409 |

Định dạng upload là `audio/wav`, RIFF WAV chuẩn 44 byte, PCM tuyến tính 16-bit little-endian, mono 16 kHz. Giới hạn mới cho VOICE là **30 giây và 960.044 byte** kể cả header. Máy chủ đối chiếu header với kích thước thực, từ chối codec sai, file rỗng và PCM toàn số 0. Bản ghi vượt giới hạn trả 413 `AUDIO_TOO_LARGE` trước khi tạo tác vụ chép lời. Bản nháp dài hơn đã được lưu trước thay đổi vẫn xác nhận được khi còn hạn.

Phản hồi gồm `id` dạng chuỗi, `status` (`TRANSCRIBING`, `READY`, `CONFIRMED`, `FAILED`, `EXPIRED`), `transcript`, `expiresAt` theo UTC, `durationMillis`, `errorCode`, `retryable`, `unknown` và `retryAt`. Transcript chỉ có trong trạng thái READY và còn hạn. Đường dẫn file và dữ liệu audio thuộc máy chủ.

GET khôi phục chỉ đọc lại trạng thái hoặc bản chép lời gốc đã lưu, không gọi Gemini. Chủ sở hữu có thể tiếp tục theo dõi bằng GET theo `audioId`. Nội dung sửa trước xác nhận nằm trong bộ nhớ của màn hình và sẽ mất khi tải lại.

Gửi lại cùng key và cùng file đọc trạng thái hiện có. Gửi file khác với key cũ trả 409. Với lỗi có thể thử lại, người học gửi lại cùng file/key và đặt `retry=true`. Hạn mức chung, thời gian chờ và số lần thử tối đa vẫn áp dụng. Với `unknown=true`, giao diện giải thích khả năng lần gọi trước đã dùng hạn mức và yêu cầu người học chọn gọi lại.

Giao diện G6 mặc định đặt `assessPronunciation=true` khi phiên làm việc bật chấm và bản ghi dài tối đa 30 giây. Backend chỉ xếp lịch Azure từ audio gốc còn hạn. CHAT xử lý bản chép đã xác nhận độc lập với Azure.

Sau khi xác nhận, việc gửi lại cùng bản ghi và cùng nội dung trả trạng thái hội thoại hiện có. Thay nội dung sau khi đã xác nhận trả 409. Nếu Gemini lỗi ở bước CHAT, câu LEARNER đã chấp nhận vẫn còn trong hội thoại và dùng nút thử lại phản hồi hiện có.

## Trạng thái chấm phát âm

Trạng thái theo lượt có từ V31. [ADR 0016](../decisions/0016-g6-direct-voice-submission.md#giới-hạn-30-giây-và-trạng-thái-chấm-theo-lượt).

DTO tin người học phát từ audio trả trạng thái chấm ngay cả khi chưa có `pronunciation_attempts`. Trả `PENDING_PREPARATION` khi đã lưu yêu cầu chuẩn bị, `SKIPPED` hoặc `FAILED` nếu dừng trước khi tạo lượt chấm. Khi đã có lượt chấm, trạng thái và điểm lấy từ lượt đó. Với dữ liệu cũ thiếu bằng chứng, trả `NOT_RECORDED`. Tin nhập bằng bàn phím không có trạng thái chấm.

`errorCode` giải thích các trường hợp như `TOO_LONG`, `AUDIO_EXPIRED`, `AUDIO_MISSING`, `PREPARATION_FAILED` hoặc `ASSESSMENT_DISABLED`. Khi đã có tác vụ Azure, dùng mã lỗi được lưu của lượt chấm, gồm hạn mức hoặc không đủ bằng chứng. Điểm thiếu trả `null`. Gửi xác nhận lặp dùng lại trạng thái đã lưu và giữ tối đa một lượt chấm trên mỗi bản ghi.
