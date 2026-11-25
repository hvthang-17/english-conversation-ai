# Scenarios

Danh sách và chi tiết tình huống dùng api/catalog-api.ts, điều phối qua app/workspace/LearningWorkspace.tsx. Backend vẫn quyết định quyền và nội dung khả dụng. Chọn vai/chế độ trước khi tạo buổi; request giữ mã chống trùng theo lựa chọn. Không gọi Gemini trực tiếp từ frontend.

## Quy chuẩn giao diện

Áp dụng [quy chuẩn chung](../../../../docs/development/interface-design-standard.md). scenarios.css sở hữu bố cục danh sách/chi tiết, dùng token và component shared. Không thêm dependency hoặc theme riêng. CSS cũ của scenarios được chuyển khỏi global.css; topic-label dùng ở hội thoại vẫn giữ tại global.

Thiết kế ngày 25/11/2026: giữ teal, Be Vietnam Pro và palette nền tảng. So sánh card icon lớn với card tập trung nội dung: chọn tiêu đề, chủ đề và bối cảnh thật; thu icon thành dấu nhỏ cạnh chủ đề, không đánh số hay trang trí thừa. Mỗi card có một liên kết rõ mục đích, tên truy cập gồm tên tình huống.

Trang chi tiết chọn bố cục bối cảnh/vai/mục tiêu bên trái, chế độ/CTA bên phải thay cho dồn mọi lựa chọn ở vùng bắt đầu. Mục tiêu là điểm nhấn teal nhạt; không hero hoặc dữ liệu giả. Mobile đọc bối cảnh, chọn vai, hiểu mục tiêu rồi chọn chế độ. Dưới 1200px chi tiết về một cột; danh sách một cột dưới 768px.

```text
Danh sách: [Trình độ | Chủ đề | Bỏ lọc khi có]
           [Tình huống      | Tình huống]
Chi tiết:  [Bối cảnh / vai  | Chế độ]
           [Mục tiêu       | Bắt đầu]
Mobile: xếp các vùng theo thứ tự tác vụ.
```

Trạng thái trống nêu cách đổi bộ lọc; có nút xem tất cả chủ đề nếu đang lọc. Lỗi tải có thử lại; không trình bày lỗi khởi tạo buổi như thành công. Khi practice chưa khả dụng, thông báo rõ thay vì để vùng thao tác rỗng.

## Kiểm chứng

TypeScript, ESLint và Vite build chạy thành công. Browser Playwright với API mock: bộ lọc, bỏ lọc từ trạng thái trống, phân trang, đổi trình độ về trang đầu, đổi vai cập nhật bối cảnh, chọn VOICE gửi đúng mode/roleCode/level, lỗi khởi tạo và retry giữ clientRequestId, lỗi tải danh sách và thử lại.

Danh sách không tràn ngang ở 320/360/768/1024/1366/1440px; chi tiết ở 320/360/768/1024px và đã xem ảnh desktop 1440px. Phím mũi tên đổi chế độ, focus có outline; tăng chữ gốc 200% trên mobile không tràn, bật reduced motion. Ảnh cục bộ ở .playwright-mcp/scenarios-list-desktop.png, scenario-detail-desktop.png, scenario-detail-mobile.png tại gốc repo.

Mock ban đầu thiếu lessonCatalogAvailable nên app hiện hồ sơ; bổ sung cờ trong fixture, không đổi feature gating sản phẩm. Chưa kiểm tra tích hợp backend/Gemini thật, screen reader hoặc thiết bị thật. Không có migration hay cấu hình mới; kết quả mock không chứng minh chất lượng AI hoặc luồng thật đầu cuối.
