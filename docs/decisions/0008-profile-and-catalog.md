# ADR 0008: Hồ sơ, catalog và seed khởi đầu

## Trạng thái
Đã hiện thực ngày 20/11/2026 cho PROFILE-01/SCENARIO-01. Kế thừa [ERD](../architecture/database.md), [quy tắc ban đầu](0005-initial-product-rules.md) và Session của P1.

## Quyết định
- V3 tạo năm bảng hồ sơ/catalog, FK/index và CHECK cho level phân biệt hoa/thường. V1/V2 đã áp dụng giữ nguyên.
- Đăng ký LEARNER tạo profile `Người học`/A2/goal null/interests rỗng cùng transaction. A2 là mặc định có thể sửa, không phải bài kiểm tra trình độ. Backfill người học cũ bằng V3, không đổi hash/quyền/trạng thái. Admin có thể không có profile.
- Profile API chỉ có /profile/me, owner lấy từ Session; PUT thay toàn bộ các trường/sở thích, tuần tự hóa bằng khóa users của owner. Không thêm userId client hoặc API sửa người khác. Tên/goal được strip, password tiếp tục giữ nguyên quy tắc P1.
- Profile dùng JPA; catalog read dùng JDBC projection để không đọc/trả instruction nội bộ; interests JDBC cùng transaction với JPA. Không tạo entity/repository rỗng chỉ để đủ lớp. Kiểm thử HTTP/MySQL xác minh transaction và FK thực tế.
- V4 seed 6 topic/8 bài/16 level links một lần bằng Flyway, không startup upsert/reset. Code ổn định, ID tự tăng không hard-code phía client; cập nhật seed đã áp dụng phải có migration mới.
- Topic API chỉ gồm active topic có bài ACTIVE/level khả dụng. List/detail yêu cầu topic active + scenario ACTIVE + có level; filter level phải khớp. Level/status ở V3 dùng ascii_bin để tránh CHECK chấp nhận chữ thường ngoài hợp đồng.
- Chọn bài ở P2 chỉ xác nhận tạm trên UI. P3 phải kiểm tra lại trạng thái và snapshot level/vai/bối cảnh/mục tiêu lúc mở buổi. Không thay schema hoặc dữ liệu lịch sử ở P2; chưa có tính năng lịch sử để chứng minh snapshot bằng regression test.

## Giới hạn và bằng chứng
[Phiếu rà nội dung catalog hiện hành](../development/acceptance/g2-content-review.md), [API](../api/profile-catalog.md), [Kế hoạch G2](../development/full-system-implementation-plan.md#g2--quản-lý-và-khám-phá-nội-dung). Không có live AI, chuyên gia tiếng Anh hoặc thử trên điện thoại thật trong giai đoạn này; chất lượng hội thoại và snapshot được kiểm chứng ở các bước sau. Hai cửa sổ chỉnh hồ sơ dùng last-write-wins có khóa, chưa có optimistic edit conflict UI.
