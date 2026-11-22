# Giai đoạn 4 — Phản hồi sau buổi luyện

Khởi động lại backend để Flyway áp V6. Với Gemini đã cấu hình theo P3, người học kết thúc buổi có ít nhất một câu đã gửi, sau đó bấm **Tạo nhận xét**. Giao diện hiển thị nhận xét, điểm tốt, mức đạt mục tiêu, lỗi cần sửa, phương án diễn đạt tự nhiên hơn, từ/mẫu câu và bước luyện tiếp theo. Các nhận xét và gợi ý được hiển thị dưới dạng văn bản.

V6 thêm session_feedback, feedback_items, feedback_suggestions. Mỗi buổi có tối đa một bản tổng hợp. Hai khóa ngoại ghép buộc correction, feedback và message có cùng session_id. Backend kiểm tra sender là LEARNER, đoạn trích nguyên văn có thực, câu đề xuất khác câu gốc và cặp category/ruleCode thuộc danh sách cho phép. OBJECT và ARRAY được kiểm tra lại ở backend. Mảng corrections rỗng hợp lệ.

FEEDBACK dùng quota và lease của P3. Backend gọi model ngoài transaction, rồi xác minh attempt còn hiệu lực. Cập nhật metadata, bản tổng hợp và chi tiết cùng transaction. Lỗi để lại một bản feedback riêng ở FAILED hoặc UNKNOWN. Thử lại giữ ID feedback và AI request, có thêm attempt tới trần hiện hành. Thành công được dùng lại. Buổi vẫn ở trạng thái COMPLETED.

Prompt cố định version `practice-feedback-v1`, dùng level chỉ để điều chỉnh độ khó giải thích và bài luyện sau. Kết luận tập trung vào hoàn thành nhiệm vụ, ngôn ngữ và cách diễn đạt từ transcript. Không chấm phát âm hoặc chứng nhận CEFR. [Hợp đồng API](../../api/feedback.md). [Structured output](https://aistudio.google.com/docs/structured-output).

## Rà soát ngôn ngữ ban đầu

| Mẫu | Kỳ vọng đã rà soát |
| --- | --- |
| “I would like soup, please.” | Câu đúng ngữ pháp và lịch sự. Để trống corrections là hợp lệ. Nhà hàng đã nhận yêu cầu món chính. Nếu người học chưa gọi đồ uống thì mục tiêu xác nhận đồ uống vẫn chưa đạt. |
| “I want order soup.” | Sửa đoạn “want order” thành “want to order”. Sau want, dùng to + động từ nguyên mẫu để nói điều người học muốn làm. Giữ nguyên món súp và ý định gọi món. |
| “I want soup.” | Câu đúng ngữ pháp. “I would like soup, please.” là gợi ý lịch sự hơn khi gọi món, được xếp IMPROVEMENT nếu đưa vào kết quả. |
| “May I have some tea, please?” | Mẫu câu tiếng Anh đúng và phù hợp vai khách. Nghĩa tiếng Việt là “Cho tôi xin trà được không?”. |

Hai mẫu đầu được giữ trong fixture MySQL để chạy lại. Kiểm thử sai nguồn bao gồm tin AI, ID không có trong transcript, đoạn trích tự tạo và phân loại sai quy tắc. Rà soát trên là kiểm tra ngôn ngữ của kết quả kỳ vọng do trợ lý thực hiện, chưa có người đánh giá độc lập hoặc mẫu phản hồi live của Gemini cho P4. Phạm vi live trước đây là bốn request P3 đã dùng hết.

## Kiểm chứng kỹ thuật

- Maven verify trên MySQL 8.4.13 đạt 35/35 test, không skip. Flyway áp V6 trên schema mới và nâng cấp từ V2 có tài khoản. HTTP test gọi POST/GET nhận xét với Session và kiểm tra CSRF. Test nghiệp vụ kiểm tra quyền owner, từ chối nội dung sai nguồn, giữ transcript khi lỗi, retry trên cùng bản và trả lại kết quả thành công đã lưu.
- Frontend qua typecheck, lint và build. Playwright dùng dữ liệu giả lập ở 390px để kiểm tra bàn phím, trạng thái chờ, lỗi, thử lại, tách lỗi và gợi ý diễn đạt, giữ transcript và đọc lại sau reload. Không có tràn ngang.
