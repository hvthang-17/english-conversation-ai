---
name: integrate-gemini
description: Implement or modify server-side Gemini conversation, audio transcription, or feedback with output validation, usage limits, and privacy controls.
---

# Gemini integration

Đọc docs/architecture/ai-integration.md và yêu cầu liên quan từ gốc repository. Tra tài liệu chính thức cho model, Java SDK/REST, audio, schema và giá tại thời điểm triển khai.

## Thực hiện
- Tách hội thoại, chép lời và phân tích cuối buổi. Transcript cần xác nhận trước khi thành lượt chat.
- API key và lời gọi ở backend; giới hạn input/context/output và usage. AI không xác thực hoặc cấp quyền.
- Tách system instruction khỏi nội dung người học. Validate đầu ra, đối chiếu câu sửa với hội thoại, cho phép không có lỗi.
- Timeout, retry hữu hạn và idempotency ở ứng dụng. Không gọi AI trong DB transaction mở hoặc retry mọi lỗi.
- Kiểm tra audio và dọn tệp local; kiểm tra retention/xóa ở provider nếu upload. Log metadata, không log hội thoại thô hay key.

## Hoàn thành
Test provider mock cho lỗi, schema sai, quá hạn mức và yêu cầu trùng. Live call chỉ khi credential và phạm vi chi phí được cấp. Không suy ra chất lượng thật từ mock; báo config và phần cần người đủ năng lực tiếng Anh đánh giá.
