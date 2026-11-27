# API hỗ trợ và phản hồi G9

Tiền tố `/api/v1`, xác thực qua Session. Các lệnh ghi và xem trước dùng CSRF. ID trả về dạng chuỗi. API học viên trả 404 khi phiếu hoặc nguồn không thuộc phạm vi truy cập.

| API học viên | Hành vi |
| --- | --- |
| `GET /support/tickets?page=0&size=12` | Danh sách phiếu của chủ phiên, mới cập nhật trước. |
| `POST /support/tickets/preview` | Nhận `{"type":"AI_MESSAGE","id":"701"}`, trả `excerpt` và `revision`. |
| `POST /support/tickets` | Nhận `kind`, `title`, `body`, `sources`, `previewRevision` và `clientRequestId` (UUID). Trả chi tiết phiếu. |
| `GET /support/tickets/{id}` | Tiêu đề, loại, trạng thái, đoạn trích nếu còn, `sourceRemoved` và thời gian. |
| `GET /support/tickets/{id}/messages?page=0&size=20` | Dòng thời gian từ cũ đến mới, gồm tin mở phiếu. |
| `POST /support/tickets/{id}/messages` | Nhận `body` và `clientRequestId` (UUID), trả 204. |

`FEEDBACK` và `TECHNICAL` gửi với danh sách nguồn rỗng. `CONTENT_REPORT` cần đúng một phần tử và `previewRevision` từ lần xem trước. Nguồn nhận `LESSON` với ID bài công khai, `AI_MESSAGE` với ID câu AI thuộc buổi của chủ phiên hoặc `FEEDBACK` với ID buổi có nhận xét đã hoàn tất. Với nhận xét, đoạn trích lấy từ phần tóm tắt. Khi đoạn trích đổi, API trả 409 `REPORT_PREVIEW_CHANGED`. Tiêu đề tối đa 200 ký tự, nội dung tối đa 4.000 ký tự, đoạn trích tối đa 480 ký tự và dấu rút gọn nếu cần. Bộ lọc che URL HTTP(S), email, chuỗi số dạng điện thoại và bí mật có nhãn. Gửi lại cùng UUID trong cùng phạm vi trả kết quả cũ.

## Hàng đợi quản trị G10.4

| API dưới `/admin/support/tickets` | Hành vi |
| --- | --- |
| `GET ?status=OPEN&page=0&size=12` | Hàng đợi phân trang. Mỗi mục trả `{ticket,assignedAdminId}`. Bỏ `status` để xem mọi trạng thái. |
| `GET /assignees` | Danh sách quản trị viên đang hoạt động có thể nhận phân công. |
| `GET /{id}`, `GET /{id}/messages` | Chi tiết phiếu gồm người phụ trách, đoạn trích đã lọc nếu có và danh sách trao đổi. Transcript đầy đủ tiếp tục yêu cầu chủ sở hữu buổi. |
| `PATCH /{id}/assignment` | Nhận hoặc phân công lại bằng `{"adminId":"12","expectedAdminId":null}`. Khi phân công lại, gửi ID người đang phụ trách trong `expectedAdminId`. Phiên bản phân công đã thay đổi hoặc phiếu đã đóng trả 409. |
| `POST /{id}/messages` | Chỉ người phụ trách được gửi `body` và `clientRequestId`. Thử lại cùng yêu cầu giữ một phản hồi và một thông báo cho chủ phiếu. |
| `PATCH /{id}/status` | Chỉ người phụ trách được chuyển theo thứ tự `OPEN → IN_REVIEW → RESOLVED → CLOSED`. Chuyển sai thứ tự hoặc trả lời phiếu đã đóng trả 409. |

Quản trị viên chưa được phân công nhận 403 khi trả lời hoặc đổi trạng thái. Phân công, phản hồi và đổi trạng thái có audit theo người thực hiện và ID phiếu. Hộp thư trả `supportTicketId` để học viên mở đúng phiếu. [API thông báo quản trị](admin.md#thông-báo-g105).

Khi xóa buổi, backend gỡ nguồn câu AI và nhận xét, xóa đoạn trích và giữ phiếu cùng trao đổi. Khi xóa tài khoản, phiếu thuộc tài khoản và thông báo liên quan được dọn theo khóa ngoại.
