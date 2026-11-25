# Chính sách thời hạn dữ liệu, thông báo và trình duyệt

## Trạng thái
Chính sách được chấp nhận tại ADR 0006. G6 đã triển khai dọn audio và transcript nháp. Nhật ký lần gọi Pronunciation Assessment có cleanup 30 ngày. Backup và purge toàn bộ metadata AI vẫn thuộc kế hoạch vận hành. Xem [ADR 0006](../decisions/0006-phase0-operating-decisions.md) và [Kịch bản nghiệm thu hội thoại](../development/chat-acceptance/README.md).

## 1. Thời hạn giữ dữ liệu

| Dữ liệu | Chính sách đã chốt | Lý do/phạm vi |
| --- | --- | --- |
| Audio gốc | Dọn khi hủy, xử lý thất bại hoặc hoàn tất các tác vụ cần audio; hạn an toàn tối đa 1 giờ từ upload | Chỉ phục vụ xử lý; không cần giữ để sửa transcript hoặc tiếp tục chat |
| Transcript chưa xác nhận | Xóa khi xác nhận/hủy/kết thúc buổi; nếu bỏ dở thì hết hạn sau 30 phút kể từ lúc READY | Người học có thời gian đọc/sửa, không giữ nháp lâu dài |
| Transcript đã xác nhận | Nội dung cuối nằm trong messages như tin nhắn văn bản; giữ tới khi người học xóa lịch sử | Không nhầm thời hạn nháp với lịch sử đã lưu |
| Log AI | Giữ metadata của tác vụ/lần gọi 30 ngày sau khi kết thúc; dọn theo lịch hằng ngày | Đủ phục vụ đánh giá độ trễ/lỗi/usage; không chứa prompt/response/audio thô |
| Backup | Một bản database mỗi ngày, mã hóa, giữ theo tuổi bản sao tối đa 7 ngày; không backup audio gốc | Quy mô đồ án, giảm dữ liệu dư thừa và chi phí |

Thời hạn là cấu hình, không hard-code. Với log đang RUNNING/UNKNOWN, phải đối soát hoặc đóng trạng thái có kiểm soát; không xóa tracking tác vụ còn hoạt động chỉ vì đủ 30 ngày.

Purge log phải xử lý FK nullable ai_request_id trong turn/audio/feedback và ai_attempts theo thứ tự phù hợp. Trạng thái/kết quả nghiệp vụ vẫn giữ theo lịch sử, không coi mất log sau retention là quyền gọi AI lại cho tác vụ đã hoàn tất.

### Dọn file và nháp
- Cleanup chạy mỗi 5 phút. Trong điều kiện hệ thống bình thường, bản quá hạn được xử lý ở lần quét tiếp theo (tối đa thêm khoảng 5 phút); không hứa xóa đúng từng giây khi máy chủ/provider ngừng hoạt động.
- Nếu local đã xóa nhưng provider chưa xong, giữ DELETION_PENDING và reference của đích còn lại, retry có theo dõi/cảnh báo; không đánh dấu hoàn tất giả hoặc mất tracking.
- Gửi yêu cầu xóa file provider ngay khi không cần nếu API có hỗ trợ. Mốc 1 giờ là chính sách storage ứng dụng, không phải cam kết xóa mọi log/backup nội bộ Google.
- G6 dùng hai TTL độc lập. `transcript_expires_at` tính từ READY, `expires_at` tính từ upload. WAV gửi inline và ứng dụng chỉ quản lý file tạm ở backend.

