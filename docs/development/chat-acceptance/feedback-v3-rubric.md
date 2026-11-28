# Bộ rà nhận xét hội thoại V3

Bộ mẫu hư cấu có 32 lượt mục tiêu. Mỗi dòng là một lượt có nhãn đề xuất. Dùng tình huống và vai trực tiếp trong cột bài thử. Nếu lượt có dữ kiện trước đó, đưa dữ kiện đó vào transcript trước câu hỏi. Người rà tiếng Anh xác nhận hoặc chỉnh nhãn trước khi ghi kết quả chính thức.

| Mẫu | Cấp độ và tình huống | AI nói | Học viên nói | Kỳ vọng |
| --- | --- | --- | --- | --- |
| 01 | A2 · mua hàng | Would you like a receipt? | No. | Đủ ý. Có thể thêm “thank you”. |
| 02 | A2 · mua hàng | Do you have the receipt? | Yes. | Đủ ý. Giữ khẳng định. |
| 03 | A2 · đổi hàng | Why would you like to return it? | I want to return it. | Thiếu lý do. Mẫu có chỗ điền. |
| 04 | A2 · đổi hàng | Why are you returning it? | It's too small. | Đủ ý. |
| 05 | A2 · đổi hàng | Which size do you need? | Medium, please. | Đủ ý và lịch sự. |
| 06 | A2 · ăn uống | Would you like water or juice? | Yes. | Thiếu lựa chọn. |
| 07 | A2 · ăn uống | What would you like to drink? | Tea, please. | Đủ ý. |
| 08 | A2 · ăn uống | How many people are in your group? | 7 | Đủ ý. Giữ số bảy. |
| 09 | A2 · đặt phòng | What time will you arrive? | 7 | Cần làm rõ sáng hay tối nếu chưa thống nhất. |
| 10 | A2 · đặt phòng | What time will you arrive? | Seven p.m. | Đủ ý. Giữ buổi tối. |
| 11 | A2 · đặt phòng | Is seven okay? | Yes. | Chưa đủ căn cứ nếu chưa thống nhất số bảy chỉ gì. Gợi ý hỏi lại. |
| 12 | A2 · đặt phòng | Is seven p.m. okay for check-in? | Yes. | Đủ ý. |
| 13 | A2 · làm quen | Why do you want to join? | 7 | Lạc đề. Gợi ý khung nêu lý do. |
| 14 | A2 · làm quen | Where are you from? | I'm from Viet Nam. | Đủ ý. Giữ địa danh. |
| 15 | A2 · làm quen | What do you like doing? | I like play football. | Đủ ý và góp ý dạng động từ. |
| 16 | A2 · hỏi đường | Are you looking for the museum? | No, the station. | Đủ ý. Giữ phủ định và đích đến. |
| 17 | B1 · phỏng vấn | Can you give an example of teamwork? | Yes. | Thiếu ví dụ. Khung để điền thông tin của học viên. |
| 18 | B1 · phỏng vấn | Can you work on weekends? | No, I can't. | Đủ ý. Giữ phủ định. |
| 19 | B1 · phỏng vấn | Why do you want this job? | I'm free on weekends. | Chưa nêu lý do ứng tuyển đủ rõ. |
| 20 | B1 · phỏng vấn | What did you do when the deadline changed? | I called the team and we agreed on a new plan. | Đủ ý. |
| 21 | B1 · phỏng vấn | What did you learn? | I learned communicate more clearly. | Đủ ý. Góp ý dạng động từ. |
| 22 | B1 · học nhóm | Which day and time work for you? | Friday. | Thiếu giờ. |
| 23 | B1 · học nhóm | What time will you arrive? | At six, sorry, at seven. | Đủ ý. Giữ giờ tự sửa cuối cùng. |
| 24 | B1 · học nhóm | Could we start at six? | No, but I can make it at seven. | Đủ ý. Giữ cả từ chối và giờ thay thế. |
| 25 | B1 · đặt phòng | Can I confirm the price is 70 dollars? | Yes. | Đủ ý. Giữ giá nếu viết lại. |
| 26 | B1 · đặt phòng | Why do you need a quieter room? | I have an online interview in the morning. | Đủ ý. |
| 27 | B1 · đặt phòng | Where do you study biology? | I booked a double room. | Nếu AI đang ở vai lễ tân và câu hỏi tự chuyển chủ đề, chưa đủ căn cứ để gán lỗi lạc đề cho học viên. |
| 28 | B1 · đổi hàng | Why do you need a refund? | I ordered a blue shirt but got a red one. | Đủ ý. Giữ đúng quan hệ xanh đã đặt, đỏ đã nhận. |
| 29 | B1 · đổi hàng | What was wrong with the item? | It doesn't work. | Đủ ý ở lượt này. Có thể gợi ý nêu thêm triệu chứng khi được hỏi tiếp. |
| 30 | B1 · hỏi đường | Which route did you try? | I tried the one by the park, but the gate was closed. | Đủ ý. Giữ vị trí và trạng thái cổng. |
| 31 | B1 · hỏi đường | Is it on the right? | No, on the left. | Đủ ý. Giữ bên trái. |
| 32 | B1 · hỏi đường | Can you repeat the last direction? | Turn left after the bank. | Đủ ý. Giữ thứ tự sau ngân hàng. |

