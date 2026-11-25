INSERT INTO pronunciation_drills (lesson_id, reference_text, locale, mode, status, sort_order, created_at, updated_at)
SELECT lesson_id, reference_text, 'en-US', mode, 'ACTIVE', sort_order, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM (
    SELECT 101 AS lesson_id, 'Hello.' AS reference_text, 'READ_ALOUD' AS mode, 1 AS sort_order
    UNION ALL SELECT 101, 'How are you?', 'SHADOWING', 2
    UNION ALL SELECT 102, 'I usually have breakfast at seven. Then I walk to class with my friend. In the evening, I read for a few minutes before I go to bed.', 'READ_ALOUD', 1
    UNION ALL SELECT 103, 'I would like to exchange this shirt for a larger size.', 'SHADOWING', 1
    UNION ALL SELECT 104, 'Go straight and turn left at the lights.', 'READ_ALOUD', 1
    UNION ALL SELECT 105, 'I would like a quiet room for two nights. Do you have anything away from the elevator? I can pay a little more if breakfast is included.', 'SHADOWING', 1
    UNION ALL SELECT 106, 'Could we meet after class instead? I have a lab at three.', 'READ_ALOUD', 1
    UNION ALL SELECT 107, 'Could you show me where to start?', 'SHADOWING', 1
    UNION ALL SELECT 108, 'I helped organize a student event last year. I wrote the schedule and answered questions from new volunteers. That experience taught me to explain tasks clearly and listen when someone needed help.', 'READ_ALOUD', 1
) AS seed;
