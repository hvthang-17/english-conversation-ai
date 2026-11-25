# Kế hoạch hoàn thiện Luyện hội thoại với AI

## Mục tiêu và phạm vi

Hoàn thiện chu trình người học A2/B1 chọn tình huống và chế độ, luyện theo nhiều lượt, được hỗ trợ khi không hiểu, nhận tổng kết rồi xem lại lịch sử. Kế hoạch bổ sung phần còn thiếu và kiểm chứng các luồng hiện có. **Trạng thái đề xuất.** Ước lượng tính theo giờ-người và cần cập nhật sau khi kiểm tra môi trường.

Giữ hai chế độ TEXT/VOICE và giới hạn 20 lượt người học. Ghi âm theo lượt, chép lời cần xác nhận, phụ đề AI mở theo yêu cầu. Giữ phạm vi đánh giá ngữ pháp, từ vựng, diễn đạt và mục tiêu qua nội dung hội thoại. Thoại thời gian thực, Whisper, Azure và chấm điểm phát âm nằm ngoài đợt này.

## Nhóm 1 — Ổn định buổi luyện (9–12 giờ-người)

### CHAT-C01 — Chốt đúng trạng thái kết thúc (4–5 giờ)

- [x] **Backend:** Chỉ tính `COMPLETED` khi có ít nhất một lượt LEARNER với câu AI phản hồi thành công. Buổi đã có trao đổi thành công nhưng lỗi ở lượt cuối vẫn hoàn thành, giữ cả câu đã gửi. Buổi chưa có trao đổi thành công lưu lịch sử với trạng thái `ABANDONED` hiện có. Tạo nhận xét và thống kê dùng trạng thái này.
- [x] **UI:** Hiển thị lời giải thích khi kết thúc buổi chưa có trao đổi thành công. Hợp đồng ghi rõ quy tắc mới áp dụng cho buổi chuyển từ ACTIVE sang trạng thái cuối.
- [x] **Nghiệm thu:** Kiểm thử buổi chỉ có câu mở đầu, một lượt có AI lỗi, một lượt thành công, và thành công trước khi lượt cuối lỗi. Mở lịch sử vẫn đọc được tin nhắn đã lưu. Buổi không đạt điều kiện hoàn thành không tăng số buổi hoàn thành.

### CHAT-C02 — Khôi phục bản chép lời đang xử lý (5–7 giờ)

- [x] **Backend:** Cho chủ sở hữu đọc trạng thái/bản nháp còn hiệu lực theo ID buổi, chọn bản chưa xác nhận mới nhất. Trả trạng thái đang xử lý hoặc nháp READY nếu đủ điều kiện. Giữ cơ chế hết hạn, xác nhận và hủy hiện có.
- [x] **Frontend:** Khi mở lại buổi VOICE, đọc và hiển thị nháp Gemini còn hạn hoặc tiếp tục theo dõi tác vụ. Giao diện giải thích phần sửa chưa xác nhận nằm trong bộ nhớ của màn hình. Khôi phục không tải lại file âm thanh và không tạo lần chép lời mới.
- [x] **Nghiệm thu:** Tải lại khi đang chép hoặc khi có nháp, sau khi hết hạn, sau khi xác nhận và sau khi mất phiên. Tài khoản khác không đọc được nháp. Xác nhận gửi lại cùng nội dung chỉ tạo một lượt. Kiểm tra trong trình duyệt và MySQL.

**Bằng chứng C01–C02 ngày 24/11/2026:** 60 bài kiểm thử MySQL đạt với Gemini giả lập. Frontend đạt typecheck, lint và build. Playwright 390px dùng API giả lập để kiểm tra bản gốc được khôi phục sau tải lại, theo dõi phiên đang chép, xử lý mất phiên, thông báo buổi đóng và không gửi lại audio.

## Nhóm 2 — Hoàn thiện hỗ trợ học viên (12–17 giờ-người)

### CHAT-C03 — Giải thích câu AI (8–11 giờ)

Học viên mở **Giải thích câu này** trên câu AI gần nhất để hiểu ý rồi tiếp tục trả lời.

- [x] **Hợp đồng:** Gửi ID câu AI và ID yêu cầu chống gửi trùng. Kiểm tra quyền sở hữu, loại câu và câu AI gần nhất khi tạo hoặc thử lại. Trả cách nói đơn giản bằng tiếng Anh và nghĩa tiếng Việt theo yêu cầu hiển thị.
- [x] **Backend:** V12 thêm tác vụ EXPLAIN và bảng lưu kết quả theo câu. Dùng schema, prompt A2/B1 và hạn mức chung. Kết quả nằm ngoài hội thoại và được đọc lại sau tải trang hoặc gửi trùng.
- [x] **Frontend:** Hiện nút trên câu AI gần nhất, trạng thái giải thích, lỗi và thử lại. Phụ đề gốc vẫn đóng cho tới khi người học mở.
- [x] **Nghiệm thu tự động:** Kiểm tra gửi trùng, truy cập chéo, gọi đồng thời, lỗi dữ liệu, timeout/quota, tải lại, số lượt và phụ đề.
- [ ] **Nghiệm thu Gemini thật:** Kiểm tra diễn đạt dễ hiểu và giữ nguyên nghĩa trên mẫu A2/B1 sau khi phạm vi gọi được cấp. Theo dõi ở CHAT-C06.

### CHAT-C04 — Tổng hợp lỗi lặp trong buổi (4–6 giờ)

