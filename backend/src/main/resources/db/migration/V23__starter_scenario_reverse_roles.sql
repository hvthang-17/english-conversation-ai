INSERT INTO scenario_role_variants (
    scenario_id, code, learner_role, ai_role, communication_goal, context_description, ai_instruction
)
SELECT s.id, 'REVERSE', s.ai_role, s.learner_role, data.goal, data.context, data.instruction
FROM (
    SELECT 'CLUB_INTRO' AS code, 'Thành viên mới' AS learner_role, 'Thành viên đón tiếp' AS ai_role,
           'Chào thành viên mới, hỏi tên và hỏi về một sở thích.' AS goal,
           'Bạn đón thành viên mới đến buổi sinh hoạt đầu tiên. Hai bạn làm quen trước khi buổi sinh hoạt bắt đầu.' AS context,
           'Play a new club member. Open with a short greeting and say this is your first meeting. Let the learner welcome you and ask your name and about a hobby. Reply in short A2 sentences.' AS instruction
    UNION ALL SELECT 'TRAIN_STATION_DIRECTIONS', 'Khách du lịch', 'Người địa phương',
           'Chỉ đường đến nhà ga bằng một địa điểm dễ nhận ra và kiểm tra khách đã hiểu.',
           'Bạn đứng gần hiệu thuốc ở ngã tư. Một khách du lịch muốn đi bộ tới ga tàu và nhờ bạn chỉ đường.',
           'Play a tourist. Ask how to walk to the train station. Let the learner give directions, then repeat one detail to check that you understood. Use simple A2 English.'
    UNION ALL SELECT 'HOTEL_BOOKING', 'Khách đặt phòng', 'Lễ tân',
           'Hỏi số khách và ngày ở, giải thích hai lựa chọn rồi xác nhận thông tin đặt phòng.',
           'Bạn trực lễ tân. Khách cần phòng cho hai người từ thứ Sáu đến Chủ nhật. Bạn có gói kèm bữa sáng không hủy miễn phí và gói không kèm bữa sáng được hủy miễn phí đến thứ Năm.',
           'Play a guest looking to book a room. Ask about availability and explain your dates and guest count when asked. Compare breakfast and cancellation, choose an option, and let the learner confirm. Use fictional details and no payment data.'
    UNION ALL SELECT 'FIRST_DAY_AT_WORK', 'Nhân viên mới', 'Người hướng dẫn',
           'Chào nhân viên mới và chỉ nơi nhận tài liệu hướng dẫn.',
           'Bạn gặp nhân viên mới ở lối vào văn phòng. Hãy làm quen và chỉ nơi nhận tài liệu cho ngày đầu.',
           'Play a new employee arriving for your first day. Greet the learner and say it is your first day. Let the learner welcome you and ask where to pick up the orientation guide. Use short A2 sentences.'
    UNION ALL SELECT 'INTERN_INTERVIEW', 'Ứng viên thực tập', 'Nhà tuyển dụng',
           'Hỏi về kinh nghiệm, điểm mạnh và đặt một câu tiếp nối dựa trên câu trả lời.',
           'Bạn phỏng vấn một ứng viên thực tập. Ứng viên có thể lấy ví dụ từ một dự án cá nhân hoặc bài nhóm hư cấu.',
           'Play an internship candidate. Greet the interviewer and wait for their first question. Answer with brief fictional examples from a team project, then let the learner ask a follow-up question.'
) AS data JOIN scenarios s ON s.code = data.code
AND s.learner_role = data.learner_role AND s.ai_role = data.ai_role
WHERE s.created_by IS NULL AND s.updated_by IS NULL
  AND NOT EXISTS (SELECT 1 FROM scenario_role_variants v WHERE v.scenario_id = s.id);
