---
name: implement-frontend-feature
description: Implement or revise React TypeScript Vite screens and interactions for this project, including API integration, recording states, and accessibility.
---

# Frontend feature

Đọc AGENTS.md tại gốc/frontend, docs/architecture/frontend.md và docs/product/user-flows.md từ gốc repository.

## Thực hiện
- Kiểm tra manifest, component, theme và API hiện có trước khi chọn thư viện. Chưa có manifest thì khởi tạo theo tác vụ, không giả định scripts tồn tại.
- Xác định loading/error/empty/success và hợp đồng backend. Fixture kiểm thử phải tách khỏi dữ liệu sản phẩm.
- React + Vite không áp dụng API riêng Next.js/RSC. Không tự đổi UI kit hoặc theme.
- Ghi âm: nghe lại, xác nhận transcript trước khi gửi, dọn media tracks khi thoát; hỗ trợ nhập văn bản khi lỗi.
- Không render HTML thô từ AI, không đặt secret trong client.

## Hoàn thành
Chạy checks thực có theo phạm vi; kiểm tra bàn phím, màn hình nhỏ và trạng thái lỗi. Dùng browser tooling nếu có, nếu không nêu giới hạn xác minh. Không tạo framework test mới cho chỉnh tài liệu.
