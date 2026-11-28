# API nhận xét cuối buổi

Phản hồi thành công có `repeatedErrors` lấy từ các mục ERROR đã lưu. Mỗi phần tử gồm `ruleCode`, `category`, `messageCount` và `example` (một correction nguồn). Hệ thống đếm tin nhắn nguồn khác nhau và chỉ hiện nhóm có ít nhất hai tin, sắp giảm dần theo số đếm rồi tăng dần theo mã, tối đa ba nhóm. Các trạng thái chưa thành công và buổi không có lỗi lặp trả mảng rỗng. Hợp đồng áp dụng cả cho phản hồi đã lưu trước đó.

Các endpoint nằm dưới `/api/v1/practice-sessions/{id}/feedback`. Tất cả yêu cầu Session hợp lệ và quyền sở hữu buổi. Hai thao tác POST yêu cầu CSRF.

| Thao tác | Kết quả |
| --- | --- |
| GET | Đọc trạng thái và kết quả đã lưu. NOT_STARTED khi chưa tạo. |
| POST | Tạo nhận xét cho buổi COMPLETED hoặc trả bản đã có. Gửi lại request đọc cùng tác vụ. |
| POST /retry | Thử lại lỗi có thể khôi phục. Body là `{"acknowledgeUnknown":false}`. Đặt true khi chấp nhận gọi lại một lần có kết quả UNKNOWN. |

Response gồm `status`, `errorCode`, `retryable`, `attempts`, `result`, `suggestionIds`. Khi SUCCEEDED, result gồm summary, strengths, goalAssessment, corrections, suggestions và nextPractice. Thứ tự suggestionIds tương ứng thứ tự suggestions để [lưu thẻ ôn](learning.md) từ đúng nguồn. Correction có sourceMessageId, itemType, category, ruleCode, originalText, suggestedText, explanationVi. Suggestion có itemType, expression, meaningVi, exampleEn.

Buổi người khác trả 404. Tạo trước khi buổi hoàn thành trả 409. Quota và thời gian chờ retry trả 429. Khi Gemini trả dữ liệu sai nguồn hoặc sai cấu trúc, API trả trạng thái FAILED với code FEEDBACK_INVALID. Transcript vẫn có thể đọc qua API buổi luyện. Luồng retry đã thành công trả lại kết quả lưu sẵn.

## Nhận xét V3

Từ V32, `result.coaching` gồm `analyzedTurns`, `totalTurns`, `turns`, `goals` và `replay`. Bản cũ trả `null`. Giao diện đối chiếu ID với transcript owner-only đã tải của cùng buổi.

- `turns` gồm `learnerMessageId`, `assistantMessageId` nếu có, `status` (COMPLETE, PARTIAL, OFF_TOPIC, UNCERTAIN), `questionQuality`, `explanationVi`, `adviceKind` và `suggestionEn`.
- `goals` gồm `labelVi`, `status` (MET, PARTIAL, NOT_MET, UNCERTAIN) và `learnerMessageIds`. Mục tiêu đạt hoặc đạt một phần có bằng chứng từ lượt đã phân tích.
- `replay` gồm `learnerMessageId`, `criterionVi` và `transferQuestionEn` khi có lượt phù hợp. Thao tác tự luyện không ghi tin nhắn vào buổi.
- `suggestions[].source` có `kind` (IN_TRANSCRIPT hoặc TOPIC_EXTENSION), `messageId`, `excerpt`, `observedForm` và `useVi`. Mục mở rộng có các trường nguồn transcript rỗng. `suggestionIds` tiếp tục dùng cho lưu thẻ.

[Quyết định lưu và kiểm tra chất lượng](../decisions/0018-conversation-feedback-v3.md).

Prompt `practice-feedback-v3.4` yêu cầu JSON và đưa cấu trúc chi tiết vào chỉ dẫn. Backend kiểm tra cặp câu AI và câu học viên, nguồn của câu sửa và các phần nhận xét cốt lõi trước khi lưu. Khi kiểm tra câu sửa, backend đối chiếu toàn bộ tin nguồn, nhận diện tự đính chính giờ với dấu hiệu rõ, như “At six, sorry, at seven”, và giữ màu gắn với hành động đặt, mua hoặc nhận trong mẫu đổi hàng. Góp ý sai dữ kiện được bỏ riêng, cùng với các mục từ hoặc bài luyện lại không hợp lệ.

Với câu hỏi rõ, câu đáp sang một việc khác dùng `OFF_TOPIC`, kể cả khi cùng chủ đề. Câu đáp trúng một phần dùng `PARTIAL`. Gemini được yêu cầu trả `FILL_IN` với một câu trả lời bám đúng câu hỏi và chỗ trống cho dữ kiện còn thiếu. Ví dụ, khi hỏi giờ tan học và người học chỉ nói giờ đến trường, hệ thống gợi ý “I finish school at [time].” Backend có mẫu dự phòng cho một số dạng câu hỏi rõ khi lời khuyên bị thiếu. Khi mở lại nhận xét V3 đã lưu còn thiếu mẫu, API có thể bổ sung mẫu dự phòng từ hội thoại của chính buổi ngay lúc đọc. Thao tác đọc không gọi Gemini.

`suggestions[].source.meaningVerified` là kết quả đối chiếu phía server. `true` cho phép lưu thẻ và dùng nghĩa, ví dụ từ mục tham chiếu. `false` hiển thị gợi ý với lưu ý nghĩa chưa được đối chiếu và API không cho tạo thẻ mới. Mục xung đột với nghĩa tham chiếu cùng từ loại bị bỏ. Trường vắng hoặc `null` thuộc nhận xét cũ và tiếp tục dùng quyền lưu hiện có. Bản này đối chiếu với học liệu công khai và mẫu đặt phòng đã biên soạn trong phạm vi nhỏ, trong cùng lần xử lý FEEDBACK.

Các yêu cầu `practice-feedback-v3` từng bị `PROVIDER_REQUEST_REJECTED` có thể dùng nút thử lại nếu còn hạn mức và còn lần thử. Khi giới hạn là hai lần thử, tác vụ `practice-feedback-v3.1` đã dùng hết hai lần và thất bại với `FEEDBACK_INVALID` được thử thêm một lần theo yêu cầu của học viên, trong hạn mức hiện hành. Lần thử mới cập nhật phiên bản prompt trên cùng tác vụ. Gemini trả HTTP 400 được ghi mã `PROVIDER_INVALID_ARGUMENT`. Nội dung phản hồi lỗi của nhà cung cấp không được đưa vào log.
