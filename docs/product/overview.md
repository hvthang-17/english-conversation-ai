# Tổng quan sản phẩm

Website hỗ trợ người học tiếng Anh A2–B1 tự luyện giao tiếp theo tình huống, nghe lời thoại mẫu và nhận phản hồi sau khi luyện.

## Bản dựng hiện tại

Chạy Maven với cấu hình mặc định để mở profile `g6`. Flyway áp dòng migration hiện tại đến V19. Kế hoạch sản phẩm gồm 10 nhóm; trạng thái từng nhóm và bằng chứng nghiệm thu được theo dõi riêng. [Thiết lập local](../development/setup.md) · [Kế hoạch triển khai](../development/full-system-implementation-plan.md) · [Biên bản nghiệm thu](../development/README.md#tiến-độ-và-nghiệm-thu)

- **G1 · Tài khoản và hồ sơ.** Tài khoản email/mật khẩu, Session JDBC, khôi phục mật khẩu qua liên kết email và quản lý dữ liệu cá nhân.
- **G2–G3 · Nội dung và ôn tập.** Khám phá bài A2/B1, chọn tình huống, lưu bài, học từ và hội thoại mẫu, ôn qua sổ tay.
- **G4 · Luyện nghe.** Làm đề đã công khai, nghe MP3 do Azure TTS tạo sẵn, tiếp tục lượt làm và xem lời giải sau khi nộp.
- **G5 · Luyện phát âm.** Đọc theo mẫu hoặc nói đuổi. Azure chấm `SCRIPTED` khi được bật.
- **G6 · Hội thoại AI.** Chọn vai và chế độ trước khi bắt đầu. Trong VOICE, học viên nghe câu AI bằng TTS cục bộ, ghi âm, nghe lại và gửi cho Gemini chép lời. CHAT dùng bản chép đã xác nhận. Khi chấm phát âm được bật, Azure `UNSCRIPTED` tự chấm bản ghi gốc đủ điều kiện sau xác nhận. Kết quả từng lượt, nhận xét và lịch sử có thể xem lại.

## Phạm vi tiếp theo

G7–G10 bao gồm lộ trình học, theo dõi tiến độ và nhắc học, hỗ trợ và quản trị đầy đủ. Kế hoạch xác định tiêu chí bàn giao. Phạm vi đồ án tập trung vào luyện theo lượt, không bao gồm thoại thời gian thực, cấp chứng chỉ hoặc kết luận CEFR.

## Đánh giá

Đánh giá hành vi chức năng, độ đúng vai, chất lượng phản hồi, thao tác micro và chi phí API. Dự kiến khảo sát khoảng 10 người. Kết quả giả lập xác nhận tích hợp; bằng chứng chất lượng cần đầu ra thật và kết quả trên thiết bị mục tiêu. [Yêu cầu sản phẩm](requirements.md) · [Luồng người dùng](user-flows.md) · [Kế hoạch đánh giá AI](../research/ai-evaluation.md)
