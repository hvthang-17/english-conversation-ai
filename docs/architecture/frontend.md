# Frontend React / TypeScript

Trạng thái: React 19 / TypeScript 5.9 / Vite 8, npm lockfile, CSS cơ bản; chưa cài Tailwind, UI kit, thư viện router hoặc thư viện state. App quản lý Session/tài khoản và điều phối trang; LearningWorkspace ghép hồ sơ, catalog và chi tiết/chọn bài bằng route hash, có thông báo lỗi/phiên hết hạn; không có dữ liệu học tập giả.

## Tổ chức
Chia theo tính năng auth, profile, scenarios, conversation, feedback, review, history, admin. Phần dùng chung gồm API client, component UI, types và utilities. Không chọn thư viện mới trước khi có nhu cầu cụ thể.

`src/app` sở hữu khởi tạo, layout, ghép workspace và điều phối hash route. `src/features` chứa nghiệp vụ; chia `components/`, `api/`, `hooks/`, `model/`, `styles/` và `types.ts` theo nhu cầu thực. `src/shared` chứa API client, UI, hook, kiểu và tiện ích dùng ở nhiều feature. Không tạo thư mục rỗng hoặc barrel export để lấp cấu trúc.

Hướng phụ thuộc: `app → features → shared`; app cũng được dùng shared trực tiếp. Feature không import app; shared không import feature/app. ESLint kiểm tra hai ranh giới này. Các phụ thuộc giữa feature dùng import trực tiếp, phải có lý do nghiệp vụ và không tạo vòng. Xem [quy tắc cấu trúc và cây thư mục](../development/frontend-structure.md).

Ôn tập, lịch sử và thống kê lần lượt nằm trong `features/review`, `features/history`, `features/statistics`; `features/learning` sở hữu sổ tay và quản lý dữ liệu. Phân trang, hook `useApiQuery`, hook `useSpeech` và chuyển WAV dùng chung nằm trong shared. CSS riêng nằm cạnh feature ở `styles/`; CSS trình bày học liệu dùng ở nhiều feature nằm trong `shared/styles/learning-content.css`, được import rõ ràng từ stylesheet sử dụng.

Trong `features/admin`, các module `overview`, `accounts`, `materials`, `scenarios` và `activity` chia theo chức năng quản lý. `components/AdminWorkspace` chỉ ghép khung và điều hướng; module sở hữu component, model/API và stylesheet khi cần. Học liệu và tình huống có mục điều hướng riêng. Giữ hash `/admin`, Session/CSRF và backend authorization. Xem [cấu trúc admin](../../frontend/src/features/admin/README.md) và [thiết kế/kiểm chứng](../development/ui/admin-improvement.md).

## Tương tác
- Mọi thao tác bất đồng bộ có trạng thái tải, thành công, lỗi và dữ liệu trống khi phù hợp.
- Trạng thái buổi luyện, lượt gửi và tạo phản hồi phải tách biệt.
- Ghi âm có idle, recording, preview, transcribing, confirming, error; dừng media tracks khi hủy hoặc unmount.
- Người học xác nhận bản chép lời trước khi gửi.
- Trang chi tiết chọn chế độ trước khi tạo buổi. Màn hình hội thoại dùng mode từ API để chọn bố cục, kể cả sau tải lại.
- Trong VOICE đang luyện, phụ đề của từng tin nhắn AI mặc định đóng. Chỉ render chữ khi người học mở phụ đề. Text ẩn cũng vắng khỏi nhãn điều khiển và vùng thông báo trợ năng. Phát lại hoặc nghe chậm không thay đổi trạng thái phụ đề.
- VOICE lấy ghi âm làm thao tác chính. Ô gõ được mở riêng cho một lượt. Nếu lỗi phát, người học có thể mở phụ đề của từng câu. Chọn chế độ trước khi tạo buổi. Dừng phát khi rời màn hình hoặc bắt đầu ghi âm. Khi xem lại buổi đã kết thúc, hiển thị đầy đủ hội thoại.
- Hiển thị nội dung AI như dữ liệu; không render HTML thô chưa xử lý.

## Giao diện và hợp đồng

Hướng thiết kế đã chốt ngày 23/11/2026 tại [ADR 0010 — Thống nhất hệ thống giao diện](../decisions/0010-interface-design-system.md): light theme, teal chủ đạo, Be Vietnam Pro, token CSS dùng chung; desktop có điều hướng bên trái và mobile dùng menu gọn. Sau đăng nhập ưu tiên không gian học thay phần giới thiệu lớn. Mã hiện tại đã có font local, token và app shell theo hướng này; chưa coi đó là bằng chứng mọi màn hình đã đồng nhất hoặc đã nghiệm thu trực quan. Mọi thay đổi giao diện phải tuân thủ [Quy chuẩn cải tiến giao diện](../development/interface-design-standard.md), gồm thiết kế trước code, quy tắc từng nhóm màn hình và checklist nghiệm thu.

Giao diện hướng dẫn tiếng Việt; nội dung luyện tiếng Anh. Có label, focus nhìn thấy, dùng bàn phím và bố cục trên màn hình nhỏ. Thông báo trạng thái không chỉ phụ thuộc màu sắc.

API client tập trung xử lý lỗi và Session cookie theo [ADR 0004](../decisions/0004-session-authentication.md). Khi triển khai AUTH-01: lấy/gửi CSRF token đúng hợp đồng, làm mới sau login/logout, xử lý 401; không lưu mã phiên vào localStorage/sessionStorage. Same-origin qua proxy là phương án chính; cookie xác thực HttpOnly không được frontend đọc. Luồng này đã triển khai tại App/shared API client; mỗi mutation lấy token mới, không tự replay. Route tài khoản chỉ render dữ liệu khi đã tải user hợp lệ; API vẫn là nơi quyết định quyền.

Không đặt khóa riêng trong VITE_*; mọi biến đó phải được coi là công khai. Không suy đoán API chưa có: thống nhất hợp đồng trước và phân biệt mock kiểm thử với dữ liệu sản phẩm.

## Màn hình bổ sung

Khung đăng nhập dùng AppFrame với sidebar theo nhóm (256px/80px) và drawer dưới 1024px. HeaderAccount đọc tên/ảnh từ API hồ sơ, có fallback; sự kiện nội bộ sau lưu hồ sơ/ảnh làm mới header. Chỉ boolean thu gọn được lưu vào localStorage; Session vẫn do cookie HttpOnly quản lý. Xem [thiết kế và giới hạn kiểm chứng khung](../development/ui/app-shell-improvement.md).

Theo [đặc tả vòng đời](../product/requirements.md), Hồ sơ thêm ảnh đại diện và lịch dự kiến (ngày/giờ/múi giờ, bật email); Tài khoản thêm trạng thái xác minh/gửi lại và xóa tài khoản có xác nhận mật khẩu. Bổ sung màn hình xác minh và đặt lại mật khẩu, token chỉ giữ bộ nhớ và không tải tài nguyên bên thứ ba. Các màn hình đã có mã triển khai; giữ theme/route hiện có và các trạng thái tải/lỗi/keyboard/mobile. API riêng tại [hợp đồng bổ sung](../api/auth.md).