### Backup và khôi phục
- Không đưa file audio vào backup. Khi thiết kế bản xuất, loại transcript nháp, file references không còn dùng và dữ liệu phiên đăng nhập; giữ schema và metadata tối thiểu cần để không phá FK. Việc xuất đã lọc cần thiết kế/kiểm thử trước triển khai, không giả định mysqldump mặc định đã làm vậy.
- Dữ liệu học tập đã xóa có thể còn trong backup cũ tới khi bản đó hết hạn tối đa 7 ngày; thông báo rõ. Không dùng backup như kho để xem lại dữ liệu người học đã xóa.
- Có nhật ký xóa tối thiểu, không chứa nội dung, giữ đủ vòng đời backup; khi restore phải áp lại các yêu cầu xóa trước khi mở dịch vụ. Không phục hồi phiên đăng nhập cũ; yêu cầu login lại.
- Backup ở vị trí độc lập với máy chạy database, giới hạn quyền truy cập và thử restore. RPO dự kiến tối đa 24 giờ theo lịch hằng ngày; chưa cam kết RTO hoặc chọn dịch vụ lưu trữ.

## 2. Thông báo xử lý âm thanh

Hiển thị trước lần đầu dùng giọng nói và luôn có liên kết xem lại. Thông báo trong ứng dụng và hộp thoại xin quyền micro là hai việc khác nhau. Chỉ upload khi người học bấm gửi; ghi âm/nghe lại/hủy ở trình duyệt không tự upload.

### Nội dung thông báo
> Khi bạn gửi bản ghi, âm thanh sẽ được chuyển qua máy chủ của website tới Google Gemini để chuyển thành văn bản. Bạn có thể kiểm tra và sửa bản chép lời trước khi gửi vào hội thoại. Nếu tính năng chấm phát âm được bật, bản ghi gốc tối đa 30 giây còn hạn được gửi sang Microsoft Azure sau khi xác nhận. Website chỉ giữ âm thanh tạm để xử lý và dọn theo chính sách dữ liệu; bản chép lời đã xác nhận được lưu trong lịch sử đến khi bạn xóa. Việc xử lý và lưu giữ phía Google tuân theo chính sách của dịch vụ được sử dụng. Bạn có thể chọn nhập văn bản thay cho ghi âm.

Hành động đã chọn: **Đã hiểu, tiếp tục** và **Dùng văn bản**. Không gọi đây là quy trình xác nhận pháp lý đầy đủ. Khi triển khai cần gắn mốc thời gian đã chấp nhận và liên kết chính sách thay vì chỉ dùng câu “không lưu lâu dài”.

Ngoài thông báo audio, giải thích rằng nội dung hội thoại văn bản cũng được gửi tới AI. Không gửi email, mật khẩu hoặc session ID trong prompt. Không quảng cáo chuyển sang text là đồng nghĩa không gửi dữ liệu ra ngoài.

