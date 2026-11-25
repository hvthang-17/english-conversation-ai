# API luyện phát âm

Các endpoint dùng Session JDBC. `POST` yêu cầu CSRF theo hợp đồng phiên hiện tại. Hồ sơ điểm cá nhân được trả với `Cache-Control: no-store`.

| Phương thức và đường dẫn dưới `/api/v1` | Hành vi |
| --- | --- |
| `GET /pronunciation/drills` | Trả `drills` đang công khai cùng `scoringAvailable`. Mỗi bài có `id`, `lessonId`, `lessonTitle`, `level`, `referenceText`, `locale`, `mode`. |
| `POST /pronunciation/drills/{id}/attempts` | Multipart `requestId` gồm 16–64 ký tự ASCII chữ, số, gạch ngang hoặc gạch dưới và `audio` loại `audio/wav`. Server lấy câu mẫu từ bài. Trả `202` cùng lượt chấm. |
| `GET /pronunciation/attempts/{id}` | Đọc trạng thái, câu mẫu snapshot, cấu hình chấm, lời nhận dạng và kết quả của chủ sở hữu. |
| `GET /pronunciation/attempts?page=0` | Lịch sử riêng, 15 lượt mỗi trang, giảm dần theo ID. Trả `attempts`, `page`, `hasNext`. |

WAV phải là PCM16 mono 16 kHz, có khung PCM và không hoàn toàn im lặng. Giới hạn định dạng cho bản ghi là 60 giây. Bộ chấm ngắn nhận tới 30 giây. Bản thu hợp lệ trên 30 giây nhận `422 PRONUNCIATION_SHORT_AUDIO_REQUIRED`. Bản ghi rỗng hoặc sai định dạng nhận `400`, bản vượt giới hạn nhận `413`. Gửi lại cùng ID, bài và WAV trả cùng lượt. Dùng lại ID cho bài hoặc audio khác nhận `409 PRONUNCIATION_REQUEST_CONFLICT`. Lượt đang xử lý của cùng người dùng trả `409 PRONUNCIATION_BUSY` khi gửi bản khác.

| Trạng thái | Ý nghĩa |
| --- | --- |
| `PENDING`, `RUNNING` | Chờ hoặc đang chấm. UI đọc lại chi tiết qua GET. |
| `SUCCEEDED` | Trả `overall`, `accuracy`, `fluency`, `completeness` trong thang 0–100. Điểm thiếu là `null`. |
| `INSUFFICIENT_EVIDENCE` | Không đủ lời nói. Điểm để trống, giao diện hướng dẫn thu lại. |
| `FAILED` | Lỗi cuối, có thể thu mới. Quota tạm thời từ Azure được thử thêm một lần nếu còn ngân sách. |
| `UNKNOWN` | Không xác định được kết quả, ví dụ timeout. Worker kết thúc lượt và giao diện cho thu mới. |

`words` gồm từ, điểm, loại lỗi, mốc thời gian theo mili giây và `phonemes` chứa âm vị IPA kỳ vọng cùng độ chính xác nếu được trả về. Nhãn **Bỏ sót**, **Nói thêm**, **Cần luyện thêm** lần lượt ánh xạ từ Azure `Omission`, `Insertion`, `Mispronunciation`. Điểm mô tả kết quả của bộ chấm với câu tham chiếu, tránh diễn giải thành phần trăm phát âm đúng tuyệt đối. Kiểm thử Live và giới hạn vận hành xem [nghiệm thu G5](../development/acceptance/g5-pronunciation-acceptance.md).
