# Phiếu rà nội dung tiếng Anh G2.2

Bộ này đối chiếu [seed V7](../../../backend/src/main/resources/db/migration/V7__starter_lessons.sql) và [bản cập nhật nội dung V8](../../../backend/src/main/resources/db/migration/V8__refine_reviewed_scenarios.sql). Trích đoạn tiếng Anh giúp người rà kiểm tra cấp độ, cách diễn đạt, vai và mục tiêu. V8 đồng bộ bối cảnh và hướng dẫn cho các mục đã sửa. Các câu trong phiếu là mẫu tham chiếu để rà nội dung.

## Danh mục đối chiếu seed

| ID và bài học                      | Chủ đề         | Cấp độ | Mã tình huống              | Mã mục tiêu           |
| ---------------------------------- | -------------- | ------ | -------------------------- | --------------------- |
| 101 · Giới thiệu bản thân          | `INTRODUCTION` | A2     | `CLUB_INTRO`               | `INTRODUCE_YOURSELF`  |
| 102 · Kể về thói quen              | `DAILY_LIFE`   | A2     | `DAILY_ROUTINE`            | `DESCRIBE_ROUTINE`    |
| 103 · Đổi món đồ đã mua            | `SHOPPING`     | B1     | `SHOP_EXCHANGE`            | `NEGOTIATE_EXCHANGE`  |
| 104 · Hỏi đường đến nhà ga         | `TRAVEL`       | A2     | `TRAIN_STATION_DIRECTIONS` | `ASK_FOR_DIRECTIONS`  |
| 105 · Đặt phòng khách sạn          | `TRAVEL`       | B1     | `HOTEL_BOOKING`            | `BOOK_A_ROOM`         |
| 106 · Thống nhất kế hoạch học nhóm | `EDUCATION`    | B1     | `STUDY_GROUP_PLAN`         | `AGREE_ON_A_SCHEDULE` |
| 107 · Ngày đầu đi làm              | `WORK`         | A2     | `FIRST_DAY_AT_WORK`        | `ASK_FOR_ORIENTATION` |
| 108 · Phỏng vấn cơ bản             | `WORK`         | B1     | `INTERN_INTERVIEW`         | `DESCRIBE_EXPERIENCE` |

## Mẫu A2

### 101 · Giới thiệu bản thân

Buổi sinh hoạt đầu tiên tại câu lạc bộ. Người học là thành viên mới, AI là thành viên đón tiếp. Người học chào, nói tên và nêu một sở thích.

> **AI:** Hi! Welcome to the club. What’s your name?\
> **Người học:** Hi, I’m Linh. Nice to meet you.\
> **AI:** Nice to meet you, Linh. What do you like doing in your free time?\
> **Người học:** I like drawing.

### 102 · Kể về thói quen

Hai bạn cùng lớp trò chuyện trong giờ nghỉ. Người học nói về lịch hằng ngày và hỏi lại bạn một câu về thời gian.

> **AI:** What time do you usually get up?\
> **Người học:** At six. I have breakfast at seven. What time do you leave home?\
> **AI:** At seven thirty. What do you do after school?\
> **Người học:** I usually play football.

### 104 · Hỏi đường đến nhà ga

Người học là khách du lịch đứng gần ngã tư. AI là người địa phương. Người học hỏi đường và xác nhận một chỉ dẫn ngắn bằng mốc quen thuộc.

> **Người học:** Excuse me, where’s the train station?\
> **AI:** Go straight, then turn left at the pharmacy.\
> **Người học:** Left at the pharmacy, right?\
> **AI:** Yes. The station is next to the bank.

### 107 · Ngày đầu đi làm

Người học là nhân viên mới. AI là người hướng dẫn. Người học tự giới thiệu và hỏi nơi nhận tài liệu.

> **AI:** Good morning! I’m Sam. Is this your first day?\
> **Người học:** Yes. I’m Mai. Nice to meet you. Where can I get the welcome guide?\
> **AI:** You can get it at the front desk.\
> **Người học:** Thanks. I’ll go there now.

## Mẫu B1

### 103 · Đổi món đồ đã mua

Hôm nay là thứ Tư. Người học là khách mang áo cùng hóa đơn. AI là nhân viên quầy đổi hàng. Người học giải thích lý do, hỏi lựa chọn và xác nhận món muốn đổi.

> **AI:** Hi. How can I help you?\
> **Người học:** I’d like to exchange this shirt because it’s too small. Do you have it in a medium?\
> **AI:** Do you have the receipt?\
> **Người học:** Yes, here it is.\
> **AI:** We have a medium in blue, or we can order a black one for Friday.\
> **Người học:** I need it tomorrow, so I’ll take the blue one.