- [x] **Backend:** Dùng các mục `ERROR` đã xác thực, nhóm theo `ruleCode` và đếm theo số tin nhắn nguồn khác nhau. Hiện nhóm xuất hiện ở ít nhất hai câu, lấy tối đa ba nhóm, sắp theo số lần rồi mã nhóm. Chọn một ví dụ nguồn cho mỗi nhóm.
- [x] **Frontend:** Trong tổng kết, hiện nhóm lỗi, số câu có lỗi và một cặp câu gốc/câu sửa, giữ gợi ý luyện tiếp. Với dữ liệu chỉ có lỗi đơn lẻ hoặc chỉ có gợi ý diễn đạt, giữ danh sách chi tiết hiện có.
- [x] **Nghiệm thu:** Hai lỗi cùng mã trên hai tin nhắn tạo một nhóm có số đếm 2. Nhiều mục cùng mã trong một tin nhắn chỉ đếm một. Mục `IMPROVEMENT` không được tính. Dùng lại phản hồi đã lưu, không gọi thêm Gemini.

**Bằng chứng C03–C04 ngày 24/11/2026:** 63 kiểm thử đạt trên MySQL 8.4.13. Flyway áp dụng V12 trên schema mới và nâng cấp có dữ liệu. Frontend đạt typecheck/lint/build. Playwright ở 390px với API giả lập kiểm tra timeout và thử lại, giữ phụ đề đóng, chủ động mở nghĩa, đọc lại sau reload và nhóm lỗi lặp.

## Nhóm 3 — Nghiệm thu tích hợp (12–16 giờ-người)

- [x] **CHAT-C05 · Kiểm thử hồi quy (4–5 giờ):** MySQL 64 ca đạt trên schema mới/nâng cấp, frontend đạt typecheck/lint/build và hai luồng A2/TEXT, B1/VOICE đạt trong Playwright. Xem [bằng chứng](../docs/development/chat-acceptance/README.md#chat-c05--kiểm-thử-hồi-quy).
- [ ] **CHAT-C06 · Thiết bị và Gemini thật (5–7 giờ):** Chạy luồng nghe → nói → chép lời → xác nhận → AI trả lời → giải thích → tổng kết trên Chrome/Edge Windows và Chrome Android theo P6.8. Dùng ít nhất một tình huống cho mỗi mức A2/B1 trong bộ nghiệm thu. Ghi phiên bản thiết bị/trình duyệt, tình trạng micro/giọng đọc, tỷ lệ hoàn thành, lỗi và độ trễ theo tác vụ. Dùng mẫu hư cấu; gọi Gemini thật chỉ khi có phạm vi và hạn mức được cấp. Nếu thiếu thiết bị hoặc quyền gọi, ghi trạng thái chờ và tiếp tục phần kiểm thử độc lập.
- [ ] **CHAT-C07 · Đánh giá và bàn giao (3–4 giờ):** Chuẩn bị tiêu chí đúng vai, dễ hiểu theo A2/B1, chép đúng ý, giải thích giữ nghĩa và sửa câu hợp lý. Cho người đánh giá rà mẫu. Liên kết bằng chứng với P8.3–P8.5 và nêu rõ kích thước mẫu. Phần thực hiện khảo sát khoảng 10 người và đánh giá bộ 30–50 mẫu thuộc P8, không cộng vào ước lượng này.

**C06–C07:** Đã chuẩn bị [ma trận thiết bị và mẫu ghi số liệu](../docs/development/chat-acceptance/README.md) và [rubric cùng 2 mẫu A2/B1](../docs/development/chat-acceptance/evaluation.md). Chờ phạm vi Gemini cụ thể, bằng chứng thiết bị và người rà độc lập.

## CHAT-C08 — Chọn vai trong cùng tình huống

- [x] V13 thêm vai thứ hai cho 8 tình huống mẫu. Người học chọn vai trên trang chi tiết cùng tình huống. Mỗi vai có bối cảnh, mục tiêu và hướng dẫn mở đầu riêng. Vai đã chọn được chụp vào snapshot.
- [x] MySQL kiểm tra vai mặc định cho buổi cũ, dữ liệu công khai, gửi lại, vai không hợp lệ, prompt theo vai và snapshot. 64 bài kiểm thử đạt.
- [x] Playwright với API giả lập kiểm tra chọn vai bằng bàn phím ở chiều rộng 390px, đổi bối cảnh/mục tiêu, gửi đúng mã vai, dùng lại mã yêu cầu khi thử lại và giữ vai khi tải lại buổi.
- [ ] Xác nhận câu mở đầu do Gemini thật sinh phù hợp với vai ở CHAT-C06.

## Thứ tự và điều kiện bàn giao

Sau khi hoàn thiện các chức năng, chạy CHAT-C05 → CHAT-C06 → CHAT-C07. Ước lượng ban đầu **33–45 giờ-người** cho C01–C07, khoảng **4–5 ngày làm việc** với hai người nếu đủ thiết bị và cấu hình.

Bàn giao gồm mã nguồn, tài liệu API/hành vi cập nhật, checklist chức năng và bằng chứng kiểm thử. Đánh dấu riêng bằng chứng MySQL, trình duyệt giả lập và nghiệm thu thiết bị/Gemini thật. Giữ mốc nộp 28/12/2026 và ưu tiên xử lý phần chặn luồng chính trước phần mở rộng.

[Yêu cầu sản phẩm](../docs/product/requirements.md) · [Kế hoạch nghiệm thu và dùng thử](../docs/development/full-system-implementation-plan.md#e2e--nghiệm-thu-dùng-thử-và-bàn-giao) · [Kịch bản nghiệm thu hội thoại](../docs/development/chat-acceptance/README.md) · [Hợp đồng hội thoại hiện tại](../docs/api/practice.md)
