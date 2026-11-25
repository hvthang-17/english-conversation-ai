# Conversation

Các component trong `components/`: PracticeChat điều phối TEXT/VOICE, lượt gửi/gợi ý, retry thao tác và kết thúc buổi; AssistantMessage sở hữu nghe/phụ đề từng câu; MessageExplanation sở hữu giải thích; VoiceTurn giữ quy trình micro, nghe lại, upload chép lời và xác nhận. `types.ts` giữ PracticeMode. Phát giọng đọc dùng `shared/hooks/useSpeech.ts`, chuyển WAV dùng `shared/lib/audio/recording.ts`; CSS hội thoại ở `styles/practice-chat.css`. [Quy chuẩn UI](../../../../docs/development/interface-design-standard.md), [API hội thoại](../../../../docs/api/practice.md), [API audio](../../../../docs/api/voice.md).

## Thiết kế 25/11/2026

Giữ palette nền tảng, teal, Be Vietnam Pro và icon chung. So sánh header nhiều đoạn thông tin trên transcript với vùng vai/mục tiêu bên cạnh: ban đầu chọn cột phụ, sau rà soát ảnh thực tế chuyển sang khối vai/mục tiêu phía trên. Hội thoại, vùng trả lời và nhận xét cùng một cột tối đa 960px, căn giữa vùng làm việc. Desktop chia thông tin trong khối bối cảnh theo chiều ngang; mobile xếp gọn, vai trò vẫn hai cột. Không giữ cột trống bên trái khi đọc transcript dài.

```text
[Tên tình huống / A2-B1 / Chế độ / Số lượt]
[Vai / Mục tiêu / Bối cảnh]
[Lượt AI]
[                    Lượt của bạn]
[Ghi âm hoặc câu trả lời]
[Gợi ý / Kết thúc]
[Nhận xét cuối buổi khi COMPLETED]
```

Tin AI dùng surface/viền, người học dùng primary-soft; có nhãn người nói, độ dài dòng giới hạn và chỉ style li trực tiếp để không tác động danh sách giải thích/IPA. Tự phản biện: tránh bubble xanh lá ngoài theme, metadata nối dấu chấm và trang trí robot/waveform; chuyển sang token và cấu trúc có ý nghĩa. Composer có viền rõ, gửi là CTA; thao tác phụ nằm ngoài. Không ghim vùng trả lời để tránh che focus hoặc nội dung khi bàn phím ảo mở.

VOICE giữ phụ đề đóng theo từng câu, không render chữ trong nội dung, aria-label hoặc live region khi chưa mở. Câu AI mới không kế thừa trạng thái phụ đề cũ. Nghe chậm/phát lại không đổi phụ đề. Gợi ý nằm ngoài transcript; giải thích có vùng hỗ trợ riêng, nghĩa Việt chỉ hiện theo yêu cầu.

VoiceTurn làm rõ thông báo dữ liệu, nút ghi/dừng, bộ đếm không live đọc từng giây, nghe lại và xác nhận. Không đổi giới hạn, hợp đồng, request ID, polling, cleanup, TTL hay quyết định đánh giá phát âm. Bản chép chưa xác nhận vẫn chưa phải tin nhắn. CSS feedback vẫn do feature feedback sở hữu.

## Kiểm chứng

TypeScript, ESLint và Vite production build chạy thành công. Playwright dùng API mock và âm thanh oscillator trong bộ nhớ, không thu giọng cá nhân hoặc gọi Gemini/Azure thật:

- TEXT: gửi lỗi giữ draft, gửi lại dùng cùng clientRequestId, xóa ô nhập sau khi được chấp nhận; gợi ý nằm ngoài transcript.
- Giải thích có tiếng Anh đơn giản, nghĩa Việt mặc định ẩn và mở theo yêu cầu. Fixture ban đầu trả NOT_STARTED sau POST nên kết quả mất; sửa mock để giữ kết quả đúng hợp đồng, không sửa logic ứng dụng vì lỗi fixture.
- VOICE: phụ đề vắng khỏi DOM trước mở, ẩn lại gỡ chữ; câu AI tiếp theo mặc định ẩn. Kiểm tra mở bằng bàn phím và nhập bàn phím được focus khi chọn fallback.
- Từ chối micro; thu/dừng/nghe lại bằng âm thanh giả; các nút playback bị khóa khi thu; track đã dừng sau thu. Upload tới mock trả READY; sửa bản chép rồi xác nhận gửi đúng content và assessPronunciation=false khi chức năng tắt. Không có LEARNER trước xác nhận.
- UNKNOWN khóa retry đến khi đánh dấu đồng ý, gửi acknowledgeUnknown=true. Kết thúc chuyển COMPLETED, bỏ composer và hiện phần nhận xét.
- TEXT không tràn ngang ở 320/360/768/1024/1366/1440px, VOICE ở 320/360/768/1024/1440px; tăng chữ gốc 200% trên VOICE mobile không tràn. Bật reduced motion, đã xem ảnh desktop/mobile.
- Ảnh cục bộ ở .playwright-mcp/conversation-text-desktop.png, conversation-text-mobile.png, conversation-voice-desktop.png, conversation-voice-mobile.png và conversation-voice-confirm-desktop.png tại gốc repo.

Chưa nghiệm thu micro/giọng đọc vật lý, chất lượng provider, toàn bộ screen reader, bàn phím ảo và mọi nhánh lỗi/TTL/polling. Mock không chứng minh tích hợp backend hoặc chất lượng AI. Không thêm dependency, cấu hình hay migration; không tự commit/push.


## Sửa căn lề theo ảnh desktop

Bỏ cột context 240px và quy tắc riêng max-width 780px/margin-left auto của feedback. Cả transcript và nhận xét dùng chung trục và chiều rộng; không khóa cuộn hoặc cắt nội dung. Kiểm tra mock buổi COMPLETED ở 1536×695, 1366×650, 1440×900, 768px, 360px và 320px: chênh lệch mép trái và chiều rộng giữa practice-main/session-feedback đều 0px, không tràn ngang. Đã xem ảnh .playwright-mcp/conversation-aligned-desktop.png; ảnh mobile cùng tên đuôi mobile. Thay đổi chỉ CSS; không đổi request hoặc trạng thái nghiệp vụ.
