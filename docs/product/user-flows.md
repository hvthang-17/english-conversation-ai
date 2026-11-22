# Luồng người dùng

Thiết kế bảng và ranh giới giao dịch tương ứng nằm trong [workflow dữ liệu](../architecture/database-workflows.md); quy tắc dữ liệu được quản lý tại [database](../architecture/database.md).

## Tài khoản — đã triển khai AUTH-01
Đăng ký email/mật khẩu → đăng nhập → xem tài khoản. Đổi mật khẩu cần mật khẩu hiện tại và xác nhận phía giao diện → kết thúc tất cả phiên → đăng nhập lại. Logout chỉ kết thúc phiên thiết bị hiện tại. Phiên hết hạn/khóa/đổi quyền trả 401 và giao diện yêu cầu đăng nhập lại; backend kiểm tra quyền độc lập với route hash của React.

Hợp đồng [API auth](../api/auth.md), quy trình [bootstrap admin](../development/phase1/README.md).

## Hồ sơ và chọn bài — đã triển khai P2
Đăng nhập → hồ sơ mặc định A2 → sửa tên/trình độ/mục tiêu/chủ đề → lưu → lọc tình huống theo trình độ/chủ đề → đọc bối cảnh/vai/mục tiêu → chọn bài. Backend kiểm tra lại tình huống và chụp snapshot khi mở buổi. Màn hình chi tiết cho chọn **Chat văn bản** hoặc **Luyện nghe và nói** trước khi bắt đầu. [ADR 0009](../decisions/0009-practice-modes.md)

## Chat văn bản
Chọn tình huống/trình độ → chọn **Chat văn bản** → thấy câu mở đầu của AI → nhập và gửi câu → thấy câu AI tiếp theo. Nút nghe là tùy chọn.

Khi đang chờ, không tạo lượt mới ngoài ý muốn. Gửi lỗi phải phân biệt với đã nhận và đang xử lý; thao tác thử lại không được tạo bản sao.

ADR 0005 chốt tối đa 20 lượt người học/buổi, opening không tính; có yêu cầu gợi ý khi bí câu, gợi ý không thêm lượt/tin nhắn vào transcript chính. Phản hồi sửa lỗi tập trung cuối buổi.

## Luyện nghe và nói

1. Chọn **Luyện nghe và nói**. Câu mở đầu hiển thị dưới dạng thẻ **Có câu AI mới** với nút **Nghe câu AI**. Có thể nghe lại hoặc nghe chậm.
2. Khi cần trợ giúp, bấm **Xem phụ đề** để đọc câu đó. Có thể ẩn lại. Câu tiếp theo bắt đầu với phụ đề đóng, kể cả khi câu trước đã được mở.
3. Bấm **Ghi câu trả lời**. Xem thông báo xử lý dữ liệu, cấp quyền micro, ghi, nghe lại hoặc hủy. Bấm gửi bản ghi để chép lời. Kiểm tra, sửa và xác nhận bản chép lời của chính mình trước khi tính một lượt.
4. Câu AI tiếp theo xuất hiện dưới dạng thẻ nghe mới. Người học chủ động phát, tiếp tục trả lời hoặc kết thúc.

Nếu không thể ghi hoặc chép lời, **Nhập câu trả lời bằng bàn phím** mở ô gõ cho lượt này, giữ nguyên trạng thái phụ đề và tự thu gọn khi câu được chấp nhận. Nếu không thể phát, báo lỗi và cho chọn **Xem phụ đề** hoặc **Chuyển sang chat văn bản** cho phần còn lại của buổi. Hủy/thoát phải dừng micro và giải phóng tài nguyên. Gợi ý khi bí câu đưa cách bắt đầu câu trả lời mà không tự mở phụ đề.

Không tự gửi bản chép lời chưa xác nhận. Khi không có micro hoặc xử lý âm thanh lỗi, người học có thể chuyển sang nhập văn bản. Hủy/thoát phải dừng micro và giải phóng tài nguyên.

## Kết thúc và phản hồi
Kết thúc → chốt trạng thái buổi → mở toàn bộ hội thoại để đọc đối chiếu và nghe lại khi có giọng phù hợp → tạo phản hồi → hiển thị điểm tốt, các mục sửa và từ/mẫu câu → người học chọn lưu để ôn.

Phân tích lỗi: giữ nguyên hội thoại và cho tạo lại phản hồi. Buổi hoàn thành và trạng thái tạo phản hồi là hai trạng thái riêng.

## Lịch sử và xóa
Xem danh sách → mở buổi thuộc sở hữu → đọc lại → chọn xóa → hiển thị tác động theo chính sách → xác nhận → xóa dữ liệu liên quan đúng chính sách.

Xóa một buổi giữ các mục ôn đã lưu nhưng bỏ nguồn; thao tác xóa toàn bộ dữ liệu học tập bao gồm cả mục ôn/lịch sử ôn. Dọn audio ngoài hệ thống có thể tiếp tục sau transaction xóa, phải thông báo và theo dõi phù hợp thay vì khẳng định đã xóa mọi bản sao provider ngay.

## Ôn tập và quản trị
Ôn tập: mở mục đã lưu → tự nhớ/xem đáp án → đánh dấu trạng thái.

Bản đầu dùng thẻ ghi nhớ. Điền từ/lịch ôn tự động để sau. Thông báo và vòng đời audio xem tại [chính sách dữ liệu](data-and-browser-proposal.md). Kiểm thử thiết bị thật được theo dõi trong [báo cáo P6](../development/phase6/README.md).

Quản trị: đăng nhập với quyền admin → mở **Quản trị** → xem số liệu tổng hợp, tìm tài khoản để khóa/mở hoặc quản lý chủ đề, tình huống và trình độ. Khi khóa, hệ thống thu hồi các phiên hiện có. Khi sửa hoặc ẩn tình huống, các buổi trước giữ snapshot. [Hướng dẫn P7](../development/phase7/README.md)
