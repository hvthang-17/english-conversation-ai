# Quản trị hệ thống

ADMIN-01. Workspace dành cho tài khoản ADMIN; backend quyết định quyền trên mọi API. Giữ Session cookie, CSRF, xử lý hết phiên và kiểm tra quyền/owner hiện hành. [Hợp đồng API](../../../../docs/api/admin.md).

## Cấu trúc theo chức năng

```text
admin/
├── components/
│   ├── AdminWorkspace.tsx          # Ghép chức năng, header và menu responsive
│   └── AdminNavigation.tsx         # Điều hướng theo nhóm công việc
├── model/navigation.ts            # Nhãn, mô tả và nhóm điều hướng
├── styles/admin.css               # Khung, panel, danh sách và bảng chung trong admin
├── overview/
│   ├── components/AdminOverview.tsx
│   └── styles/overview.css
├── accounts/
│   ├── api/accounts-api.ts
│   ├── components/AdminAccounts.tsx
│   ├── styles/accounts.css
│   └── types.ts
├── materials/
│   ├── components/MaterialAdmin.tsx
│   ├── components/MaterialEditor.tsx
│   ├── model/material-definitions.ts
│   └── styles/materials.css
├── scenarios/
│   ├── components/CatalogAdmin.tsx
│   ├── components/ScenarioEditor.tsx
│   ├── model/scenario-fields.ts
│   └── styles/scenarios.css
└── activity/components/AdminActivity.tsx
```

`overview` đọc số liệu và dẫn tới công việc. `accounts` tìm email, phân trang, xác nhận khóa/mở hoặc đổi quyền; tài khoản hiện tại không có thao tác tự thay quyền/khóa. `materials` quản lý chủ đề, bài học, từ/mẫu câu, hội thoại, đề nghe và bài phát âm. `scenarios` quản lý tình huống, trình độ và biến thể vai theo chủ đề. `activity` đọc nhật ký quản trị và metadata tác vụ AI; không thêm quyền đọc hội thoại riêng.

Mỗi module sở hữu UI, định nghĩa trường và CSS của chức năng. Chỉ tạo thư mục khi có file thực; dùng import trực tiếp, không file chuyển tiếp hoặc barrel export. Khung dùng lại Brand, Icon, PageHeading; nghiệp vụ dùng API client, useApiQuery, useSpeech và Pagination trong shared. Không import app từ feature. Cấu hình editor học liệu nằm ở model để các loại nội dung dùng chung luồng biên tập, không nhân bản sáu editor giống nhau.

## Hành vi được giữ

- Khung vào qua `AdminWorkspace` và hash `/admin`; lựa chọn chức năng nằm trong bộ nhớ như trước. Không bổ sung router hoặc lưu phiên ở client.
- Thay quyền/trạng thái có xác nhận đối tượng; mỗi mutation lấy CSRF mới, không tự replay. API vẫn kiểm tra quyền, thu hồi phiên và audit.
- Học liệu tạo/sửa nháp, sao chép nội dung công khai thành nháp, công bố/ẩn và xóa nháp; đề nghe giữ endpoint công bố riêng. Nghe thử dùng giọng cục bộ, công bố đề nghe có thể gọi Azure theo cấu hình backend và vẫn có xác nhận.
- Tình huống giữ điều kiện chủ đề mở, A2/B1 và rà chỉ dẫn AI trước khi lưu công khai; giữ tối đa 12 biến thể và tải vai hiện có trước khi biên tập.
- Yêu cầu hỗ trợ và các mục thông báo vẫn ghi rõ chưa triển khai.

## Giao diện và kiểm chứng

Giữ token, font local và hướng sáng/teal của ADR 0010. Desktop có menu nhóm và chế độ thu gọn 80px; dưới 1024px dùng menu mở trong luồng trang, đóng bằng Escape. Danh sách tài khoản chuyển sang hàng xếp dọc khi thiếu chỗ; nhật ký cuộn trong vùng có tên. Không có dependency hoặc API mới.

Thiết kế, kết quả kiểm tra tĩnh, browser fixture và giới hạn tại [biên bản cải tiến admin](../../../../docs/development/ui/admin-improvement.md). Browser mock không thay thế kiểm thử tích hợp MySQL/backend hoặc xác minh quyền phía server.

Quản lý chủ đề dùng `topics/components/TopicList.tsx` và `topics/styles/topics.css` để trình bày bảng/responsive riêng. Query, editor và thao tác vẫn dùng luồng materials hiện có, giữ hợp đồng API.
