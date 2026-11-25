# Kiểm thử tích hợp G6

Profile `g6` là môi trường thử hội thoại. Từ thư mục backend, chạy `mvn spring-boot:run` với MySQL dùng dòng Flyway hiện tại. V16 bổ sung buổi luyện, lượt, tin nhắn, audio, giải thích và phản hồi. V16 dùng lại hai bảng tác vụ AI từ V13. Maven mặc định dùng profile G6 và áp Flyway đến V18.

Gemini mặc định tắt. Khi có phạm vi thử thật, cấu hình `GEMINI_CHAT_ENABLED`, `GEMINI_API_KEY`, `GEMINI_CHAT_MODEL`, `CHAT_FINGERPRINT_SECRET` và `CHAT_TOTAL_ATTEMPT_LIMIT` trong tệp môi trường local. Luồng nghe và nói phát câu AI bằng giọng tiếng Anh cục bộ; Gemini xử lý bản ghi sau khi gửi và CHAT dùng bản chép đã xác nhận.

G6 dùng Flyway V17 để liên kết kết quả Azure UNSCRIPTED với bản ghi. Đặt CONVERSATION_PRONUNCIATION_ENABLED=true và PRONUNCIATION_AZURE_ENABLED=true cùng cấu hình Azure và khóa HMAC của G5, rồi khởi động lại profile g6. Giao diện mặc định yêu cầu chấm. Audio gốc tối đa 30 giây chỉ được chuyển vào hàng đợi sau xác nhận khi còn hạn. Lượt dài hơn vẫn dùng được cho hội thoại. Worker dùng chung hạn mức gọi Azure với G5, còn Gemini có bộ đếm riêng. Chính sách triển khai theo [ADR 0016](../../decisions/0016-g6-direct-voice-submission.md).

## Bằng chứng kỹ thuật

MySQL 9.2 trên schema rỗng đã áp Flyway V1–V16. Năm ca chạy qua profile G6 và Gemini giả lập đã kiểm tra Session/CSRF, kiểm tra WAV, xác nhận bản chép đã sửa, giải thích câu AI và trạng thái kết thúc. Frontend typecheck, lint và build đều đạt. Người dùng báo đã thử dịch vụ thật thành công. Lần tích hợp này xác minh riêng với fixture, không gửi bản ghi đến Azure.
Năm ca hồi quy bổ sung đều đạt sau khi khởi động lại schema V16. Các ca bao gồm snapshot, quyền sở hữu, giới hạn lượt, gợi ý, timeout và retry, điều phối gọi đồng thời, kiểm tra nguồn phản hồi và giữ thẻ sau xóa buổi. Flyway xác nhận schema đã cập nhật và không chạy migration lại.

V17 đã nâng cấp từ V16 trên MySQL 9.2. Sáu ca Maven đạt, gồm xác nhận trùng, Azure giả lập timeout độc lập với kết thúc buổi, giữ thẻ khi xóa và kiểm tra quota. Frontend typecheck, lint và build đạt.

## G6.1 — Chọn tình huống, vai và mở lại buổi

Ba ca tích hợp trên MySQL 9.2 schema rỗng từ V1–V17 đạt. Dùng tình huống Gặp bạn mới ở câu lạc bộ (A2) và Đổi hàng tại quầy (B1). Ca B1 xác minh cả vai mặc định TEXT và vai đảo VOICE, từ chối vai hoặc cấp độ ngoài catalog và không tạo trùng buổi khi gửi lại. Sau khi thay vai trên catalog, đọc lại buổi và lịch sử vẫn giữ snapshot, cấp độ, chế độ và câu mở đầu. Ca HTTP kiểm tra thêm Session và CSRF. Câu mở đầu dùng Gemini giả lập để đối chiếu prompt và lưu trữ; chất lượng lời mở đầu của Gemini thật vẫn cần mẫu A2/B1 để nghiệm thu.

## G6.2 — CHAT nhiều lượt

Năm ca tích hợp đạt trên MySQL 9.2 schema rỗng V1–V17 với Gemini giả lập. Kiểm tra quyền sở hữu, cùng mã cùng nội dung, cùng mã khác nội dung, lượt 21, quota trước khi gọi, hai gửi đồng thời, timeout UNKNOWN và retry giữ nguyên câu đã lưu. Đối chiếu usage/latency trong ai_attempts. Với CHAT_CONTEXT_LIMIT=2400, lời gọi giữ câu mới nhất, bỏ lịch sử cũ khỏi prompt và vẫn lưu đủ transcript. Ca hồi quy VOICE xác nhận bản chép đã sửa vẫn tạo đúng một lượt.

## G6.3 — TEXT và VOICE nghe trước

