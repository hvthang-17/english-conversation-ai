# Giai đoạn 6 — Giọng nói theo lượt và nghe câu AI

Đã triển khai luồng ghi, nghe lại, gửi để Gemini chép lời, sửa và xác nhận thành lượt CHAT. Nút nghe câu AI dùng giọng tiếng Anh cục bộ từ Web Speech API. Giao diện vẫn cho nhập và đọc văn bản khi micro hoặc giọng hệ thống không khả dụng.

Trang chi tiết tình huống cho chọn **Chat văn bản** hoặc **Luyện nghe và nói**. Ở chế độ nghe, câu AI mặc định ẩn, phụ đề chỉ hiện khi người học yêu cầu. Flyway V9 giữ buổi cũ ở TEXT, lưu chế độ ban đầu để chống trùng và đọc lại chế độ hiện tại sau tải lại. [ADR 0009](../../decisions/0009-practice-modes.md)

## Vận hành

- Flyway áp dụng V8 khi backend khởi động. Migration giữ nội dung TEXT trước đó và thêm FK ghép cùng buổi cho tin nhắn AUDIO. Xem [hợp đồng API](../../api/voice.md).
- Trình duyệt chọn codec ghi có sẵn, ưu tiên WebM/Opus. Sau thao tác gửi, Web Audio giải mã và chuẩn hóa thành WAV PCM mono 16 kHz. Máy chủ kiểm tra định dạng, kích thước và thời lượng thật trước khi gọi Gemini.
- REST gửi WAV inline vào `generateContent`, dùng model đã cấu hình ở `GEMINI_CHAT_MODEL` và cùng hạn mức với CHAT/HINT. Model cần hỗ trợ audio input. Lời gọi chép lời và CHAT sau xác nhận được hạch toán riêng. Trước khi mở dùng thử, xác minh khả năng model và điều khoản của project với mẫu hư cấu trong phạm vi gọi được cấp. [Tài liệu audio của Google](https://ai.google.dev/gemini-api/docs/audio)
- Đặt `VOICE_TEMP_DIRECTORY` trên ổ riêng tư của tiến trình backend, ngoài thư mục tĩnh và bản sao lưu. Mặc định là `.runtime/voice` theo thư mục làm việc backend. Nếu chạy nhiều instance, chúng phải thấy cùng thư mục để tiến trình dọn xử lý được file đã ghi.
- Thời hạn audio và nháp có cấu hình riêng. Mặc định tối đa 1 giờ từ lúc upload và 30 phút kể từ lúc READY. File audio thường được dọn ngay khi chép lời kết thúc. Job chạy mỗi 5 phút để dọn file/nháp quá hạn, thử lại khi xóa lỗi và xử lý tham chiếu còn lại sau xóa buổi. Inline audio không tạo đối tượng Files API để ứng dụng xóa ở Google. Log AI và chính sách backup vẫn theo [ADR 0006](../../decisions/0006-phase0-operating-decisions.md).

## Kiểm thử tự động ngày 22/11/2026

- MySQL 8.4.13 riêng, 42 kiểm thử đạt. Flyway chạy trên schema rỗng và giữ tin nhắn TEXT đã có khi nâng cấp V7 → V8. Kiểm thử HTTP xác nhận multipart qua Session/CSRF. Các ca khác kiểm tra đúng biên 60 giây, codec và audio rỗng, gửi trùng/tái dùng ID, owner, mất phản hồi provider, TTL độc lập và xóa buổi trong lúc còn tác vụ.
- Frontend đạt `npm run typecheck`, `npm run lint` và `npm run build`.
- Playwright ở chiều rộng 390px, dùng luồng audio tổng hợp, API chặn giả lập và Web Speech giả lập. Đã kiểm tra từ chối micro, dừng track khi hủy/thoát, nghe lại qua phần tử audio, chỉ upload sau thao tác gửi, WAV chuẩn, mất mạng rồi gửi lại cùng ID, sửa/xác nhận nháp, và tín hiệu phát/dừng. Chất lượng chép lời, micro thiết bị thật và giọng hệ thống nằm trong nghiệm thu bên dưới.
- Provider trong các phép thử là fixture. Chưa gọi Gemini thật cho TRANSCRIPTION ở giai đoạn này.

## Kiểm thử hai chế độ

- MySQL 8.4.13 và Gemini giả lập, 42 kiểm thử đạt. V9 chạy trên database rỗng và trên schema V7 có tin nhắn cũ qua V8 → V9. Kiểm tra VOICE đọc lại qua GET, quyền sở hữu, CSRF, chuyển một chiều, số lượt và yêu cầu tạo gửi lại sau chuyển.
- Frontend đạt typecheck, lint và build. Playwright tại chiều rộng 390px kiểm tra chọn mode bằng bàn phím, câu mở đầu ẩn trong DOM và cây trợ năng, nghe lại/nghe chậm, phụ đề từng câu, ô gõ theo lượt, tải lại, thiếu giọng, chuyển mode và xem toàn văn khi kết thúc.
- Playwright dùng âm thanh tổng hợp để kiểm tra ghi → nghe lại → gửi → sửa và xác nhận. Kiểm tra số lượt giữ nguyên trong lúc nháp và câu AI tiếp theo vẫn ẩn. API và giọng đọc được giả lập.

## Nghiệm thu trên thiết bị thật

| Môi trường | Bằng chứng |
| --- | --- |
| Chrome 138 trên Windows | P0 thu micro thật bằng WebM/Opus, giải mã và nghe rõ bản ghi 11,28 giây. Cần chạy lại luồng P6 tích hợp trên Chrome Windows |
| Edge trên Windows | Chưa kiểm thử micro và phát giọng hệ thống trên thiết bị thật |
| Chrome trên Android | Chưa kiểm thử micro, chuyển WAV và phát trên điện thoại thật |
| Safari/iPhone | Văn bản theo khả năng tương thích. Chức năng giọng nói chưa nằm trong matrix nghiệm thu |

Nguồn bằng chứng vật lý hiện có. [Metadata Chrome Windows của P0](../phase0/evidence/desktop-audio.json)

Trên từng thiết bị, mở app qua HTTPS hoặc localhost trên chính thiết bị chạy app, đăng nhập và chọn một tình huống. Kiểm tra từ chối/cấp lại quyền micro, hủy và rời màn hình đang ghi, nghe lại, giới hạn 60 giây, gửi bản ghi hư cấu, sửa và xác nhận nháp, cuối cùng phát/dừng câu AI. Ghi phiên bản trình duyệt, hệ điều hành, thiết bị, codec gốc và kết quả từng bước. Địa chỉ HTTP qua LAN từ Android cần chuyển sang HTTPS để browser cho truy cập micro.
