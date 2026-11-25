# Bản thử Azure TTS đã ngừng sử dụng

G4 dùng Azure Speech **Free (F0)** theo [ADR 0014](../../decisions/0014-azure-speech-tts-g4.md). Bản thử gồm một câu A2 với `en-US-AriaNeural` và một đoạn B1 với hai giọng `en-US-GuyNeural` và `en-US-AriaNeural`. Audio đầu ra là MP3 mono 24 kHz, 48 kbps. [Tài liệu Azure TTS REST](https://learn.microsoft.com/en-us/azure/ai-services/speech-service/rest-text-to-speech)

Trong Azure Portal, mở tài nguyên Speech F0 → **Keys and Endpoint**. Lưu **KEY 1** và mã vùng của tài nguyên trong `.env` ở gốc repo bằng các biến `AZURE_SPEECH_KEY` và `AZURE_SPEECH_REGION`. Ví dụ, mã vùng cho Southeast Asia là `southeastasia`. [Mã vùng Azure Speech](https://learn.microsoft.com/en-us/azure/ai-services/speech-service/regions)

Từ gốc repo, kiểm tra cấu hình mà không gọi Azure.

```powershell
./experiments/g4/azure-tts.ps1
```

Kết quả `configured: true` xác nhận script đọc được khóa và mã vùng đúng định dạng. Khi đã xác nhận tài nguyên F0 và muốn thử thật, dùng Windows PowerShell 5.1 hoặc PowerShell 7.

```powershell
./experiments/g4/azure-tts.ps1 -Live
```

Một lần chạy gửi tối đa hai yêu cầu, không tự thử lại. Nếu cần chạy lại, giữ tổng đợt thử tối đa bốn yêu cầu. Script ghi độ trễ, dung lượng và đường dẫn MP3 trong `.runtime/g4-azure-tts-*/`. Kiểm tra câu A2 rõ ở 1× và 0,75× và đoạn B1 phân biệt được hai vai. Nghiệm thu backend G4.2 kiểm tra thêm việc phát lại cùng audio và giữ kín kịch bản trước nộp.

## Kết quả bản thử thật

Một lần chạy `-Live` trên Windows PowerShell 5.1 tạo thành công cả hai MP3 từ Azure. Dung lượng tệp được đối chiếu trên máy với kết quả script.

| Mẫu | Độ trễ nhận audio | Dung lượng |
| --- | ---: | ---: |
| A2 · Một câu | 822 ms | 19.296 byte |
| B1 · Đoạn hai vai | 397 ms | 62.352 byte |

Bằng chứng này xác nhận Azure trả audio cho hai mẫu. Độ rõ ở 1× và 0,75×, khả năng phân biệt hai vai, phát lại cùng tệp và bảo vệ kịch bản trước khi nộp còn chờ kiểm tra. Chấm phát âm G5 có quyết định và tiêu chí kiểm chứng riêng tại [ADR 0015](../../decisions/0015-azure-pronunciation-assessment-g5.md).
