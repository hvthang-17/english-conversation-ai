# Đề xuất nâng cấp nhận xét hội thoại Gemini

**Trạng thái:** Đã triển khai đến V3.3 cho nhận xét cuối buổi TEXT và VOICE. V3.3 xử lý hồi quy giữ dữ kiện và đối chiếu nghĩa mục từ trước khi lưu thẻ. Đã soạn [bộ rà 32 lượt](../docs/development/chat-acceptance/feedback-v3-rubric.md). FB-05 chờ gọi Gemini thật theo phạm vi được cấp, người rà tiếng Anh và người dùng thử.

## Vấn đề ở bản trước

Prompt đã nhận cấp độ, tên tình huống, mục tiêu và transcript. Phản hồi được kiểm tra về cấu trúc, ID tin người học, trích đoạn và mã lỗi. Prompt chưa yêu cầu đánh giá mức độ trả lời của từng lượt dựa trên câu AI trước đó. Nhóm lỗi lặp được tính từ tối đa sáu mục sửa do Gemini chọn. Kiểm tra cấu trúc chỉ xác minh được một phần chất lượng ngôn ngữ. [Phân tích hiện tại](../backend/src/main/java/vn/englishconversation/feedback/service/FeedbackAnalysis.java) · [Tổng hợp lỗi](../backend/src/main/java/vn/englishconversation/feedback/repository/FeedbackStore.java).

## Trải nghiệm học viên

1. Khi kết thúc buổi đủ điều kiện, học viên tạo nhận xét như hiện nay. Phần đầu tóm tắt mục tiêu nào đã đạt, mục tiêu nào mới đạt một phần và một việc ưu tiên để luyện lần sau. Mỗi nhận định cụ thể có thể mở câu làm căn cứ.
2. Trong **Xem từng lượt**, học viên đọc câu AI, câu mình gửi, mức độ đáp ứng và một nhận xét ngắn. Nếu có điểm cần cải thiện, hiển thị một câu gợi ý phù hợp vai và cấp độ. Với VOICE, phần này đánh giá bản chép lời đã xác nhận. Điểm Azure tiếp tục được trình bày riêng.
3. Trong **Từ và cụm từ nên ôn**, học viên xem hai nhóm **Trong hội thoại** và **Mở rộng theo chủ đề**. Mỗi mục giải thích ngắn vì sao đáng học. Có thể xem câu nguồn nếu mục xuất hiện trong buổi, nghe bằng TTS và lưu vào sổ tay kèm nghĩa, từ loại và ví dụ.
4. Từ góp ý ưu tiên, bấm **Luyện lại câu này** để xem lại câu hỏi, đọc một mục tiêu cụ thể và thử trả lời bằng lời của mình. Ví dụ, trả lời lại “Why would you like to return it?” với mục tiêu **Nêu một lý do đổi hàng**. Bản đầu cho tự đối chiếu với tiêu chí gắn với góp ý, giữ nguyên câu trả lời và nhận xét đã lưu của buổi.

### Ví dụ kỳ vọng

| Câu AI | Người học | Nhận xét và cách luyện |
| --- | --- | --- |
| Would you like a receipt? | No. | **Đủ ý.** Có thể dùng “No, thank you.” để lịch sự hơn. |
| Do you have any experience working in a team? Can you give an example? | Yes. | **Thiếu ví dụ.** Dùng khung “Yes, I do. For example, I worked with a team on [a real project].” Người học tự điền thông tin đúng với mình. |
| How many people are in your group? | 7 | **Đủ ý.** Khi luyện câu đầy đủ, có thể nói “There are seven people in my group.” |
| What time would you like to check in? | 7 | **Cần làm rõ sáng hay tối** nếu trước đó chưa thống nhất. Gợi ý “I'd like to check in at seven [a.m./p.m.].” Dùng giờ đã thống nhất nếu transcript xác nhận. |
| Why do you want to join the club? | 7 | **Chưa trả lời đúng câu hỏi.** Hướng dẫn nêu lý do bằng khung “I'd like to join because [your reason].” |
| Is seven okay? | Yes. | **Chưa đủ căn cứ** nếu chưa biết số bảy chỉ điều gì. Gợi ý hỏi lại “Do you mean seven p.m.?” nếu hai bên đang bàn về giờ, hoặc “What do you mean by seven?” nếu ngữ cảnh chưa rõ. |

### Từ và cụm từ nên ôn

