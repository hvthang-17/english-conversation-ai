# Bản thử Kokoro TTS cho G4

Tài liệu lưu lại thí nghiệm Kokoro. Cấu hình G4 hiện hành và lệnh thử nằm ở [Azure TTS cho G4](../experiments/g4-azure-tts.md).

G4 thay Azure Speech bằng Kokoro-82M chạy CPU ở phía máy chủ theo [ADR 0013](../../decisions/0013-replace-azure-speech.md). Không cần tài khoản, thẻ hoặc API key Azure. Model và thư viện Kokoro dùng Apache-2.0; kiểm tra thêm giấy phép dependency bắc cầu khi đóng gói triển khai. [Model và ví dụ chính thức](https://huggingface.co/hexgrad/Kokoro-82M) · [thư viện](https://pypi.org/project/kokoro/0.9.4/).

## Kiểm tra offline

Từ gốc repo, dùng Python 3.11:

```powershell
python experiments/g4/kokoro-tts.py
python -m unittest discover -s experiments/g4 -p "test_*.py" -v
```

Mặc định chỉ in kế hoạch hai mẫu hư cấu, không tải model, không import thư viện ML và không gọi API. Unit test dùng pipeline giả, không chứng minh chất lượng tiếng nói thật.

## Thiết lập và sinh audio local

```powershell
python -m venv .runtime/kokoro-venv
./.runtime/kokoro-venv/Scripts/python.exe -m pip install -r experiments/g4/requirements-kokoro.txt
./.runtime/kokoro-venv/Scripts/python.exe experiments/g4/kokoro-tts.py --generate
```

Đây là lệnh thiết lập dự kiến cho Python 3.11, chưa xác minh cài dependency và inference trên máy hiện tại. Cần Internet ở lần đầu để tải package, trọng số, giọng và tài nguyên G2P; không có lời gọi Azure hay dịch vụ tổng hợp trả phí. Chuẩn bị cache trước khi triển khai offline. Không coi thư viện cài thành công là đã có đủ model cache. Tiếng Anh ngoài từ điển có thể cần espeak-ng; nếu thiếu, kiểm tra cảnh báo và độ đầy đủ lời đọc trước nghiệm thu. Không tự cài công cụ hệ thống toàn máy.

Kết quả nằm trong `.runtime/g4-kokoro-tts-<id>/`. Hai tệp WAV PCM16 mono 24 kHz dùng `af_heart` cho A2, `am_michael` và `af_heart` cho hai vai B1, ngắt 250 ms giữa vai. WAV thay định dạng MP3 Azure cũ để tránh thêm dependency chuyển mã vào probe. File tạo dở được dọn nếu probe lỗi. Không đưa trọng số hoặc audio vào Git.

## Nghiệm thu và tích hợp

- Nghe kiểm tra đủ từ, phát âm rõ ở 1×/0,75× và phân biệt hai vai; ghi máy, trình duyệt, thời gian khởi tạo, thời gian tổng hợp, dung lượng.
- G4.2 còn cần adapter backend/worker, timeout tiến trình, giới hạn tài nguyên và kiểm thử API; script thử chưa phải tính năng người dùng.
- Backend tạo một lần theo phiên bản đề, giữ cùng file khi nghe lại hoặc đổi thiết bị. Chỉ phục vụ audio trước nộp; không gửi kịch bản xuống trình duyệt để đọc bằng Web Speech vì sẽ lộ lời giải.
- Nếu model thiếu hoặc sinh audio lỗi, trả lỗi và giữ đáp án nháp. Không tự chuyển sang dịch vụ trả phí.
- Chưa chạy inference thật hoặc đánh giá chất lượng; không ghi nghiệm thu G4.2 từ kết quả dry-run.