### Kiểm tra dịch vụ trước dùng thử thật
Điều khoản Google phân biệt dịch vụ miễn phí và trả phí về xử lý dữ liệu; không được hứa chung rằng mọi nội dung không dùng để cải thiện dịch vụ hoặc Google xóa ngay. Đề xuất dùng môi trường/gói phù hợp cho dữ liệu người dùng thật; lựa chọn tier/ngân sách chưa chốt. Không tự chuyển sang gói trả phí hoặc phát sinh chi phí ở bước tài liệu này. Kiểm tra lại [điều khoản Gemini](https://ai.google.dev/gemini-api/terms) theo project/gói/khu vực sử dụng trước mở dùng thử.

## 3. Phạm vi trình duyệt

Đây là mục tiêu hỗ trợ đã chấp nhận, không phải kết quả toàn matrix đã xác minh. P0 ưu tiên micro thật trên máy tính theo yêu cầu; đã có metadata Chrome Windows do chủ dự án gửi tại [báo cáo audio](../development/phase0/audio.md). Edge/Android và phiên bản hiện hành vẫn phải kiểm tra sau.

| Nền tảng | Mục tiêu bản đầu |
| --- | --- |
| Chrome trên Windows | Hỗ trợ chính cho văn bản, ghi âm theo lượt và phát âm thanh |
| Edge trên Windows | Hỗ trợ chính, cùng các luồng với Chrome |
| Chrome trên Android | Hỗ trợ chính trên thiết bị thật, đặc biệt quyền micro/codec/nghe lại |
| Safari trên macOS, iPhone/iPad và trình duyệt khác | Best effort giao diện/văn bản; chưa cam kết chức năng giọng nói. Nếu Safari/iPhone trở thành bắt buộc, thử audio sớm trước khi chốt codec |

- Test bản stable hiện hành và một major trước đó tại ngày nghiệm thu; ghi browser/OS/thiết bị cụ thể trong báo cáo, không tuyên bố hỗ trợ mọi phiên bản.
- Dùng feature detection: getUserMedia, MediaRecorder và MediaRecorder.isTypeSupported. Chọn MIME/codec được cả trình duyệt lẫn backend/provider chấp nhận; không hard-code một định dạng rồi coi các browser như nhau.
- Có HTTPS khi triển khai, xử lý từ chối/thu hồi quyền micro, không có thiết bị, gián đoạn ghi, audio rỗng, bấm gửi lại và playback bị chặn. Thiếu khả năng audio thì giải thích và cho nhập văn bản.
- SpeechSynthesis là phương án đầu; giọng/khả năng tiếng Anh khác thiết bị. Giữ văn bản và thông báo khi không có giọng phù hợp, không cam kết giọng đồng nhất.
- Playwright hỗ trợ kiểm tra UI/HTTP, không thay thế kiểm thử micro, codec và âm thanh trên điện thoại thật. Chưa có kết quả đầy đủ browser matrix này.

Nguồn kỹ thuật: [getUserMedia và secure context](https://developer.mozilla.org/en-US/docs/Web/API/MediaDevices/getUserMedia), [MediaRecorder.isTypeSupported](https://developer.mozilla.org/en-US/docs/Web/API/MediaRecorder/isTypeSupported_static).

## Chính sách bổ sung — tài khoản và email

Token đặt lại mật khẩu lưu dưới dạng băm. Outbox mã hóa payload chứa liên kết và dọn payload sau khi gửi, hết hạn hoặc hủy. Metadata mail giữ tối đa 30 ngày. Avatar phải bỏ metadata, kiểm tra ảnh và dọn file cũ. Xóa tài khoản thu hồi Session/token, hủy lịch/mail và theo dõi việc dọn dữ liệu cùng file. Backup giữ tối đa 7 ngày và quy trình restore áp lại tombstone. SMTP mặc định tắt. [Đặc tả và phạm vi xử lý dữ liệu](requirements.md).

### Phạm vi xóa và thời hạn dữ liệu bổ sung

Xóa tài khoản bao gồm users, hồ sơ, sở thích, token, lịch/delivery/outbox, mọi buổi và dữ liệu con, thẻ/lịch sử ôn, avatar và audio/file references. Giữ job xóa cùng các reference file trong bảng hiện có cho tới khi hoàn tất; không cascade làm mất reference cần dọn. Luồng đang chạy không được ghi lại dữ liệu sau khi yêu cầu xóa được chấp nhận. Không xóa catalog dùng chung hoặc audit quản trị một cách dây chuyền; gỡ/ẩn danh tham chiếu tài khoản theo chính sách trước khi xóa users.

Thao tác xóa dữ liệu học tập hiện có tiếp tục giữ tài khoản, hồ sơ, avatar và lịch; giải thích phạm vi này trong hộp xác nhận. Chỉ xóa tài khoản mới hủy lịch. Không thêm thao tác xuất dữ liệu hoặc đổi email trong đợt này.

Job xóa xử lý trong lần quét tiếp theo khi hệ thống bình thường, trạng thái PENDING/RETRY/COMPLETED có theo dõi. Không hứa xóa tức thì mọi bản sao provider. Backup giữ tối đa 7 ngày theo chính sách hiện hành; giữ tombstone định danh kỹ thuật tối thiểu đủ áp lại xóa sau restore, không chứa email/nội dung/mật khẩu, dọn sau khi tất cả backup liên quan hết hạn. Delivery/outbox kết thúc giữ metadata tối đa 30 ngày; token hết hạn/đã dùng giữ metadata tối đa 30 ngày và được quét định kỳ; payload nhạy cảm dọn ngay. Avatar cũ được dọn sau thay thành công và retry nếu lỗi. File tạm thất bại/hủy được dọn định kỳ.
