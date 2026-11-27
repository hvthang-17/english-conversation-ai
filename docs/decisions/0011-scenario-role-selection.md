# ADR 0011 — Chọn vai trong cùng tình huống

Một tình huống có vai mặc định từ `scenarios` và có thể có các biến thể trong `scenario_role_variants`. V7 thêm vai đảo cho bài đổi hàng. V23 bổ sung vai đảo cho năm bài mẫu còn lại có hai vai khác nhau, trong đó có bài gặp bạn mới ở câu lạc bộ. V24 bổ sung biến thể còn thiếu khi tình huống mẫu đã được chỉnh nhưng vẫn giữ cặp vai gốc và chưa có biến thể. V25 thêm vai hỏi về thói quen và vai điều phối nhóm để cả tám tình huống mẫu đều có lựa chọn vai. Mỗi lựa chọn có vai người học, vai AI, bối cảnh, mục tiêu và hướng dẫn mở đầu phù hợp với người dẫn dắt cuộc trò chuyện. Các biến thể dùng cùng chủ đề và trình độ đã công khai của tình huống.

Học viên chọn **Vai của bạn** hoặc **Người đối thoại** trước khi bắt đầu. Giao diện giữ vai còn lại nếu cặp đó hợp lệ, hoặc chọn cặp khả dụng và cập nhật bối cảnh, mục tiêu tương ứng. Thao tác tạo buổi nhận `roleCode` cùng tình huống, trình độ và chế độ. Server đọc cấu hình vai từ catalog và chụp vào snapshot, dùng tiếp cho các lượt hội thoại và phản hồi. Mã lựa chọn được lưu trên buổi để kiểm tra gửi trùng. Vai đã chọn cố định trong buổi. Muốn luyện vai kia, học viên mở một buổi mới trong cùng tình huống.

Buổi cũ và client cũ dùng `DEFAULT`. Snapshot cũ giữ nguyên. Danh mục người học chỉ trả trường công khai của vai. Phiên bản này seed biến thể qua Flyway. Tình huống tạo mới từ trang quản trị có vai mặc định cho tới khi được bổ sung biến thể.

[API catalog](../api/profile-catalog.md#catalog) · [API buổi luyện](../api/practice.md)
