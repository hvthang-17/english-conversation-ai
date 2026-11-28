# ADR 0016 — Xác nhận bản chép lời trong hội thoại G6

**Trạng thái.** Đã chốt cho G6.

- Trong VOICE, học viên ghi âm, có thể nghe lại hoặc hủy, rồi bấm **Gửi bản ghi**. Trước khi gửi, giao diện thông báo Gemini xử lý âm thanh và Azure chấm phát âm mặc định khi tính năng được bật.
- Sau khi nhận bản ghi, backend gọi Gemini chép lời. Giao diện hiển thị bản chép nháp để học viên xem, sửa và xác nhận. Chỉ sau xác nhận, backend mới tạo tin người học và dùng nội dung đã xác nhận cho CHAT.
- Chấm phát âm mặc định cho lượt VOICE khi dịch vụ được bật. Sau khi học viên xác nhận, nếu bản ghi tối đa 30 giây và còn hạn, Azure nhận bản ghi gốc bằng `UNSCRIPTED`, locale `en-US`. Tác vụ chấm độc lập với CHAT. Nếu chấm lỗi hoặc audio hết hạn, hội thoại vẫn tiếp tục. Điểm hiện riêng ở từng lượt người học và trong tổng kết khi có dữ liệu hợp lệ.
- Dùng lại TTS tiếng Anh cục bộ cho câu AI. Giữ nghe theo thao tác, nghe lại, nghe chậm và phụ đề riêng từng câu.
- Giữ TTL audio một giờ. Nháp chờ xác nhận hết hạn sau 30 phút kể từ READY và được khôi phục khi tải lại nếu còn hạn. Backend giữ tham chiếu đến khi các tác vụ cần audio kết thúc hoặc tệp hết hạn, đồng thời dọn khi xóa buổi.

## Giới hạn 30 giây và trạng thái chấm theo lượt

**Trạng thái.** Triển khai ở G6.4a với Flyway V31.

- Hội thoại VOICE ghi âm, tiếp nhận WAV mới và chấm phát âm tối đa **30 giây**. Giao diện hiển thị bộ đếm và tự dừng. Backend chấp nhận đúng 30 giây, trả HTTP 413 `AUDIO_TOO_LARGE` nếu vượt ngưỡng trước khi tạo tác vụ Gemini. Bài luyện phát âm riêng tiếp tục theo [ADR 0015](0015-azure-pronunciation-assessment-g5.md).
- Bản nháp dài hơn 30 giây đã lưu trước thay đổi vẫn được đọc và xác nhận khi còn hạn. Lượt đó tiếp tục CHAT và ghi `TOO_LONG` cho phần chấm. Các điểm đã lưu trước đây vẫn đọc được.
- Khi xác nhận, lưu tin người học và quyết định chấm trong cùng giao dịch. Với lượt đủ điều kiện, lưu yêu cầu chuẩn bị chấm bền vững. Worker chuẩn bị audio ngoài giao dịch xác nhận, kiểm tra lại hạn, tình trạng tệp và quyền tiếp tục xử lý trước khi tạo tối đa một lượt chấm cho mỗi audio. Khởi động lại có thể tiếp tục yêu cầu đang chờ. Việc chuẩn bị hoặc gọi Azure thất bại vẫn cho phép CHAT tiếp tục.
- Phân biệt đang chuẩn bị, đang chấm, chấm thành công, bỏ qua và chấm không thành công. Ghi nguyên nhân cụ thể như vượt giới hạn cũ, audio hết hạn, thiếu tệp, lỗi chuẩn bị, tính năng tắt, hết hạn mức hoặc không đủ lời nói. Lượt cũ thiếu cả trạng thái lẫn kết quả hiển thị **Chưa có thông tin chấm phát âm**.
- Tin nhắn và tổng kết dùng cùng ánh xạ trạng thái. Tổng kết hiện số lượt có điểm, đang xử lý và thiếu điểm theo lý do. Điểm thiếu để trống. Điểm 0 hợp lệ hiện `0/100`. Giữ lý do sau khi dọn audio và dọn cùng dữ liệu buổi khi xóa.

[Kế hoạch và kiểm thử G6](../development/full-system-implementation-plan.md#g6--luyện-hội-thoại-với-ai)
