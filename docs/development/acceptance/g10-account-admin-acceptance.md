# G10.1 — Quản lý tài khoản

Mở **Quản trị hệ thống → Tài khoản** bằng tài khoản ADMIN đang hoạt động. Tìm theo một phần email và dùng phân trang. Có thể đổi quyền hoặc khóa/mở tài khoản khác sau khi xác nhận.

Quy tắc bảo vệ quyền quản trị áp dụng ở backend. Admin không tự đổi quyền, khóa hoặc xóa tài khoản. Nếu cần xóa tài khoản admin, một admin khác phải chuyển tài khoản đó sang người học trước. Hai thao tác đồng thời khóa các tài khoản theo cùng thứ tự và kiểm tra lại quyền của người thực hiện. Tài khoản đang chờ xóa bị từ chối khi đổi quyền hoặc trạng thái.

Flyway V18 tạo nhật ký quản trị. Thay đổi có hiệu lực thu hồi phiên và ghi actor, target, quyền/trạng thái cũ và mới. [Hợp đồng API](../../api/admin.md)

## Kiểm chứng

Ca HTTP tích hợp trên MySQL với schema rỗng V1–V18 đạt. Ca kiểm tra người học nhận 403, tìm kiếm/phân trang, từ chối admin duy nhất tự hạ quyền hoặc tự khóa, cookie cũ nhận 401 sau khóa và đổi quyền, đăng nhập lại theo quyền mới, và audit đúng tác nhân/đích. Dùng Gemini giả lập cho phần hồi quy catalog/hội thoại trong cùng ca.

ADMIN được đưa tới #/admin khi đăng nhập hoặc khôi phục phiên và luôn thấy khung quản trị. Các mục có dữ liệu gồm Bảng quản trị, Tài khoản, Audit, catalog chủ đề/tình huống và metadata tác vụ AI. Quản lý học liệu chi tiết, Ticket, Announcement và Notification đang chờ API tương ứng.
