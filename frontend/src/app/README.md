# Khung ứng dụng

- App.tsx: điều phối phiên, capability và lựa chọn layout/màn hình.
- routing/routes.ts: nhận diện hash route, nhóm trang được bảo vệ và trang đầu.
- workspace/LearningWorkspace.tsx: ghép các màn hình học tập.
- layouts/AppFrame.tsx: header, menu theo nhóm, thu gọn desktop/drawer mobile và nội dung trang.
- layouts/HeaderAccount.tsx: tên/ảnh người dùng từ hồ sơ, fallback và cập nhật sau lưu.
- styles/app-frame.css: CSS riêng khung; token nền ở styles/global.css.
- layouts/PublicLayout.tsx và styles/public-layout.css: bố cục trước đăng nhập; không hiển thị dữ liệu tài khoản.

Tuân thủ [quy tắc cấu trúc](../../../docs/development/frontend-structure.md). Biểu mẫu đăng nhập/đăng ký/đổi mật khẩu nằm tại features/auth/components/AuthPanel.tsx; app không xuất layout cho feature import ngược.

Đọc [quy chuẩn UI](../../../docs/development/interface-design-standard.md) và [thiết kế/kiểm chứng khung](../../../docs/development/ui/app-shell-improvement.md) trước khi sửa. Giữ route, feature và role gating hiện có. Chỉ lưu boolean thu gọn vào localStorage; không lưu dữ liệu phiên. Header nghe sự kiện từ shared/lib/profile-events.ts sau khi lưu hồ sơ/ảnh thành công.
