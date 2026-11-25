# UI dùng chung

Tuân thủ [quy chuẩn giao diện](../../../../docs/development/interface-design-standard.md). Chỉ thêm component khi có nhu cầu thực tế; API client và tiện ích dữ liệu không thuộc lớp UI.

- `Brand`: dấu hai lượt hội thoại, tên tiếng Anh và mô tả tiếng Việt. Truyền `href` theo trang đích hiện có; mặc định giữ `#/scenarios`. Toàn vùng là một liên kết có tên truy cập, không gắn thêm hành động vào logo.
- `Icon`: SVG cùng viewBox và nét vẽ; `size` nhận 20, 22 (mặc định), 24 hoặc 32. Icon luôn trang trí (`aria-hidden`, không nhận focus); nút chỉ icon phải có tên truy cập ở nút. `dialogue` dành cho dấu thương hiệu, `conversation` giữ nguyên ở chức năng hội thoại.
- `PageHeading`: một h1 với `id` để section tham chiếu; mô tả tùy chọn và vùng `action` tự xuống dòng. Chỉ dùng một lần trong nội dung chính mỗi trang; truyền hành động thật, không dùng nhãn trang trí.
- `Pagination`: phân trang có nhãn truy cập, vô hiệu hóa đầu/cuối hoặc khi đang xử lý. Nhận số trang và callback qua props, không biết API nghiệp vụ.

`shared-ui.css` sở hữu kiểu ba component, được import theo component và dùng token từ global.css. Không khai báo lại màu/theme hoặc sao chép selector sang CSS tính năng. Phần cấu trúc app shell vẫn do app sở hữu.

## Thiết kế và xác minh 25/11/2026

Giữ sáu màu nền tảng và Be Vietnam Pro theo quy chuẩn. Chọn nhịp hội thoại làm điểm nhấn của Brand; tiêu đề trang căn trái, trọng lượng 700, mô tả yên tĩnh hơn, CTA cạnh nội dung trên desktop và xuống dưới trên mobile. So sánh tiêu đề trong panel trang trí với tiêu đề mở trên nền: chọn phương án mở để không thêm card vô ích. Tự phản biện: không thêm màu, badge hay đường trang trí chỉ để làm header nổi hơn; thay tracking âm mạnh bằng khoảng chữ dễ đọc tiếng Việt.

```text
Desktop: [Tiêu đề + mô tả                  Hành động]
Mobile:  [Tiêu đề + mô tả]
         [Hành động]
```

Đã chạy TypeScript, ESLint và Vite production build thành công bằng Node 24.22.3 của môi trường. Đã xem ảnh component desktop/mobile và trang hồ sơ dùng API mock. Trang hồ sơ và bản xem riêng không tràn ngang ở 320/360/768/1440px; bản xem riêng tăng chữ gốc 200% không tràn. Bàn phím đi từ Brand tới CTA đúng thứ tự; SVG được ẩn với công nghệ hỗ trợ. Trang auth ở 320px không tràn và không xuất hiện lại dòng hết hạn phiên đã bỏ.

Ảnh cục bộ: `.playwright-mcp/shared-components-desktop.png`, `shared-components-mobile.png`, `shared-profile-desktop.png`, `shared-auth-mobile.png` tại gốc repo. Bản xem component chỉ dựng trong browser, không thêm route hoặc dữ liệu mẫu vào ứng dụng. Chưa nghiệm thu screen reader, thiết bị thật hay mọi màn hình tiêu thụ component. Không thay API, dependency, cấu hình hoặc migration.
