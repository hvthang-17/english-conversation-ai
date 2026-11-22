# API thư viện, lịch sử và thống kê

Các route dưới `/api/v1/learning` xác thực bằng Session. Backend xác định owner từ principal. POST và DELETE yêu cầu CSRF. Định danh trả về trong JSON là chuỗi. Buổi hoặc thẻ của người khác trả 404.

| Method và route | Kết quả |
| --- | --- |
| GET /cards?status=NEEDS_REVIEW&page=0&size=12 | Trang thẻ có `items,page,size,total`. Status tùy chọn, nhận NEEDS_REVIEW hoặc REMEMBERED. |
| POST /cards/from-suggestion/{suggestion} | Sao chép nội dung suggestion từ nhận xét SUCCEEDED của owner. Gửi lại trả cùng thẻ và trạng thái hiện tại. |
| POST /cards/{id}/attempts | Body `{"result":"REMEMBERED","clientRequestId":"review-request-01"}`. Ghi một attempt FLASHCARD và cập nhật thẻ. |
| GET /history?page=0&size=12 | Lịch sử mới nhất trước, gồm cả buổi có thể tiếp tục. Cùng định dạng phân trang với thẻ. |
| DELETE /history/{id} | 204. Xóa buổi, transcript và nhận xét. Thẻ đã lưu giữ bản sao và mất liên kết nguồn. |
| DELETE /data | 204. Xóa toàn bộ buổi, transcript, nhận xét, thẻ và attempt của owner. |
| GET /stats | Số buổi hoàn thành, tin nhắn LEARNER, ngày luyện, tối đa 30 ngày có luyện gần nhất, chủ đề, nhóm lỗi và tối đa 5 bài gợi ý. |

Danh sách nhận size từ 1 đến 50. Cùng mã attempt và kết quả được xử lý idempotent. Cùng mã và kết quả khác trả 409. Chi tiết lịch sử dùng lại GET practice-sessions/{id} và GET practice-sessions/{id}/feedback. Khi xin xóa toàn bộ, giao diện yêu cầu xác nhận riêng trước khi gọi DELETE.