| Nhóm | Gemini chọn mục theo tiêu chí nào? | Ví dụ trong tình huống đặt phòng |
| --- | --- | --- |
| **Trong hội thoại** | Từ, cụm từ hoặc mẫu câu đã xuất hiện trong câu của AI hoặc học viên. Ưu tiên cụm dùng để đạt mục tiêu, từ dễ dùng sai và cách nói học viên có thể dùng lại. | **a quiet room** · *cụm danh từ* · phòng yên tĩnh. Câu nguồn “We have a quiet room on the third floor.” Ví dụ “Could I book a quiet room?” |
| **Mở rộng theo chủ đề** | Mục chưa xuất hiện trong transcript nhưng hỗ trợ đúng tình huống, vai và mục tiêu buổi luyện. Mỗi mục ghi lúc nào học viên có thể dùng. | **late check-out** · *cụm danh từ* · trả phòng muộn. Hữu ích khi cần hỏi có được trả phòng trễ hơn giờ quy định. Ví dụ “Is late check-out available?” |

Mỗi mục có từ hoặc cụm từ, loại mục, từ loại theo ngữ cảnh, nghĩa tiếng Việt, ví dụ tiếng Anh và lý do nên ôn. Bản đầu đề xuất tối đa **sáu mục**, ưu tiên đến bốn mục từ hội thoại và đến hai mục mở rộng. Có thể trả ít hơn khi không có mục phù hợp. Mục mở rộng ưu tiên mẫu dùng được ngay ở A2 hoặc cụm giúp hỏi rõ điều kiện và lựa chọn ở B1. Nếu chủ đề rộng, dùng tình huống cụ thể để chọn. Chẳng hạn, **đặt phòng khách sạn** ưu tiên giờ nhận/trả phòng và tiện nghi của phòng.

## Hợp đồng phân tích

- Server dựng dữ liệu theo lượt gồm ID tin LEARNER, ID câu AI ngay trước đó nếu có và nội dung tương ứng. Gửi thêm vai, bối cảnh, trình độ và mục tiêu từ snapshot của buổi. Dùng câu AI thực sự đã gửi để xác định người học đang trả lời điều gì.
- Mỗi lượt có một kết quả về mức độ trả lời, gồm **Đủ ý**, **Thiếu ý**, **Chưa đúng câu hỏi** hoặc **Chưa đủ căn cứ**. Gemini nêu yêu cầu cụ thể chưa được đáp ứng. Giá trị như `Yes`, `No` hoặc con số chỉ là tín hiệu để xem xét theo ngữ cảnh.
- Rà câu AI vừa hỏi theo vai, độ rõ và thông tin thực sự có trong buổi. Nếu câu AI mơ hồ hoặc lệch vai khiến việc đánh giá thiếu căn cứ, trả **Chưa đủ căn cứ**, dẫn câu AI nguồn và gợi ý một cách hỏi lại. Với VOICE, xử lý dấu câu và chữ hoa theo đặc điểm bản chép hội thoại.
- Tách mức độ trả lời khỏi nhóm góp ý **lỗi ngôn ngữ**, **cách diễn đạt tự nhiên hơn** và **mở rộng ý**. Đánh giá mục tiêu giao tiếp toàn buổi dựa trên các lượt có bằng chứng. Với tình huống chưa có mục tiêu nhỏ được biên soạn, bắt đầu từ mục tiêu trong snapshot rồi hiện các phần Gemini có thể dẫn nguồn.
- Gợi ý viết lại có hai loại. **Viết lại giữ ý** giữ câu khẳng định hoặc phủ định, dữ kiện, số lượng, thời gian và đúng vai. **Khung để tự điền** dùng chỗ trống khi câu gốc thiếu thông tin, tránh tự gán kinh nghiệm, lý do hoặc sở thích. Phiên bản A2 ưu tiên một câu ngắn và từ quen thuộc. B1 có thể luyện thêm lý do, ví dụ hoặc câu hỏi tiếp nối khi phù hợp.
- Mỗi lỗi thực sự lưu mã lỗi và ID tin nguồn. Backend đếm các tin nguồn khác nhau theo mã trên toàn bộ phần đã được phân tích. Giới hạn giao diện áp dụng sau khi đếm, chẳng hạn tối đa ba nhóm và một ví dụ mỗi nhóm. Hiện phạm vi đã phân tích nếu buổi chỉ có kết quả một phần.
- Gợi ý từ và cụm từ dùng `IN_TRANSCRIPT` hoặc `TOPIC_EXTENSION`. Với `IN_TRANSCRIPT`, Gemini trả `sourceMessageId` của câu AI hoặc học viên và `sourceExcerpt` là đoạn trích có cụm cần ôn. Nếu tên mục được đưa về dạng từ điển, ghi cả dạng đã xuất hiện trong câu. Với `TOPIC_EXTENSION`, để trống hai trường nguồn và ghi mục tiêu cụ thể hoặc tình huống sử dụng. Mục hợp lệ được đưa vào sổ tay theo cơ chế bản sao hiện có. Nhận xét cũ thiếu thông tin nguồn vẫn đọc được với cách hiển thị hiện tại.

## Kiểm tra và xử lý lỗi