## Cách ghi nhận

- So sánh mỗi prompt trên cùng transcript và cùng model. Ghi riêng nhãn đúng trên tổng lượt, góp ý giữ nghĩa trên tổng mục rà, góp ý hữu ích trên tổng mục rà, số lỗi gán sai và độ phủ. Bổ sung token, độ trễ và số yêu cầu.
- Từ vựng có hai nhóm. Với **Trong hội thoại**, người rà kiểm tra đúng câu nguồn, nghĩa và ví dụ. Với **Mở rộng theo chủ đề**, người rà kiểm tra mục từ hỗ trợ đúng vai, tình huống và cấp độ.
- Chọn một số lượt cần luyện tiếp như mẫu 03, 09, 17 và 22. Ghi câu ban đầu, câu luyện lại và câu ứng dụng ở câu hỏi mới. Người rà dùng cùng tiêu chí cho cả ba. Báo số học viên đạt trên số người hoàn thành từng bước và ghi trường hợp đã xem khung gợi ý.

Kết quả Gemini thật và lượt thử với học viên sẽ được điền sau khi có phạm vi gọi, người rà và người tham gia. Các nhãn trên là kỳ vọng dùng để đối chiếu.

## Chạy script FB-05

Script cần Java 21 và Maven hoặc Maven Wrapper. Các lệnh dưới đây chạy từ gốc repository và đã được kiểm tra với PowerShell 7. Script đọc 32 dòng trên, gồm 16 A2 và 16 B1, rồi dựng từng lượt độc lập với vai và bối cảnh cố định. Mẫu `VOICE` dùng bản chép lời hư cấu. Script dùng prompt, adapter Gemini và bộ xác thực của backend, với bộ từ vựng seed làm tham chiếu cố định. Nó chạy độc lập với ứng dụng và MySQL.

```powershell
# Kiểm tra bộ mẫu, bộ xác thực và định dạng báo cáo bằng fixture
.\scripts\phase3\test-feedback-quality.ps1

# Thử 4 mẫu sau khi đã cấp phạm vi gọi
.\scripts\phase3\test-feedback-quality.ps1 -Live -Cases '03','11','23','28' -MaxRequests 4

# Chạy đủ 32 mẫu trên prompt đang triển khai
.\scripts\phase3\test-feedback-quality.ps1 -Live -MaxRequests 32 -RequestsPerMinute 6

# So sánh hai prompt trên cùng dữ liệu và model
.\scripts\phase3\test-feedback-quality.ps1 -Live -CompareV32 -MaxRequests 64 -RequestsPerMinute 6
```

