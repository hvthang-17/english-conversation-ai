# Flyway migrations

V1 users; V2 schema MySQL Spring Session JDBC 4.1.1; V3 năm bảng hồ sơ/catalog và backfill LEARNER có sẵn; V4 6 topic, 8 scenario và 16 level links A2/B1. Tổng 8 bảng runtime + flyway_schema_history, chưa phải toàn bộ [ERD](../../../../../../docs/architecture/database.md).

Flyway sở hữu DDL; Hibernate validate, Spring Session initialize-schema=never. Không chỉnh migration đã áp dụng ở môi trường chia sẻ hoặc xóa Flyway history để seed lại. Thay đổi tiếp theo dùng version mới; không tạo SQL rỗng cho tính năng chưa làm.

P2 verify bằng MySQL thật trên schema rỗng và nâng cấp V2 có learner/password sẵn, giữ hash và tạo profile; migrate lại không ghi đè title đã sửa. Xem [kết quả P2](../../../../../../docs/development/phase2/README.md).
