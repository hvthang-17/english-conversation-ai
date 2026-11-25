# Bằng chứng và quy trình kiểm tra audio

## Micro thật trên bản dựng sạch ngày 24/11/2026

Chủ dự án kiểm thử trên Chrome 138 Windows và xác nhận nghe rõ bản ghi WebM/Opus dài **8,16 giây**. Probe chuyển bản ghi sang WAV PCM 16-bit mono 16 kHz và giải mã lại được với cùng thời lượng. Bản ghi được xử lý tại trình duyệt, `uploaded = false`. [Metadata micro thật](evidence/clean-build-chrome-microphone.json).

Trình duyệt báo có 3 giọng tiếng Anh local. Cùng một giọng `en-US` đã phát thành công 5 lần ở tốc độ `1`, trung vị thời gian bắt đầu **263 ms**, và 1 lần ở tốc độ `0.75`, thời gian bắt đầu **270 ms**. Đây là số đo thử local từ một thiết bị.

## Kiểm tra lại micro thật và TTS ngày 25/11/2026

Lúc 00:20 giờ Việt Nam, chủ dự án ghi và nghe rõ bản WebM/Opus dài **6,78 giây** trên Chrome 138 Windows. Probe chuyển âm thanh sang WAV PCM 16-bit mono 16 kHz và giải mã lại với cùng thời lượng. Bản ghi được xử lý tại trình duyệt, `uploaded = false`.

Probe ghi nhận 5 lần TTS bắt đầu với 3 giọng `en-US` trên thiết bị, ở tốc độ `1` và `0.75`. Bốn lần được hủy sau khi bắt đầu. Giọng số 2 phát hết ở tốc độ `0.75` với độ trễ bắt đầu **224 ms**. Kết hợp với lần thử ngày 24/11, đã có hai giọng phát hết và bằng chứng hủy phát trên Chrome Windows. [Metadata lần kiểm tra lại](evidence/chrome-windows-microphone-recheck.json).

## Kiểm thử trình duyệt tự động

Ngày 25/11/2026, Chrome 138 và Edge 158 headless trên Windows đều ghi tone WebM/Opus và giải mã lại WAV PCM 16-bit mono 16 kHz. Đặt quyền micro thành `denied` qua giao thức DevTools rồi gọi `getUserMedia` thu được `NotAllowedError`. Khi phát sự kiện `pagehide`, track tone kết thúc. Mỗi trình duyệt phát hai giọng TTS local ở tốc độ `1` và `0.75`, rồi hủy một lần phát. Hai ca dùng tone tổng hợp và xử lý tại trình duyệt. [Metadata ngày 25/11](evidence/desktop-browser-recheck.json) · [Lần kiểm tra Chrome ban đầu](evidence/clean-build-browser.json).

## Kiểm tra thủ công quyền micro

Theo ca thử Chrome Windows đang theo dõi, chủ dự án ghi nhận trang không thu âm khi chưa cấp quyền micro. Sau khi rời trang, mở probe ở tab khác và cấp quyền, có thể thu âm lại. Kết quả này xác nhận việc thu âm phụ thuộc vào quyền và có thể tiếp tục sau khi cấp quyền ở tab mới.

Để nghiệm thu ca chặn quyền, đặt quyền trang thành **Chặn** và lưu kết quả khi bấm ghi. Để nghiệm thu ca dọn micro, bắt đầu thu rồi điều hướng trong **cùng tab** mà chưa bấm **Dừng ghi**. Quan sát chỉ báo micro tắt trước khi mở hoặc cấp quyền ở tab khác.

## Ma trận nghiệm thu thiết bị thật

| Trình duyệt | Cấp quyền, ghi và nghe lại | Từ chối quyền, rời trang khi đang thu | TTS local |
| --- | --- | --- | --- |
| Chrome Windows | Đạt trên Chrome 138, gồm chuyển WAV. Đã ghi nhận chưa cấp quyền thì không thu được và cấp quyền ở tab khác thì thu lại được. | Chờ bằng chứng đặt quyền **Chặn** và quan sát chỉ báo micro tắt khi rời trang đang thu. | Đạt. Hai giọng phát hết qua hai lần thử. Đã thử tốc độ `1`, `0.75` và hủy phát. |
| Edge Windows | Chờ thử thật | Chờ thử thật | Chờ thử thật |
| Chrome Android | Chờ thiết bị và URL HTTPS | Chờ thiết bị và URL HTTPS | Chờ thiết bị. Ghi số giọng thực tế. |

## Chạy lại

Từ gốc repo, chạy:

```powershell
python -m http.server 18765 --bind 127.0.0.1 --directory experiments/phase0/audio
```

Mở http://127.0.0.1:18765. Dùng câu hư cấu và kiểm tra nghe rõ, MIME/codec, thời lượng và kết quả chuyển WAV. Đặt lại quyền của trang để thử **Chặn**, rồi cấp lại và rời trang khi đang ghi để kiểm tra chỉ báo micro tắt. Thử hai tốc độ, đổi giọng và hủy TTS nếu có hỗ trợ. Ghi OS, phiên bản trình duyệt, kết quả từ chối quyền và rời trang cùng JSON metadata. Với Android, dùng URL HTTPS có thể truy cập từ điện thoại. [Biên bản và điều kiện thử provider](README.md#các-ca-cần-hoàn-thành-để-nghiệm-thu).
