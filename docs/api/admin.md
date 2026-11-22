# API quản trị

Các route dưới `/api/v1/admin` yêu cầu Session có quyền ADMIN. POST, PUT và PATCH yêu cầu CSRF. Thao tác ghi kiểm tra lại tài khoản quản trị còn hiệu lực trước khi cập nhật. ID trong JSON là chuỗi.

| Phương thức và đường dẫn | Nội dung |
| --- | --- |
| GET `/overview` | Số người học, tài khoản khóa, chủ đề đang mở, tình huống công khai, buổi hoàn thành và các lần gọi AI trong 24 giờ gần nhất |
| GET `/users?email=&page=0&size=12` | Tìm chuỗi con email, không phân biệt hoa thường. Mỗi mục có ID, email, vai trò và trạng thái |
| PATCH `/users/{id}/access` | Body `{"status":"LOCKED"}` hoặc `{"status":"ACTIVE"}`. Có thể truyền `role` là ADMIN/LEARNER nếu gọi theo hợp đồng API đã có |
| GET `/topics?page=0&size=12` | Danh sách cả chủ đề đang mở và đang ẩn |
| POST `/topics`, PUT `/topics/{id}` | Tạo hoặc lưu toàn bộ chủ đề |
| GET `/topics/{topicId}/scenarios?page=0&size=12` | Danh sách tình huống thuộc chủ đề, gồm bản nháp và bản ẩn |
| POST `/topics/{topicId}/scenarios`, PUT `/topics/{topicId}/scenarios/{id}` | Tạo hoặc lưu tình huống và tập trình độ |

Các danh sách phân trang dạng `{items,page,size,total}`, bắt đầu từ 0, tối đa 50 phần tử/trang. Thay quyền hoặc trạng thái tăng phiên bản bảo mật và thu hồi phiên hiện có. Mở tài khoản cho phép đăng nhập lại. Lặp lại cùng quyền/trạng thái không đổi phiên bản và không ghi thêm audit.

Chủ đề nhận `code`, `name`, `description`, `active`, `sortOrder` từ 0 đến 9999. Tình huống nhận `code`, `title`, `description`, `learnerRole`, `aiRole`, `communicationGoal`, `contextDescription`, `aiInstruction`, `status` và `levels`. Mã dùng chữ ASCII viết hoa, chữ số hoặc dấu gạch dưới, bắt đầu bằng chữ. Mã trùng trả 409.

Để lưu tình huống `ACTIVE`, chủ đề phải đang mở và tập trình độ phải có ít nhất một mục `A2` hoặc `B1`. Trình độ lặp trả 400, điều kiện công khai chưa đủ trả 422. Dùng `ARCHIVED` để ẩn tình huống, hoặc `active=false` để ẩn cả chủ đề trong danh mục người học. Mở lại chủ đề sẽ hiện lại các tình huống vẫn ACTIVE và có trình độ. Tình huống vẫn thuộc chủ đề trên đường dẫn.

Audit V10 lưu actor, hành động, loại/ID đối tượng và thời gian. Sự kiện tài khoản ghi thêm quyền/trạng thái trước và sau. Sự kiện catalog chỉ ghi đích thay đổi. Admin có quyền đọc hướng dẫn AI của tình huống để biên tập. Các API quản trị giới hạn ở tài khoản, catalog và số liệu tổng hợp. Hội thoại riêng vẫn yêu cầu quyền sở hữu buổi.
