# Phiếu rà chất lượng hội thoại AI

Hai mẫu dưới đây do nhóm dự án chuẩn bị để kiểm tra A2/B1. Câu trả lời kỳ vọng là tiêu chí đối chiếu. Phiếu chưa có đầu ra Gemini thật hoặc điểm của người đánh giá độc lập.

## Mẫu đầu vào

| Mã | Tình huống và vai | Nội dung hư cấu | Điều cần quan sát |
| --- | --- | --- | --- |
| A2-FOOD | Gọi món. Học viên là khách, AI là nhân viên | Đọc “I want order vegetable soup, please.” | Nhân viên hiểu khách muốn súp rau. Bản chép lời giữ từ chỉ món và ý định, không tự sửa ngữ pháp trước khi xác nhận. Nhận xét nhận ra có thể sửa thành “I want to order vegetable soup, please.” |
| B1-INTERVIEW | Phỏng vấn cơ bản. Học viên là người phỏng vấn, AI là ứng viên | Đọc “Could you give me an example of how you worked with a team?” | AI chào và để học viên dẫn dắt, sau đó trả lời bằng ví dụ hư cấu, đúng vai và hợp câu hỏi. Lời góp ý giữ nguyên ý định hỏi về làm việc nhóm. |

Với cả hai mẫu, lưu vào phiếu câu AI thực sự nói và giải thích nó sinh ra. Chấm cặp này theo ý nghĩa và ngữ cảnh. Chấp nhận nhiều diễn đạt đúng, không chấm bằng so khớp máy móc với một câu kỳ vọng.

## Rubric dành cho người rà tiếng Anh

| Tiêu chí | 2 · Đạt | 1 · Đạt một phần | 0 · Cần sửa |
| --- | --- | --- | --- |
| Đúng vai và ngữ cảnh | Thể hiện đúng vai đã chọn, trả lời đúng câu trước và giữ chi tiết | Đúng vai nhưng chung chung hoặc bỏ sót chi tiết phụ | Đảo vai, sai mục đích, mâu thuẫn chi tiết quan trọng |
| Phù hợp A2/B1 | Câu rõ, độ dài và từ vựng phù hợp với trình độ đã chọn | Có từ hoặc cấu trúc hơi khó nhưng vẫn hiểu được | Quá khó hoặc không đủ để tiếp tục hội thoại |
| Chép đúng ý | Giữ từ và chi tiết trọng yếu, kể cả lỗi ngữ pháp nghe rõ | Có lỗi từ nhỏ nhưng không đổi ý | Đổi món, hành động, đối tượng hoặc ý định |
| Giải thích giữ nghĩa | Tiếng Anh đơn giản hơn và nghĩa Việt giữ mục đích, chi tiết chính | Cơ bản đúng nhưng bỏ sắc thái hoặc chưa đơn giản rõ | Trả lời thay học viên, thêm thông tin hoặc đổi nghĩa |
| Chất lượng sửa câu | Dẫn đúng nguồn, giải thích hữu ích, giữ nghĩa. Câu đúng có thể giữ nguyên hoặc nhận gợi ý diễn đạt | Nhận diện đúng nhưng cách giải thích còn thiếu hoặc khó hiểu | Bịa lỗi, sửa sai, dẫn sai nguồn hoặc đổi ý |

Nếu một bước chưa có dữ liệu để chấm, điền **N/A** kèm lý do. Không tính N/A thành 0. Phiếu này đánh giá chất lượng đầu ra của ứng dụng trên mẫu nhỏ, không dùng để xếp trình độ hay chấm phát âm người học.

## Ghi kết quả

| Mã mẫu | Người rà (bí danh) | Đúng vai | Trình độ | Chép lời | Giải thích | Sửa câu | Trích đoạn và lý do |
| --- | --- | --- | --- | --- | --- | --- | --- |
| A2-FOOD | Chờ rà độc lập | — | — | — | — | — | — |
| B1-INTERVIEW | Chờ rà độc lập | — | — | — | — | — | — |

**Cỡ mẫu:** 2 kịch bản chuẩn bị, 0 kịch bản live được chấm, 0 người rà độc lập ở lượt chuẩn bị. Sau đánh giá, báo số kịch bản chấm được theo từng tiêu chí và giữ nhận xét minh họa. Dùng nhận xét để phát hiện lỗi cần sửa trước khi mở rộng bộ mẫu P8.3.

## Câu hỏi dùng thử theo nhiệm vụ

1. Bạn hoàn thành bước nào mà không cần trợ giúp và dừng ở bước nào?
2. Bạn có hiểu vai của mình, biết khi nào phụ đề đang ẩn và phân biệt bản chép lời nháp với câu đã gửi không?
3. Chọn một góp ý hữu ích và thử nói hoặc viết lại câu đó. Phần giải thích có giúp bạn trả lời tiếp không?

Thu kết quả tự nguyện bằng mã người tham gia, cùng thời gian hoàn thành và lỗi định tính. Dùng tỷ lệ hoàn thành và mức dễ hiểu để mô tả đợt thử nhỏ, chưa suy ra cải thiện dài hạn.
