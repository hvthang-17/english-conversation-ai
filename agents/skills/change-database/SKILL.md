---
name: change-database
description: Change MySQL schema, Flyway migrations, JPA mappings, or database queries while preserving data and ownership constraints.
---

# Database change

Đọc docs/architecture/database.md, hướng dẫn backend và migration thực có từ gốc repository. Xác định môi trường local/test hay dữ liệu đang được sử dụng trước thao tác.

## Thực hiện
- Làm rõ quan hệ, ràng buộc, truy vấn và tác động dữ liệu. Chốt tác động xóa lịch sử tới review item/log trước khi cấu hình cascade.
- Dùng MySQL và Flyway; không coi H2 hoặc Hibernate schema update là bằng chứng tương thích migration.
- Tạo migration mới; không sửa migration đã áp dụng ở môi trường chia sẻ.
- Đồng bộ entity, DTO, repository và chỉ mục. Kiểm tra database rỗng và đường nâng cấp có dữ liệu khi phù hợp.
- Với thao tác mất dữ liệu hoặc dữ liệu dùng chung, xác định backup/phục hồi và quyền thực hiện trước khi chạy. Yêu cầu sửa schema không đồng nghĩa quyền xóa production.

## Hoàn thành
Xác minh bằng MySQL tương thích khi có runtime; nêu rõ nếu chỉ review tĩnh. Ghi tác động dữ liệu, thứ tự triển khai và hạn chế phục hồi; cập nhật mô hình.
