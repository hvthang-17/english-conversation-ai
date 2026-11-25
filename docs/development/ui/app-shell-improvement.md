# Cải tiến khung ứng dụng — 25/11/2026

Áp dụng [quy chuẩn UI](../interface-design-standard.md) và phương pháp frontend-design. Giữ palette teal, Be Vietnam Pro và shared Brand/Icon; không thêm UI kit. Phạm vi chính là AppFrame, HeaderAccount và CSS khung; nối sự kiện từ thao tác lưu hồ sơ/ảnh để header cập nhật sau khi server xác nhận.

## Bố cục được chọn

So sánh menu phẳng luôn mở với menu chia nhóm có thể thu gọn: chọn phương án hai để người học quét theo tác vụ, dành thêm chiều ngang cho bài học khi cần. Header giữ thương hiệu, danh tính và nút menu mobile. Nút thu gọn desktop nằm ở đầu sidebar: sát mép phải khi mở, căn giữa khi thu gọn. Không thêm thống kê, thông báo hoặc menu tài khoản chưa có nghiệp vụ. Tự phản biện: icon phải có nghĩa riêng thay vì dùng cùng biểu tượng sách/thẻ/hội thoại cho nhiều mục.

```text
Desktop mở:  [Thương hiệu                            Tên / Ảnh]
             [Học và thực hành  [Thu gọn] | Nội dung          ]
Desktop gọn: [Thương hiệu                            Tên / Ảnh]
             [Mở rộng / Icon | Nội dung rộng hơn               ]
Mobile:      [Menu | Thương hiệu                         Ảnh  ]
             [Nội dung]; menu mở thành drawer có nền che.
```

Nút thu gọn nằm bên phải nhãn “Học và thực hành” trên cùng một hàng; vùng bấm 32px, icon 16px. Khi thu gọn, nút căn giữa phía trên các tab; mobile giữ nút menu ở header.

Desktop từ 1024px: sidebar 256px hoặc 80px, header 76px. Mobile dùng drawer; header nhỏ 72px dưới 641px. Header và sidebar giữ vị trí khi đọc trang dài; chỉ vùng link của sidebar cuộn khi thiếu chiều cao, đăng xuất ở dưới riêng để không bị cắt. Tất cả control tối thiểu 44px, focus nhìn thấy.

| Mục | Icon |
| --- | --- |
| Khám phá bài học | `book` (quyển sách mở) |
| Bài đã lưu | `bookmark` |
| Sổ tay ôn tập | `notebook` |
| Luyện nghe | `headphones` |
| Luyện phát âm | `microphone` |
| Tình huống | `conversation` |
| Ôn tập | `cards` |
| Lịch sử | `history` |
| Thống kê | `chart` |
| Hồ sơ học tập | `profile` |
| Dữ liệu học tập | `database` |
| Tài khoản | `settings` |
| Quản trị hệ thống | `shield` |

Giữ feature/role gating và hash route hiện có, bao gồm điều kiện hiển thị quản trị sẵn có; không cấp quyền qua UI. Menu gọn giữ tên truy cập, aria-current và title gợi ý. Tùy chọn gọn/mở lưu riêng dưới localStorage key `english-conversation.sidebar-collapsed`, chỉ chứa boolean, không chứa phiên hay dữ liệu cá nhân; storage bị chặn vẫn thao tác được trong bộ nhớ.

Drawer mobile có tên, aria-modal, focus vào nút đóng, giới hạn Tab trong menu, Escape/nền che/chọn link để đóng và trả focus. Khóa cuộn body lúc mở, dọn khi đóng/unmount hoặc chuyển sang desktop. Không giữ nhãn ẩn của desktop khi mở menu mobile.

## Ảnh đại diện

HeaderAccount đọc hồ sơ qua client hiện có, lấy tên và tình trạng avatar. Ảnh dùng endpoint owner cùng cấu hình API `profile/me/avatar`; không đọc URL người dùng tùy ý. Liên kết ảnh/tên dẫn tới Hồ sơ để xem và chỉnh sửa. Ảnh lỗi hoặc không có hồ sơ dùng chữ cái thay thế; liên kết vẫn có tên truy cập đầy đủ.

Sau khi lưu hồ sơ, tải hoặc xóa ảnh thành công, profile phát sự kiện nội bộ `learning-profile-changed` không chứa dữ liệu. Header tải lại hồ sơ và đổi revision ảnh; không đổi contract backend hoặc cơ chế Session. Sự kiện không mang thông tin qua tab khác; thay đổi từ thiết bị/tab khác sẽ được đọc lại khi khung ứng dụng được tải lại.

## Kiểm chứng

TypeScript, ESLint và Vite production build đã đạt. Browser Playwright dùng API mock, hình avatar thử và file PNG nhỏ tách khỏi runtime:

- 12 link người học có SVG khác nhau; tab hiện tại giữ aria-current khi gọn, tên vẫn truy cập được.
- Thu gọn về 80px, mở lại, giữ tùy chọn qua reload/chuyển route.
- Header tải ảnh, cập nhật tên sau lưu, thay/xóa ảnh phản ánh ngay; ảnh lỗi và profile 404 có fallback.
- 1366×650: nút đăng xuất nằm trong viewport, danh sách link có cuộn riêng.
- Không tràn ngang ở 320/360/768/1024/1440px; tăng chữ gốc 200% ở 320px không tràn.
- Drawer focus vào đóng, Shift+Tab tới cuối menu, Escape trả focus/khôi phục cuộn; click nền che và chọn Tài khoản đóng menu.
- Đã xem ảnh expanded/collapsed và mobile menu. Ảnh cục bộ tại `.playwright-mcp/app-shell-expanded.png`, `app-shell-collapsed.png`, `app-shell-mobile.png`, `app-shell-mobile-menu.png` ở gốc repo. Avatar minh họa là fixture, không phải ảnh người dùng thật.

Chưa nghiệm thu backend/storage ảnh thật, screen reader toàn diện, thiết bị cảm ứng thật hoặc mọi tổ hợp feature/admin. Mock không chứng minh quyền API hay xử lý file thật. Không thêm dependency, migration hoặc cấu hình môi trường.
