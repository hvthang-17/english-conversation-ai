# Rà soát 8 tình huống khởi đầu

Ngày 20/11/2026. Nguồn nội dung do dự án biên soạn, lưu trong [V4](../../backend/src/main/resources/db/migration/V4__starter_catalog.sql). Rà soát cấu trúc/nội dung bởi trợ lý phát triển; chưa có chuyên gia tiếng Anh độc lập hoặc kết quả hội thoại Gemini thật. Không coi metadata level là chứng nhận CEFR.

| Code | Bài / chủ đề | Vai người học ↔ đối thoại | Mục tiêu cụ thể | Level |
| --- | --- | --- | --- | --- |
| SELF_INTRODUCTION | Giới thiệu bản thân / Đời sống | Thành viên mới ↔ Thành viên câu lạc bộ | Chào hỏi, giới thiệu, hỏi lại một câu | A2, B1 |
| STUDENT_DAY | Cuộc sống sinh viên / Sinh viên | Sinh viên ↔ Bạn cùng lớp | Nói lịch học, hỏi lịch rảnh, thống nhất buổi học nhóm | A2, B1 |
| RESTAURANT_ORDER | Gọi món / Ăn uống | Khách ↔ Phục vụ | Hỏi thành phần, chọn đồ ăn/uống, xác nhận đơn | A2, B1 |
| SHOPPING_CLOTHES | Mua sắm / Mua sắm | Người mua ↔ Nhân viên | Hỏi màu/kích cỡ/giá, thử phương án khác, quyết định lịch sự | A2, B1 |
| ASK_DIRECTIONS | Hỏi đường / Du lịch | Người hỏi ↔ Người địa phương | Hỏi thư viện, trái/phải, mốc đường, xác nhận lại | A2, B1 |
| HOTEL_BOOKING | Đặt phòng / Du lịch | Khách ↔ Lễ tân | Hỏi ngày/số đêm/loại phòng/bữa sáng, xác nhận yêu cầu | A2, B1 |
| WEEKEND_PLANS | Sở thích/cuối tuần / Đời sống | Người rủ ↔ Bạn | Nói sở thích, mời, so sánh lựa chọn, thống nhất lịch | A2, B1 |
| BASIC_INTERVIEW | Phỏng vấn cơ bản / Công việc | Ứng viên ↔ Người phỏng vấn | Giới thiệu, điểm mạnh/ví dụ đơn giản, động lực, hỏi công việc | A2, B1 |

## Kết quả rà soát biên soạn
- Đủ tám trường công khai cần dùng: tên/giới thiệu, hai vai, mục tiêu, bối cảnh, chủ đề và levels; cả tám có instruction phía server.
- Bối cảnh có điểm bắt đầu và mục tiêu để đối thoại tiến triển; không chỉ là tên chủ đề. Vai AI nhất quán với vai người học.
- A2 dùng câu hỏi ngắn/từ thông dụng; B1 cho phép giải thích và chi tiết hơn qua chỉ dẫn server-selected level. Chất lượng thực tế cần đánh giá khi gọi AI được cho phép.
- Nhà hàng có menu giả định; hỏi đường dùng khu phố giả định nhất quán; đặt phòng không phải booking thật, không yêu cầu hộ chiếu/thẻ/thông tin liên lạc. Phỏng vấn nhận ví dụ học tập/hư cấu, tránh câu hỏi riêng tư hoặc hứa kết quả tuyển dụng.
- Không yêu cầu dữ liệu cá nhân thật, không chấm phát âm/CEFR, không tự tạo lỗi trong hội thoại. Giá/ngày/lịch đều là mô phỏng.
- V4 chỉ chạy một lần dưới Flyway, không ON DUPLICATE KEY UPDATE hoặc startup seed ghi đè. Code ổn định, FK tra topic theo code, không hard-code ID. Sau áp dụng, nội dung thay đổi bằng migration mới hoặc tính năng admin được giao sau.
- HTTP/MySQL test đọc đủ 8 bài ở cả A2/B1, kiểm tra vai/mục tiêu/bối cảnh, filter/ẩn/level và không lộ instruction. Test không chứng minh AI thực hiện đúng prompt; chuyên gia và live rubric vẫn thuộc bước AI tiếp theo.
