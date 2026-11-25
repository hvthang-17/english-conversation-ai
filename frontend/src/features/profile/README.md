# Profile

PROFILE-01 đã triển khai: api/profile-api.ts kiểm tra response/get/save, components/ProfileForm.tsx chỉnh hồ sơ owner-only. Form dùng cùng Session/CSRF client, không gửi userId để xác định owner. Xem docs/api/profile-catalog.md từ root.

## Giao diện

Áp dụng [quy chuẩn](../../../../docs/development/interface-design-standard.md), CSS riêng tại `profile.css`; không ghi đè selector profile trong global.css. Desktop từ 1200px có cột chính cho thông tin học tập và cột phụ cho ảnh/lịch. Dưới 1200px form lên trước; mobile xếp mọi phần thành một cột. Mỗi form lưu độc lập, không gộp request.

A2/B1 là radio native có mô tả và focus; chủ đề và ngày luyện là checkbox có nền chọn cùng nhãn. Ngày hiển thị T2–CN nhưng tên truy cập giữ đầy đủ. Ảnh dùng placeholder khi chưa có; định dạng và giới hạn nằm cạnh vùng ảnh. Không hiển thị điểm đánh giá năng lực hay tiến độ giả.

## Thiết kế và kiểm chứng 25/11/2026

Giữ teal, sáu màu nền tảng, Be Vietnam Pro và thang khoảng cách của quy chuẩn. So sánh form dài kèm aside giải thích với form chính và cột cài đặt: chọn phương án hai để tận dụng chiều ngang, đưa giải thích trình độ ngay vào nơi chọn. Điểm nhấn là cặp A2/B1; các khối còn lại dùng nền trắng, viền và tiêu đề đơn giản, không gradient/bóng trang trí. Mobile ưu tiên tên, trình độ, mục tiêu và nút lưu trước phần ảnh/lịch. Trang cài đặt nhiều nội dung vẫn cuộn tự nhiên.

```text
Desktop: [Thông tin học tập | Ảnh đại diện]
         [                 | Lịch luyện tập]
Mobile:  [Thông tin học tập]
         [Ảnh đại diện]
         [Lịch luyện tập]
```

TypeScript, ESLint và Vite build đã chạy thành công. Browser dùng API mock riêng: lưu hồ sơ, đổi A2/B1, lưu lịch; lỗi theo trường vẫn giữ dữ liệu; tải/xóa ảnh mẫu; lỗi tải lịch và thử lại. Khi tải lịch lỗi, không tiếp tục hiển thị “Đang tải lịch”. Câu chữ email theo yêu cầu hiện tại: phụ thuộc dịch vụ nhắc học, không yêu cầu xác minh email độc lập.

Không tràn ngang ở 320/360/768/1024/1366/1440px; kiểm tra phím mũi tên chuyển A2/B1 và focus nhìn thấy; tăng chữ gốc 200% trên mobile không tràn. Đã bật reduced motion, xem ảnh desktop/mobile tại `.playwright-mcp/profile-redesign-desktop.png` và `profile-redesign-mobile.png` ở gốc repo. Mock không chứng minh lưu backend/storage hoặc SMTP thật; chưa nghiệm thu screen reader, bàn phím ảo/thiết bị thật và mọi cặp tương phản. Không thêm dependency, cấu hình hoặc migration.
