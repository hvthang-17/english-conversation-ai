# Mục lục tài liệu

## Sản phẩm
- [Tổng quan và phạm vi](product/overview.md)
- [Yêu cầu và tiêu chí nghiệm thu](product/requirements.md)
- [Luồng người dùng](product/user-flows.md)
- [Chính sách retention, thông báo audio và trình duyệt — đã chốt, chưa triển khai](product/data-and-browser-proposal.md)

## Kiến trúc
- [Tổng thể](architecture/overview.md)
- [Backend](architecture/backend.md)
- [Frontend](architecture/frontend.md)
- [Cơ sở dữ liệu](architecture/database.md)
- [Từ điển trường của 20 bảng](architecture/database-dictionary.md)
- [Workflow dữ liệu và giao dịch](architecture/database-workflows.md)
- [Gemini và âm thanh](architecture/ai-integration.md)

## API
- [Tài khoản và Session](api/auth.md)
- [Hồ sơ và catalog](api/profile-catalog.md)
- [Buổi luyện văn bản](api/practice.md)
- [Nhận xét cuối buổi](api/feedback.md)
- [Ôn tập, lịch sử và thống kê](api/learning.md)

- [Rà soát 8 tình huống khởi đầu](product/starter-catalog-review.md)

## Phát triển
- [Kế hoạch thực hiện theo giai đoạn](development/implementation-plan.md)
- [Giai đoạn 5: ôn tập, lịch sử và thống kê](development/phase5/README.md)
- [Giai đoạn 4: nhận xét cuối buổi và mẫu rà soát](development/phase4/README.md)
- [Giai đoạn 3: hội thoại văn bản và gợi ý](development/phase3/README.md)
- [Giai đoạn 2: hồ sơ, catalog và kiểm thử](development/phase2/README.md)
- [Giai đoạn 1: kết quả, admin bootstrap và kiểm thử](development/phase1/README.md)
- [Kết quả và phần còn lại của giai đoạn 0](development/phase0/README.md)
- [MySQL local](development/phase0/mysql.md), [Gemini/key](development/phase0/gemini.md), [micro/codec](development/phase0/audio.md)
- [Thiết lập môi trường](development/setup.md)
- [Agent tooling và skill](development/agents.md)
- [Quy ước lập trình](development/coding-conventions.md)
- [Kiểm thử](development/testing.md)
- [Quy tắc AI tại gốc](../AGENTS.md)

## Quyết định
- [0001: Kiến trúc và các quyết định đang mở](decisions/0001-system-architecture.md)
- [0002: Cấu trúc và phiên bản ứng dụng](decisions/0002-application-scaffold.md)
- [0003: ERD và vòng đời dữ liệu](decisions/0003-database-design.md)
- [0004: Session cookie và Spring Session JDBC](decisions/0004-session-authentication.md)
- [0005: Bảy quy tắc bản đầu đã chốt](decisions/0005-initial-product-rules.md)
- [0006: Nguồn lực, hạn nộp, retention/browser và giới hạn Gemini phase0](decisions/0006-phase0-operating-decisions.md)

- [0007: Hiện thực tài khoản, email/hash và thu hồi Session](decisions/0007-auth-implementation.md)

- [0008: Hồ sơ và catalog khởi đầu](decisions/0008-profile-and-catalog.md)

## Nghiên cứu và đánh giá
- [Khảo sát nhu cầu](research/needs-survey.md)
- [Đánh giá AI](research/ai-evaluation.md)
- [Kịch bản dùng thử khoảng 10 người](research/usability-test.md)

Yêu cầu mô tả hành vi mong muốn; tài liệu kiến trúc mô tả thiết kế. Trạng thái triển khai phải được xác minh bằng mã nguồn và kết quả kiểm tra, không suy ra từ sự tồn tại của tài liệu.
