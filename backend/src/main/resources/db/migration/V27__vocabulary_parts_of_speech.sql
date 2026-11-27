ALTER TABLE language_items ADD COLUMN part_of_speech VARCHAR(24) NULL;
ALTER TABLE feedback_suggestions ADD COLUMN part_of_speech VARCHAR(24) NULL;
ALTER TABLE review_items ADD COLUMN part_of_speech VARCHAR(24) NULL;

-- Classify the starter entries by the meaning used in the original lessons.
UPDATE language_items
SET part_of_speech = CASE
  WHEN expression IN ('usually','straight') THEN 'ADVERB'
  WHEN expression IN ('practice','exchange') THEN 'VERB'
  WHEN expression = 'Nice to meet you.' THEN 'EXPRESSION'
  WHEN kind = 'WORD' THEN 'NOUN'
  ELSE 'SENTENCE'
END
WHERE part_of_speech IS NULL AND lesson_id BETWEEN 101 AND 108
  AND (lesson_id,kind,expression,meaning_vi,example_en) IN (
    (101,'WORD','hobby','Sở thích','Drawing is my hobby.'),
    (101,'WORD','member','Thành viên','I’m a new member of the club.'),
    (101,'WORD','club','Câu lạc bộ','I go to the art club on Fridays.'),
    (101,'PHRASE','Nice to meet you.','Rất vui được gặp bạn.','Hi, I’m Linh. Nice to meet you.'),
    (101,'PHRASE','What do you like doing?','Bạn thích làm gì?','What do you like doing after class?'),
    (105,'WORD','reservation','Đặt phòng','May I confirm your reservation?'),
    (105,'WORD','guest','Khách lưu trú','We need a room for two guests.'),
    (105,'WORD','cancellation','Việc hủy đặt chỗ','The price includes free cancellation until Thursday.'),
    (105,'PHRASE','Is breakfast included?','Giá đã bao gồm bữa sáng chưa?','Is breakfast included in the room price?'),
    (105,'PHRASE','What time can we check in?','Chúng tôi có thể nhận phòng lúc mấy giờ?','What time can we check in on Friday?'),
    (102,'WORD','usually','Thường','I usually get up at six.'),
    (102,'WORD','breakfast','Bữa sáng','I have breakfast at seven.'),
    (102,'WORD','practice','Luyện tập','I practice English after school.'),
    (102,'PHRASE','What time do you usually get up?','Bạn thường thức dậy lúc mấy giờ?','On school days, what time do you usually get up?'),
    (102,'PHRASE','What do you do after school?','Bạn làm gì sau giờ học?','What do you do after school on Mondays?'),
    (103,'WORD','receipt','Hóa đơn mua hàng','I brought the receipt with me.'),
    (103,'WORD','exchange','Đổi hàng','Can I exchange this shirt for a larger size?'),
    (103,'WORD','medium','Cỡ vừa','Do you have this shirt in a medium?'),
    (103,'PHRASE','Which would you prefer?','Bạn thích lựa chọn nào hơn?','We have blue or black. Which would you prefer?'),
    (103,'PHRASE','I’ll take the blue one.','Tôi sẽ lấy chiếc màu xanh.','I need it tomorrow, so I’ll take the blue one.'),
    (104,'WORD','station','Nhà ga','The train station is next to the bank.'),
    (104,'WORD','pharmacy','Nhà thuốc','Turn left at the pharmacy.'),
    (104,'WORD','straight','Thẳng về phía trước','Go straight for one block.'),
    (104,'PHRASE','Excuse me, where’s the train station?','Xin lỗi, ga tàu ở đâu?','Excuse me, where’s the train station? Is it near here?'),
    (104,'PHRASE','It’s next to the bank.','Nó ở bên cạnh ngân hàng.','Look for the station. It’s next to the bank.'),
    (106,'WORD','deadline','Hạn chót','The deadline for our project is Friday.'),
    (106,'WORD','slides','Các trang trình chiếu','We can finish the slides together.'),
    (106,'WORD','notes','Ghi chú','I’ll send my notes before we meet.'),
    (106,'PHRASE','Would Thursday work for you?','Thứ Năm có tiện cho bạn không?','Would Thursday work for you instead of Wednesday?'),
    (106,'PHRASE','Let’s meet on Thursday at six.','Chúng ta gặp nhau lúc sáu giờ thứ Năm nhé.','We’re both free then. Let’s meet on Thursday at six.'),
    (107,'WORD','office','Văn phòng','This is my first day at the office.'),
    (107,'WORD','guide','Tài liệu hướng dẫn','The guide is at the front desk.'),
    (107,'WORD','colleague','Đồng nghiệp','I meet my new colleague in the morning.'),
    (107,'PHRASE','Is this your first day?','Đây là ngày đầu đi làm của bạn phải không?','Good morning! Is this your first day?'),
    (107,'PHRASE','Where can I get the welcome guide?','Tôi có thể lấy tài liệu dành cho người mới ở đâu?','I’m new here. Where can I get the welcome guide?'),
    (108,'WORD','strength','Điểm mạnh','My main strength is organizing tasks.'),
    (108,'WORD','internship','Kỳ thực tập','I’m applying for a summer internship.'),
    (108,'WORD','experience','Kinh nghiệm','I have experience working in a team.'),
    (108,'PHRASE','I’m good at organizing tasks.','Tôi giỏi sắp xếp công việc.','I’m good at organizing tasks and setting deadlines.'),
    (108,'PHRASE','Could you give an example?','Bạn có thể đưa ra một ví dụ không?','You mentioned teamwork. Could you give an example?')
  );

UPDATE review_items r JOIN language_items i ON i.id=r.source_language_item_id
SET r.part_of_speech=i.part_of_speech
WHERE r.part_of_speech IS NULL AND i.part_of_speech IS NOT NULL
  AND CAST(r.expression AS BINARY)=CAST(i.expression AS BINARY)
  AND CAST(r.meaning_vi AS BINARY)=CAST(i.meaning_vi AS BINARY)
  AND (CAST(r.example_en AS BINARY) <=> CAST(i.example_en AS BINARY));
