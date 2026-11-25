# Nghiệm thu G4 — Luyện nghe

## Dữ liệu và API đã kiểm tra

| Ca | Kết quả |
| --- | --- |
| MySQL 9.2 | Flyway dựng từ rỗng đến V12. Nâng riêng V10 lên V12 giữ học liệu và dấu trang. Chạy lại áp 0 migration. |
| Học liệu | Tám đề nháp A2/B1, một đề cho mỗi bài G2, 24 câu hỏi và đủ bốn dạng. Kiểm tra seed và ca mã câu trùng, thiếu đáp án, thứ tự hoặc giọng sai. |
| Công khai đề | Azure giả lập lỗi hoặc trả MP3 sai thì đề giữ DRAFT. Audio hợp lệ mới cho công khai. Công khai lại phiên bản ACTIVE không tổng hợp thêm. Đề ẩn vẫn phục vụ audio cho lượt cũ. |
| Chủ sở hữu và nháp | Session/CSRF, ADMIN công khai, chặn truy cập chéo. Hai thiết bị mở cùng đề nhận một lượt. Nháp cũ trả 409 và tải lại thấy bản mới. |
| Nộp và lịch sử | Backend chấm bốn dạng. Kiểm tra đúng, sai, bỏ trống và mã lạ. Nộp đồng thời cùng đáp án trả cùng kết quả. Đáp án khác sau nộp trả 409. Làm lại tạo lượt mới với phần kết quả ẩn. |
| Build | Maven package đạt. Chạy 19 ca, bỏ qua 65 ca opt-in thuộc phạm vi khác, không có lỗi. Frontend typecheck, lint và build đều đạt. |
| Chrome Windows | Viewport 390×844. Phát MP3 kiểm thử ở 0,75×, lưu nháp và tải lại, mô phỏng lỗi phát rồi tải lại audio, nộp 3/3, làm lại với phần lời ẩn, bỏ dở và xem lịch sử. Audio phát trong ca này là tệp fixture, dùng để kiểm tra cơ chế phát. |
| Danh sách đề công khai | API MySQL trả đề ACTIVE ngay cả khi tài khoản chưa có lượt. Đề nháp và phiên bản đã ẩn vắng khỏi danh sách. Kiểm tra trên Chrome với tài khoản mới cho thấy có thể mở đề trực tiếp từ menu Luyện nghe. |

MySQL kiểm thử chạy riêng ở `127.0.0.1:13306` vì dịch vụ MySQL92 trên cổng 3306 đang dừng và tài khoản thực thi thiếu quyền khởi động dịch vụ. Schema phát triển của người dùng chưa được nâng cấp trong lần kiểm thử này.

## Kiểm chứng Azure

Hai MP3 từ probe Azure thật đã được ghi trong [biên bản TTS](../experiments/g4-azure-tts.md#kết-quả-bản-thử-thật). Lần triển khai này dùng Azure giả lập trong test backend. Còn cần công khai đề bằng tài nguyên F0 trên môi trường phát triển, nghe lại để rà lời và giọng, rồi ghi nhận nghiệm thu G4.2.

## Chạy lại kiểm thử

Tạo hai schema riêng, rỗng, theo mẫu `g4_test_<12 ký tự hex>` trên MySQL local. Đặt tài khoản MySQL qua môi trường. Dùng `G4_MYSQL_TEST=true` với `G4_TEST_DB_URL` để chạy HTTP và `G4_UPGRADE_MYSQL_TEST=true` với `G4_UPGRADE_DB_URL` để thử nâng cấp. URL có dạng `jdbc:mysql://127.0.0.1:3306/<schema>?connectionTimeZone=UTC`. Các ca cũng chấp nhận instance thử riêng trên cổng 13306.

Tại `backend/`, chạy `mvn "-Dtest=ListeningRulesTest,ListeningMysqlTest,ListeningMigrationMysqlTest" test`. [API và quy trình công khai đề](../../api/listening.md)
