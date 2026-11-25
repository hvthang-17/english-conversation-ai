# Auth

`components/AuthPanel.tsx` hiển thị đăng nhập, đăng ký và đổi mật khẩu; `RecoveryPanel` quản lý khôi phục; `AccountLifecycleActions` quản lý xác nhận xóa tài khoản. `model/user.ts` giữ kiểu và kiểm tra phản hồi tài khoản. App điều phối phiên và trang tại `src/app/App.tsx`; feature không import app. API client và CSRF dùng chung tại shared/api/client.ts.

Giao diện áp dụng [quy chuẩn](../../../../docs/development/interface-design-standard.md). AuthField dùng chung label, trợ giúp, lỗi và hiện/ẩn mật khẩu; styles/auth.css sở hữu biểu mẫu auth. Bố cục public và hội thoại mẫu tĩnh có nhãn thuộc app/layouts/PublicLayout và app/styles/public-layout.css; khôi phục dùng chế độ compact.

Xem [thiết kế và kiểm chứng](../../../../docs/development/ui/auth-interface-improvement.md). Browser dùng API mock, chưa chứng minh tích hợp backend hoặc gửi email thật.
