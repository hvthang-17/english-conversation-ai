# ADR 0013 — Thay Azure Speech

## Trạng thái và bối cảnh

**Đã được thay thế.** Xem [ADR 0014](0014-azure-speech-tts-g4.md) cho TTS và [ADR 0015](0015-azure-pronunciation-assessment-g5.md) cho phát âm. Các mục dưới ghi lại quyết định ở thời điểm thử Kokoro.

Ngày 25/11/2026, người dùng yêu cầu thay Azure sau khi không đăng ký được Free Account và email trường chưa được hệ thống xác minh nhận diện. **Đã chọn thay TTS bằng Kokoro local; phương án thay bộ chấm phát âm còn chờ chốt mức đầu ra.** Không yêu cầu chuyển sang Pay-as-you-go.

## Quyết định TTS

Phần này lưu quyết định tạm thời về Kokoro. [ADR 0014](0014-azure-speech-tts-g4.md) là quyết định TTS hiện hành cho G4.

- Thay phần Azure TTS của ADR 0012 bằng Kokoro-82M, tổng hợp ở phía máy chủ, tiếng Anh Mỹ, WAV PCM16 mono 24 kHz. Giữ cache theo phiên bản đề và chính sách AFTER_SUBMIT của G4.
- Probe tách biệt dùng Python 3.11 và kokoro 0.9.4, CPU. Chưa thêm dependency vào backend/frontend. Model/thư viện Apache-2.0; kiểm tra dependency bắc cầu trước đóng gói. Cần tải model và tài nguyên ở lần thiết lập đầu. Không dùng API demo công cộng làm hạ tầng ứng dụng.
- G3 và phát câu AI G6 tiếp tục dùng giọng cục bộ trình duyệt theo thiết kế hiện tại.
- Bỏ yêu cầu AZURE_SPEECH_KEY/REGION khỏi cấu hình mẫu. Probe Azure cũ ngừng hoạt động. [Bản thử thay thế](../development/archive/g4-local-tts.md).

## Bộ chấm phát âm

[ADR 0015](0015-azure-pronunciation-assessment-g5.md) đã chốt Azure Pronunciation Assessment cho G5. Phần bên dưới ghi bối cảnh trước khi chốt, còn chấm lời nói tự do G6 vẫn đang mở.

Không coi Kokoro là mô hình nhận dạng hoặc chấm phát âm. G5 SCRIPTED và phần G6 UNSCRIPTED cần bộ chấm thay thế riêng. Gemini có thể phân tích audio và đưa nhận xét; tài liệu đó không chứng minh tương đương điểm 0–100 hoặc căn chỉnh từng âm vị của Azure. Không suy điểm phát âm từ độ khớp transcript và không tự bỏ tiêu chí điểm/âm vị đã chốt.

Đang chờ người dùng chọn giữa nhận xét audio Gemini (thay đổi tiêu chí nghiệm thu) và bộ chấm chuyên dụng khác (giữ tiêu chí). Trong thời gian đó, các nhiệm vụ phụ thuộc bộ chấm ở G5/G6 giữ trạng thái chờ; không tiếp tục coi Azure là cấu hình bắt buộc. Các ví dụ Azure cũ trong database/P0 là thiết kế/fixture tham khảo, không phải hợp đồng provider mới hoặc bằng chứng tích hợp.

## Giới hạn xác minh

Chỉ có probe và kiểm thử hợp đồng offline. Chưa chạy Kokoro thật, chưa tích hợp API G4.2 và chưa gọi Gemini thật. Không thay đổi migration hoặc dữ liệu đã lưu. Quyền gọi API thật và phạm vi chi phí vẫn cần được cấp riêng theo quy tắc dự án.
