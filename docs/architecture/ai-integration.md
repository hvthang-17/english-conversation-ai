# Gemini và âm thanh

G6 dùng Gemini ở backend cho hội thoại, gợi ý, giải thích, chép lời và nhận xét nội dung. P0 đã kiểm tra fixture offline. Model và kết quả provider cho bản dựng sạch còn chờ thử trong phạm vi được cấp. [Probe P0](../development/phase0/gemini.md).

## Các tác vụ
1. Hội thoại: tình huống, vai trò, trình độ, mục tiêu, lịch sử giới hạn và câu mới → câu AI.
2. Chép lời: audio phù hợp → văn bản để người học xem/sửa; không tự chuyển thành lượt hội thoại.
3. Phản hồi cuối buổi: hội thoại → summary, strengths, corrections, vocabulary, nextPractice.
4. Gợi ý HINT theo ADR 0005: bối cảnh hiện tại → gợi ý để người học tự trả lời; hiển thị tạm, không tạo lượt/tin nhắn trong transcript chính. Vẫn tính usage/quota AI.
5. Giải thích EXPLAIN: câu AI gần nhất và trình độ → cách nói tiếng Anh đơn giản và nghĩa tiếng Việt. Backend dùng JSON Schema, xác thực đầu ra và lưu theo câu trong `assistant_explanations`. Tác vụ dùng quota chung, phần giải thích nằm ngoài transcript và lịch sử gửi Gemini khi hội thoại tiếp. [Hợp đồng](../api/practice.md#giải-thích-câu-ai) · [Structured outputs](https://ai.google.dev/gemini-api/docs/structured-output)

Corrections cần câu gốc, câu đề xuất, loại lỗi/đề xuất diễn đạt và giải thích. Đối chiếu ID tin của học viên thuộc chính buổi và kiểm tra đoạn trích có trong tin nguồn. Cho phép danh sách lỗi rỗng. Không suy ra điểm phát âm/CEFR từ bản chép lời.

## Đầu vào và đầu ra
System instruction thuộc server; nội dung người dùng là dữ liệu. Không cho hội thoại thay đổi quyền truy cập, đọc secrets, gọi công cụ hoặc sửa database. Validate cấu trúc, giới hạn và kiểu dữ liệu đầu ra; không thực thi mã hoặc render HTML thô từ AI.

Giới hạn tối đa 20 lượt LEARNER/buổi đã chốt tại ADR 0005; opening, HINT và retry không tính thêm. Giới hạn ký tự/token/context/usage phải cấu hình. Giới hạn audio 60 giây đã chốt cho giai đoạn giọng nói. Không tự cắt ngữ cảnh làm sai vai trò; chính sách rút gọn phải có kiểm thử.

## Lỗi và chi phí
Timeout và retry có giới hạn; không retry lỗi đầu vào/xác thực. Xử lý 429 theo hướng dẫn dịch vụ, tránh vòng lặp gọi mất phí. RequestId/idempotency do ứng dụng quản lý; không giả định nhà cung cấp chống trùng. Không ghi thành công khi chưa có kết quả hợp lệ.

Ghi model, tác vụ, thời gian, trạng thái và usage nếu API cung cấp. Chi phí chỉ là ước tính theo bảng giá và thời điểm, không coi số token là hóa đơn.

Thiết kế lưu trữ thống nhất tại [database](database.md) và [workflow](database.md#6-luồng-dữ-liệu): ai_requests là tác vụ logic, ai_attempts là từng lần gọi provider; không dùng bảng ai_request_logs cũ. conversation_turns/audio_submissions/session_feedback giữ FK tới tác vụ phù hợp. Không lưu prompt/response thô vào log. Timeout chưa rõ kết quả dùng UNKNOWN và xử lý có giới hạn; không tự coi là chưa phát sinh chi phí. Ước tính thiết kế dùng USD, chưa biết để NULL.

Phiên bản prompt chỉ lưu mã version trong log; bối cảnh phục vụ buổi được snapshot theo thiết kế session. Nguồn message của feedback phải kiểm tra cùng session, LEARNER và câu trích hợp lệ. Hoàn tất kết quả phải kiểm tra đối tượng chưa bị xóa; không tái tạo dữ liệu vì provider trả muộn.

## Âm thanh
Kiểm tra MIME/container/codec được trình duyệt tạo và model chấp nhận; kiểm tra kích thước và thời lượng ở server. Định dạng khai báo từ client không đủ bằng chứng. HTTPS phục vụ micro khi triển khai.

Thông báo dữ liệu gửi ngoài trước thao tác. Dọn tài nguyên local sau thành công/lỗi và định kỳ dọn tệp sót hết hạn. Nếu dùng upload phía nhà cung cấp, kiểm tra retention và thao tác xóa riêng; không hứa rằng xóa local đồng nghĩa xóa mọi bản sao bên ngoài.

G6 gửi WAV đã xác minh bằng inlineData trong generateContent. Tác vụ TRANSCRIPTION dùng cùng admission/quota với hội thoại, có prompt version riêng và lưu usage khi provider cung cấp; chi phí âm thanh để NULL khi chưa có đơn giá phù hợp. audio_submissions giữ transcript chờ xác nhận. Xóa buổi chuyển audio sang DELETION_PENDING, xóa transcript và gỡ session. Worker giữ storage_key đến khi file local đã dọn.

## Tài liệu chính thức cần kiểm tra khi triển khai
- [Gemini API](https://ai.google.dev/gemini-api/docs)
- [Audio understanding](https://ai.google.dev/gemini-api/docs/audio)
- [Structured output](https://ai.google.dev/gemini-api/docs/structured-output)
- [Pricing](https://ai.google.dev/gemini-api/docs/pricing)

Các liên kết là điểm tra cứu; chưa xác minh model/giá cụ thể cho runtime của dự án.

G6 áp dụng hai TTL độc lập cho audio và bản chép nháp, thông báo xử lý ngoài hệ thống và kiểm tra giới hạn bản ghi 60 giây. Matrix thiết bị, purge log và backup được theo dõi trong [chính sách dữ liệu và trình duyệt](../product/data-and-browser-proposal.md). Không lấy TTL local làm cam kết retention Google. Lần phase0 hiện tại chỉ chuẩn bị offline theo yêu cầu, không gọi API thật; xem [bản thử và hướng dẫn key](../development/phase0/gemini.md). Các kết quả chép lời và nhận xét thật cần ghi riêng sau khi chạy.

## G6 chép lời và chấm phát âm

Trong G6, TRANSCRIPTION bắt đầu sau khi học viên bấm gửi. Bản chép hợp lệ được giữ làm nháp để học viên xem và sửa. Sau xác nhận, nội dung đã xác nhận được lưu vào tin người học và dùng cho CHAT. Khi chấm phát âm được bật, ứng dụng mặc định yêu cầu Azure UNSCRIPTED en-US chấm audio gốc tối đa 30 giây và còn hạn sau xác nhận. Giọng câu AI dùng TTS tiếng Anh cục bộ. Chấm dùng hạn mức Azure riêng với Gemini. Lỗi chấm hoặc audio hết hạn không chặn CHAT. [ADR 0016](../decisions/0016-g6-direct-voice-submission.md)
