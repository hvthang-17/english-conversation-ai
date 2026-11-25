# Lessons

Catalog, bài đã lưu và chi tiết nằm trong `LessonCatalog.tsx`; học liệu nằm trong `LessonMaterialsPanel.tsx`. `lesson-api.ts` kiểm tra response và giữ hợp đồng Session/CSRF hiện có. Tuân thủ [quy chuẩn UI](../../../../docs/development/interface-design-standard.md), [API bài học](../../../../docs/api/lessons.md) và [API học liệu](../../../../docs/api/lesson-materials.md).

## Thiết kế 25/11/2026

Giữ teal, sáu màu nền tảng, Be Vietnam Pro và token chung. So sánh hàng văn bản kèm nút bên cạnh với thẻ bài có phân cấp: chọn thẻ hai cột cho các bài ngang cấp, tên/mô tả/mục tiêu rồi đến dấu cá nhân. Chỉ tiêu đề dẫn tới bài, các nút Yêu thích/Học sau là hai hành động độc lập, có icon và trạng thái chữ. Không thêm số liệu học tập, ảnh trang trí hoặc dữ liệu runtime giả.

```text
Catalog: [Tìm kiếm | Chủ đề | Cấp độ | Tìm bài]
         [Bài học / mục tiêu | Bài học / mục tiêu]
Chi tiết:[Mục tiêu          | Hội thoại / Phát âm / Dấu lưu]
         [Từ vựng           | Mẫu câu]
         [Hội thoại mẫu]
         [Đề luyện nghe]
Mobile: các vùng xếp một cột, thứ tự đọc giữ nguyên.
```

Tự phản biện: không biến mọi học liệu thành thẻ giống nhau. Từ và mẫu câu có nghĩa/ví dụ; hội thoại dùng chuỗi lượt có người nói, tiếng Anh và bản dịch điều khiển riêng. Teal nhạt nhấn mục tiêu; học liệu còn lại dùng chữ và khoảng trắng. Desktop dưới 1200px xếp phần tổng quan một cột; catalog và nhóm học liệu một cột dưới 768px.

`styles/lesson-catalog.css` sở hữu kiểu lesson; `styles/lesson-materials.css` sở hữu học liệu. Biến thể học liệu được giới hạn trong `.lesson-materials` để không đổi sổ tay. CSS nền dùng chung nằm trong shared/styles/learning-content.css, được import rõ ràng. Thêm icon heart/bookmark vào hệ Icon hiện có, không thêm thư viện. Học liệu đặt trước luyện nghe để theo trình tự chuẩn bị rồi thực hành. API, quyền, dấu cá nhân, tốc độ phát và vòng đời speech không thay đổi.

## Kiểm chứng

TypeScript, ESLint và Vite production build chạy thành công. Playwright với API và speech mock tách runtime đã kiểm tra:

- Tìm kiếm không có kết quả và quay về tất cả bài; Yêu thích cập nhật mà Học sau giữ độc lập.
- Bài không còn khả dụng không có liên kết mở nhưng vẫn gửi được DELETE bỏ dấu; tab Học sau dùng trạng thái navigation theo route.
- Hội thoại mặc định hiện lời Anh/ẩn bản dịch; bật/tắt hai phần độc lập; lưu từ vào sổ tay rồi hiện liên kết đã lưu.
- Mô phỏng phát từ, đổi tốc độ 0,75, dừng, phát cả đoạn và chuyển câu. Không gọi giọng đọc bên ngoài hoặc đánh giá chất lượng TTS thật.
- Lỗi tải học liệu và thử lại về trạng thái trống; thao tác checkbox bằng bàn phím; tăng chữ gốc 200% trên mobile không tràn ngang.
- Catalog không tràn ở 320/360/768/1024/1366/1440px; chi tiết ở 320/360/768/1024px, có xem ảnh desktop 1440px. Ảnh tại `.playwright-mcp/lessons-catalog-desktop.png`, `lesson-detail-desktop.png`, `lesson-detail-mobile.png` ở gốc repo.

Mock không chứng minh lọc/tìm kiếm backend, lưu dữ liệu thật hoặc chất lượng audio. Chưa nghiệm thu screen reader, thiết bị thật, mọi biến thể phân trang và lỗi quyền. Không gọi Gemini/Azure, không thêm dependency/cấu hình/migration.
