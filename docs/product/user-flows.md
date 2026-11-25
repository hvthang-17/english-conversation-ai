# Luồng người dùng

Thiết kế bảng và ranh giới giao dịch tương ứng nằm trong [workflow dữ liệu](../architecture/database.md#6-luồng-dữ-liệu); quy tắc dữ liệu được quản lý tại [database](../architecture/database.md).

## Tài khoản — đã triển khai AUTH-01
Đăng ký email/mật khẩu → đăng nhập → xem tài khoản. Đổi mật khẩu cần mật khẩu hiện tại và xác nhận phía giao diện → kết thúc tất cả phiên → đăng nhập lại. Logout chỉ kết thúc phiên thiết bị hiện tại. Phiên hết hạn/khóa/đổi quyền trả 401 và giao diện yêu cầu đăng nhập lại; backend kiểm tra quyền độc lập với route hash của React.

Hợp đồng [API auth](../api/auth.md), quy trình [Kế hoạch G1](../development/full-system-implementation-plan.md#g1--tài-khoản-và-hồ-sơ-học-viên).

## Hồ sơ và chọn bài — đã triển khai P2
Đăng nhập → hồ sơ mặc định A2 → sửa tên/trình độ/mục tiêu/chủ đề → lưu → lọc tình huống theo trình độ/chủ đề → đọc bối cảnh/vai/mục tiêu → chọn bài. Backend kiểm tra lại tình huống và chụp snapshot khi mở buổi. Màn hình chi tiết cho chọn **Chat văn bản** hoặc **Luyện nghe và nói** trước khi bắt đầu. [ADR 0009](../decisions/0009-practice-modes.md)

## Luyện nghe phục vụ giao tiếp (G4)

Mở đề công khai từ menu **Luyện nghe** hoặc trong chi tiết bài → bắt đầu hoặc tiếp tục → nghe ở 1× hoặc 0,75× → lưu nháp hoặc nộp bài → xem từng đáp án, điểm, lời thoại và bản dịch. Nháp đã lưu có thể tiếp tục trên thiết bị khác. Nếu thiết bị khác đã lưu thay đổi mới hơn, tải lại bản nháp từ máy chủ. Xem điểm và lời thoại của lượt đã nộp trong **Lịch sử luyện nghe**. Làm lại mở lượt mới với phần lời thoại và đáp án ẩn. [API luyện nghe](../api/listening.md)

## Chat văn bản

Ở trang chi tiết tình huống có nhiều vai, chọn **Vai của bạn** để đổi người đối thoại, bối cảnh và mục tiêu hiển thị. Sau đó chọn chế độ và bắt đầu. Muốn luyện vai kia, mở một buổi mới trong cùng bài. [Quyết định chọn vai](../decisions/0011-scenario-role-selection.md)
Chọn tình huống/trình độ → chọn **Chat văn bản** → thấy câu mở đầu của AI → nhập và gửi câu → thấy câu AI tiếp theo. Nút nghe là tùy chọn.

Khi đang chờ, không tạo lượt mới ngoài ý muốn. Gửi lỗi phải phân biệt với đã nhận và đang xử lý; thao tác thử lại không được tạo bản sao.

ADR 0005 chốt tối đa 20 lượt người học/buổi, opening không tính; có yêu cầu gợi ý khi bí câu, gợi ý không thêm lượt/tin nhắn vào transcript chính. Phản hồi sửa lỗi tập trung cuối buổi.

## Luyện nghe và nói

1. Chọn **Luyện nghe và nói**. Câu mở đầu hiển thị dưới dạng thẻ **Có câu AI mới** với nút **Nghe câu AI**. Có thể nghe lại hoặc nghe chậm.
2. Khi cần trợ giúp, bấm **Xem phụ đề** để đọc câu đó. Có thể ẩn lại. Câu tiếp theo bắt đầu với phụ đề đóng, kể cả khi câu trước đã được mở.
3. Bấm **Ghi câu trả lời**. Xem thông báo xử lý dữ liệu, cấp quyền micro, ghi, nghe lại hoặc hủy. Bấm **Gửi bản ghi** để Gemini chép lời. Xem, sửa nếu cần rồi xác nhận để AI trả lời theo nội dung đã xác nhận. Khi dịch vụ chấm phát âm được bật, Azure tự chấm bản ghi gốc tối đa 30 giây còn hạn sau xác nhận. Điểm hiện riêng ở lượt đó và tổng kết.
4. Câu AI tiếp theo xuất hiện dưới dạng thẻ nghe mới. Người học chủ động phát, tiếp tục trả lời hoặc kết thúc.

Nếu không thể ghi hoặc chép lời, **Nhập câu trả lời bằng bàn phím** mở ô gõ cho lượt này, giữ nguyên trạng thái phụ đề và tự thu gọn khi câu được chấp nhận. Nếu không thể phát, giao diện báo lỗi và cho mở **Xem phụ đề** của từng câu. Chế độ được chọn ở màn hình tình huống trước khi bắt đầu buổi. Hủy/thoát phải dừng micro và giải phóng tài nguyên. Gợi ý khi bí câu đưa cách bắt đầu câu trả lời mà không tự mở phụ đề.

Không tự gửi bản chép lời chưa xác nhận. Khi không có micro hoặc xử lý âm thanh lỗi, người học có thể chuyển sang nhập văn bản. Hủy/thoát phải dừng micro và giải phóng tài nguyên.

## Kết thúc và phản hồi
Kết thúc → chốt trạng thái buổi → mở toàn bộ hội thoại để đọc đối chiếu và nghe lại khi có giọng phù hợp → tạo phản hồi → hiển thị điểm tốt, các mục sửa và từ/mẫu câu → người học chọn lưu để ôn.

Trong buổi đang luyện, **Giải thích câu này** trên câu AI gần nhất cho xem cách nói đơn giản hơn. Người học có thể mở nghĩa tiếng Việt khi cần. Ở chế độ nghe, phụ đề gốc vẫn do người học điều khiển. Tổng kết hiển thị tối đa ba nhóm lỗi lặp ở ít nhất hai câu người học, mỗi nhóm có một ví dụ sửa.

Buổi mới kết thúc cần ít nhất một lượt người học được AI trả lời thành công để tính hoàn thành và tạo nhận xét. Nếu chưa có lượt như vậy, giao diện giải thích lý do và vẫn cho đọc hội thoại đã lưu. Khi mở lại buổi VOICE đang luyện, hệ thống khôi phục bản chép lời gốc còn hạn hoặc tiếp tục theo dõi lần chép đang xử lý.

Phân tích lỗi: giữ nguyên hội thoại và cho tạo lại phản hồi. Buổi hoàn thành và trạng thái tạo phản hồi là hai trạng thái riêng.

## Lịch sử và xóa
Xem danh sách → mở buổi thuộc sở hữu → đọc lại → chọn xóa → hiển thị tác động theo chính sách → xác nhận → xóa dữ liệu liên quan đúng chính sách.

Xóa một buổi giữ các mục ôn đã lưu nhưng bỏ nguồn; thao tác xóa toàn bộ dữ liệu học tập bao gồm cả mục ôn/lịch sử ôn. Dọn audio ngoài hệ thống có thể tiếp tục sau transaction xóa, phải thông báo và theo dõi phù hợp thay vì khẳng định đã xóa mọi bản sao provider ngay.

## Ôn tập và quản trị
Trong G3, người học mở bài → xem từ, mẫu câu và IPA → nghe giọng tiếng Anh cục bộ theo câu hoặc cả đoạn. Mặc định hiện lời tiếng Anh và ẩn bản dịch. Có thể bật/tắt từng phần, đổi tốc độ và nghe lại. Khi thiếu giọng, giao diện hiện thông báo và vẫn hiển thị văn bản để học.

Lưu từ hoặc mẫu câu → mở sổ tay → tự nhớ và lật flashcard → đánh dấu **Cần ôn / Đã nhớ**. Với quiz chọn nghĩa, người học chọn đáp án rồi nhận kết quả backend chấm. Khi thiếu lựa chọn phù hợp, giao diện cho ôn bằng flashcard. Xóa thẻ xóa các lượt ôn của thẻ đó. Điền từ và lịch ôn tự động thuộc các giai đoạn sau. [Quyết định G3](../development/full-system-implementation-plan.md#g3--từ-vựng-mẫu-câu-hội-thoại-mẫu-và-ôn-tập).

Quản trị: đăng nhập với quyền admin → mở **Quản trị** → xem số liệu tổng hợp, tìm tài khoản để khóa/mở hoặc quản lý chủ đề, tình huống, trình độ và biến thể vai. Trước khi công bố tình huống, admin rà chỉ dẫn AI theo vai mặc định và từng biến thể. Khi khóa, hệ thống thu hồi các phiên hiện có. Khi sửa hoặc ẩn tình huống, các buổi trước giữ snapshot. [Kế hoạch G10](../development/full-system-implementation-plan.md#g10--quản-trị-hệ-thống)

## Luồng bổ sung đã có mã triển khai — tài khoản và lịch học

Đăng ký → đăng nhập; quên mật khẩu → liên kết đặt lại; Hồ sơ → thay/xóa avatar và tạo lịch tuần → bật email nhắc học; Tài khoản → xác nhận mật khẩu và phạm vi xóa → thu hồi phiên → job dọn dữ liệu. Chi tiết màn hình, trạng thái, giới hạn và trường hợp lỗi tại [đặc tả mở rộng](requirements.md). Đến giờ lịch không tự tạo buổi; mail dẫn tới chọn bài sau đăng nhập. Xóa dữ liệu học tập giữ lịch/avatar; xóa tài khoản hủy lịch và dọn avatar.

### Chi tiết luồng khôi phục và lịch

Luồng khôi phục: nhập email → thông báo chung → mở liên kết → nhập và xác nhận mật khẩu mới → POST token/mật khẩu → yêu cầu đăng nhập lại. Token ngẫu nhiên mật mã ít nhất 256 bit, lưu SHA-256, ràng buộc user/email, một lần dùng. Token reset có hạn 30 phút. Phát hành lại vô hiệu token reset cũ; tiêu thụ nguyên tử chống race. Không ghi token, liên kết hoặc mật khẩu vào log. Giới hạn yêu cầu reset theo email/IP và giới hạn thử token theo IP. Response không tiết lộ sự tồn tại tài khoản qua nội dung hoặc khác biệt công việc đồng bộ rõ rệt.

Link token đặt trong fragment của URL frontend; trang đặt lại mật khẩu không tải tài nguyên bên thứ ba, dùng Referrer-Policy: no-referrer, bỏ token khỏi URL sau khi đọc, chỉ giữ trong bộ nhớ. Không lưu localStorage/sessionStorage. Nếu email thất bại, trả thông báo chung và cho gửi yêu cầu mới theo hạn mức.

Luồng lịch: mở Hồ sơ → tải lịch hoặc trạng thái chưa thiết lập → chọn ngày/giờ/múi giờ → chủ động bật email → lưu → hiển thị tóm tắt và lần dự kiến tiếp theo. Tạm dừng giữ cấu hình. Đổi lịch chỉ áp dụng tương lai và hủy các lần cũ chưa gửi; không gửi bù hàng loạt. Ngày có giờ không tồn tại do DST thì bỏ lần đó; giờ lặp thì chỉ nhắc một lần ở lần xuất hiện đầu. UI có tải/lỗi/thử lại/thành công, label và sử dụng bàn phím/màn hình nhỏ.
