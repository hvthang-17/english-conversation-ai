# Kiểm chứng MySQL cho bản dựng sạch

| Hạng mục | Kết quả đã kiểm chứng |
| --- | --- |
| Instance | MySQL 9.2.0 tại `127.0.0.1:3306`, UUID `7cda631b-1b69-11f0-98fc-14133328141a` |
| Schema | `english_conversation`. Kiểm tra ngày 25/11/2026 thấy 0 bảng trước khi chạy migration. |
| Flyway | `V1__users_and_learner_profiles.sql` từ `db/migration` chạy thành công. Restart giữ đúng schema và báo không có migration cần áp dụng. Truy vấn sau test thấy một bản ghi V1 thành công trong lịch sử Flyway. |
| JDBC | Hai ca `PhaseZeroMysqlTest` đạt. Unicode và UTC đạt. MySQL chặn dữ liệu vi phạm UNIQUE, FK và CHECK. Truy vấn sau test xác nhận `users` và `learner_profiles` đều có 0 dòng. |

Dùng [hướng dẫn kết nối và chạy profile](../setup.md#đích-mysql-cho-bản-dựng-sạch). Profile `bootstrap,p0-db` trỏ tới `english_conversation`. Khi schema đích đã có đúng dòng Flyway mới và đã cấp `P0_DB_USERNAME`, `P0_DB_PASSWORD`, có thể chạy lại `./scripts/phase0/test-clean-db.ps1`.

Probe ban đầu chạy trên `english_conversation_dev` cùng instance. Lần chạy ngày 25/11 quan sát schema đích đã rỗng trước khi áp V1. Kiểm tra MySQL 8.4, Session JDBC và Hibernate validate trên runtime nghiệp vụ thuộc G1/E2E. [Biên bản P0](README.md).
