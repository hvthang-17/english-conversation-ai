# API luyện nghe G4

Tiền tố `/api/v1`. Dùng Session và CSRF hiện có. ID trả về dưới dạng chuỗi.

| API | Hành vi |
| --- | --- |
| `GET /lessons/{id}/listening` | Trả đề ACTIVE của bài học và chủ đề đang công khai. |
| `GET /listening/exercises?page=0&size=12` | Danh sách đề công khai trên trang Luyện nghe, kể cả khi người học chưa có lượt làm. |
| `POST /listening/exercises/{id}/attempts` | Gửi `clientRequestId`. Mở lượt mới hoặc tiếp tục lượt `IN_PROGRESS` đang có của cùng phiên bản đề. |
| `GET /listening/attempts/{id}` | Trả đề, các câu hỏi công khai, đáp án của chính người học và `draftVersion`. `result` chỉ có khi đã nộp. |
| `GET /listening/attempts/{id}/audio` | Phát MP3 của phiên bản gắn với lượt. Browser có thể yêu cầu từng khoảng byte. |
| `PUT /listening/attempts/{id}/draft` | Lưu toàn bộ bản nháp nếu phiên bản còn hợp lệ và trả `draftVersion` mới. |
| `POST /listening/attempts/{id}/submit` | Chấm và khóa lượt. Nộp lại cùng đáp án trả kết quả đã lưu. |
| `POST /listening/attempts/{id}/abandon` | Chốt bỏ dở lượt đang làm, trả 204. |
| `GET /listening/attempts?page=0&size=12` | Lịch sử phân trang theo người đang đăng nhập. |
| `GET /admin/listening-exercises` | ADMIN đọc danh sách phiên bản. |
| `POST /admin/listening-exercises/{id}/publish` | ADMIN chuẩn bị MP3 và công khai đề. Nếu thành công, ẩn phiên bản trước của bài. |

Lưu nháp và nộp dùng `{"draftVersion":0,"answers":{"q1":"B","q2":"Hue","q3":["step1","step2"]}}`. Chọn đáp án gửi mã lựa chọn. Sắp xếp gửi mảng mã theo thứ tự. Điền và nghe–chép gửi văn bản. Bỏ trống bằng cách bỏ khóa tương ứng. Mã thuộc đề khác hoặc lựa chọn lạ trả 400.

Nháp cũ trả `409 LISTENING_DRAFT_CHANGED`. Nộp lại cùng đáp án trả kết quả ngay cả khi phiên bản nháp trong yêu cầu chưa thay đổi. Nộp khác đáp án sau khi khóa trả `409 LISTENING_ALREADY_SUBMITTED`. Tài khoản khác truy cập lượt trả 404. `result` sau nộp có tổng điểm, lời thoại, bản dịch, điểm từng câu, đáp án và giải thích. Lượt mới khi làm lại có `result: null`. Lượt cũ vẫn đọc được qua lịch sử.

Tám đề seed là bản nháp. ADMIN mở **Luyện nghe → Công khai đề nghe**. Bật `LISTENING_AZURE_ENABLED=true` cùng khóa và mã vùng của tài nguyên Speech F0 trước khi công khai. Tệp lưu tại `LISTENING_AUDIO_DIRECTORY` phía backend, mặc định `.runtime/listening` tính từ thư mục làm việc backend. Khi triển khai, đặt đường dẫn ngoài thư mục phục vụ tệp tĩnh và giữ tệp cùng phiên bản dữ liệu. Không chép tệp thử của một kịch bản khác vào kho audio đề.
