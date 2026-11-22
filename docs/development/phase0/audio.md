# Bằng chứng và quy trình kiểm tra audio

## Đã thực hiện

### Tự động — không dùng micro
Chromium Headless 138 trên Windows: secure context, MediaRecorder/getUserMedia được phát hiện; hỗ trợ được khai báo cho WebM/Opus, WebM và MP4. Đã capture/decode âm tổng hợp WebM/Opus ở 48 kHz. Metadata tại .runtime/phase0/browser-audio-headless.json; không có file lời nói người dùng hoặc upload.

Lần thử 700 ms ban đầu tạo blob rỗng do clock/graph audio chưa có frame. Đã tái hiện, kiểm tra clock và timeslice, nối graph chạy ở âm lượng 0 và tăng thời gian thử tổng hợp lên 1.8 giây; capture/decode sau sửa đạt. Không dùng kết quả tổng hợp để khẳng định micro thật hoặc chép lời AI đạt.

### Micro thật — bằng chứng chủ dự án gửi
[Metadata gốc đã ghi nguồn](evidence/desktop-audio.json) do chủ dự án chạy trên máy tính và gửi trong chat, không phải agent trực tiếp quan sát micro:

| Thuộc tính | Kết quả |
| --- | --- |
| Browser | Chrome 138.0.0.0, Windows |
| Nguồn | PHYSICAL_MICROPHONE |
| Kết quả | CAPTURED_AND_DECODED |
| MIME | audio/webm;codecs=opus |
| Dung lượng | 181769 bytes |
| Thời lượng decode | 11.28 giây |
| Sample rate | 48000 Hz |
| RMS | Khoảng 0.04958, chỉ là tín hiệu năng lượng, không điểm phát âm |
| Nghe lại | Người dùng xác nhận có lời nói nghe rõ |
| Upload | false |

Đủ bằng chứng smoke test micro desktop sớm cho P0.5 theo ưu tiên của chủ dự án. Không suy ra Chrome stable mới nhất, Edge, Android, Safari, độ dài 60 giây, lời nói trong môi trường ồn hoặc Gemini đã được kiểm thử. Browser matrix mục tiêu vẫn cần kiểm tra ở giai đoạn 6, đặc biệt bản hiện hành và một major trước theo chính sách đã chấp nhận.

## Chạy lại
Từ gốc repository:

```powershell
python -m http.server 8765 --bind 127.0.0.1 --directory experiments/phase0/audio
```

Mở http://127.0.0.1:8765/ bằng Chrome/Edge trên máy tính. Trang chỉ phục vụ thư mục thử nghiệm, không root .env; CSP chặn connect ra ngoài.

1. Bấm kiểm tra âm tổng hợp nếu muốn kiểm tra codec mà chưa cấp micro.
2. Bấm Bắt đầu ghi âm thật, cấp quyền và đọc câu mẫu không cá nhân.
3. Dừng, nghe lại và chỉ đánh dấu nghe rõ nếu đúng.
4. Sao chép JSON metadata nếu cần báo cáo; không cần gửi file audio. Đóng tab/xóa để bỏ bản ghi khỏi bộ nhớ.

Không tự upload; chỉ yêu cầu micro sau thao tác của người dùng. Giới hạn local 60 giây. Dừng server bằng Ctrl+C khi xong. Trang này là công cụ thử riêng, không tích hợp vào giao diện sản phẩm.

## Kết luận codec hiện tại
WebM/Opus là **candidate desktop có bằng chứng ghi/decode**. MIME provider và khả năng chép lời phải thử riêng khi được phép live; không tự gắn nhãn tương thích end-to-end. Chuẩn bị thêm fixture WAV tổng hợp local cho probe AI, không ép mọi browser phải xuất WAV.
