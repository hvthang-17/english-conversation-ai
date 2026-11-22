# ADR 0001: Kiến trúc và công nghệ

## Trạng thái
Chấp nhận stack chính; chi tiết bên dưới còn mở. Đây là quyết định thiết kế, không phải bằng chứng đã triển khai.

## Bối cảnh
Đồ án website luyện giao tiếp A2–B1 cần quản lý tài khoản, hội thoại theo lượt, phản hồi, ôn tập và thống kê. Người dùng xác định backend Java/Spring Boot và database MySQL.

## Quyết định
- React + TypeScript + Vite cho frontend.
- Java + Spring Boot, Maven, Spring Security, Spring Data JPA/Hibernate cho backend.
- MySQL và Flyway cho dữ liệu/migration.
- Xác thực bằng Session cookie + Spring Security + Spring Session JDBC/MySQL theo ADR 0004; chưa triển khai AUTH-01.
- Backend một ứng dụng chia module; Gemini chỉ gọi từ server.
- Thu âm theo lượt; bản chép lời được người học xác nhận.
- Bốn skill nội bộ và tài liệu repo-local hỗ trợ Codex.
- Bổ sung năm skill upstream theo nguồn người dùng chọn; nội dung chính tại agents/skills/, .agents/skills là junction khám phá local. Revision và phạm vi trong docs/development/agents.md.

## Lý do và hệ quả
Phù hợp stack được chọn và phạm vi đồ án; giảm vận hành so với microservices. Tích hợp âm thanh/Gemini cần thử nghiệm riêng về tương thích, độ trễ và chi phí. Không huấn luyện mô hình.

## Quyết định đang mở
| Nội dung | Thời điểm cần chốt |
| --- | --- |
| Java, Spring Boot, Node, MySQL, base package | Đã chốt tại ADR 0002; SDK Gemini còn mở |
| Xác thực và vòng đời phiên | ADR 0004/0005 chốt Session JDBC, idle 30 phút, nhiều thiết bị; còn hiện thực CSRF/cookie theo môi trường |
| Package manager, Tailwind/UI kit và thư viện frontend | Đã chọn npm, CSS cơ bản; UI kit/Tailwind chỉ thêm khi có nhu cầu |
| Model và hạn mức Gemini, audio codec, timeout/retry | Trước tích hợp AI |
| Quy tắc tài khoản/buổi luyện/ôn tập | Bảy lựa chọn đã chốt tại ADR 0005; tối đa 20 lượt, có gợi ý, FLASHCARD trước |
| Browser hỗ trợ và thông báo âm thanh | Đã chốt ở ADR 0006; P0 có micro Chrome desktop, toàn matrix kiểm tra tiếp |
| speechSynthesis là phương án đầu, chưa bảo đảm giọng đồng nhất | Trước PLAYBACK-01 |
| Xóa lịch sử/retention | ADR 0003/0006 chốt giữ thẻ khi xóa buổi, audio/nháp/log/backup; retention provider và xóa tài khoản cần làm rõ riêng |
| Hạ tầng/port/domain/HTTPS, backup, monitoring | Trước runtime/triển khai |
| Lịch và giấy phép | Hạn 28/12/2026, nhóm 2 người × 5 giờ/ngày × 5 ngày/tuần; kế hoạch lịch cụ thể đã cập nhật, giấy phép còn mở |

Các chi tiết triển khai thông thường có thể chọn trong tác vụ được giao và ghi lại. Quyết định thay đổi phạm vi hoặc tác động dữ liệu cá nhân cần được làm rõ với chủ dự án khi chưa có căn cứ.
