# Nghiệm thu G5 — Luyện phát âm

## Chạy local

Tại `backend`, chạy `mvn spring-boot:run`. Profile Maven mặc định `g6` áp tới Flyway V17 và gồm tính năng G5. Dùng schema cùng dòng V1–V12 hoặc schema mới. Tại `frontend`, chạy `npm run dev`. Đăng nhập rồi mở **Luyện phát âm** trong menu hoặc từ chi tiết một bài học.

Mặc định có thể nghe mẫu, thu và nghe lại trên trình duyệt. Trước khi bật chấm, xác nhận tài nguyên Speech cấp F0 hỗ trợ chấm theo mẫu và quota còn đủ. Dùng khóa và vùng của chính tài nguyên đó. Tạo khóa HMAC ngẫu nhiên ổn định riêng cho từng môi trường, tối thiểu 32 byte. Ghi các biến ở `.env` local.

```dotenv
PRONUNCIATION_AZURE_ENABLED=false
PRONUNCIATION_FINGERPRINT_KEY=
PRONUNCIATION_DAILY_REQUEST_LIMIT=6
PRONUNCIATION_PROVIDER_DAILY_LIMIT=6
# Dùng AZURE_SPEECH_KEY và AZURE_SPEECH_REGION đã cấu hình cho tài nguyên Speech.
```

Đặt `PRONUNCIATION_AZURE_ENABLED=true` khi chạy phiên nghiệm thu có phạm vi đã chốt, sau đó khởi động lại backend. Mỗi UTC ngày, mặc định tối đa sáu lượt gửi logic mỗi học viên và sáu lần gọi thực cho toàn ứng dụng, gồm retry. Cần giữ khóa HMAC ổn định để bộ đếm và chống trùng tiếp tục hoạt động khi restart. Triển khai nhiều backend phải dùng cùng MySQL và thư mục tệp tạm riêng dùng chung. Người học được thông báo trước khi upload sang Azure.

Tệp WAV giữ tối đa một giờ theo TTL, được dọn ngay khi lượt kết thúc và được quét sau lỗi dọn. Worker còn quét tệp mồ côi quá hạn. Lịch sử điểm lưu câu mẫu tại thời điểm chấm và phần kết quả đã lọc. Nhật ký từng lần gọi hết hạn sau 30 ngày. Xóa dữ liệu học tập hoặc tài khoản dọn bản ghi và lượt phát âm. Có thể dùng `PRONUNCIATION_AUDIO_DIRECTORY` để chỉ định vùng lưu riêng.

## Kiểm chứng kỹ thuật ngày 25/11/2026

- Maven verify thành công: 24 kiểm thử chạy và đạt, 67 ca opt-in ngoài cấu hình của lần chạy được bỏ qua. Riêng G5 có 7 ca đạt. MySQL 9.2 kiểm chứng V1–V15 từ rỗng, nâng cấp V12–V15 giữ catalog/đề nghe và chạy lại không nạp trùng.
- Frontend typecheck, lint và build đều đạt. Chrome 138 trên Windows ở viewport 390 px xác minh mở bài, từ chối quyền, dừng mẫu Shadowing trước khi bật micro, tạo WAV từ thiết bị giả lập, hủy không upload và tắt track khi đổi trang. Ca này dùng micro và TTS giả lập, còn chờ kiểm tra G5 bằng thiết bị vật lý.
- Maven chạy ca phân tích kết quả IPA, điểm thiếu, điểm ngoài thang và kiểu lỗi từ do provider trả.
- `PronunciationMysqlTest` dùng hai schema mới trên MySQL local cổng 13306. Đặt `G5_MYSQL_TEST=true`, `G5_TEST_DB_URL`, `G5_UPGRADE_DB_URL` tới schema có mẫu tên `g5_test_<12 ký tự hex>` với URL `jdbc:mysql://127.0.0.1:13306/<schema>?connectionTimeZone=UTC`. Chạy `mvn "-Dtest=AzureAssessmentOutputTest,PronunciationMysqlTest" test`. Ca nâng cấp phải bắt đầu trên schema rỗng mới để tạo V12 và chứng minh đúng ba migration V13–V15.
- Ca HTTP–MySQL dùng provider giả lập. Kiểm tra Session/CSRF, ghi thử trùng đồng thời, đổi payload, truy cập chéo, MIME, PCM/mono, audio rỗng, audio quá 60 giây và đường giới hạn chấm 30 giây. Các fixture kiểm tra thiếu tiếng nói, retry quota, timeout, snapshot, lỗi dọn và kết quả tới muộn sau khi xóa.
- Điểm ngữ điệu tắt. Chế độ chấm liên tục cho đoạn trên 30 giây chờ phạm vi riêng. Các mẫu đoạn ngắn nên được người rà đọc thử để xác nhận thời lượng.

