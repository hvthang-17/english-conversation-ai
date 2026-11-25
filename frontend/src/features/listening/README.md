# Listening

`ListeningScreens.tsx` cung cấp đề nghe trong bài học, danh sách đề/lịch sử, màn hình làm bài, kết quả và vùng công khai đề dành cho admin. `listening-api.ts` giữ hợp đồng request; `listening.css` sở hữu giao diện, dùng token và component chung. Tuân thủ [quy chuẩn UI](../../../../docs/development/interface-design-standard.md) và [API luyện nghe](../../../../docs/api/listening.md).

## Thiết kế 25/11/2026

Giữ teal, sáu màu nền tảng và Be Vietnam Pro theo quy chuẩn. So sánh toàn bộ nội dung trong một danh sách thẻ dọc với phân cấp theo tác vụ: chọn lưới cho đề ngang cấp, hàng cho lịch sử và bố cục trình phát cạnh câu hỏi khi làm bài. Một vùng nhấn ở mỗi bước: hành động bắt đầu trong catalog, câu hỏi khi làm, tổng điểm khi xem kết quả. Không waveform giả, huy hiệu năng lực hay hình trang trí.

```text
Danh sách: [Đề nghe      | Đề nghe]
           [Lịch sử theo hàng     ]
Làm bài:   [Trình phát  | Câu hỏi]
           [            | Nháp / Nộp / Bỏ dở]
Kết quả:   [Trình phát  | Điểm / Lời thoại / Giải thích]
Mobile: trình phát trước nội dung trả lời hoặc kết quả.
```

Desktop từ 1200px dùng hai cột khi làm bài; dưới mốc này xếp một cột. Catalog một cột dưới 768px. Trình phát không tự chạy hoặc ghim đè lên focus. Radio có viền/nền chọn và focus rõ; câu hỏi dùng fieldset/legend. Nút lưu nháp, nộp bài và bỏ dở giữ nghiệp vụ hiện có. Vùng xác nhận bỏ dở thêm “Tiếp tục làm bài” để đóng xác nhận rõ ràng.

Sau nộp, tổng điểm tách khỏi giải thích từng câu; đúng/chưa đúng có nhãn bằng chữ. Lời thoại, đáp án và giải thích chỉ render khi API có result. Bản dịch trong kết quả mở theo yêu cầu bằng details native. Lịch sử tách tên bài, ngày, trình độ, trạng thái và điểm thay vì chuỗi metadata nối dấu chấm.

## Kiểm chứng

TypeScript, ESLint và Vite production build chạy thành công. Playwright dùng API mock và WAV thử sinh trong bộ nhớ; không gọi Azure/Gemini hoặc công khai đề thật:

- Mở đề từ catalog, đổi playbackRate sang 0,75; radio, điền từ, sắp xếp/bỏ mục cuối và ô nghe–viết hiện đúng.
- Lưu nháp, nộp bài, khóa form sau nộp; lời thoại không hiện trước nộp, bản dịch sau nộp mặc định đóng.
- Mở/hủy xác nhận bỏ dở; xung đột 409 khóa nộp và tải lại bản đã lưu mở lại thao tác.
- Audio lỗi có nút thử tải lại, phần trả lời vẫn thao tác được.
- Không tràn ngang màn hình làm bài ở 320/360/768/1024/1366/1440px; phím mũi tên chọn radio và focus rõ. Bật reduced motion, tăng chữ gốc 200% trên mobile không tràn.
- Đã xem ảnh catalog desktop, làm bài desktop và kết quả mobile. Ảnh cục bộ ở `.playwright-mcp/listening-catalog-desktop.png`, `listening-attempt-desktop.png`, `listening-result-desktop.png`, `listening-result-mobile.png` tại gốc repo.

Chưa xác minh backend/storage, chất lượng audio thật, SMTP, screen reader hoặc thiết bị thật; chưa chạy công khai đề admin/provider. Mock không chứng minh tính đúng đắn chấm điểm hoặc tích hợp đầu cuối. Không thêm dependency, cấu hình hoặc migration.
