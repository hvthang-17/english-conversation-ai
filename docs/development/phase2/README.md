# Giai đoạn 2 — Hồ sơ và tình huống luyện tập

Thực hiện ngày **20/11/2026**, kế thừa M1. Phạm vi PROFILE-01, SCENARIO-01; chưa thực hiện hội thoại/Gemini hoặc quản trị catalog đầy đủ.

| Đầu việc | Kết quả |
| --- | --- |
| P2.1 | V3 tạo learner_profiles/user_topic_interests/topics/scenarios/scenario_levels, FK/CHECK/index; backfill LEARNER cũ, signup tạo profile cùng transaction |
| P2.2 | GET/PUT /profile/me chỉ lấy owner từ Session, validation tên/A2-B1/goal/phút/interests; atomic replace và CSRF |
| P2.3 | V4 biên soạn đủ 8 tình huống trong 6 topic, 16 liên kết A2/B1 |
| P2.4 | Mỗi bài có bối cảnh, hai vai, mục tiêu và instruction; [biên bản rà soát](../../product/starter-catalog-review.md); Flyway seed một lần, không ghi đè khi restart |
| P2.5 | API topic/list/detail, phân trang/filter, ẩn DRAFT/ARCHIVED/topic inactive/không có level; projection không đọc/trả ai_instruction |
| P2.6 | Hồ sơ, lọc danh sách, chi tiết/chọn bài; tải/trống/lỗi/thử lại/401; label, bàn phím và layout 390px |

## Chạy với database của bạn

Root .env đã cấu hình datasource. Từ backend chạy:

```powershell
mvn spring-boot:run
```

Flyway kiểm tra V1/V2 rồi áp **V3/V4**; không sửa migration cũ hoặc xóa database. Nếu đang chạy bản backend cũ, dừng rồi chạy lại. Không sửa ddl-auto=validate hoặc tự tạo bảng theo ERD. Sau đó từ frontend chạy npm run dev; vào http://127.0.0.1:5173/#/profile hoặc #/scenarios sau login.

V3 backfill tên `Người học`, A2 và timestamp UTC cho LEARNER hiện có; password/role/status giữ nguyên. Tên/trình độ có thể đổi trong UI. Catalog khởi đầu gồm 8 bài active ở cả A2/B1. Admin thiếu profile có thể tự tạo bằng form; không tạo profile cho admin bootstrap tự động.

## Kiểm thử tái lập

```powershell
# Root; Java 21, MySQL portable local 3307 đã chuẩn bị từ P0
.\scripts\phase0\start-mysql.ps1
.\scripts\phase2\test-mysql.ps1
# Frontend
cd frontend
npm run typecheck
npm run lint
npm run build
```

Script P2 tạo hai database mới (fresh và upgrade) trên 3307, cùng một tài khoản random chỉ có quyền trên hai database ấy; không phụ thuộc username/password datasource trong .env hiện tại. Root option file của instance local P0 dùng để chuẩn bị; không dùng MySQL 3306 của chủ dự án. Không reset/drop database nào. Credential sinh ra chỉ nằm trong .runtime/phase2/test-connection.json bị ignore; log/evidence công khai không chứa mật khẩu.

Test mới bao phủ owner-only/extra userId/CSRF, default profile/rollback, validation/duplicate/hidden topic, catalog tám bài/level/filter/pagination/no instruction, trạng thái ẩn và khóa tài khoản. Upgrade test áp V1/V2, thêm learner, áp V3/V4 rồi xác minh password không đổi/profile được tạo; sửa title, migrate lại không seed đè. Test cũ auth/Session và env import vẫn chạy. Script P1 không cấp PHASE2_UPGRADE_URL nên bỏ qua riêng test upgrade; dùng script P2 cho đầy đủ suite.

## Bằng chứng và giới hạn

Kết quả cuối: **29/29 test đạt, 0 failure/error/skipped**; frontend typecheck/lint/build đạt. Xem [verification.json](verification.json). Browser smoke chạy trên backend 8081/Vite 5174 với DB test 3307, tạo fixture example.test riêng: signup/login → sửa hồ sơ B1/mục tiêu/interests → lọc Du lịch còn hai bài → xem/chọn Đặt phòng B1. Sau reload, danh sách mặc định B1 theo hồ sơ đã lưu. Đã thử loading bằng giữ request, 503 giả lập riêng trong browser rồi retry với API thật, danh sách rỗng bằng filter không tồn tại, keyboard Tab, phiên đã logout trả 401 và chuyển login. Không lưu mã phiên trong localStorage/sessionStorage.

Đã xem screenshot profile/catalog/detail ở 390px và kiểm tra không tràn ngang; đây là Chromium desktop với viewport hẹp, không phải Android/Edge/Safari thật. Server thử đã dừng sau xác minh, không thay đổi database 3306 của bạn.

Không gọi Gemini. Chọn bài chỉ xác nhận bài/level trong UI, chưa tạo buổi, message hoặc snapshot. Không có dữ liệu lịch sử để chạy test hồi quy snapshot; P3 phải chụp snapshot khi mở buổi và không tái dựng lịch sử từ profile/catalog mới. Chất lượng phân vai/level của provider và chuyên gia tiếng Anh chưa được kiểm chứng.

Hợp đồng: [API hồ sơ/catalog](../../api/profile-catalog.md). Để điều chỉnh seed đã áp dụng, tạo migration mới; không sửa V4 hoặc xóa Flyway history.
