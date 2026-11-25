# ADR 0012 — Chốt ERD 46 bảng và phạm vi TTS

**Trạng thái: đã chốt.** TTS G4 nằm trong [ADR 0014](0014-azure-speech-tts-g4.md). Chấm phát âm G5 nằm trong [ADR 0015](0015-azure-pronunciation-assessment-g5.md).

## Quyết định

- Dùng [ERD 46 bảng](../architecture/database.md#2-erd-logic) làm thiết kế dữ liệu bản đầu cho 10 nhóm chức năng. Thiết kế khởi tạo gồm 46 bảng. Dùng [từ điển 46 bảng](../architecture/database.md#3-từ-điển-46-bảng-và-dữ-liệu-mẫu) để tra trường và dữ liệu mẫu.
- Học liệu công khai G3 dùng TTS tiếng Anh cục bộ trên trình duyệt, ưu tiên `en-US` rồi dùng giọng tiếng Anh cục bộ sẵn có. Trình duyệt đọc văn bản tiếng Anh. IPA dùng để tra cứu. `voice_key` tùy chọn, một giọng có thể đọc cả hai vai. Khi thiết bị thiếu giọng, người học vẫn xem được văn bản. Đề nghe G4 giữ quy tắc tổng hợp âm thanh phía backend theo chính sách hiển thị kịch bản. Dời kho audio, ảnh và video thu sẵn cùng các khóa ngoại liên quan sang giai đoạn sau.
- Bộ đề đầu G4 dùng Azure Speech Text to Speech, bắt đầu từ F0, giọng `en-US` và MP3 mono 24 kHz, 48 kbps. Lưu audio trên máy chủ theo phiên bản đề và phát lại cùng tệp. Backend chỉ công bố kịch bản theo chính sách `AFTER_SUBMIT`. [Bản thử và tiêu chí Azure TTS](../development/experiments/g4-azure-tts.md).
- Lưu cấu trúc đọc cùng bản ghi cha như câu hỏi nghe và chi tiết từ/âm vị trong JSON đã xác thực. Giữ cột quan hệ, quyền sở hữu, trạng thái và điểm tổng để kiểm tra và truy vấn.

## Hệ quả triển khai

Triển khai DDL từ [đặc tả schema khởi tạo](../architecture/database.md), theo từng nhóm chức năng. Bản dựng sạch dùng database phát triển mới, rỗng, và dòng migration Flyway mới bắt đầu từ `V1`. Khi chuyển nhánh hiện tại, bỏ migration cũ khỏi đường chạy và dọn bảng cũ trong database phát triển đã xác định. Xác nhận instance, schema và phạm vi xóa trước khi thực hiện. Dữ liệu ngoài schema phát triển này nằm ngoài phạm vi chuyển đổi.

### Đích MySQL phát triển cho P0.2

| Đối tượng | Đích đã ghi nhận |
| --- | --- |
| Instance phát triển | MySQL TCP `127.0.0.1:3306`, theo JDBC URL local đã đối chiếu. |
| Đích cấu hình của bản dựng sạch | `english_conversation` trên `127.0.0.1:3306`. Chỉ chạy dòng V1 mới khi schema rỗng hoặc đã có đúng lịch sử migration mới. |
| Schema đã dùng để kiểm chứng P0 | Probe ban đầu chạy trên `english_conversation_dev`. Ngày 25/11/2026, V1 và restart đạt trên `english_conversation`. [Bằng chứng](../development/phase0/mysql.md). |
| Xử lý dữ liệu cũ tại đích | Lần kiểm tra trước khi áp V1 mới thấy `english_conversation` rỗng. Nếu gặp schema có lịch sử cũ ở lần dựng khác, xác nhận riêng phạm vi và cách xử lý dữ liệu trước khi dọn. |

Instance MySQL portable `127.0.0.1:3307` của đợt kiểm thử trước nằm ngoài đích chuyển đổi này. [Các bước đối chiếu MySQL](../development/setup.md#đích-mysql-cho-bản-dựng-sạch).

Luồng thu âm của học viên tiếp tục dùng file tạm. Điểm phát âm dự kiến được lưu riêng với nhận xét nội dung hội thoại. Mức bàn giao chấm Azure được chốt theo kết quả P0. [Kế hoạch triển khai](../development/full-system-implementation-plan.md).
