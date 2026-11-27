INSERT INTO scenario_role_variants (
    scenario_id, code, learner_role, ai_role, communication_goal, context_description, ai_instruction
)
SELECT s.id, data.variant_code, data.learner_role, data.ai_role,
       data.goal, data.context, data.instruction
FROM (
    SELECT 'DAILY_ROUTINE' AS scenario_code, 'Bạn cùng lớp' AS default_role,
           'ROUTINE_INTERVIEWER' AS variant_code,
           'Bạn cùng lớp hỏi về thói quen' AS learner_role, 'Bạn cùng lớp kể về thói quen' AS ai_role,
           'Hỏi bạn về giờ thức dậy và việc làm sau giờ học, rồi nhắc lại một thông tin để xác nhận.' AS goal,
           'Trong giờ nghỉ, bạn muốn biết một ngày đi học của bạn cùng lớp. Bạn chủ động hỏi về buổi sáng và sau giờ học.' AS context,
           'Play a classmate talking about your daily routine. Open with a short greeting and offer to tell the learner about your day. Let the learner ask the questions. Answer one question at a time in short A2 present-simple sentences and confirm one detail when they repeat it.' AS instruction
    UNION ALL SELECT 'STUDY_GROUP_PLAN', 'Thành viên nhóm', 'GROUP_COORDINATOR',
           'Người điều phối nhóm', 'Thành viên báo lịch rảnh',
           'Hỏi lịch rảnh, đề xuất hai khung giờ và xác nhận ngày giờ họp đã thống nhất.',
           'Bạn điều phối nhóm có bài nộp vào thứ Sáu. Một thành viên chỉ rảnh sau 17 giờ vào thứ Tư hoặc thứ Năm. Bạn cần chốt một buổi họp.',
           'Play a study group member who is free after 5 p.m. on Wednesday or Thursday. Open by asking to agree on a meeting before Friday. Let the learner lead the scheduling, explain one constraint, and ask the learner to confirm the agreed day and time. Use B1 English.'
) AS data JOIN scenarios s ON s.code = data.scenario_code
AND CAST(s.learner_role AS BINARY) = CAST(data.default_role AS BINARY)
AND CAST(s.ai_role AS BINARY) = CAST(data.default_role AS BINARY)
WHERE s.created_by IS NULL
  AND NOT EXISTS (SELECT 1 FROM scenario_role_variants v WHERE v.scenario_id = s.id);
