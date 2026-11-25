# Giai đoạn 0 — Kết quả thực hiện và phần còn lại

## Kết quả P0 của bản dựng sạch 46 bảng

Đối chiếu [P0.1–P0.6 trong kế hoạch hiện tại](../full-system-implementation-plan.md#p0--nền-tảng-và-kiểm-chứng-sớm). Kết quả kiểm tra ngày 24–25/11/2026 theo múi giờ Việt Nam.

**Đạt phần nền tảng.** Backend bootstrap và frontend khởi động được, V1 chạy trên đúng schema `english_conversation`, bộ kiểm tra P0 chạy lại được. **Nghiệm thu P0 còn mở** ở ma trận micro thật và kiểm chứng provider. Mốc này dựng hai bảng nền, các migration còn lại đi theo từng nhóm và được đối chiếu ở E2E.

| Mục | Kết quả lần này | Còn cần |
| --- | --- | --- |
| P0.1 | **Đạt.** Bản sao frontend sạch cài từ cache và nhận HTTP 200 `UP` qua proxy Vite. Lần chạy lại trên workspace đạt typecheck, lint và build. Khi thiếu `P0_DB_USERNAME`, startup báo đúng tên biến và test xác nhận thông báo lỗi không chứa secret giả lập. | Kiểm tra cấu hình triển khai cùng Session JDBC ở G1. |
| P0.2 | **Đạt V1 trên đích hiện tại.** Ngày 25/11, kiểm tra được `english_conversation` rỗng trước khi áp V1 từ `db/migration`. Hai ca MySQL đạt. Health trả HTTP 200, restart giữ schema và không chạy lại V1. Truy vấn sau kiểm thử xác nhận đúng script, hai bảng nền và không còn dòng fixture. [Kết quả MySQL](mysql.md). | Kiểm tra MySQL 8.4 và Session JDBC ở G1, đối chiếu đủ 46 bảng ở E2E. |
| P0.3 | **Đạt hợp đồng nền bằng fixture.** Năm ca health/API chạy lại đạt. MockMvc kiểm tra token CSRF, thiếu CSRF, lỗi JSON, validation và hợp đồng phân trang với fixture. Proxy Vite có bằng chứng từ lần khởi động bản sao sạch. | Kiểm tra thuộc tính cookie Session JDBC ở G1 và truy vấn phân trang trên catalog thật ở G2. |
| P0.4 | **Đạt một phần.** Micro thật trên Chrome 138 Windows thu rõ 8,16 giây và 6,78 giây, chuyển được WAV PCM 16-bit mono 16 kHz. Thử thủ công ghi nhận chưa cấp quyền thì không thu được, cấp quyền ở tab khác thì thu lại được. Hai giọng TTS đã phát hết qua hai lần thử. Kiểm tra tự động trên Chrome và Edge xác nhận chuyển mã, từ chối quyền và dừng track tone khi rời trang. [Bằng chứng và ma trận thiết bị](audio.md). | Hoàn tất ma trận micro thật và xác nhận tiếp khả năng provider nhận WAV. |
| P0.5 | **Đạt offline, chờ thử thật.** Tám test Java Gemini và bốn test Python đạt. Fixture bao phủ CHAT, TRANSCRIPTION, FEEDBACK, Azure SCRIPTED/UNSCRIPTED, thiếu trường, JSON lỗi, quota, timeout và không có tiếng nói. Tổng số lần gọi Gemini/Azure thật là 0. | Xác nhận phạm vi gọi, đo kết quả thật và dùng bằng chứng để chốt mức bàn giao phát âm ở G5/G6. |
| P0.6 | **Đạt phần chuẩn bị.** Maven `verify` ngày 25/11 chạy đạt 13 ca, gồm SMTP giả lập local. 55 ca MySQL opt-in được bỏ qua trong lệnh này, hai ca MySQL P0 đã chạy riêng. Bộ đánh giá có 32 kịch bản A2/B1, kiểm tra các mốc TTL và [phiếu dùng thử](../../research/usability-test.md) với mẫu số hoàn thành. | Tuyển người, chạy worker/restore thực tế và đo các chỉ số ở các mốc tương ứng trong kế hoạch. |

### Chạy lại bộ kiểm tra

- Tại `frontend/`, dùng Node 24 đúng phiên bản trong manifest, chạy `npm run typecheck`, `npm run lint` và `npm run build`. Chạy `npm run dev` và backend profile `bootstrap` để kiểm tra đường dẫn `/api/v1/system/health` qua proxy.
- Tại `backend/`, chạy `./mvnw.cmd -Dtest=PhaseZeroApiTest,HealthEndpointTest test`. Với MySQL, cấp `P0_DB_USERNAME` và `P0_DB_PASSWORD` cho schema mới rồi chạy `scripts/phase0/test-clean-db.ps1` từ thư mục gốc. Profile `bootstrap,p0-db` chạy JDBC/Flyway và health. Các API nghiệp vụ tiếp tục được giới hạn trong profile này cho tới khi triển khai migration và runtime tương ứng.
- Từ gốc, chạy `python experiments/phase0/offline_checks.py`. Bộ test Gemini chạy từ `experiments/phase0/gemini` bằng Maven `test`. Để mở probe audio, chạy `python -m http.server 18765 --bind 127.0.0.1 --directory experiments/phase0/audio` rồi mở `http://127.0.0.1:18765`. Biểu mẫu chỉ ghi metadata; chọn hai giọng local, thử nghe/dừng và sao chép JSON sau khi xác nhận bản ghi nghe rõ. Kiểm thử thiết bị Android cần một URL HTTPS có thể truy cập từ điện thoại để cấp quyền micro.

### Các ca cần hoàn thành để nghiệm thu

1. **Thiết bị thật.** Trên Chrome Windows, bổ sung ca đặt quyền **Chặn** và quan sát chỉ báo micro tắt khi rời trang đang thu. Chạy đủ ca cấp/từ chối quyền, thu, nghe lại và dừng khi rời trang trên Edge Windows và Chrome Android. Thử hai giọng TTS local nếu thiết bị có sẵn, kiểm tra nghe chậm và hủy phát. Ghi OS, phiên bản trình duyệt, mã codec, trạng thái micro và độ trễ TTS theo [ma trận](audio.md).
2. **Gemini thật.** Sau khi có phạm vi được cấp, dùng hội thoại hư cấu chạy CHAT A2, CHAT B1, một mẫu chép lời WAV và một mẫu FEEDBACK. Ghi model, số lần gọi, kết quả đúng vai và mức độ, cấu trúc phản hồi, độ trễ và usage nếu có. Có thể dùng tối đa bốn lần gọi ban đầu nếu được duyệt.
3. **Azure thật.** Sau khi có phạm vi được cấp, thử `en-US` ở hai chế độ bên dưới. Ghi region, định dạng đầu vào, thời lượng, kết quả theo từ/âm vị, điểm ngữ điệu nếu có và lỗi có thể gặp. Chốt tiêu chí bàn giao G5/G6 từ kết quả quan sát.

#### Mẫu thử Azure

Dùng cùng một mẫu WAV tiếng Anh hư cấu ngắn và một mẫu không có tiếng nói. Với `SCRIPTED`, gửi câu tham chiếu. Với `UNSCRIPTED`, bỏ câu tham chiếu. Xác nhận hỗ trợ IPA, điểm cấp từ/âm vị, điểm ngữ điệu tùy chọn và trường hợp dịch vụ trả thiếu dữ liệu. Đo thời gian và kết quả từng chế độ. Với file trên 30 giây, kiểm chứng chế độ nhận dạng liên tục và quy tắc đối chiếu lỗi đọc thiếu trước khi chọn cách tích hợp G5/G6. [Azure Pronunciation Assessment](https://learn.microsoft.com/en-us/azure/ai-services/speech-service/how-to-pronunciation-assessment)
