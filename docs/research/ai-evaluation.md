# Đề cương đánh giá AI

Trạng thái: quy trình và mẫu đã chuẩn bị; **chưa có kết quả Gemini thật hoặc đánh giá người**. Hiện chủ dự án chỉ cho chuẩn bị offline; không chạy các case này như lời gọi có phí.

## Bộ dữ liệu
[32 trường hợp tổng hợp](../../experiments/phase0/fixtures/ai-evaluation-cases.json) bao phủ 8 tình huống × 2 level × câu đúng/câu có lỗi chủ đích. Đây là fixture do nhóm biên soạn, chưa phải chuẩn đánh giá đã được chuyên gia xác nhận. Kiểm tra lại ngữ pháp, tính tự nhiên và độ khó trước dùng làm gold set.

Bản probe chỉ chuẩn bị 4 yêu cầu giới hạn (chat A2, chat B1, feedback và audio tổng hợp), không tự chạy toàn bộ 32 case. Fixture không dùng thông tin người học thật.

## Tiêu chí và cách ghi
Người đánh giá đủ năng lực tiếng Anh chấm từng tiêu chí từ 1 (không đạt) đến 5 (đạt rõ), kèm dẫn chứng; không quy đổi sang trình độ CEFR của người học.

| Tiêu chí | Cần quan sát |
| --- | --- |
| Đúng vai/tình huống | AI không đổi vai hoặc đi lạc mục tiêu |
| Phù hợp level chọn | Độ dài/từ vựng/cấu trúc vừa với A2 hoặc B1 trong bài luyện |
| Duy trì hội thoại | Câu trả lời liên quan và có bước tiếp hợp lý |
| Sửa lỗi | Xác định đúng lỗi, không bịa lỗi trong câu đúng; phân biệt ERROR/IMPROVEMENT |
| Giải thích | Tiếng Việt rõ, câu đề xuất đúng và không làm đổi ý ngoài cần thiết |
| Nội dung ôn | Từ/mẫu câu hữu ích, phù hợp ngữ cảnh |

Mẫu bảng ghi: case_id, model, prompt_version, evaluator_code, các điểm, false_positive, missed_error, trích dẫn/nhận xét đã loại thông tin riêng. Không commit raw hội thoại cá nhân; nội dung fixture có thể dùng để ghi bằng chứng theo phạm vi nghiên cứu được chấp nhận.

## Kiểm tra tự động độc lập với chất lượng ngôn ngữ
- Schema/type/enum hợp lệ, không có câu nguồn ngoài session.
- Câu trích có trong message LEARNER; cho phép corrections rỗng.
- Không biến đề xuất tự nhiên hơn thành lỗi sai bắt buộc.
- Không trả điểm phát âm/CEFR; nội dung user không được đổi quyền hoặc đọc secret.
- Đầu ra không hợp lệ phải được từ chối dù mô hình trông có vẻ trả lời đúng.

## Hiệu năng và chi phí
Ghi latency riêng từng tác vụ, HTTP/status, retry, input/output/total tokens nếu có, model/version và điều kiện mạng. Báo sample size; bốn request khác tác vụ không đủ để tuyên bố P95 đáng tin hoặc so sánh hiệu năng model. Unknown usage/cost là NULL, không phải 0.

Chỉ tính chi phí khi đã kiểm tra bảng giá đúng gói/model/thời điểm. Request count và max output không phải hard billing cap. Muốn live test cần phê duyệt mới về gói/ngân sách/case và credential môi trường, không suy ra quyền từ sự tồn tại của key.

## Đánh giá chép lời
Với câu tổng hợp đã biết transcript, ghi tỷ lệ từ sai theo quy tắc chuẩn hóa được công bố, trường hợp từ quan trọng bị sai và thời gian phản hồi. Âm SAPI sạch không đại diện giọng người học/tiếng ồn. Sau đó mới dùng mẫu người tham gia đồng ý và chính sách dữ liệu phù hợp; tuyệt đối không suy ra điểm phát âm từ sai số chép lời.

## Ngưỡng đề xuất cho lần dùng thử đầu
Không chấp nhận source ngoài buổi/đầu ra vi phạm schema được lưu. Đề xuất trung vị ít nhất 4/5 ở đúng vai, liên quan và giải thích; mọi lỗi ngôn ngữ nghiêm trọng cần phân tích riêng. Đây là ngưỡng đánh giá đề xuất, chưa có kết quả đạt và chưa được dùng để khẳng định cải thiện năng lực dài hạn.
