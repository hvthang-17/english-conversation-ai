# Hướng dẫn frontend

Phạm vi: frontend/. Tuân thủ hướng dẫn gốc và đọc [thiết kế frontend](../docs/architecture/frontend.md).

React 19 + TypeScript 5.9 + Vite 8; Node 24.19 trở lên trong nhánh 24, npm và package-lock.json. Chạy tại frontend: `npm ci`, `npm run dev`, `npm run typecheck`, `npm run lint`, `npm run build`. Dev port 5173, proxy /api tới backend 8080. Playwright chưa cài trong ứng dụng.

- React + TypeScript + Vite; chưa chọn UI kit. Quy tắc Next.js/RSC không áp dụng.
- Dùng CSS cơ bản tại src/app/styles/global.css; chưa cài Tailwind. Không tự thêm UI kit hoặc React Router khi chưa có nhu cầu màn hình/điều hướng.
- Tổ chức theo tính năng; API client dùng chung; kiểu dữ liệu rõ.
- Có trạng thái tải, lỗi, trống; giao diện tiếng Việt, hội thoại tiếng Anh.
- Chỉ gửi bản chép lời sau xác nhận; dừng media tracks và playback đúng vòng đời.
- Dùng label/focus/keyboard và kiểm tra màn hình nhỏ cho thay đổi giao diện.
- Không đặt secret trong VITE_* hoặc gọi Gemini trực tiếp.
- Không thêm dữ liệu sản phẩm giả để che API chưa triển khai.
- Tham khảo skill implement-frontend-feature và user-flows.md.