Chrome headless với API/TTS giả lập xác minh phát theo yêu cầu, nghe lại và nghe chậm 0,75×, phụ đề theo từng câu, gõ thay một lượt, tải lại giữ VOICE và hiển thị toàn bộ transcript khi kết thúc. Người học chọn chế độ ở chi tiết tình huống trước khi bắt đầu.

## G6.4 — Ghi âm, xác nhận và chấm tùy chọn

Bảy ca tích hợp MySQL với Gemini/Azure giả lập đạt. Kiểm tra quyền truy cập và CSRF, gửi trùng, khôi phục TRANSCRIBING và READY, chép lời không nhận được tiếng nói, timeout và retry khi bật Azure, nháp và audio hết hạn độc lập, xác nhận duy nhất một lượt, xóa trong khi đang chép và Azure lỗi vẫn kết thúc được buổi. Chỉ bản ghi có nháp READY được giữ để chấm tùy chọn; bản chép lời thất bại giải phóng audio ngay để thử lại. Nếu audio hết hạn nhưng nháp còn hạn, CHAT dùng được bản chép đã xác nhận và Azure không nhận bản ghi.

Chrome headless với API giả lập xác minh tải lại khi đang chép, theo dõi tới READY mà không upload lại, tải lại bản chép READY và phần sửa cục bộ chưa xác nhận được thay bằng bản gốc. Đợt kiểm thử này không gọi dịch vụ thật.

## G6.5 — Gợi ý và giải thích câu AI

Ba ca MySQL với Gemini giả lập đã đạt. Gửi trùng trả kết quả hoặc trạng thái hiện có, yêu cầu nhầm buổi hoặc tài khoản khác trả 404, tạo giải thích mới cho câu cũ trả 409. Timeout cần xác nhận khi gọi lại. Hạn mức được kiểm tra trước khi gọi. HINT và EXPLAIN giữ nguyên transcript và số lượt. Chrome headless với API giả lập xác nhận nghĩa tiếng Việt chỉ hiện khi bấm mở và cả gợi ý lẫn giải thích giữ phụ đề đóng. Giao diện đợi tải trạng thái giải thích trước khi nhận thao tác tạo lần đầu. Đợt này không gọi Gemini thật.

## G6.6 — Kết thúc và tổng kết

Bốn ca MySQL với Gemini giả lập đạt. Kiểm tra buổi chỉ có opening, lỗi lượt đầu, trao đổi thành công, và thành công rồi lỗi lượt cuối. Tin đã gửi vẫn đọc được, chỉ buổi đủ điều kiện được tạo phản hồi và tính hoàn thành. Bộ kiểm tra từ chối trích dẫn sai, tin học viên thuộc buổi khác và tin của AI. Kiểm tra nhóm lỗi lặp đếm theo số tin khác nhau. Chrome headless với API giả lập xác nhận nhãn nhóm góp ý, phần luyện tiếp và mục phát âm riêng. Điểm theo chỉ số còn hợp lệ vẫn hiện khi thiếu điểm tổng. Chất lượng góp ý của Gemini thật không được đánh giá trong đợt này.

## G6.7 — Lịch sử và xóa dữ liệu

Chín ca MySQL với Gemini/Azure giả lập đạt. Kiểm tra phân trang theo chủ sở hữu, xem buổi và phản hồi, lưu gợi ý một lần, xóa buổi giữ bản sao thẻ, xóa toàn bộ dọn thẻ và lượt ôn, từ chối truy cập chéo, quota và retry. Xóa trong lúc CHAT, chép lời hoặc Azure đang xử lý không khôi phục dữ liệu buổi. Nhánh dọn audio giữ tham chiếu tới tệp Azure đến khi worker xóa thành công. Chrome headless với API giả lập xác minh trang kế tiếp, quay về trang trước sau khi xóa mục cuối trang và trạng thái trống sau xóa toàn bộ.

## Bàn giao tích hợp G6

Đã kiểm thử MySQL cho buổi A2 với TEXT và bài Đổi hàng B1 với vai đảo trong VOICE, từ tạo buổi đến tổng kết. Các ca VOICE riêng bao phủ chép lời, tải lại nháp và xác nhận. Ca Chrome G6.3–G6.7 kiểm tra tương tác khi dùng fixture. Đợt này chưa thực hiện một lượt chạy thiết bị thật với micro và Gemini từ đầu đến cuối cho cả A2 và B1. Tiêu chí đúng vai, dễ hiểu và hợp lý của nhận xét cần được rà trên đầu ra thật. Người dùng đã báo thử dịch vụ thật thành công trước đó, chưa có biên bản đủ hai kịch bản cho đợt bàn giao này.

