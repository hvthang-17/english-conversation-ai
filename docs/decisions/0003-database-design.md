# ADR 0003: Mô hình dữ liệu cho luyện giao tiếp

Phạm vi toàn hệ thống cho bản đầu được cập nhật tại [ADR 0012 — ERD 46 bảng](0012-system-database-scope.md). ADR này ghi các quyết định của mô hình nền.

## Trạng thái

Chấp nhận làm thiết kế cơ sở; được [ADR 0004](0004-session-authentication.md) bổ sung hai bảng Session JDBC. **Chưa tạo migration, JPA entity nghiệp vụ hoặc bảng MySQL.** Retention còn mở; cơ chế xác thực đã chốt Session. Không coi ADR là quyền thao tác dữ liệu production.

## Bối cảnh

Mô hình sơ bộ 10 bảng chưa tách lượt và tin nhắn, transcript chưa xác nhận, đề xuất với nội dung thực sự đã lưu, hay tác vụ AI với từng lần gọi. Thiếu các ràng buộc này dễ gây tin nhắn trùng, thống kê sai khi retry và mất khả năng dọn audio sau xóa lịch sử.

## Quyết định

- Ban đầu 18 bảng ứng dụng (15 nghiệp vụ, 3 vận hành); hiện thêm 2 bảng hạ tầng Session theo ADR 0004, tổng 20 ở [database](../architecture/database.md). [Dictionary](../architecture/database.md#3-từ-điển-46-bảng-và-dữ-liệu-mẫu) sở hữu trường/ý nghĩa, [workflow](../architecture/database.md#6-luồng-dữ-liệu) sở hữu trình tự/giao dịch.
- Tách practice_sessions, conversation_turns và messages; cấp lượt/chống trùng tại server, khóa session khi điều phối. Thứ tự theo turn_no và sender.
- Snapshot trình độ/bối cảnh trong session để catalog/hồ sơ thay đổi không sửa ý nghĩa lịch sử.
- Tách audio_submissions khỏi messages; transcript phải được người học xác nhận. Audio gốc chỉ giữ tạm, không BLOB hoặc URL công khai dài hạn.
- Tách feedback_items (lỗi/gợi ý sửa) khỏi feedback_suggestions (nội dung nên ôn). review_items là bản sao người học đã lưu; review_attempts là lịch sử ôn.
- ai_requests + ai_attempts thay thiết kế ai_request_logs cũ. Retry không tự tạo lượt học mới; timeout không chứng minh provider chưa thực hiện.
- Xóa một buổi giữ các thẻ ôn đã lưu và gỡ nguồn; xóa toàn bộ dữ liệu học tập xóa cả thẻ/lịch sử ôn. UI cần diễn đạt hai phạm vi riêng.
- Khi xóa buổi, audio cleanup giữ tombstone DELETION_PENDING không transcript, có thể không còn session; worker vẫn dọn được references ngoài DB. Không hứa xóa DB và provider nguyên tử.
- Thống kê tính từ dữ liệu gốc; chưa cần user_statistics, vector DB, RBAC nhiều bảng, phát âm/chứng chỉ hoặc bảng ngoài phạm vi.
- BIGINT ID API dạng chuỗi, thời gian UTC, MySQL InnoDB/utf8mb4, trạng thái VARCHAR với validation/CHECK; schema thực tế qua Flyway, Hibernate validate.

## Hệ quả

Nhiều bảng hơn thiết kế sơ bộ nhưng mỗi bảng có vòng đời rõ. Cần giao dịch ngắn, FK ghép cùng session, kiểm tra owner ở service và kiểm thử MySQL/cạnh tranh/xóa. Đầu ra AI vẫn phải kiểm tra cấu trúc và nguồn, FK không xác minh chất lượng ngôn ngữ. Profile bootstrap và mock test không chứng minh schema/provider thật hoạt động.

Xác thực đã chọn Spring Session JDBC với schema thư viện quản lý qua Flyway, không thêm refresh token. Nếu dùng USD cho estimated_cost phải giữ nhất quán; giá/usage không biết để NULL. Nếu sau này tái sinh feedback đã thành công cần thiết kế versioning nguồn review trước.

## Quyết định tiếp theo

- Session ở ADR 0004, idle 30 phút và email trim/case-insensitive ở ADR 0005 đã chốt; cần hiện thực thu hồi phiên, CSRF và collation đúng quy tắc trước AUTH-01.
- ADR 0006 đã chốt retention local/log/backup và chu kỳ cleanup; cần triển khai timestamp độc lập, retry/restore và xác minh retention provider trước dữ liệu thật.
- Chính sách quota nguyên tử, xử lý UNKNOWN/lease và retry trước tích hợp provider; chỉ ghi log không chặn được vượt ngân sách.
- Chính sách xóa tài khoản nếu bổ sung; hiện chỉ có khóa tài khoản và xóa dữ liệu học tập.
- Migration nhóm nào trước theo chức năng thực hiện; không tạo toàn bộ bảng hoặc SQL rỗng chỉ vì tài liệu có ERD.
