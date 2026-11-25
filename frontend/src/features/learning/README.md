# Learning

Feature này sở hữu sổ tay (`components/NotebookPanel.tsx`), quản lý dữ liệu (`components/LearningDataPanel.tsx`) và API sổ tay (`api/notebook-api.ts`). Flashcard từ phản hồi, lịch sử hội thoại và thống kê nằm tại `features/review`, `features/history`, `features/statistics`. `useApiQuery`, `reportError`, kiểu phân trang và `Pagination` nằm trong shared. Dùng client Session/CSRF hiện có. Tuân thủ [quy chuẩn UI](../../../../docs/development/interface-design-standard.md) và [API học liệu/sổ tay](../../../../docs/api/lesson-materials.md).

## Thiết kế 25/11/2026

Giữ teal, sáu màu nền tảng, Be Vietnam Pro và token chung. So sánh dãy nút ngang đồng mức với nhóm tác vụ có phân cấp: chọn nội dung tiếng Anh làm trọng tâm, lật thẻ/quiz là hành động học, lịch sử/xóa là tiện ích tách phía dưới. Giảm độ nổi của nút xóa lặp trên từng thẻ; xác nhận nguy hiểm vẫn có kiểu riêng. Không thêm streak, điểm năng lực hoặc dữ liệu thống kê giả.

```text
Sổ tay: [Lọc trạng thái                       Tổng thẻ]
        [Từ/câu + Lật/Quiz | Từ/câu + Lật/Quiz]
        [Lịch sử / Xóa    | Lịch sử / Xóa]
Thống kê: số liệu hoạt động → ngày luyện / nhận xét / chủ đề / gợi ý.
Dữ liệu: lối vào từng nhóm → vùng xóa và phạm vi rõ ràng.
```

Sổ tay hai cột từ 1200px, một cột khi hẹp; flashcard một cột trên mobile. Lịch sử là hàng dễ quét, không biến thành lưới thẻ. Thống kê có meter kèm số lượt đọc được bằng chữ, giữ diễn giải hoạt động chứ không đánh giá CEFR. Trên mobile, meter xuống dòng để ngày và số không bị ép hẹp.

`styles/learning.css` sở hữu sổ tay và quản lý dữ liệu; biến thể sổ tay được giới hạn trong `.notebook`. CSS chung của danh sách, bộ lọc và control học liệu nằm tại `shared/styles/learning-content.css`; từng feature import trực tiếp phần cần dùng. Không đổi API hoặc vòng đời request. Nghĩa/đáp án vẫn ẩn trước lật, không lộ mặt sau khi làm quiz. Tự phản biện: không dùng bo góc/màu để làm mọi vùng nổi ngang nhau; giữ chữ lớn cho nội dung cần nhớ và hạ thao tác phụ xuống hàng riêng.

Quản lý dữ liệu tách khỏi kiểu panel đăng nhập, dùng PageHeading chung. Link chỉ hiện theo feature đã có. Xóa một buổi giữ thẻ; xóa dữ liệu học tập dọn các nhóm theo hợp đồng nhưng giữ tài khoản, hồ sơ, ảnh và lịch. Không gửi yêu cầu xóa khi mới mở xác nhận.

## Kiểm chứng

TypeScript, ESLint và Vite production build chạy thành công. Browser dùng API mock tách runtime:

- Sổ tay: lật thẻ/ghi đã nhớ, quiz chọn nghĩa/nhận kết quả, không lộ mặt sau trong quiz, xem lịch sử, lọc, mở/hủy xóa thẻ.
- Flashcard: đáp án ban đầu ẩn, Enter để mở, đánh dấu đã nhớ cập nhật danh sách lọc.
- Lịch sử: mở/hủy xác nhận xóa một buổi và toàn bộ dữ liệu; kiểm tra câu chữ phạm vi giữ thẻ/tài khoản. Trang dữ liệu mở/hủy xác nhận; không xóa dữ liệu thật.
- Lỗi tải sổ tay, thử lại về danh sách trống.
- Sổ tay không tràn ở 320/360/768/1024/1440px; review/history/stats/data ở 320/768/1440px. Sổ tay tăng chữ gốc 200% trên mobile không tràn, bật reduced motion, thao tác lật và checkbox bằng bàn phím.
- Đã chụp ảnh các trang tại `.playwright-mcp/learning-*-desktop.png`; ảnh mobile sổ tay và quản lý dữ liệu cùng thư mục ở gốc repo. Đã xem trực quan sổ tay, thống kê và quản lý dữ liệu desktop.

Mock không chứng minh persistence, tính đúng đắn thống kê backend hoặc quyền dữ liệu thật. Chưa nghiệm thu toàn diện screen reader, thiết bị thật và tất cả biến thể phân trang/quiz hết hạn. Không gọi Gemini/Azure, không thêm dependency, cấu hình hoặc migration.
