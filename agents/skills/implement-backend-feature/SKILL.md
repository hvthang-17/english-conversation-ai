---
name: implement-backend-feature
description: Implement or refactor Java Spring Boot backend features in this project, including DTOs, business logic, authorization, and API tests.
---

# Backend feature

Đọc AGENTS.md tại gốc/backend và docs/architecture/backend.md theo đường dẫn từ gốc repository. Xác định mã yêu cầu trong docs/product/requirements.md trước khi thay đổi.

## Thực hiện
- Kiểm tra build/dependency và API thực có; nếu chưa khởi tạo, hoàn thành việc chọn phiên bản và wrapper trong phạm vi tác vụ trước khi giả định có lệnh chạy.
- Xác định hợp đồng HTTP, validation, lỗi và owner/role. Không tin userId từ client để cấp quyền.
- Đặt nghiệp vụ trong service, DTO tại biên và truy cập dữ liệu trong repository; không serialize entity.
- Với hội thoại: giữ transaction ngắn, kiểm soát lượt đồng thời và chống trùng yêu cầu. Không giữ DB transaction chờ AI.
- Khi schema hoặc AI bị ảnh hưởng, đọc tài liệu chuyên biệt tương ứng, không nhân bản quy tắc vào implementation.

## Hoàn thành
Kiểm thử nghiệp vụ và API bảo vệ dữ liệu phù hợp. Mock dịch vụ ngoài, báo rõ phần chưa chạy. Đồng bộ hợp đồng/tài liệu nếu hành vi đổi; không coi build thành công là bằng chứng đủ cho phân quyền.