- Kiểm tra đúng phạm vi buổi, đúng loại người gửi, trích đoạn, cặp hỏi–đáp, mã lỗi và ID hợp lệ. Kiểm tra một dòng đánh giá cho mỗi lượt trong phạm vi được gửi. Khử trùng theo mã lỗi và tin nguồn trước khi đếm.
- Với câu viết lại, kiểm tra các dữ kiện có thể đối chiếu rõ như số, giờ, đơn vị, tên được trích và cực tính yes/no trong mẫu đơn giản. Chuẩn hóa các cách biểu diễn tương đương như `7` và `seven`. Nhận định còn mơ hồ có thể bị hạ thành khung gợi ý hoặc bỏ qua. Kiểm tra mục từ xuất hiện đúng dạng hoặc biến thể cho phép trong ví dụ trước khi cung cấp thao tác lưu.
- Rà kỹ các mục có khả năng dạy sai. Đối chiếu ý gốc, vị trí và lý do của lỗi, vai và cấp độ. Gắn **Cách nói khác** nếu câu gốc đã dùng được. Khi câu viết lại mâu thuẫn với dữ kiện đã xác nhận hoặc lời giải thích thiếu căn cứ, bỏ mục đó hoặc dùng khung người học tự điền nếu phù hợp. Bộ rà có ca giữ nguyên phủ định, số, giờ và ca học viên tự sửa ý trong lúc nói.
- Kiểm tra trích đoạn `IN_TRANSCRIPT` thuộc đúng tin của chính buổi và chứa dạng xuất hiện đã khai báo. Mục mở rộng phải có tình huống sử dụng cụ thể. Nếu phát hiện cụm này thực tế đã xuất hiện trong transcript, chỉ hiển thị ở nhóm trong hội thoại khi truy được tin nguồn hợp lệ. Gộp mục trùng theo cụm chuẩn hóa và nghĩa đang học. Độ phù hợp của nghĩa, ví dụ và mục mở rộng được rà bằng mẫu A2/B1. Quyền dẫn nguồn từ AI ở mục từ áp dụng riêng, còn lỗi cần sửa vẫn dẫn câu học viên.
- Các quy tắc trên giúp phát hiện mâu thuẫn cụ thể. Độ tự nhiên, giải thích ngữ pháp và tương đương nghĩa phức tạp cần người rà tiếng Anh. Schema JSON dùng để kiểm tra hình dạng dữ liệu. [Gemini Structured Outputs](https://ai.google.dev/gemini-api/docs/structured-output).
- Bản đầu dùng một lời gọi FEEDBACK có đầu ra gọn cho buổi nằm trong giới hạn. Tách phần tổng kết chi tiết khỏi danh sách đánh giá lượt ngắn. Với buổi quá dài so với cấu hình đã kiểm chứng, chọn nhóm lượt liên tiếp và công bố độ phủ hoặc báo trạng thái chưa phân tích đầy đủ. Chỉ mở chia lô tự động hoặc bước Gemini thẩm định riêng sau khi đánh giá chất lượng và chi phí. Khi chia lô, ghép theo ID tin và tổng hợp lỗi từ phần đã qua xác thực.
- Dùng lại `ai_requests`, `ai_attempts`, khóa chống trùng và cơ chế thử lại. Ghi phiên bản prompt, log metadata và lưu kết quả đã kiểm tra. Giữ khả năng đọc nhận xét cũ. Lỗi nhận xét không làm mất transcript hoặc điểm Azure. Chốt chi tiết lưu đánh giá từng lượt sau khi chốt API, ưu tiên tận dụng bảng hiện có và chỉ thêm dữ liệu cần truy vấn.

## Kế hoạch triển khai và nghiệm thu

| Task | Việc làm | Tiêu chí nghiệm thu |
| --- | --- | --- |
| FB-01 | Soạn rubric và bộ mẫu A2/B1 theo vai. Bổ sung ca AI hỏi mơ hồ, lệch vai, học viên tự sửa ý và câu viết lại đổi dữ kiện. | Người rà tiếng Anh thống nhất đáp án kỳ vọng và xác định rõ khi nào nên hướng dẫn hỏi lại. |
| FB-02 | Chuẩn hóa ngữ cảnh theo lượt, prompt và schema có phiên bản. Phân nguồn mục từ và thêm đánh giá độ rõ của câu hỏi AI. | Ca yes/no, số đứng riêng, câu trả lời lạc đề và câu đúng ngắn nhận đúng loại. Khi câu AI không đủ rõ thì nêu lý do và gợi ý hỏi lại. |
| FB-03 | Xác thực bằng chứng, rà góp ý có nguy cơ đổi ý hoặc gán sai lỗi, giữ lịch sử và tổng hợp lỗi lặp. | Thử đổi phủ định, giờ hoặc số, sai nguồn, lỗi trùng và ví dụ sai nghĩa. Câu gốc hợp lệ hiện **Cách nói khác** nếu có đề xuất thay thế. |
| FB-04 | Nối tổng kết, xem từng lượt, hai nhóm từ nên ôn và **Luyện lại câu này** vào UI. | Kiểm tra trên trình duyệt mở đúng câu nguồn, nghe TTS, lưu mục từ, tải lại và xem được nhận xét cũ. Mở luyện lại đưa đúng câu hỏi và một tiêu chí cụ thể, học viên tự thử trả lời mà lịch sử buổi giữ nguyên. Frontend qua typecheck, lint và build. |
| FB-05 | So sánh prompt hiện tại và đề xuất trên cùng mẫu hư cấu. | Báo cỡ mẫu, phân loại đúng/tổng lượt, câu viết lại giữ nghĩa/tổng câu rà, góp ý hữu ích/tổng góp ý, lỗi gán sai, độ phủ, số lần gọi, độ trễ và token. Gọi Gemini thật theo model và hạn mức được cấp. |

Đề xuất vòng đầu dùng **30–50 lượt có nhãn trong các đoạn A2/B1**, gồm nhiều kiểu câu hỏi và câu trả lời ngắn phù hợp. Điều kiện phát hành ưu tiên không có ca đổi ý nghiêm trọng trong bộ rà, xác thực nguồn đạt trên toàn bộ fixture và bản mới giảm bỏ sót câu thiếu ý trên cùng tập so sánh. Ghi số mẫu và sai lệch cụ thể. Kết quả này dùng đánh giá chất lượng nhận xét trong bộ mẫu.

## Kiểm chứng học viên áp dụng được góp ý

Mỗi nhận xét ưu tiên phải giúp học viên trả lời ba câu hỏi **Mình đã làm được gì?**, **Lần tới cần làm gì khác?** và **Làm sao biết đã làm được?**. Phần đầu của tổng kết chọn một việc đáng luyện nhất theo mục tiêu buổi. Góp ý dẫn đến câu nguồn, giải thích tác động trong hội thoại, cho một mẫu ngắn và đặt một tiêu chí quan sát được. Chẳng hạn, dùng **Nêu một lý do đổi hàng** thay cho **Cần trả lời chi tiết hơn**. Ưu tiên ý thiếu ảnh hưởng đến mục tiêu hoặc khiến người đối thoại phải hỏi lại. Nếu lượt đã đạt, củng cố một cách làm tốt và có thể đề xuất thử ở tình huống tương tự.

| Bước | Ví dụ luyện diễn đạt lý do |
| --- | --- |
| Xem góp ý | Khi được hỏi “Why would you like to return it?”, học viên nói “I want to return it.” Góp ý chỉ ra học viên đã nêu đúng yêu cầu đổi/trả và còn thiếu lý do nhân viên cần biết. |
| Luyện lại cùng câu hỏi | Học viên tự trả lời lại. Tiêu chí là **Nêu một lý do phù hợp và giữ đúng dữ kiện**. Mẫu hỗ trợ “I'd like to return it because [your reason].” |
| Áp dụng ở câu hỏi tương tự | Đưa câu hỏi mới với cùng kỹ năng, chẳng hạn “What wasn't right about the item?” Học viên tự nói lý do trước khi xem mẫu. Rà theo cùng tiêu chí và chấp nhận cách diễn đạt khác đạt mục tiêu. |

Trong FB-05, rà riêng hai phần:

- **Chất lượng góp ý.** Người rà tiếng Anh kiểm tra góp ý đúng ý, đúng vai, vừa sức và có một hành động cụ thể. Ghi số mục đạt trên tổng số được rà và những góp ý có thể làm người học hiểu sai.
- **Khả năng áp dụng trong bài thử.** Với người học thử tự nguyện, ghi câu trả lời ban đầu, câu luyện lại và câu trả lời cho câu hỏi tương tự. Người rà dùng cùng tiêu chí cho cả ba, chẳng hạn **đạt**, **đạt một phần** hoặc **chưa đạt**. Báo số người đạt trên số người hoàn thành ở mỗi bước. Ghi rõ ai dùng khung gợi ý. Nếu so sánh hai bản nhận xét, dùng các câu hỏi có độ khó tương đương và đổi thứ tự dùng hai bản giữa những người thử khi có thể.

Ca nghiệm thu giao diện cần xác nhận học viên mở được đúng câu cần luyện, hiểu tiêu chí và có thể thử lại bằng câu của mình. Báo riêng kết quả khi làm lại câu cũ và khi áp dụng vào câu mới. Phạm vi kết luận là kỹ năng được thử trên nhóm người tham gia. Đợt thử sẽ giúp xác định góp ý nào cần sửa trước khi dùng rộng hơn.
