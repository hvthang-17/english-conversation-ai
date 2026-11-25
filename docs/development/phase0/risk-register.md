# Rủi ro và điều kiện nghiệm thu

Theo [kế hoạch 10 nhóm](../full-system-implementation-plan.md), các mốc đóng khi có bằng chứng đạt. Ước lượng 548 giờ-người gồm dự phòng.

| Rủi ro | Trạng thái | Việc xử lý |
| --- | --- | --- |
| Gemini, Azure và TTS server | Probe offline đạt. Ca thật còn chờ phạm vi quyền gọi. | Chốt model/locale, trần gọi, mẫu hư cấu và chi phí trước thử thật. Dùng kết quả Azure để chốt G5/G6. |
| Ma trận micro/codec | Chrome Windows đạt ghi thật, chuyển WAV, thử TTS local và ghi âm lại khi cấp quyền ở tab khác. Kiểm tra tự động trên Chrome và Edge đạt. | Ghi bằng chứng đặt quyền **Chặn** và chỉ báo micro tắt khi rời trang đang thu trên Chrome Windows. Chạy ma trận thật trên Edge Windows và Chrome Android. [Ma trận](audio.md). |
| MySQL và dòng Flyway mới | V1, restart và ràng buộc đạt trên `english_conversation`, MySQL 9.2 local. | Kiểm tra MySQL 8.4 và Session JDBC ở G1, đủ 46 bảng ở E2E. |
| TTL, xóa và restore | Đã kiểm tra biên TTL bằng fixture. | Kiểm thử worker, xóa khi có kết quả muộn và restore trước khi bàn giao. |
| Chất lượng nội dung và dùng thử | Có 32 fixture A2/B1 và biểu mẫu. | Rà nội dung với người đủ năng lực tiếng Anh và ghi cỡ mẫu/số người thực tế ở E2E. |
