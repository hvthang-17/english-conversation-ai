INSERT INTO scenarios (
    topic_id, code, title, description, learner_role, ai_role, communication_goal,
    context_description, ai_instruction, status, created_at, updated_at
)
SELECT t.id, data.code, data.title, data.description, data.learner_role, data.ai_role,
       data.goal, data.context, data.instruction, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM (
    SELECT 'INTRODUCTION' AS topic, 'CLUB_INTRO' AS code, 'Gặp bạn mới ở câu lạc bộ' AS title,
           'Làm quen trước buổi sinh hoạt đầu tiên.' AS description, 'Thành viên mới' AS learner_role,
           'Thành viên đón tiếp' AS ai_role, 'Chào, nói tên và nêu một sở thích.' AS goal,
           'Bạn tới sớm vài phút trước buổi sinh hoạt đầu tiên. Một thành viên tới chào và hỏi vì sao bạn tham gia.' AS context,
           'Play a friendly club member. Open with a short greeting and ask one simple question at a time about names and hobbies.' AS instruction
    UNION ALL SELECT 'DAILY_LIFE', 'DAILY_ROUTINE', 'Trò chuyện giờ nghỉ',
           'Trao đổi về một ngày điển hình ở trường.', 'Bạn cùng lớp', 'Bạn cùng lớp',
           'Mô tả một ngày và hỏi về giờ sinh hoạt.', 'Trong giờ nghỉ, hai bạn so sánh lịch buổi sáng và việc thường làm sau giờ học.',
           'Play a classmate. Ask about one routine at a time, answer in short present-simple sentences, and invite the learner to ask about time.'
    UNION ALL SELECT 'SHOPPING', 'SHOP_EXCHANGE', 'Đổi hàng tại quầy',
           'Trao đổi lựa chọn khi món đồ đã mua chưa phù hợp.', 'Khách hàng', 'Nhân viên quầy đổi hàng',
           'Nêu lý do đổi hàng, so sánh và chọn phương án.', 'Bạn mang áo còn nhãn đến quầy cùng hóa đơn. Cửa hàng còn một màu hoặc cỡ khác.',
           'Play the returns clerk. Ask for the reason and receipt, offer two plausible exchange options, then ask the learner to confirm a choice.'
    UNION ALL SELECT 'TRAVEL', 'TRAIN_STATION_DIRECTIONS', 'Hỏi đường ở ngã tư',
           'Hỏi và xác nhận đường đến nhà ga.', 'Khách du lịch', 'Người địa phương',
           'Hỏi đường và xác nhận một chỉ dẫn ngắn.', 'Bạn đứng ở ngã tư gần một hiệu thuốc và muốn tới ga tàu bằng cách đi bộ.',
           'Play a passer-by. After the learner asks, give one or two simple directions using a visible landmark and let them confirm.'
    UNION ALL SELECT 'TRAVEL', 'HOTEL_BOOKING', 'Trao đổi với lễ tân khách sạn',
           'Hỏi phòng và so sánh điều kiện đặt.', 'Khách đặt phòng', 'Lễ tân',
           'Hỏi điều kiện, so sánh lựa chọn và xác nhận lịch.', 'Bạn cần phòng cho hai người vào cuối tuần. Một lựa chọn có bữa sáng, lựa chọn kia linh hoạt hơn khi hủy.',
           'Play a receptionist. Ask for dates and guest count, present two contrasting options, and check the final dates without asking for real payment data.'
    UNION ALL SELECT 'EDUCATION', 'STUDY_GROUP_PLAN', 'Hẹn làm bài nhóm',
           'Chọn giờ gặp và phân chia bước tiếp theo.', 'Thành viên nhóm', 'Thành viên nhóm',
           'Đề xuất lịch, giải thích vướng mắc và thống nhất giờ họp.', 'Nhóm phải nộp bài vào thứ Sáu. Một thành viên chỉ rảnh sau 17 giờ vào thứ Tư hoặc thứ Năm.',
           'Play a study partner. Offer a time, explain one scheduling constraint, ask for an alternative, then summarize the agreed meeting.'
    UNION ALL SELECT 'WORK', 'FIRST_DAY_AT_WORK', 'Gặp đồng nghiệp ngày đầu',
           'Tự giới thiệu và hỏi cách bắt đầu ngày làm việc.', 'Nhân viên mới', 'Người hướng dẫn',
           'Giới thiệu ngắn và hỏi nơi nhận hướng dẫn.', 'Bạn đến văn phòng ngày đầu. Người hướng dẫn gặp bạn ở lối vào và có thể chỉ nơi nhận tài liệu.',
           'Play a helpful colleague. Greet the learner, invite a short introduction and answer simple questions about where to go.'
    UNION ALL SELECT 'WORK', 'INTERN_INTERVIEW', 'Phỏng vấn thực tập',
           'Trình bày kinh nghiệm và điểm mạnh trong buổi phỏng vấn.', 'Ứng viên thực tập', 'Nhà tuyển dụng',
           'Tóm tắt kinh nghiệm, nêu điểm mạnh và trả lời câu tiếp nối.', 'Bạn ứng tuyển một vị trí thực tập. Có thể dùng ví dụ từ bài nhóm, câu lạc bộ hoặc dự án cá nhân hư cấu.',
           'Play an interviewer for an internship. Ask one question at a time, then ask for a concrete example related to the learner’s answer.'
) AS data JOIN topics t ON t.code = data.topic;