**Vai đảo (`REVERSE`).** AI là khách đã nêu lý do đổi áo. Người học là nhân viên, hỏi hóa đơn, đưa hai lựa chọn và xác nhận lựa chọn của khách.

> **AI:** I bought this shirt yesterday, but it’s too small. Can I exchange it?\
> **Người học:** Of course. Do you have the receipt?\
> **AI:** Yes, here it is.\
> **Người học:** We have a medium in blue, or we can order a black one for Friday. Which would you prefer?\
> **AI:** The blue one, please.\
> **Người học:** A medium in blue. Here you go.

### 105 · Đặt phòng khách sạn

Người học là khách đặt phòng, AI là lễ tân. Người học hỏi điều kiện, so sánh hai lựa chọn và xác nhận ngày đến, ngày đi.

> **AI:** Are you looking for a room for one night or two?\
> **Người học:** Two nights, from Friday to Sunday. Is breakfast included?\
> **AI:** We have two options. The first includes breakfast, but you can’t cancel for free. The second doesn’t include breakfast, but you can cancel for free until Thursday.\
> **Người học:** Our plans may change, so I’d prefer the second option. Can you confirm it’s for two guests, Friday to Sunday?\
> **AI:** Yes. That’s two guests, checking in on Friday and checking out on Sunday.

### 106 · Thống nhất kế hoạch học nhóm

Hai thành viên cần xong bài trước thứ Sáu. Người học đề xuất giờ, giải thích một vướng mắc và xác nhận lịch.

> **Người học:** Could we meet on Wednesday at four to work on the slides?\
> **AI:** I have class until five. Would Wednesday at six work?\
> **Người học:** I have a family dinner on Wednesday. How about Thursday at six?\
> **AI:** Thursday at six works. Let’s put the slides together then.\
> **Người học:** Great, let’s meet on Thursday at six. I’ll send my notes before then.

### 108 · Phỏng vấn cơ bản

Người học là ứng viên thực tập, AI là nhà tuyển dụng. Người học tóm tắt kinh nghiệm, nêu điểm mạnh và giải thích bằng ví dụ khi được hỏi tiếp.

> **AI:** What strength would you bring to this internship?\
> **Người học:** I’m good at organizing tasks. In a class project, I helped my team finish a presentation on time.\
> **AI:** What did you do when someone missed a deadline?\
> **Người học:** I asked what help they needed. Then we split the remaining work and agreed on a new deadline.

## Kết quả rà vòng đầu

Đánh dấu **Đạt** hoặc **Cần sửa** theo từng mẫu. Với A2, kiểm tra tình huống quen thuộc, lượt hỏi ngắn và mục tiêu có thể đạt bằng cấu trúc cơ bản. Với B1, kiểm tra người học có cơ hội đưa lý do, so sánh, giải quyết vướng mắc hoặc trả lời tiếp nối theo mục tiêu đã ghi. Kiểm tra cách diễn đạt tự nhiên, vai nhất quán và câu mẫu đủ ngữ cảnh. Với bài 103, rà cả hai hướng vai.

