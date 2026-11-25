# Nghiệm thu học liệu và ôn tập G3

## Rà nội dung

| Mẫu thử trước khi biên soạn sáu bài còn lại | Kết quả rà nội bộ |
| --- | --- |
| A2 · 101 Giới thiệu bản thân | Bốn lượt, có tên và sở thích. Câu hỏi ngắn, đúng vai hai người làm quen ở câu lạc bộ. |
| B1 · 105 Đặt phòng khách sạn | Năm lượt, có hai gói với điều kiện hủy khác nhau. Khách giải thích lý do chọn gói rồi xác nhận ngày và số khách. |

Bộ seed có 24 từ kèm IPA en-US, 16 mẫu câu và tám hội thoại 4–6 lượt. Mỗi mục có nghĩa và ví dụ Anh–Việt. Đã rà nội bộ độ khó, vai, mục tiêu và bản dịch cho hai mẫu rồi dùng cùng cấu trúc cho sáu bài còn lại. [Nguồn học liệu](../../../backend/src/main/resources/content/starter-language-materials.json).

## Kiểm tra đã chạy ngày 25/11/2026

| Ca | Kết quả |
| --- | --- |
| Flyway MySQL 9.2 | Schema riêng dựng mới đến V10. Nâng cấp một schema V8 có dấu và sở thích lên V10 bảo toàn ID bài và lịch sử V1–V8. Chạy lại Flyway thực hiện 0 migration và giữ ID học liệu. |
| Backend | Bộ Maven chạy 16 ca, 62 ca opt-in khác được bỏ qua. Lần chạy riêng thực hiện thêm 4 ca MySQL gồm nâng cấp V8 và hồi quy catalog G2. Hai lần đều không có lỗi. |
| Tính nhất quán | API kiểm tra tám bài, cấu trúc từng đoạn, bài ẩn, CSRF, lưu đồng thời, owner-only, quiz đúng/sai và gửi trùng, thiếu lựa chọn, xóa nguồn, xóa thẻ và xóa dữ liệu học tập giữ hồ sơ/lịch. |
| Frontend | `npm run typecheck`, `npm run lint`, `npm run build` đạt. |
| Chrome Windows, viewport 390×844 | Qua luồng mở bài A2/B1, kiểm tra hai nút phụ đề độc lập, lưu thẻ, lật bằng Space, lưu trạng thái, làm quiz, xem lịch sử và xóa. Accessibility tree có nhãn các phương án và câu hỏi. |
| Web Speech mô phỏng trên Chrome | Qua ca ưu tiên en-US, dùng một giọng en-GB cho hai vai, thiếu giọng, lỗi phát, nối câu, nghe lại, lặp, tốc độ 1×/0,75× và hủy khi chuyển bài. |
| Web Speech cục bộ trên Windows | Chrome 138 và Edge 158, mỗi trình duyệt báo ba giọng tiếng Anh cục bộ và phát sự kiện `start` cho một từ ở tốc độ 1×, giọng en-US. Đây là phép đo sự kiện của trình duyệt headless. |

Schema phát triển `english_conversation` trên `127.0.0.1:3306` đã áp V9 và V10. Đối chiếu trước và sau xác nhận giữ checksum V1–V8, ID bài và số tài khoản, hồ sơ, sở thích, dấu bài. Kiểm tra `mvn spring-boot:run` với profile mặc định trên cổng HTTP riêng cho thấy health `UP`, học liệu khả dụng và Flyway ở V10.

G3.4 đã được đánh dấu hoàn thành trong kế hoạch. Kiểm tra bàn phím và cây trợ năng Chromium đã chạy. Ca xác minh nhãn và thông báo bằng trình đọc màn hình thật chưa chạy.

### Kiểm thử bổ sung bằng trình đọc màn hình

Mở Sổ tay bằng bàn phím, di chuyển đến **Lật thẻ**, dùng Space để hiện nghĩa rồi chọn **Đã nhớ**. Kiểm tra trình đọc màn hình công bố trạng thái mở và thông báo đã ghi lượt ôn. Mở **Quiz chọn nghĩa**, chuyển giữa ba đáp án bằng phím mũi tên và nộp. Kiểm tra nhãn nhóm, từng lựa chọn và thông báo đúng/sai được đọc.

### Chạy lại ca MySQL

Tạo hai schema trống riêng trên MySQL local, đặt tên theo mẫu `g3_test_<12 ký tự hex>`. Dùng danh tính MySQL thử nghiệm qua `SPRING_DATASOURCE_USERNAME` và `SPRING_DATASOURCE_PASSWORD`. Đặt hai URL có dạng `jdbc:mysql://127.0.0.1:3306/<schema>?connectionTimeZone=UTC`.

| Biến | Ý nghĩa |
| --- | --- |
| `G3_MYSQL_TEST=true`, `G3_TEST_DB_URL` | Ca API, CSRF, Session JDBC và dữ liệu học tập. |
| `G3_UPGRADE_MYSQL_TEST=true`, `G3_UPGRADE_DB_URL` | Ca nâng cấp từ V8. Mỗi lần dùng schema trống. |

Tại `backend/`, chạy `mvn "-Dtest=SampleDialogueFormatTest,LessonMaterialsMysqlTest,StarterMaterialsMigrationMysqlTest" test`.

Hợp đồng và lỗi đọc tại [API học liệu và sổ tay](../../api/lesson-materials.md). Trình duyệt đọc văn bản tiếng Anh bằng giọng cục bộ. IPA dùng để tra cứu.