INSERT INTO scenario_levels (scenario_id, level)
SELECT id, CASE WHEN code IN ('CLUB_INTRO', 'DAILY_ROUTINE', 'TRAIN_STATION_DIRECTIONS', 'FIRST_DAY_AT_WORK')
                THEN 'A2' ELSE 'B1' END
FROM scenarios
WHERE code IN ('CLUB_INTRO', 'DAILY_ROUTINE', 'SHOP_EXCHANGE', 'TRAIN_STATION_DIRECTIONS',
               'HOTEL_BOOKING', 'STUDY_GROUP_PLAN', 'FIRST_DAY_AT_WORK', 'INTERN_INTERVIEW');

INSERT INTO scenario_role_variants (
    scenario_id, code, learner_role, ai_role, communication_goal, context_description, ai_instruction
)
SELECT id, 'REVERSE', 'Nhân viên quầy đổi hàng', 'Khách hàng',
       'Hỏi lý do đổi hàng, đưa hai phương án và xác nhận lựa chọn.',
       'Bạn trực quầy. Một khách đến đổi áo vì cỡ chưa phù hợp và mang theo hóa đơn.',
       'Play a customer with the receipt and a shirt that does not fit. Let the clerk lead and choose one of the options offered.'
FROM scenarios WHERE code = 'SHOP_EXCHANGE';

INSERT INTO lessons (id, topic_id, level, scenario_id, title, summary, objective_code,
                     learning_objective, status, created_at, updated_at)
SELECT data.id, s.topic_id, l.level, s.id, data.title, data.summary, data.objective,
       s.communication_goal, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM (
    SELECT 101 AS id, 'CLUB_INTRO' AS scenario, 'Giới thiệu bản thân' AS title,
           'Luyện lời chào khi gặp một người mới.' AS summary, 'INTRODUCE_YOURSELF' AS objective
    UNION ALL SELECT 102, 'DAILY_ROUTINE', 'Kể về thói quen', 'Hỏi và kể về lịch sinh hoạt trong ngày.', 'DESCRIBE_ROUTINE'
    UNION ALL SELECT 103, 'SHOP_EXCHANGE', 'Đổi món đồ đã mua', 'Giải thích vấn đề và chọn cách đổi món đồ.', 'NEGOTIATE_EXCHANGE'
    UNION ALL SELECT 104, 'TRAIN_STATION_DIRECTIONS', 'Hỏi đường đến nhà ga', 'Xin chỉ đường đi bộ và xác nhận địa điểm.', 'ASK_FOR_DIRECTIONS'
    UNION ALL SELECT 105, 'HOTEL_BOOKING', 'Đặt phòng khách sạn', 'So sánh lựa chọn đặt phòng rồi xác nhận ngày đến và ngày đi.', 'BOOK_A_ROOM'
    UNION ALL SELECT 106, 'STUDY_GROUP_PLAN', 'Thống nhất kế hoạch học nhóm', 'Sắp xếp buổi gặp khi các thành viên có lịch khác nhau.', 'AGREE_ON_A_SCHEDULE'
    UNION ALL SELECT 107, 'FIRST_DAY_AT_WORK', 'Ngày đầu đi làm', 'Làm quen đồng nghiệp và hỏi nơi nhận hướng dẫn.', 'ASK_FOR_ORIENTATION'
    UNION ALL SELECT 108, 'INTERN_INTERVIEW', 'Phỏng vấn cơ bản', 'Nêu điểm mạnh bằng một ví dụ cụ thể.', 'DESCRIBE_EXPERIENCE'
) AS data JOIN scenarios s ON s.code = data.scenario
JOIN scenario_levels l ON l.scenario_id = s.id;
