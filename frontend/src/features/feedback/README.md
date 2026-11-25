# Feedback

SessionFeedback hiển thị nhận xét cuối buổi đã lưu, tạo/thử lại theo hợp đồng, polling khi đang xử lý và lưu gợi ý thành thẻ ôn. [API](../../../../docs/api/feedback.md) sở hữu trạng thái và quyền; [quy chuẩn UI](../../../../docs/development/interface-design-standard.md) sở hữu hướng thiết kế.

## Thiết kế 25/11/2026

Giữ palette nền tảng, teal và Be Vietnam Pro theo quy chuẩn. So sánh văn bản/liệt kê liên tục với bố cục từ tổng kết đến hành động: chọn tổng kết ngắn, mục tiêu/điểm tốt, câu cần sửa, cách nói tự nhiên hơn, từ/mẫu câu và bước luyện tiếp. Không thêm điểm năng lực, kết luận CEFR hoặc lời khen ngoài dữ liệu nhận xét.

```text
[Tổng kết]
[Mục tiêu | Điểm tốt]
[Lỗi lặp: mở khi cần]
[Câu gốc | Gợi ý sửa] + lý do
[Cách nói tự nhiên hơn: tách khỏi lỗi]
[Từ/mẫu câu + Lưu để ôn]
[Bước luyện tiếp theo]
```

Tự phản biện: tránh tô đỏ toàn bộ nhận xét hoặc coi mọi gợi ý là lỗi. Lỗi và cải thiện có tiêu đề, nhãn câu gợi ý và đường nhấn khác nhau; cả hai giữ câu gốc đọc được. Nhóm lỗi lặp mở bằng details native để không lặp nội dung dài mặc định. Bước luyện tiếp là vùng teal nhạt giúp chọn hành động sau khi đọc. Desktop so sánh hai cột; dưới 768px xếp dọc, không ép câu tiếng Anh vào cột hẹp.

feedback.css sở hữu kiểu tính năng; bỏ CSS feedback cũ khỏi practice-chat.css để không chồng quy tắc. Thành phần vẫn nằm sau hội thoại trong PracticeChat, dùng h2/h3 phù hợp ngữ cảnh. Không đổi request, retry, polling, xác nhận UNKNOWN hoặc ánh xạ suggestionIds sang gợi ý. Lỗi không thể thử lại hướng người học đọc hội thoại/quay lại sau; không lộ chi tiết cấu hình nội bộ.

## Kiểm chứng

TypeScript, ESLint và Vite production build chạy thành công. Browser Playwright dùng API mock tách runtime:

- Hiển thị lỗi/cải thiện riêng, mục tiêu, điểm tốt, nhóm lỗi lặp, gợi ý ôn và bước tiếp theo; không có lỗi cần sửa vẫn là kết quả hợp lệ.
- Mở nhóm lỗi lặp bằng Enter; lưu đúng suggestion thành thẻ, hiển thị liên kết ôn sau khi lưu; lưu lỗi có thể thử lại.
- NOT_STARTED → tạo → RUNNING → polling SUCCEEDED; lỗi GET/tải lại; FAILED không retry; UNKNOWN phải đánh dấu xác nhận trước khi gửi acknowledgeUnknown=true.
- Không tràn ngang ở 320/360/768/1024/1440px; tăng chữ gốc 200% ở mobile không tràn; bật reduced motion. Đã xem ảnh desktop/mobile của vùng nhận xét.
- Ảnh cục bộ tại .playwright-mcp/feedback-desktop.png và feedback-mobile.png ở gốc repo, dùng nội dung thử hư cấu.

Chưa xác minh provider/backend thật, chất lượng nhận xét AI, toàn bộ screen reader hoặc thiết bị thật. Không gọi Gemini/Azure, không thêm dependency/cấu hình/migration. Mock không chứng minh độ đúng của nhận xét hoặc tích hợp đầu cuối.
