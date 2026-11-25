# Mục lục tài liệu

## Sản phẩm
- [Tổng quan và phạm vi](product/overview.md)
- [Yêu cầu và tiêu chí nghiệm thu](product/requirements.md)
- [Luồng người dùng](product/user-flows.md)
- [Chính sách lưu dữ liệu, thông báo âm thanh và trình duyệt](product/data-and-browser-proposal.md)

## Kiến trúc
- [Tổng thể](architecture/overview.md)
- [Backend](architecture/backend.md)
- [Frontend](architecture/frontend.md)
- [Thiết kế MySQL 46 bảng, ERD, dữ liệu mẫu và workflow](architecture/database.md)
- [Gemini và âm thanh](architecture/ai-integration.md)

## API
- [Tài khoản và Session](api/auth.md)
- [Hồ sơ và catalog](api/profile-catalog.md)
- [Học liệu, sổ tay và quiz chọn nghĩa](api/lesson-materials.md)
- [Luyện nghe và công khai đề](api/listening.md)
- [Luyện phát âm](api/pronunciation.md)
- [Hội thoại TEXT/VOICE](api/practice.md) · [Gửi và xác nhận bản ghi](api/voice.md)
- [Nhận xét cuối buổi](api/feedback.md)
- [Ôn tập, lịch sử và thống kê](api/learning.md)

- [Phiếu rà nội dung catalog G2](development/acceptance/g2-content-review.md)

## Phát triển
- [Mục lục phát triển](development/README.md) — thiết lập, quy ước, kế hoạch, kiểm thử và nghiệm thu theo nhóm.
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
- [0010: Thống nhất hệ thống giao diện](decisions/0010-interface-design-system.md)
- [0013: Thay Azure Speech](decisions/0013-replace-azure-speech.md)
- [0014: Chốt Azure Speech TTS cho G4](decisions/0014-azure-speech-tts-g4.md)
- [0015: Chốt Azure Pronunciation Assessment cho G5](decisions/0015-azure-pronunciation-assessment-g5.md)
- [0012: Chốt ERD 46 bảng và phạm vi TTS](decisions/0012-system-database-scope.md)

## Nghiên cứu và đánh giá
- [Khảo sát nhu cầu](research/needs-survey.md)
- [Đánh giá AI](research/ai-evaluation.md)
- [Kịch bản dùng thử khoảng 10 người](research/usability-test.md)

Yêu cầu mô tả hành vi mong muốn; tài liệu kiến trúc mô tả thiết kế. Trạng thái triển khai phải được xác minh bằng mã nguồn và kết quả kiểm tra, không suy ra từ sự tồn tại của tài liệu.
