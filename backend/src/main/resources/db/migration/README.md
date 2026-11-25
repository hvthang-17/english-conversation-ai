# Flyway migrations

Dòng hiện tại dùng Flyway trên MySQL. Bắt đầu từ schema rỗng hoặc schema có lịch sử của dòng này.

| Phiên bản | Phạm vi |
| --- | --- |
| V1 | Tài khoản và hồ sơ người học |
| V2 | Hai bảng Session JDBC từ Spring Session JDBC 4.1.1 |
| V3 | Chủ đề và sở thích của người học |
| V4 | Token đặt lại mật khẩu, outbox, rate limit, lịch và job xóa tài khoản |
| V5 | Sáu chủ đề ban đầu để người học thiết lập sở thích |
| V6 | Tình huống, cấp độ, vai thay thế, bài học và dấu cá nhân |
| V7 | Tám bài A2/B1 cùng tình huống trên sáu chủ đề hiện có |
| V8 | Đồng bộ bối cảnh và hướng dẫn của các tình huống đổi hàng, đặt phòng và học nhóm với phiếu rà |
| V9 | Tạo bảng từ vựng, hội thoại mẫu, sổ tay và lượt ôn |
| V10 | Kiểm tra cấu trúc rồi nạp 40 mục từ/mẫu câu và tám hội thoại mẫu từ `content/starter-language-materials.json` |
| V11 | Tạo đề luyện nghe và lượt làm với phiên bản nháp |
| V12 | Kiểm tra và nạp tám đề nghe ở trạng thái DRAFT từ `content/starter-listening-exercises.json` |

| V13 | Tạo tác vụ AI và nhật ký từng lần gọi, giữ chỗ cho liên kết buổi G6 |
| V14 | Tạo bài và lượt phát âm theo mẫu, FK tới tác vụ AI |
| V15 | Nạp chín bài phát âm từ, câu và đoạn ngắn cho tám bài học |
| V16 | G6 tạo buổi, tin nhắn, audio, phản hồi và giải thích, dùng lại tác vụ AI V13 |

| V17 | G6 liên kết bản ghi hội thoại với lượt chấm UNSCRIPTED |
| V18 | Nhật ký quản trị G10.1 |
| V19 | Phân biệt chủ đề nháp và chủ đề đã ẩn cho G10.2 |

Profile `g6` mặc định khi chạy Maven, áp tới V19. Profile `g5` áp tới V15. Profile `g4` áp tới V12. Profile `g3` áp tới V10, `g2` áp tới V8, `g1` áp tới V5 và `bootstrap,p0-db` áp tới V1 để tái lập các mốc trên schema riêng. V10 và V12 dùng checksum của JSON. Khi sửa nội dung đã nạp, tạo phiên bản đề mới bằng migration. Schema có lịch sử từ dòng cũ cần xác nhận cách xử lý dữ liệu riêng trước khi áp dụng dòng này.

[Thiết kế 46 bảng](../../../../../../docs/architecture/database.md) · [Kết quả P0 trên MySQL](../../../../../../docs/development/phase0/mysql.md)