Chế độ live lấy `GEMINI_API_KEY` và `GEMINI_CHAT_MODEL` từ môi trường tiến trình hoặc `.env` ở gốc. `-Model` chọn model cho lần chạy. Chỉ bật `-Live` trong phạm vi đã được cấp cho model, project và gói thực tế. Chọn `-RequestsPerMinute` phù hợp với quota của project. `-MaxRequests` là trần số lần gọi của script trong một lần chạy. Chi phí còn tùy model, gói và token. Script gọi tuần tự, không retry tự động và dừng sớm khi gặp lỗi xác thực, quota, timeout hoặc lỗi kết nối. Các mẫu trả JSON không hợp lệ vẫn được ghi để đo tỷ lệ lỗi. [Hạn mức của Gemini](https://ai.google.dev/gemini-api/docs/rate-limits)

`-CompareV32` dùng bản prompt V3.2 được chụp trong repo, cùng schema và cùng bộ xác thực V3.4 ở cả hai nhánh. Nhờ đó phép so sánh đo thay đổi prompt trên cùng đầu vào. Mỗi nhánh cần một request cho mỗi mẫu. Báo cáo lưu kết quả sau bộ xác thực, gồm mẫu trả lời dự phòng khi được áp dụng. Script trực tiếp gọi adapter nên dùng trần và nhịp gọi riêng, không đi qua quota ứng dụng, lưu lịch sử hoặc chấm phát âm.

### Đọc báo cáo và rà chất lượng

- Thư mục `.runtime/feedback-quality/<lần-chạy>/` chứa `report.json` và `review.csv` khi có phản hồi hợp lệ. `report.json` lưu trạng thái từng mẫu, nhãn dự kiến và nhãn thực tế, số phân loại khớp trên tổng mẫu, độ phủ chung và theo A2/B1, số câu thiếu ý bị đánh giá là đủ ý, số lỗi bị gắn ngoài kỳ vọng, model, token và độ trễ median/P95 kèm cỡ mẫu. Lần chạy dừng sớm giữ dữ liệu của các mẫu đã xử lý. Dữ liệu đầu ra gồm nhận xét đã xác thực và hội thoại hư cấu. Key và lỗi thô của Gemini không đi vào báo cáo.
- Các nhãn máy dùng nhãn kỳ vọng trong bảng. Mẫu 09 được tính `PARTIAL`, mẫu 19 chấp nhận `PARTIAL` hoặc `OFF_TOPIC`, còn mẫu 11/27 cần `UNCERTAIN` với lý do tương ứng. Người rà có thể ghi lý do khác với nhãn máy vào `Notes`. Fixture offline điền sẵn nhãn để thử đường xử lý, nên tỷ lệ của lần chạy đó chỉ dùng kiểm tra script.
- Giữ nguyên `ItemId` trong `review.csv`. Điền `YES` hoặc `NO` cho các cột áp dụng. `ClassificationCorrect` dùng cho `TURN`. `KeepsMeaning` dùng cho `ADVICE`, `ERROR` và `CORRECTION`. `VocabularyCorrect` dùng cho `VOCABULARY`, gồm nghĩa, từ loại, ví dụ và nguồn theo ngữ cảnh. `FalseError` dùng cho `ERROR`, điền `YES` nếu Gemini gán lỗi sai. `Useful` và `SevereIssue` áp dụng cho mọi dòng. Ghi rõ dữ kiện đổi ý hoặc góp ý dễ dạy sai ở `Notes`. Để trống các cột không áp dụng. Nếu sửa bằng Excel, lưu dạng CSV UTF-8, phân cách bằng dấu phẩy.

```powershell
.\scripts\phase3\test-feedback-quality.ps1 -ReviewDirectory '.runtime/feedback-quality/<lần-chạy>'
```

Lệnh tổng hợp tạo `review-summary.json`, gồm số mục đạt, số mục sai và số mục đã chấm trên số áp dụng cho mỗi prompt. Nếu lượt trước dừng trước khi xuất CSV, lệnh này tạo lại phiếu trống từ báo cáo còn lại. Mã thoát `0` của lần chạy nghĩa là mọi mẫu đã chọn có kết quả cấu trúc hợp lệ. Mã `2` cho biết có mẫu không được xác thực hoặc lượt chạy dừng sớm. Kết luận FB-05 cần kết quả live đủ mẫu, phiếu người rà và bằng chứng áp dụng góp ý vào câu hỏi mới. Khi so sánh A/B, đối chiếu hai phiên bản trên cùng mẫu, xem số góp ý đổi ý nghiêm trọng và số ca thiếu ý bị bỏ sót trước khi kết luận.
