# Nghiệm thu G2 — Quản lý và khám phá nội dung

## Chạy và kiểm thử

Chạy `mvn spring-boot:run` từ `backend/`. Maven chọn profile `g2`, Flyway áp tới V8 và Hibernate kiểm tra schema. Chạy `npm run dev` từ `frontend/`. Sau khi đăng nhập, mở **Khám phá bài học** hoặc **Bài đã lưu**. [Thiết lập local](../setup.md#chạy-tài-khoản-hồ-sơ-và-catalog) · [Hợp đồng API](../../api/lessons.md).

MySQL test dùng schema riêng với tên `g2_test_<12 ký tự hex>` trên cổng 3306. Đặt `G2_TEST_DB_URL` theo mẫu `jdbc:mysql://127.0.0.1:3306/g2_test_012345abcdef?connectionTimeZone=UTC` và cấp datasource qua môi trường. Từ gốc repo chạy `./scripts/g2/test-mysql.ps1`. Dùng schema mới để kiểm tra Flyway từ rỗng. Bộ này dùng tài khoản hư cấu và không gọi Gemini.

## Kết quả ngày 25/11/2026

| Task | Bằng chứng |
| --- | --- |
| G2.1 | MySQL 9.2 dựng V1–V7 từ schema rỗng. Kiểm tra riêng đường V5 → V7 giữ cùng user ID, topic ID và mã chủ đề đã lưu. Khởi động lại không nạp seed lần hai. FK chặn cấp độ bài lệch tình huống, CHECK chặn bài công khai thiếu mục tiêu và cột level không nhận NULL. Schema phát triển `english_conversation` đã lên V7, giữ số dòng users, hồ sơ và sở thích. |
| G2.2 | V7 có tám bài, tám tình huống, sáu chủ đề, bốn bài A2 và bốn bài B1. Tình huống đổi hàng có vai đảo. Hai ca seed MySQL riêng đều đạt. Đã biên soạn đoạn thoại tham chiếu cho tám bài và rà nội bộ. Còn chờ người rà tiếng Anh ký nhận. [Phiếu rà nội dung](g2-content-review.md). |
| G2.3 | MySQL trả cùng bài cho `dat phong` và `ĐẶT PHÒNG`. Lọc Du lịch và B1 cho đúng bài, đổi sang A2 trả rỗng. Thử phân trang không trùng, từ khóa chứa ký tự wildcard, bài ẩn trả 404 và JSON tình huống dùng DTO công khai. Bài còn công khai khi tình huống gợi ý bị ẩn. |
| G2.4 | Hai tài khoản không quản lý dấu của nhau. Một tài khoản giữ đồng thời hai loại dấu, PUT lặp không tạo trùng. Bỏ một dấu giữ dấu kia. Sau khi ẩn bài, chủ sở hữu vẫn bỏ được dấu. Xóa dữ liệu học tập xóa dấu của đúng tài khoản. |
| Trình duyệt | Chrome headless trên Windows, viewport 390 × 844. Đăng ký, đăng nhập, xem tám bài, tìm tiếng Việt không dấu, lọc chủ đề và cấp độ, thấy trạng thái trống, lưu cả hai loại dấu, bỏ một dấu, tải lại danh sách, mở tình huống và đổi vai. |

Maven `verify` đạt 43 ca gồm G2 và hồi quy G1, còn 30 ca opt-in của P0 và các nhóm sau được bỏ qua trong lượt này. Frontend đạt `typecheck`, `lint` và `build`.

## Kiểm tra riêng seed G2.2

Trên MySQL 9.2, `StarterLessonSeedMysqlTest` chạy **2 ca đạt**. Ca schema rỗng áp V1–V8 và đối chiếu đủ tám bộ ID bài, mã tình huống, mã mục tiêu, chủ đề và cấp độ. Ca nâng cấp dựng V5, tạo hồ sơ B1 có sở thích `WORK`, áp V6–V7 rồi mô phỏng một chỉnh sửa catalog trước V8. Test xác nhận checksum V1–V5 và V7, sở thích đã lưu và hướng dẫn đã chỉnh đều giữ nguyên. Cả hai đường chạy lại Flyway đều áp thêm 0 migration.

V8 đồng bộ bối cảnh và hướng dẫn cho bài 103 (cả hai vai), 105 và 106 với [phiếu rà nội dung](g2-content-review.md). Kiểm thử xác nhận vai đảo cho AI đưa hóa đơn khi được hỏi và để người học xác nhận lựa chọn, tình huống đặt phòng nêu điều kiện hủy miễn phí và tình huống học nhóm mời người học xác nhận lại lịch.

Schema phát triển `english_conversation` đã áp V8. Truy vấn MySQL xác nhận ba tình huống và vai đảo bài 103 đều nhận hướng dẫn cập nhật. Checksum V1–V7, ID bài và liên kết chủ đề/tình huống giữ nguyên.

Để chạy lại, tạo hai schema MySQL rỗng có tên khác nhau, dạng `g2_seed_<12 ký tự hex>`, trên `127.0.0.1:3306`. Đặt `G2_SEED_FRESH_DB_URL` và `G2_SEED_UPGRADE_DB_URL` theo dạng `jdbc:mysql://127.0.0.1:3306/g2_seed_012345abcdef?connectionTimeZone=UTC`, cấp `SPRING_DATASOURCE_USERNAME` và `SPRING_DATASOURCE_PASSWORD` qua môi trường. Tại `backend/`, chạy:

```powershell
$env:G2_SEED_MYSQL_TEST = 'true'
.\mvnw.cmd "-Dtest=StarterLessonSeedMysqlTest" test
```
