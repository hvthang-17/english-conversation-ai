# ADR 0016 — Xác nhận bản chép lời trong hội thoại G6

**Trạng thái.** Đã chốt cho G6.

- Trong VOICE, học viên ghi âm, có thể nghe lại hoặc hủy, rồi bấm **Gửi bản ghi**. Trước khi gửi, giao diện thông báo Gemini xử lý âm thanh và Azure chấm phát âm mặc định khi tính năng được bật.
- Sau khi nhận bản ghi, backend gọi Gemini chép lời. Giao diện hiển thị bản chép nháp để học viên xem, sửa và xác nhận. Chỉ sau xác nhận, backend mới tạo tin người học và dùng nội dung đã xác nhận cho CHAT.
- Chấm phát âm mặc định cho lượt VOICE khi dịch vụ được bật. Sau khi học viên xác nhận, nếu bản ghi tối đa 30 giây và còn hạn, Azure nhận bản ghi gốc bằng `UNSCRIPTED`, locale `en-US`. Tác vụ chấm độc lập với CHAT. Nếu chấm lỗi hoặc audio hết hạn, hội thoại vẫn tiếp tục. Điểm hiện riêng ở từng lượt người học và trong tổng kết khi có dữ liệu hợp lệ.
- Dùng lại TTS tiếng Anh cục bộ cho câu AI. Giữ nghe theo thao tác, nghe lại, nghe chậm và phụ đề riêng từng câu.
- Giữ TTL audio một giờ. Nháp chờ xác nhận hết hạn sau 30 phút kể từ READY và được khôi phục khi tải lại nếu còn hạn. Backend giữ tham chiếu đến khi các tác vụ cần audio kết thúc hoặc tệp hết hạn, đồng thời dọn khi xóa buổi.

[Kế hoạch và kiểm thử G6](../development/full-system-implementation-plan.md#g6--luyện-hội-thoại-với-ai)