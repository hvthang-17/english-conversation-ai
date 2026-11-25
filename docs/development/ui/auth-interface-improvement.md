# Cải tiến giao diện auth — 25/11/2026

Áp dụng [quy chuẩn](../interface-design-standard.md), frontend-design và ADR 0010. Phạm vi: đăng nhập, đăng ký, khôi phục, đổi mật khẩu, xác nhận xóa tài khoản và PublicLayout. Không thay API, Session/CSRF, backend hoặc schema.

## Thiết kế và tự phản biện

Mục tiêu: người học A2–B1 nhìn thấy form cần dùng và nhận ra không gian luyện giao tiếp. Giữ palette #F3F7F8, #FFFFFF, #193747, #516875, #17687A, #E5F1F4, token ngữ nghĩa và Be Vietnam Pro 400/600/700. Căn trái nội dung, một điểm nhấn là cuộc hội thoại mẫu.

So sánh hero lớn phía trên form với hội thoại cạnh form: chọn phương án thứ hai để không đẩy tác vụ xuống dưới. Icon bong bóng và danh sách lợi ích cũ còn chung chung; thay bằng ví dụ gọi món có vai và nhãn rõ, không giả làm chat thật. Sau khi xem ảnh ở 768px, chuyển tablet sang một cột để tránh ép hẹp câu mẫu.

```text
Desktop >=1024px         Tablet/mobile
Thương hiệu              Thương hiệu
Hội thoại | Form         Form
                         Hội thoại
Khôi phục: một form giữa trang, không lặp hero.
```

## Thay đổi

- CSS public/auth chuyển khỏi global về auth.css, dùng token hiện có. Form lên trước trong DOM và trên màn hình nhỏ.
- AuthField dùng chung label, hint, lỗi và hiện/ẩn mật khẩu có tên truy cập; giữ autocomplete và giới hạn.
- CTA 48px, navigation có mục đang chọn; trang khôi phục có trạng thái hoàn tất, thiếu mã và đường quay về.
- Khu vực xóa tách khỏi đổi mật khẩu, giữ phạm vi và xác nhận; không trùng ID khi mở form.
- Skip link focus vào tiêu đề form, không thay hash route.

## Kiểm chứng

TypeScript (tsc -b --pretty false), ESLint (eslint . --max-warnings 0) và Vite production build chạy thành công bằng Node 24.22.3 của môi trường. Launcher npm mặc định báo không tìm thấy Node 24.19.0; không sửa cấu hình Node máy. Không thêm dependency.

Browser Playwright dùng API mock tách khỏi runtime:

- Đăng nhập lỗi giữ email; hiện/ẩn mật khẩu; đăng ký có xác nhận mật khẩu không khớp.
- Khôi phục thành công, reset thiếu mã, reset thành công và mã mẫu được gỡ khỏi URL.
- Tài khoản hiển thị, mở/hủy form xóa; không gửi xóa thật; không trùng ID.
- Đăng nhập không tràn ngang ở 320, 360, 767, 768, 1023, 1024, 1440px; tài khoản ở 320, 768, 1440px.
- Tab/Enter trên skip link giữ route đăng ký và focus tiêu đề; outline nhìn thấy. Bật reduced motion; tăng cỡ chữ gốc 200% ở 320px không tràn ngang.
- Chụp và xem ảnh đăng nhập desktop/mobile, đăng ký tablet và tài khoản; ảnh cục bộ trong .playwright-mcp/, không dùng dữ liệu riêng tư thật.
- Tương phản token: chữ chính/trắng 12,51:1; chữ phụ/trắng 5,85:1; chữ phụ/nền ứng dụng 5,43:1; primary/trắng 6,37:1; viền control/trắng 3,27:1; focus/trắng 5,02:1; danger/nền lỗi 6,39:1.

Chưa nghiệm thu toàn diện screen reader, bàn phím ảo/thiết bị thật, mọi biến thể tương phản hoặc browser zoom. Text resize chưa thay thế zoom trình duyệt. Chưa kiểm tra đăng ký/đăng nhập/đổi mật khẩu với backend thật hay SMTP; mock không chứng minh tích hợp đầu cuối. Không gọi Gemini/Azure, không cần cấu hình hoặc migration mới.


## Điều chỉnh desktop thấp và icon mật khẩu

Đã tái hiện đăng ký ở viewport 1536 × 695: nội dung cao khoảng 980px, form 713px do header, khoảng đệm và nhịp form cộng dồn. Với desktop từ 1024px và chiều cao tối đa 850px, giảm khoảng đệm/heading/nhịp field; vẫn giữ control tối thiểu 44px, không khóa cuộn hay cắt nội dung. Rút gọn hướng dẫn mật khẩu và bỏ liên kết quên mật khẩu ở form đăng ký (vẫn có ở đăng nhập). Nội dung lỗi dài hoặc người dùng tăng chữ vẫn được cuộn bình thường.

AuthField dùng icon mắt/mắt gạch từ Icon dùng chung, giữ aria-label và title diễn tả thao tác. Kiểm tra browser mock: đăng ký không cuộn ở 1536×695, 1366×650, 1280×720 và 1440×900; đăng nhập không cuộn ở 1366×650; mobile 360px không tràn ngang. Nút mắt chuyển type text/password đúng. Đã xem ảnh `.playwright-mcp/auth-register-compact-desktop.png`. TypeScript, ESLint, build thành công; không thay backend hoặc API.
