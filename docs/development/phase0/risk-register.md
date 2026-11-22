# Rủi ro, chủ trì và thời điểm xử lý

Vai A/B là đề xuất phân công cho hai thành viên, không phải agent tự gửi việc. Các mốc là lịch mục tiêu; không thay quyền gọi API hoặc chi phí.

| ID | Rủi ro/trạng thái | Tác động | Người chủ trì đề xuất | Hành động và mốc xử lý |
| --- | --- | --- | --- | --- |
| R01 | Live Gemini hoãn theo yêu cầu, chưa có key/gói/quota được kiểm chứng | Chưa chốt được chất lượng, độ trễ, model và chi phí; không đóng P0.4/P0.6 | Chủ dự án + A | Chỉ chuẩn bị offline hiện tại; quyết định quyền live và kiểm chứng trước CHAT-01 dự kiến 02/12, nếu tiếp tục hoãn phải điều chỉnh mốc M2 |
| R02 | Model/SDK thay đổi, tài liệu online có thể mới hơn version dự án | Lỗi tích hợp, access khác project hoặc hết quota | A | Dùng config/model ID rõ, kiểm tài liệu đúng version, không auto fallback có phí; quyết định runtime sau live evidence |
| R03 | Chỉ Chrome 138 desktop có báo cáo micro thật, chưa có toàn matrix | Voice có thể lỗi trên Edge/Android hoặc phiên bản mục tiêu | B | Test sớm trước giai đoạn 6; desktop hiện tại đã đạt smoke, Android/Edge cần bằng chứng trước M4 21/12 |
| R04 | Thời gian danh nghĩa 270 giờ-người đến 28/12, thực tế có thể ít hơn | Kế hoạch sát hạn nếu tích hợp muộn | Cả hai | Lát cắt end-to-end, review mỗi mốc, dành 25 giờ cross-cutting và 20 giờ dự phòng; không thêm chức năng ngoài bản đầu |
| R05 | MySQL portable là local, Docker chưa có | Không tự suy ra môi trường người khác/production tương đương | A | Đã có start/verify/stop và restart thật; mỗi thành viên tái lập môi trường trước AUTH-01, production/backup cần kiểm chứng riêng |
| R06 | Cleanup/backup mới là chính sách, chưa có job; TTL audio/nháp khác nhau | Giữ dư dữ liệu hoặc xóa mất reference provider | A | Thiết kế transcript_expires_at riêng, DELETION_PENDING và restore áp lại xóa; kiểm trước dữ liệu thật và P8 |
| R07 | 32 fixture chưa được chuyên gia xác nhận; chưa có người dùng thử | Đánh giá AI/trải nghiệm không đủ căn cứ | B + người đánh giá tiếng Anh | Chuẩn bị sẵn form/rubric; tuyển theo cách chủ dự án cho phép, xác nhận reviewer trước P4; dùng thử 10 người mục tiêu ở P8 |
| R08 | Hiểu nhầm có key nghĩa là được gọi hoặc free tier không có ràng buộc | Gửi dữ liệu/chi tiền ngoài phạm vi | Cả hai | Wrapper hiện từ chối Live, store=false ở payload; không bật billing, không đưa key lên frontend/log; yêu cầu mới trước live |
| R09 | CSRF/session, idempotency hoặc worker muộn sai | Truy cập sai dữ liệu, trùng lượt, dữ liệu tái xuất hiện sau xóa | A, B kiểm chéo | Tests MySQL/HTTP từ P1/P3, không chỉ bootstrap; quyền/status kiểm ở backend, xác nhận mỗi mốc |

Không có rủi ro nào được ghi “đã giải quyết” chỉ vì có phương án. R05 phần local đã kiểm chứng, môi trường khác vẫn cần thử; R01 được hoãn có chủ đích chứ không phải API đã lỗi. Không tự áp mã mock vào sản phẩm để che phần chưa có evidence.
