# API học liệu và sổ tay G3

Tiền tố `/api/v1`. Các API dùng Session cookie. Thao tác ghi cần CSRF từ `GET /auth/csrf`. ID nghiệp vụ trả về dạng chuỗi.

| API | Kết quả |
| --- | --- |
| `GET /lessons/{id}/materials` | Trả `items` và `dialogues` theo thứ tự biên soạn. Bài ẩn hoặc thuộc chủ đề ẩn trả 404. |
| `GET /learning/notebook?status=&page=0&size=12` | Trang thẻ của người đăng nhập. Có thể lọc `NEEDS_REVIEW` hoặc `REMEMBERED`. |
| `POST /learning/notebook/from-language-item/{id}` | Lưu bản sao từ nguồn đang công khai. Gửi lại trả thẻ đã có của cùng chủ sở hữu. |
| `DELETE /learning/notebook/{id}` | Trả 204 và xóa các lượt ôn của thẻ. |
| `GET /learning/notebook/{id}/attempts?page=0&size=12` | Lịch sử ôn phân trang theo thời điểm, mới nhất trước. |
| `POST /learning/notebook/{id}/quiz` | Tạo câu hỏi có ba lựa chọn, mỗi lựa chọn có `id` và `meaningVi`. Trả thêm `id` câu hỏi, `expression`, `expiresAt`. |
| `POST /learning/notebook/{id}/attempts` | Chấm hoặc ghi lượt ôn. Trả `id`, `mode`, `result`, `correct`, `reviewedAt`. |
| `DELETE /learning/data` | Xóa dấu bài học, thẻ và các lượt ôn. Giữ tài khoản, hồ sơ và lịch học. |

Một mục học liệu có `kind`, `expression`, `ipa`, `meaningVi`, `exampleEn`, `exampleVi` và `savedCardId` theo tài khoản. Mỗi đoạn mẫu có tiêu đề và các lượt gồm `line_no`, `speaker_label`, `text_en`, `text_vi`, `voice_key` tùy chọn. Trình duyệt phát cục bộ từ `text_en`.

### Ghi lượt ôn

```json
{"mode":"FLASHCARD","result":"REMEMBERED","clientRequestId":"3d22326a-8f4b-4b12-a5c8-06a77dd813da"}
```

Flashcard chấp nhận `REMEMBERED` hoặc `NEEDS_REVIEW`, lưu lựa chọn và thời điểm ôn trong cùng giao dịch. Trường `correct` trả `null`.

```json
{"mode":"QUICK_QUIZ","clientRequestId":"<id câu hỏi>","optionId":"<id lựa chọn>"}
```

Câu hỏi có hiệu lực tối đa 10 phút trong phiên phát hành. Backend chấm từ lựa chọn, cập nhật thời điểm ôn và giữ trạng thái hiện tại của thẻ trong `result`. `correct` là đúng/sai. Khi nhận `QUIZ_EXPIRED`, học viên có thể mở câu hỏi mới. `QUIZ_UNAVAILABLE` nghĩa là chưa chọn được đủ phương án phân biệt. Học viên vẫn dùng được flashcard.

Gửi lại cùng mã và nội dung trả kết quả đã ghi. Đổi nội dung với mã đã ghi trả 409 `REQUEST_ID_REUSED`. Thẻ thuộc tài khoản khác trả 404 cho đọc lịch sử, tạo quiz, nộp hoặc xóa.
