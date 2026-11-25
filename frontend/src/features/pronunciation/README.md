# Pronunciation

Giao diện luyện đọc theo mẫu và nói đuổi dùng Session/CSRF client, speech playback và `usePronunciationRecorder` hiện có. [API](../../../../docs/api/pronunciation.md) sở hữu giới hạn và trạng thái; [quy chuẩn UI](../../../../docs/development/interface-design-standard.md) sở hữu hướng thiết kế. Không gọi Azure trực tiếp từ trình duyệt.

## Thiết kế 25/11/2026

Giữ sáu màu nền tảng, teal và Be Vietnam Pro theo quy chuẩn. So sánh trang dài tuyến tính với khu luyện và lịch sử song song: chọn hai cột trên desktop từ 1200px, một cột ở màn hình nhỏ. Câu mẫu tiếng Anh trên nền teal nhạt là điểm nhấn duy nhất; không thêm waveform giả, điểm minh họa hoặc huy hiệu năng lực. Các lớp nội dung còn lại dùng chữ và khoảng cách để phân cấp.

```text
Desktop: [Chọn bài             | Lịch sử]
         [Mẫu / nghe / ghi âm  |        ]
         [Kết quả khi mở       |        ]
Mobile: Chọn bài → luyện → kết quả → lịch sử.
```

`pronunciation.css` sở hữu kiểu tính năng, thay CSS cũ trong global.css. Giữ vòng đời microphone, hủy upload, chống trùng, polling và quy tắc gửi chấm. Nghe từng câu mở bằng details native để đoạn dài không luôn chiếm vùng nhìn. Nút thu là CTA trước khi có bản ghi; gửi chấm là CTA ở vùng nghe lại. Giới hạn thu 60 giây, gửi chấm tối đa 30 giây và thông báo chuyển dữ liệu tới Azure tiếp tục hiển thị.

Kết quả dùng nhóm điểm 0–100 với nhãn, câu mẫu và lời nhận dạng tách riêng, nhận xét theo từng từ/IPA. Điểm null hiển thị “Chưa có điểm”. Nhãn Bỏ sót/Nói thêm/Cần luyện thêm không chỉ dựa vào màu. Ghi rõ điểm so với câu tham chiếu, không phải chứng nhận hoặc tỷ lệ phát âm đúng tuyệt đối. Chưa đủ bằng chứng hoặc lỗi không hiển thị nhóm điểm thành công.

## Kiểm chứng

TypeScript, ESLint và Vite production build chạy thành công. Browser dùng API mock và âm thanh tổng hợp từ oscillator trong bộ nhớ; không thu tiếng nói cá nhân, không gọi Azure/Gemini thật:

- Chọn bài đọc/nói đuổi, mở từng câu bằng bàn phím, mở kết quả từ lịch sử, hiển thị điểm thiếu và nhận xét theo từ.
- Từ chối quyền microphone; thu/dừng tạo bản nghe lại, xác nhận các track đã dừng; gửi multipart tới mock và trở lại trạng thái sẵn sàng.
- Catalog rỗng/lỗi, dịch vụ chấm chưa khả dụng, kết quả INSUFFICIENT_EVIDENCE không có nhóm điểm.
- Không tràn ngang khu luyện ở 320/360/768/1024/1366/1440px; kết quả tăng chữ gốc 200% trên mobile không tràn. Đã xem ảnh desktop/mobile; nội dung dài và kết quả vẫn cuộn tự nhiên.
- Ảnh cục bộ tại `.playwright-mcp/pronunciation-practice-desktop.png`, `pronunciation-result-desktop.png`, `pronunciation-mobile.png` ở gốc repo.

Chưa xác minh chất lượng giọng đọc cục bộ, microphone vật lý, chấm Azure thật, toàn bộ screen reader hoặc mọi trạng thái polling/quota. Kết quả mock không chứng minh tích hợp backend/provider hoặc chất lượng chấm. Không thêm dependency, cấu hình hay migration.
