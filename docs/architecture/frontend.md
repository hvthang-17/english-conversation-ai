# Frontend React / TypeScript

Trạng thái: React 19 / TypeScript 5.9 / Vite 8, npm lockfile, CSS cơ bản; chưa cài Tailwind, UI kit, router hoặc thư viện state. AuthApp quản lý Session/tài khoản; LearningWorkspace ghép hồ sơ, catalog và chi tiết/chọn bài bằng route hash, có thông báo lỗi/phiên hết hạn; không có dữ liệu học tập giả.

## Tổ chức
Chia theo tính năng auth, profile, scenarios, conversation, feedback, review, history, admin. Phần dùng chung gồm API client, component UI, types và utilities. Không chọn thư viện mới trước khi có nhu cầu cụ thể.

src/app chứa ghép màn hình/styles; src/features chứa tính năng (thêm statistics và system); src/shared chứa API client/ui/lib. Feature chưa triển khai chỉ có README ghi trách nhiệm, thêm file khi có chức năng thực.

## Tương tác
- Mọi thao tác bất đồng bộ có trạng thái tải, thành công, lỗi và dữ liệu trống khi phù hợp.
- Trạng thái buổi luyện, lượt gửi và tạo phản hồi phải tách biệt.
- Ghi âm có idle, recording, preview, transcribing, confirming, error; dừng media tracks khi hủy hoặc unmount.
- Người học xác nhận bản chép lời trước khi gửi.
- Trang chi tiết chọn chế độ trước khi tạo buổi. Màn hình hội thoại dùng mode từ API để chọn bố cục, kể cả sau tải lại.
- Trong VOICE đang luyện, phụ đề của từng tin nhắn AI mặc định đóng. Chỉ render chữ khi người học mở phụ đề. Text ẩn cũng vắng khỏi nhãn điều khiển và vùng thông báo trợ năng. Phát lại hoặc nghe chậm không thay đổi trạng thái phụ đề.
- VOICE lấy ghi âm làm thao tác chính. Ô gõ được mở riêng cho một lượt. Nếu lỗi playback, cung cấp hành động mở phụ đề hoặc chuyển sang TEXT sau xác nhận. Dừng phát khi rời màn hình hoặc bắt đầu ghi âm. Khi xem lại buổi đã kết thúc, hiển thị đầy đủ hội thoại.
- Hiển thị nội dung AI như dữ liệu; không render HTML thô chưa xử lý.

## Giao diện và hợp đồng
Giao diện hướng dẫn tiếng Việt; nội dung luyện tiếng Anh. Có label, focus nhìn thấy, dùng bàn phím và bố cục trên màn hình nhỏ. Thông báo trạng thái không chỉ phụ thuộc màu sắc.

API client tập trung xử lý lỗi và Session cookie theo [ADR 0004](../decisions/0004-session-authentication.md). Khi triển khai AUTH-01: lấy/gửi CSRF token đúng hợp đồng, làm mới sau login/logout, xử lý 401; không lưu mã phiên vào localStorage/sessionStorage. Same-origin qua proxy là phương án chính; cookie xác thực HttpOnly không được frontend đọc. Luồng này đã triển khai tại AuthApp/shared API client; mỗi mutation lấy token mới, không tự replay. Route tài khoản chỉ render dữ liệu khi đã tải user hợp lệ; API vẫn là nơi quyết định quyền.

Không đặt khóa riêng trong VITE_*; mọi biến đó phải được coi là công khai. Không suy đoán API chưa có: thống nhất hợp đồng trước và phân biệt mock kiểm thử với dữ liệu sản phẩm.
