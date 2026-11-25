-- Refresh the reviewed defaults only while both fields still match V7.
UPDATE scenarios
SET context_description = 'Hôm nay là thứ Tư. Bạn mang áo còn nhãn và hóa đơn đến đổi vì áo quá nhỏ. Cửa hàng có áo xanh cỡ M ngay hoặc có thể đặt áo đen cỡ M cho thứ Sáu.',
    ai_instruction = 'Play the returns clerk. Confirm the reason for the exchange if needed, ask for the receipt, then offer a medium in blue today or a medium in black on Friday. Ask the learner to confirm a choice.',
    updated_at = UTC_TIMESTAMP(6)
WHERE code = 'SHOP_EXCHANGE'
  AND CAST(context_description AS BINARY) = CAST('Bạn mang áo còn nhãn đến quầy cùng hóa đơn. Cửa hàng còn một màu hoặc cỡ khác.' AS BINARY)
  AND CAST(ai_instruction AS BINARY) = CAST('Play the returns clerk. Ask for the reason and receipt, offer two plausible exchange options, then ask the learner to confirm a choice.' AS BINARY);

UPDATE scenario_role_variants v
JOIN scenarios s ON s.id = v.scenario_id
SET v.context_description = 'Hôm nay là thứ Tư. Bạn trực quầy đổi hàng. Khách muốn đổi áo vì cỡ quá nhỏ và có mang hóa đơn. Bạn có sẵn áo xanh cỡ M hoặc có thể đặt áo đen cỡ M cho thứ Sáu.',
    v.ai_instruction = 'Play a customer whose shirt is too small. State the reason in your opening. Show the receipt if asked, let the learner offer two alternatives, then choose one and give the learner room to confirm the exchange.'
WHERE s.code = 'SHOP_EXCHANGE' AND v.code = 'REVERSE'
  AND CAST(v.context_description AS BINARY) = CAST('Bạn trực quầy. Một khách đến đổi áo vì cỡ chưa phù hợp và mang theo hóa đơn.' AS BINARY)
  AND CAST(v.ai_instruction AS BINARY) = CAST('Play a customer with the receipt and a shirt that does not fit. Let the clerk lead and choose one of the options offered.' AS BINARY);

UPDATE scenarios
SET context_description = 'Bạn cần phòng cho hai người từ thứ Sáu đến Chủ nhật. Một gói có bữa sáng nhưng không được hủy miễn phí. Gói kia không có bữa sáng nhưng được hủy miễn phí đến thứ Năm.',
    ai_instruction = 'Play a receptionist. Ask for dates and guest count. Present two complete options: breakfast included without free cancellation, or no breakfast with free cancellation until Thursday. Ask the learner to choose, then confirm the guest count and check-in and check-out dates. Do not ask for real payment data.',
    updated_at = UTC_TIMESTAMP(6)
WHERE code = 'HOTEL_BOOKING'
  AND CAST(context_description AS BINARY) = CAST('Bạn cần phòng cho hai người vào cuối tuần. Một lựa chọn có bữa sáng, lựa chọn kia linh hoạt hơn khi hủy.' AS BINARY)
  AND CAST(ai_instruction AS BINARY) = CAST('Play a receptionist. Ask for dates and guest count, present two contrasting options, and check the final dates without asking for real payment data.' AS BINARY);

UPDATE scenarios
SET ai_instruction = 'Play a study partner. Offer a time, explain one scheduling constraint and invite an alternative. Once a time works for both of you, invite the learner to confirm the agreed day and time in their own words.',
    updated_at = UTC_TIMESTAMP(6)
WHERE code = 'STUDY_GROUP_PLAN'
  AND CAST(context_description AS BINARY) = CAST('Nhóm phải nộp bài vào thứ Sáu. Một thành viên chỉ rảnh sau 17 giờ vào thứ Tư hoặc thứ Năm.' AS BINARY)
  AND CAST(ai_instruction AS BINARY) = CAST('Play a study partner. Offer a time, explain one scheduling constraint, ask for an alternative, then summarize the agreed meeting.' AS BINARY);
