# API nhận xét cuối buổi

Các endpoint nằm dưới `/api/v1/practice-sessions/{id}/feedback`. Tất cả yêu cầu Session hợp lệ và quyền sở hữu buổi. Hai thao tác POST yêu cầu CSRF.

| Thao tác | Kết quả |
| --- | --- |
| GET | Đọc trạng thái và kết quả đã lưu. NOT_STARTED khi chưa tạo. |
| POST | Tạo nhận xét cho buổi COMPLETED hoặc trả bản đã có. Gửi lại request đọc cùng tác vụ. |
| POST /retry | Thử lại lỗi có thể khôi phục. Body là `{"acknowledgeUnknown":false}`. Đặt true khi chấp nhận gọi lại một lần có kết quả UNKNOWN. |

Response gồm `status`, `errorCode`, `retryable`, `attempts`, `result`, `suggestionIds`. Khi SUCCEEDED, result gồm summary, strengths, goalAssessment, corrections, suggestions và nextPractice. Thứ tự suggestionIds tương ứng thứ tự suggestions để [lưu thẻ ôn](learning.md) từ đúng nguồn. Correction có sourceMessageId, itemType, category, ruleCode, originalText, suggestedText, explanationVi. Suggestion có itemType, expression, meaningVi, exampleEn.

Buổi người khác trả 404. Tạo trước khi buổi hoàn thành trả 409. Quota và thời gian chờ retry trả 429. Khi Gemini trả dữ liệu sai nguồn hoặc sai cấu trúc, API trả trạng thái FAILED với code FEEDBACK_INVALID. Transcript vẫn có thể đọc qua API buổi luyện. Luồng retry đã thành công trả lại kết quả lưu sẵn.
