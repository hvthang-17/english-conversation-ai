# ADR 0014 — Azure Speech TTS cho G4

**Trạng thái.** Đã chốt nhà cung cấp cho G4. Quyết định này thay thế phần TTS của [ADR 0013](0013-replace-azure-speech.md). G5 có quyết định chấm phát âm riêng tại [ADR 0015](0015-azure-pronunciation-assessment-g5.md).

- Bản đầu dùng tài nguyên Azure Speech **Free (F0)** để tổng hợp kịch bản luyện nghe `en-US` từ backend. Định dạng MP3 mono 24 kHz, 48 kbps. Khóa và mã vùng được cấp qua môi trường hoặc `.env` local.
- Đề nhiều vai lưu các lượt và mã giọng trong `script_segments_json` để backend tạo SSML. `transcript_en` dùng để hiển thị theo `AFTER_SUBMIT`.
- Tạo MP3 trước khi công khai, giữ tệp phía máy chủ theo phiên bản đề và phát qua API kiểm tra quyền. Dùng lại tệp khi nghe ở 1× hoặc 0,75×, tải lại, đổi thiết bị và làm lại. Giữ audio phiên bản cũ để hỗ trợ lượt đang làm.
- [Bản thử Azure TTS](../development/experiments/g4-azure-tts.md) có hai mẫu hư cấu. Kiểm tra offline và kiểm chứng bằng Azure thật là hai bước riêng. Tích hợp backend G4.2 có tiêu chí nghiệm thu riêng.
