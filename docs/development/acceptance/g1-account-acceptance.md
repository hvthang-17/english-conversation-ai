# Nghiệm thu G1 — Tài khoản và hồ sơ học viên

Kiểm thử ngày 25/11/2026 trên MySQL 9.2. Profile `g1` chạy Flyway, Hibernate validate và các API tài khoản, hồ sơ, chủ đề nền. V1 giữ nguyên. V2 thêm Session JDBC, V3 thêm chủ đề và sở thích, V4 thêm vòng đời tài khoản, V5 nạp sáu chủ đề để thiết lập hồ sơ.

Maven `verify` đạt 40 ca. Bộ này bỏ qua 30 ca opt-in của P0 và các nhóm sau. Hai ca hồi quy MySQL P0 chạy riêng đạt sau khi `english_conversation` lên V5. Frontend đạt typecheck, lint và build.

| Hạng mục | Bằng chứng |
| --- | --- |
| Migration | Flyway áp V1–V5 trên schema thử nghiệm rỗng. Trên `english_conversation` tại `127.0.0.1:3306`, Flyway áp V2–V5 từ V1 hiện có. Kiểm tra checksum V1 và số dòng tài khoản/hồ sơ trước và sau đều khớp. Lần khởi động thứ hai nhận health `UP` và Flyway báo schema đã cập nhật. |
| G1.1–G1.2 | JUnit chạy qua HTTP và Session JDBC thật. Kiểm tra email chuẩn hóa và đăng ký đồng thời, mật khẩu băm, bootstrap admin, rotation, idle 30 phút, CSRF, logout một phiên, đổi mật khẩu thu hồi hai phiên, khóa và role cũ. Cookie production có `Secure` trong test profile. |
| G1.3 | Đăng ký và đăng nhập ngay trên Chrome. Mailpit nhận thư khôi phục, liên kết mở form frontend, mật khẩu mới dùng được. Cả Chrome và Edge mất phiên sau reset. Gửi lại token đã dùng bị từ chối. Ca token hết hạn, email không tồn tại và SMTP lỗi kiểm tra bằng JUnit. |
| G1.4 | Chrome lưu và tải lại tên, B1, mục tiêu phút và sở thích. JUnit kiểm tra owner, level sai, ảnh không hợp lệ, giới hạn kích thước ảnh, lịch sai, lưu tùy chọn nhắc và lưu/xóa ảnh. |
| G1.5 | Edge xác nhận xóa dữ liệu luyện tập khi kho luyện tập còn rỗng. Chrome thử xóa tài khoản với mật khẩu sai rồi xác nhận bằng mật khẩu đúng. JUnit kiểm tra giữ hồ sơ/lịch sau xóa dữ liệu, thu hồi phiên và lưu tham chiếu ảnh khi dọn lỗi để job thử lại. Các nhóm học bổ sung phần xóa dữ liệu của mình khi được tích hợp. |
| G1.6 | Kiểm thử UI bằng Chrome và Edge headless trên Windows, dùng backend và MySQL thật. Edge ở khung nhìn 390 × 844. Kiểm tra truy cập trang bảo vệ, đăng nhập quay về trang dữ liệu, lưu và tải lại hồ sơ, chuyển về đăng nhập khi lưu bằng phiên cũ, logout một thiết bị, khôi phục mật khẩu và lỗi mật khẩu cũ. |

## Chạy lại

1. Dùng [hướng dẫn phía backend và frontend](../setup.md#chạy-tài-khoản-hồ-sơ-và-catalog), chỉ định profile `g1` để tái lập phạm vi nghiệm thu. Với thử nghiệm email, dùng Mailpit nội bộ và đặt URL frontend đúng địa chỉ người dùng mở.
2. Tạo schema MySQL rỗng tên theo mẫu `g1_test_<12 ký tự hex>` và cấp tài khoản thử quyền trên schema đó. Đặt `G1_TEST_DB_URL` theo mẫu `jdbc:mysql://127.0.0.1:3306/g1_test_012345abcdef?connectionTimeZone=UTC`. Cấp `SPRING_DATASOURCE_USERNAME` và `SPRING_DATASOURCE_PASSWORD` qua môi trường. Từ thư mục gốc chạy `./scripts/g1/test-mysql.ps1`. Mỗi lần nghiệm thu dùng schema mới để kiểm tra bootstrap và Flyway từ rỗng.
3. Tại `frontend/`, chạy `npm run typecheck`, `npm run lint` và `npm run build`.

SMTP production, AI thật, catalog tình huống, worker nhắc học và quy trình khôi phục backup có mốc nghiệm thu riêng. Các kiểm thử G1 dùng nội dung hư cấu.
