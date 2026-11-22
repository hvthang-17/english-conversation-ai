# ADR 0006: Chính sách vận hành và nguồn lực sau giai đoạn 0

## Trạng thái
Các lựa chọn dưới đây được chủ dự án xác nhận trong giai đoạn 0. **Chính sách được chốt không đồng nghĩa đã có code cleanup/backup/giọng nói trong sản phẩm.** Đọc [báo cáo phase0](../development/phase0/README.md) để biết bằng chứng thực tế.

## Thời hạn và nguồn lực
- Hạn nộp 28/12/2026 (năm theo ngữ cảnh dự án hiện tại).
- Hai người, mỗi người 5 giờ/ngày, 5 ngày/tuần; khoảng 10 người dùng thử.
- Lịch 20/11–28/12 có 27 ngày thứ Hai–thứ Sáu, tối đa danh nghĩa 270 giờ-người nếu đủ thời gian. Không coi đây là giờ đã làm hoặc bảo đảm hoàn thành.
- Phân vai đề xuất A backend/data/AI, B frontend/audio/research; tích hợp và kiểm tra chéo cùng thực hiện. Không phải yêu cầu tạo subagent hoặc phân công đã được hai thành viên xác nhận tên.

## Dữ liệu và browser đã chốt
- Audio gốc: xóa sớm sau xử lý/hủy/không còn dùng, TTL an toàn 1 giờ từ upload. Cleanup mỗi 5 phút; outage/provider có thể trì hoãn, cần theo dõi/retry, không báo xóa giả.
- Transcript chưa xác nhận: xóa khi xác nhận/hủy/kết thúc, nếu bỏ dở tối đa 30 phút từ READY. Transcript cuối đã xác nhận là messages, giữ theo lịch sử.
- ai_requests/ai_attempts: metadata 30 ngày sau kết thúc; không prompt/response/audio thô. Đối soát RUNNING/UNKNOWN trước purge, xử lý FK nullable và giữ trạng thái nghiệp vụ.
- Backup database mỗi ngày, mã hóa, giữ 7 ngày; không backup audio. Quy trình xuất/khôi phục phải xử lý dữ liệu nháp/phiên không cần giữ và áp lại yêu cầu xóa, không làm mất toàn vẹn FK. Chưa có backup job thật.
- Thông báo audio: trước lần đầu và xem lại được; chỉ upload sau bấm gửi; người học sửa/xác nhận transcript; nói rõ Google Gemini là dịch vụ ngoài, văn bản cũng gửi AI, chính sách provider phụ thuộc gói.
- Bản ghi tối đa 60 giây; backend vẫn phải kiểm tra thật, không chỉ giới hạn UI.
- Hỗ trợ mục tiêu: Chrome/Edge Windows và Chrome Android; Safari/iPhone chưa cam kết giọng nói. Kiểm thử bản stable hiện hành và một major trước khi nghiệm thu, không suy từ một bản Chrome đã thử sang toàn matrix.
- P0.5 ưu tiên micro thật trên máy tính theo yêu cầu hiện tại. Chủ dự án đã gửi metadata Chrome Windows nghe rõ; Android/Edge vẫn phải thử ở giai đoạn 6, không bị coi là đã đạt hoặc bị bỏ khỏi mục tiêu.

Chi tiết tại [chính sách dữ liệu/browser](../product/data-and-browser-proposal.md); tên file giữ để không phá liên kết cũ, trạng thái đã chuyển từ đề xuất sang chốt.

## Gemini: ranh giới cho lần thực hiện hiện tại
**Chưa gọi API thật; chỉ chuẩn bị mã thử và hướng dẫn lấy key.** Không tự bật billing hoặc chi tiền. Tạo key sau này không tự cấp quyền thực hiện live call. Live model selection, provider latency/usage/quality còn cần yêu cầu mới và phạm vi chi phí rõ.

Đã có Java REST Interactions prototype offline; đây là lựa chọn công cụ thử, không chốt Java SDK/REST hoặc model cho runtime chỉ dựa vào build. Tham khảo chính thức và candidate model được ghi trong báo cáo, không coi là kết quả đo.

## Điều chỉnh dữ liệu và triển khai
Giữ 20 bảng; thêm thiết kế audio_submissions.transcript_expires_at để tách TTL transcript khỏi expires_at của file audio. Chưa viết migration. Chính sách xóa buổi giữ thẻ ôn vẫn theo ADR 0003; session auth vẫn theo ADR 0004/0005.

Có thể bắt đầu tài khoản khi MySQL và quy tắc auth đã đủ căn cứ. Không đóng toàn bộ giai đoạn 0 khi P0.4/P0.6 chưa có live evidence; ghi chúng là hoãn theo yêu cầu và kiểm tra lại trước tích hợp hội thoại.