## Bản thử F0 và người rà tiếng Anh

Tài liệu Microsoft công bố hạn mức Speech-to-Text thời gian thực cho F0 và mô tả nhóm điểm cơ bản của Pronunciation Assessment. Cần xác minh khả năng dùng đúng SKU/vùng của tài nguyên, hạn mức còn lại và chi phí áp dụng trước buổi thử. Giới hạn tối đa 6 lần gọi thực cho toàn bộ bản thử, tính cả lần thử lại. Mẫu âm thanh do người thử đồng ý cung cấp hoặc giọng tổng hợp cục bộ, chỉ dùng câu hư cấu. Từ chối WAV im lặng hoàn toàn ở phía backend là một ca kiểm chứng riêng; để tạo NoMatch từ Azure có thể dùng WAV chỉ chứa tiếng nền. [Hạn mức F0](https://learn.microsoft.com/en-us/azure/ai-services/speech-service/speech-services-quotas-and-limits) · [Cơ chế và dữ liệu chấm](https://learn.microsoft.com/en-us/azure/ai-services/speech-service/how-to-pronunciation-assessment).

| Mẫu | Câu tham chiếu | Bản ghi cần thử | Dữ liệu rà |
| --- | --- | --- | --- |
| Đọc đúng | How are you? | Đọc đủ ba từ, tốc độ tự nhiên | Nhận dạng, thứ tự từ và IPA có hợp lý không |
| Sai âm mục tiêu | Hello. | Đọc lệch nguyên âm đầu, đọc phần còn lại rõ | So nhận xét của người rà và IPA/điểm Azure cho từ |
| Bỏ từ | How are you? | How you? | Kiểm tra nhãn bỏ sót trên từ `are` |
| Thêm từ | How are you? | How are you today? | Kiểm tra nhãn nói thêm trên `today` |
| Chỉ tiếng nền | How are you? | Tiếng nền nhỏ, không có lời nói | Kiểm tra thiếu bằng chứng và để trống điểm |
| Đổi tốc độ | Bài đoạn ngắn B1 | Đọc nhanh hoặc chậm với các khoảng nghỉ | Rà đủ lời và ghi chỗ bị cắt trong chế độ ngắn |

Với mỗi mẫu, ghi thời lượng WAV, locale/phiên bản cấu hình, số lần gọi, độ trễ, mã trạng thái, từ/âm mục tiêu, nhận xét của người rà và kết luận khớp/lệch/thiếu dữ liệu. Kiểm tra độ đúng của lời nhận dạng riêng với mức hữu ích của phản hồi phát âm. So điểm giữa các lượt có cùng chế độ, locale và phiên bản chấm. Giữ bản ghi và dữ liệu nhạy cảm ngoài Git.

**Trạng thái live.** Chờ xác minh tài nguyên F0 dành cho Pronunciation Assessment và buổi rà mẫu được phép. Bằng chứng Azure TTS của G4 chỉ áp dụng cho tạo MP3. Chưa có căn cứ để nghiệm thu chất lượng điểm Azure hoặc kết luận về phát âm của học viên.
