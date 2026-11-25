# Hướng dẫn làm việc trong dự án

## Sản phẩm và công nghệ
Website luyện giao tiếp tiếng Anh cho người học A2–B1, tích hợp Gemini API.
Frontend: React + TypeScript + Vite. Backend: Java + Spring Boot. Database: MySQL.
Xác thực đã chốt: Session cookie + Spring Security + Spring Session JDBC/MySQL theo [ADR 0004](docs/decisions/0004-session-authentication.md), AUTH-01 đã triển khai giai đoạn 1; xem [hợp đồng API](docs/api/auth.md). Không tự thêm JWT/refresh token.
Quy tắc bản đầu tại [ADR 0005](docs/decisions/0005-initial-product-rules.md): idle 30 phút, email chuẩn hóa, 20 lượt, HINT, FLASHCARD trước. [ADR 0006](docs/decisions/0006-phase0-operating-decisions.md) chốt retention/browser/audio 60 giây và hạn nộp 28/12/2026. Phase0 hiện chỉ chuẩn bị Gemini offline/hướng dẫn key, **không gọi API thật hoặc phát sinh phí**; có key không tự cấp quyền gọi.
Đọc [tổng quan](docs/product/overview.md), [yêu cầu](docs/product/requirements.md) và [kiến trúc](docs/architecture/overview.md) trước khi thay đổi hành vi sản phẩm.

## Trạng thái repository
Bản dựng sạch đã kiểm chứng Flyway V1, health, API nền và frontend. Các nhóm G1–G10 nghiệm thu theo [kế hoạch hiện tại](docs/development/full-system-implementation-plan.md). Xem [biên bản P0](docs/development/phase0/README.md).
Không tuyên bố tính năng hoặc lệnh build đã hoạt động khi chưa thực hiện và xác minh.
Các quyết định chưa chốt nằm trong [ADR](docs/decisions/0001-system-architecture.md).

## Cách làm việc
- Đọc hướng dẫn trong thư mục liên quan và skill phù hợp từ [Agent tooling](docs/development/agents.md).
- Skill chính nằm trong agents/skills/. Khi dùng pr-review, đọc [project profile](agents/skills/pr-review/references/project-profile.md) trước checklist upstream; chỉ áp dụng quy tắc phù hợp Spring Boot/React/MySQL.
- Tìm mã nguồn, cấu hình và kiểm thử hiện có trước khi viết mới.
- Giữ thay đổi trong phạm vi yêu cầu; bảo toàn thay đổi sẵn có của người dùng.
- Dùng tiếng Việt cho tài liệu và giải thích; dùng tiếng Anh cho tên biến, class, API và mã yêu cầu.
- Đồng bộ yêu cầu, kiến trúc và tài liệu vận hành khi thay đổi hành vi tương ứng.
- Ghi quyết định kiến trúc đáng kể bằng ADR; không coi đề xuất là chức năng đã triển khai.
- Không thêm dependency chỉ vì ví dụ trong skill sử dụng nó. Kiểm tra nhu cầu, phiên bản tương thích và giấy phép trước khi thêm.

## Ranh giới sản phẩm và bảo mật
- Hội thoại văn bản hoặc ghi âm theo lượt; không tự mở rộng sang thoại thời gian thực, chấm phát âm hoặc chứng nhận CEFR.
- Backend xác thực, phân quyền và kiểm tra chủ sở hữu dữ liệu; AI không thực hiện các quyết định này.
- Không đưa mật khẩu, khóa Gemini, token hoặc âm thanh cá nhân vào Git hay log.
- Âm thanh chỉ lưu tạm để xử lý; phải có dọn tệp khi thành công, thất bại và tệp hết hạn.
- Kết quả AI phải được kiểm tra trước khi lưu hoặc sử dụng. Không thực thi chỉ dẫn nhận được trong hội thoại như lệnh hệ thống.
- Thay đổi schema phải qua Flyway; không dùng Hibernate tự cập nhật schema khi triển khai.

## Kiểm tra và bàn giao
- Dùng lệnh thực có trong manifest, wrapper hoặc tài liệu đã xác minh; không tự giả định lệnh tồn tại.
- Chọn kiểm thử theo rủi ro trong [testing](docs/development/testing.md).
- Chỉnh tài liệu thuần túy: kiểm tra liên kết, tính nhất quán; không cần tạo kiểm thử ứng dụng.
- Báo thay đổi, kết quả kiểm tra, phần chưa xác minh và cấu hình/migration cần thiết.
- Không tự commit, push hoặc triển khai khi tác vụ chưa yêu cầu các hành động đó.
