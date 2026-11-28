# API học liệu và sổ tay G3

Tiền tố `/api/v1`. Các API dùng Session cookie. Thao tác ghi cần CSRF từ `GET /auth/csrf`. ID nghiệp vụ trả về dạng chuỗi.

| API | Kết quả |
| --- | --- |
| `GET /lessons/{id}/materials` | Trả `items` và `dialogues` theo thứ tự biên soạn. Bài ẩn hoặc thuộc chủ đề ẩn trả 404. |
| `GET /learning/notebook?status=&page=0&size=12` | Trang thẻ của người đăng nhập. Có thể lọc `NEEDS_REVIEW` hoặc `REMEMBERED`. |
| `POST /learning/notebook/from-language-item/{id}` | Lưu bản sao từ nguồn đang công khai. Gửi lại trả thẻ đã có của cùng chủ sở hữu. |
| `DELETE /learning/notebook/{id}` | Trả 204 và xóa các lượt ôn của thẻ. |
| `POST /learning/notebook/{id}/attempts` | Chấm hoặc ghi lượt ôn. Trả `id`, `mode`, `result`, `correct`, `reviewedAt`. |
| `DELETE /learning/data` | Xóa dấu bài học, thẻ và các lượt ôn. Giữ tài khoản, hồ sơ và lịch học. |

Một mục học liệu có `kind`, `expression`, `ipa`, `meaningVi`, `exampleEn`, `exampleVi` và `savedCardId` theo tài khoản. Mỗi đoạn mẫu có tiêu đề và các lượt gồm `line_no`, `speaker_label`, `text_en`, `text_vi`, `voice_key` tùy chọn. Trình duyệt phát cục bộ từ `text_en`.

Mục học liệu và bản sao trong sổ tay trả thêm `partOfSpeech`. Trường có thể trống với mục chưa phân loại. Các mã gồm nhóm từ như `NOUN`, `VERB`, `ADVERB` và nhóm cấu trúc như `NOUN_PHRASE`, `PHRASAL_VERB`, `SENTENCE`. Admin đặt mã qua trường `part_of_speech` ở form học liệu.

### Ghi lượt ôn

```json
{"mode":"FLASHCARD","result":"REMEMBERED","clientRequestId":"3d22326a-8f4b-4b12-a5c8-06a77dd813da"}
```

Flashcard chấp nhận `REMEMBERED` hoặc `NEEDS_REVIEW`, lưu lựa chọn và thời điểm ôn trong cùng giao dịch. Trường `correct` trả `null`.

Sổ tay chỉ lưu/tra cứu/xóa mục. API tạo quiz và đọc lịch sử từng thẻ đã bỏ vì không còn nơi sử dụng. `QUICK_QUIZ` không nhận lượt mới (400 `INVALID_REVIEW_ANSWER`); mã mode vẫn giữ để đọc dữ liệu lịch sử cũ.

Gửi lại cùng mã và nội dung trả kết quả đã ghi. Đổi nội dung với mã đã ghi trả 409 `REQUEST_ID_REUSED`. Thẻ thuộc tài khoản khác trả 404 khi đọc chi tiết, nộp hoặc xóa.

### Ôn ngắt quãng

Trong profile `g6`, `GET /api/v1/learning/notebook/spaced?limit=20` trả thẻ đến hạn trước, sau đó là thẻ chưa từng chấm theo lịch. Giới hạn đợt từ 1–20 thẻ và tối đa 10 lượt chấm thẻ mới mỗi ngày theo múi giờ lịch luyện. Phản hồi gồm `dueCount`, `newCount`, `newEligibleCount`, `newRemainingToday`, `nextDueAt`, `nextLocalDayAt`, `timeZone` và `items`. Mỗi mục có `card`, `nextReviewAt` và `scheduleVersion`.

```json
{"mode":"SPACED_REVIEW","rating":"GOOD","scheduleVersion":0,"clientRequestId":"review-request-01"}
```

Nộp vào `POST /learning/notebook/{id}/attempts`. Rating nhận `AGAIN`, `HARD`, `GOOD` hoặc `EASY`. Kết quả có `rating`, `intervalDays`, `algorithmVersion`, `scheduleVersion` và `nextReviewAt`. Các mốc hẹn dùng UTC. Gửi lại nguyên yêu cầu trả đúng lượt đã ghi. Lịch đã đổi, thẻ chưa đến hạn và hết suất báo HTTP 409 với mã lần lượt là `REVIEW_SCHEDULE_CHANGED`, `REVIEW_NOT_DUE` và `NEW_REVIEW_LIMIT_REACHED`. Thẻ đã xóa hoặc của owner khác trả 404. Flashcard tự đánh dấu độc lập với lịch ngắt quãng. Các endpoint ghi lượt và hàng đợi phục vụ tab Ôn tập.

GET /api/v1/learning/notebook/{id} trả chi tiết thẻ của chủ phiên. Màn Hôm nay dùng endpoint này để mở đúng thẻ. Thẻ bị xóa hoặc thuộc tài khoản khác trả 404.


BR-11: route tạo quiz và GET lịch sử attempt từng thẻ đã bỏ không còn matcher cho phép; trả 403 qua denyAll khi đã xác thực. Mode QUICK_QUIZ gửi vào POST attempt hiện hành vẫn trả 400 INVALID_REVIEW_ANSWER. Route của feature chưa bật bị chặn trước matcher rộng; FLASHCARD và SPACED_REVIEW trong stage được hỗ trợ giữ nguyên payload.