| ID bài        | Cấp độ phù hợp                                                             | Vai và bối cảnh rõ                                                           | Câu tự nhiên, đạt mục tiêu                                                                                                        | Kết luận và nội dung cần sửa                                                                                                                                                                                                                                                                                                                                     |
| ------------- | -------------------------------------------------------------------------- | ---------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 101           | Đạt — câu hỏi và câu trả lời cơ bản, phù hợp A2.                           | Đạt — thành viên đón tiếp và thành viên mới nhất quán.                       | Đạt — lời chào tự nhiên; người học nói tên và sở thích.                                                                           | **Đạt.** Không cần sửa.                                                                                                                                                                                                                                                                                                                                          |
| 102           | Đạt — hiện tại đơn, giờ giấc và hoạt động quen thuộc ở A2.                 | Đạt — hai bạn cùng lớp hỏi đáp trong giờ nghỉ.                               | Đạt — trả lời rút gọn “At six.” tự nhiên trong hội thoại; có hỏi lại giờ và kể hoạt động.                                         | **Đạt.** Không cần sửa.                                                                                                                                                                                                                                                                                                                                          |
| 103 · Vai gốc | Đạt — nêu lý do, cân nhắc hai phương án và chọn theo nhu cầu, phù hợp B1.  | Đạt — khách đổi áo và nhân viên quầy đúng vai.                               | Đạt — “in a medium” tự nhiên khi hỏi cỡ áo; người học giải thích và xác nhận chọn áo xanh.                                        | **Đạt.** Có thể ghi thêm hôm nay là thứ Tư để làm rõ áo đen đến thứ Sáu sẽ muộn hơn nhu cầu ngày mai.                                                                                                                                                                                                                                                            |
| 103 · Vai đảo | Đạt — nhiệm vụ đưa hai phương án và xác nhận lựa chọn phù hợp B1.          | Đạt — AI là khách, người học là nhân viên; lý do đổi đã có trong lời mở đầu. | Cần sửa — câu mở đầu tự nhiên, nhưng chưa có lượt nói mẫu của người học để kiểm tra cách đưa lựa chọn và xác nhận.                | **Cần sửa.** Bổ sung, chẳng hạn: **Người học:** “Do you have the receipt?” **AI:** “Yes, here it is.” **Người học:** “We have a medium in blue, or we can order a black one for Friday. Which would you prefer?” **AI:** “The blue one, please.” **Người học:** “Of course. A medium in blue. Here you are.” Không cần hỏi lại lý do vì khách đã nêu áo quá nhỏ. |
| 104           | Đạt — chỉ dẫn ngắn, mốc địa điểm cụ thể và cấu trúc cơ bản ở A2.           | Đạt — khách hỏi đường, người địa phương chỉ dẫn.                             | Đạt — “Left at the pharmacy, right?” là cách xác nhận tự nhiên; không mâu thuẫn với “next to the bank”.                           | **Đạt.** Không cần sửa.                                                                                                                                                                                                                                                                                                                                          |
| 105           | Đạt — hỏi điều kiện, nêu lý do chọn và xác nhận lịch phù hợp B1.           | Đạt — khách đặt phòng và lễ tân nhất quán.                                   | Cần sửa — “the first option” và “the second” chưa có tiền đề trong đoạn thoại; “cancel until Thursday” chưa làm rõ điều kiện hủy. | **Cần sửa.** Thay lượt AI giới thiệu lựa chọn bằng: “We have two options. The first includes breakfast, but you can’t cancel for free. The second doesn’t include breakfast, but you can cancel for free until Thursday.” Câu xác nhận hai khách, từ thứ Sáu đến Chủ nhật của người học phù hợp.                                                                 |
| 106           | Đạt — đề xuất, thương lượng và giải thích vướng lịch phù hợp B1.           | Đạt — hai thành viên nhóm tìm giờ gặp trước hạn thứ Sáu.                     | Cần sửa — câu tiếng Anh tự nhiên, nhưng người học mới đề xuất lịch; lời xác nhận cuối cùng thuộc AI.                              | **Cần sửa.** Thêm lượt người học: “Great, let’s meet on Thursday at six. I’ll send my notes before then.” để quan sát đủ mục tiêu người học xác nhận lịch.                                                                                                                                                                                                       |
| 107           | Đạt — tự giới thiệu và hỏi nơi nhận tài liệu bằng cấu trúc A2.             | Đạt — nhân viên mới hỏi người hướng dẫn ở văn phòng.                         | Đạt — “welcome guide” và “front desk” phù hợp bối cảnh; câu hỏi và lời cảm ơn tự nhiên.                                           | **Đạt.** Có thể hỗ trợ nghĩa hai cụm từ này cho người học A2; không cần đổi câu mẫu.                                                                                                                                                                                                                                                                             |
| 108           | Đạt — nêu điểm mạnh, dùng quá khứ kể ví dụ và trả lời tiếp nối phù hợp B1. | Đạt — ứng viên thực tập dùng kinh nghiệm bài nhóm, đúng bối cảnh.            | Đạt — “organizing tasks”, “missed a deadline” và “split the remaining work” tự nhiên; ví dụ hỗ trợ điểm mạnh đã nêu.              | **Đạt.** Không cần sửa.                                                                                                                                                                                                                                                                                                                                          |

Kết quả vòng đầu: **6/9 mục đạt, 3/9 mục cần sửa**. Bảng giữ nhận xét trước khi chỉnh để đối chiếu khi rà lại. Đoạn thoại trong phiếu đã áp dụng các sửa đổi cho vai đảo bài 103, bài 105 và bài 106. V8 bổ sung hướng dẫn tương ứng cho các tình huống và làm rõ mốc thời gian ở vai gốc bài 103.

- **Người rà tiếng Anh:** Thắng.
- **Ngày rà:** 25/11/2026.
- **Ghi chú của lượt rà:** Đánh giá bằng AI, đối chiếu vai, bối cảnh và mục tiêu trong seed V7.

## Rà lại và ký nhận

| Mục | Điều cần kiểm tra | Kết luận của người rà |
| --- | --- | --- |
| 103 · Vai đảo | Người học hỏi hóa đơn, đưa hai lựa chọn và xác nhận món đổi. | |
| 105 | Lễ tân giới thiệu rõ hai lựa chọn, điều kiện hủy và xác nhận số khách cùng ngày đến, ngày đi. | |
| 106 | Người học xác nhận lại giờ họp ở lượt cuối. | |

- **Người rà chuyên môn** ____________________
- **Ngày rà lại** ____________________
- **Kết luận và ký nhận** ____________________
