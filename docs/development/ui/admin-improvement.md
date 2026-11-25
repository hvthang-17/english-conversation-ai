# Tổ chức và cải tiến giao diện quản trị

Ngày: 25/11/2026. Phạm vi: `frontend/src/features/admin`; giữ hợp đồng [API quản trị](../../api/admin.md), Session/CSRF và quyền ADMIN.

## Thiết kế trước triển khai

Người dùng là quản trị viên quản lý tài khoản và nội dung giao tiếp A2–B1. Hành động chính là tìm đúng đối tượng, biên tập và xác nhận thay đổi. Điểm nhấn là khu vực nội dung đang quản lý, với tên đối tượng, trạng thái và thao tác cạnh nhau.

Tái sử dụng token ADR 0010: nền `#F3F7F8`, mặt phẳng `#FFFFFF`, chữ `#193747`, chữ phụ `#516875`, teal `#17687A`, vùng chọn `#E5F1F4`. Be Vietnam Pro local; tiêu đề trang 28–32px/700, nhóm 20px/600, nội dung 16px/400 và nhãn 14px/600. Căn trái, bo panel 12px và control 8px. Không tạo bảng màu hoặc dependency mới.

So sánh hai phương án:

```text
A. Điều hướng gộp                 B. Điều hướng theo công việc (chọn)
Menu: Catalog                     Tài khoản / Nội dung / Giám sát
  Chọn Học liệu / Tình huống       Học liệu và Tình huống có mục riêng
  Chọn loại học liệu              Tên chức năng + mô tả ngắn
  Danh sách + form dài            Bộ lọc + danh sách + editor khi cần

Desktop                           Mobile
+------------+-----------------+  +----------------------------+
| Menu nhóm  | Tên chức năng   |  | Thương hiệu     Menu       |
|            | Bộ lọc / CTA   |  | Tên chức năng              |
|            | Danh sách     |  | Bộ lọc và thao tác         |
|            | Editor mở ra  |  | Danh sách / form một cột   |
+------------+-----------------+  +----------------------------+
```

Chọn B vì giảm tầng lựa chọn và thể hiện rõ nơi quản lý tài khoản, nội dung học, nhật ký. Menu mobile mở trong luồng trang, đóng bằng Escape; không dùng dialog nếu không cần khóa tương tác nền. Sidebar desktop có chế độ gọn. Trạng thái tải, lỗi có tải lại, trống, đã lưu và xác nhận thay đổi phải hiện rõ bằng chữ. Bảng tài khoản chuyển thành từng hàng xếp dọc trên mobile; bảng nhật ký có vùng cuộn riêng được đặt tên.

Tự phản biện: một dashboard số liệu lớn hoặc header tối sẽ lấn át việc quản lý bài luyện. Loại bỏ ô số nổi bật và màu nền riêng; dùng số liệu theo nhóm, mô tả nội dung thực tế và danh sách thao tác. Những mục chưa triển khai tiếp tục được ghi rõ, không mô phỏng tính năng hoạt động. Giữ xác nhận khóa/đổi quyền, tự bảo vệ tài khoản hiện tại, công bố/ẩn/xóa nháp, biến thể vai và nghe thử cục bộ. Không tự gọi AI/Azure trong kiểm chứng.

## Phân chia trách nhiệm

`components` chỉ ghép workspace và điều hướng. Các module `overview`, `accounts`, `materials`, `scenarios`, `activity` sở hữu component, model/types và API khi cần. CSS khung dùng chung trong admin; CSS chức năng được import trực tiếp. Không barrel export, không thư mục rỗng, không thay đổi API/backend/schema.

## Kiểm chứng

Đã thực thi ngày 25/11/2026:

| Kiểm tra | Kết quả |
| --- | --- |
| TypeScript `tsc -b --pretty false` | Đạt. |
| ESLint `eslint . --max-warnings 0` | Đạt, không cảnh báo. |
| Vite `vite build` sau typecheck | Đạt, 99 module. |
| Import tương đối và đồ thị phụ thuộc TypeScript trong src | Không phát hiện import không phân giải được hoặc vòng phụ thuộc. |
| Liên kết nội bộ trong các tài liệu thay đổi | Đạt. |

Các lệnh chạy qua Node đóng gói trong môi trường, tương ứng các bước của script `typecheck`, `lint`, `build` trong package.json. Shim Node/npm của nvm trên PATH chưa cấu hình phiên bản nên không chạy trực tiếp bằng shim đó; không thay đổi cấu hình máy hoặc dependency.

Browser dùng Vite local và route fixture chặn toàn bộ `/api/v1/**`. Chỉ có tài khoản/nội dung tổng hợp `example.test`; không ghi dữ liệu hoặc gọi provider thật. Đã kiểm tra:

- Tổng quan: số liệu fixture, lỗi 500, tải lại và trạng thái đang tải.
- Tài khoản: tìm email, kết quả trống, xóa tìm kiếm, không có nút tự khóa/đổi quyền tài khoản hiện tại; khóa/mở/đổi quyền đúng đối tượng qua PATCH và CSRF. Lỗi 409 giữ xác nhận để thử lại. Focus vào xác nhận, hủy trả focus về nút ban đầu; cập nhật xong focus về tìm kiếm.
- Học liệu: tạo nháp chủ đề, công bố, ẩn, xóa nháp; mở editor cả sáu loại, sao chép học liệu công khai thành nháp. Công bố đề nghe dùng đúng endpoint riêng và được mock. Kiểm tra focus khi mở/đóng editor, mở/hủy xác nhận; nút theo hàng có tên đối tượng cho công nghệ hỗ trợ. Không xác minh âm thanh nghe thử thực tế trong đợt này.
- Tình huống: chọn chủ đề, tải vai hiện có trước khi sửa, giữ trình độ và biến thể trong payload PUT. Chưa đánh dấu rà chỉ dẫn thì không gửi lưu công khai. Không thay đổi validation hoặc hợp đồng trường.
- Nhật ký và tác vụ AI: hiển thị metadata, nhật ký trống; bảng có tiêu đề cột và vùng cuộn mang tên. 401 chuyển về trang đăng nhập.
- Desktop 1440px, sidebar thu gọn 80px; kiểm tra danh sách tài khoản ở 320/360/768/1024/1440px không cuộn ngang toàn trang. Editor JSON ở 320px, tình huống ở 360px và bảng nhật ký mobile không tràn ngang toàn trang.
- Menu mobile mở/đóng, chọn chức năng tự đóng; Escape từ menu hoặc nút mở trả focus đúng. Nút dùng bàn phím có outline 3px; thử thu gọn bằng Enter với reduced motion. Không ghi nhận lỗi JavaScript `pageerror`; lỗi HTTP 409/500/401 được chủ động tạo để kiểm tra.
- Đo token thực tế trong browser: chữ/nền 11,60:1; chữ phụ/nền 5,43:1; primary/surface 6,37:1; selected 7,45:1; success 6,33:1; danger 6,39:1; warning 5,94:1; viền control/surface 3,27:1; focus/surface 5,02:1. Đây là các cặp đã đo, không phải chứng nhận mọi tổ hợp hoặc trạng thái.

Đã xem ảnh desktop tổng quan/editor và mobile tài khoản/editor/tình huống. Sau tự rà soát, bỏ khoảng margin thừa của mô tả học liệu, gắn label riêng cho select và hoàn thiện việc trả focus. Ảnh local trong `.playwright-mcp/` (thư mục bị gitignore):

- [Tổng quan desktop](../../../.playwright-mcp/admin-modules-overview-desktop.png)
- [Tài khoản desktop](../../../.playwright-mcp/admin-modules-accounts-desktop.png) và [mobile](../../../.playwright-mcp/admin-modules-accounts-mobile.png)
- [Editor học liệu desktop](../../../.playwright-mcp/admin-modules-editor-desktop.png) và [mobile](../../../.playwright-mcp/admin-modules-editor-mobile.png)
- [Tình huống desktop](../../../.playwright-mcp/admin-modules-scenarios-desktop.png) và [mobile](../../../.playwright-mcp/admin-modules-scenarios-mobile.png)

Chưa xác minh tích hợp backend/MySQL, thu hồi Session thật, SMTP, Azure/Gemini, chất lượng audio, trình đọc màn hình hoặc zoom trình duyệt 200%. Các luồng mock không chứng minh quyền phía server. Không thay đổi backend, API, schema, dependency hoặc cơ chế xác thực; không cần migration/cấu hình mới. Không commit/push.


## Cải tiến Quản lý chủ đề

Danh sách chủ đề được tách thành `topics/components/TopicList.tsx` với stylesheet riêng. Luồng query/mutation tiếp tục do MaterialList điều phối, không nhân bản API. Desktop dùng bảng tên/mô tả, thứ tự, trạng thái và thao tác; dưới 1200px xếp hàng dọc để các nút không chật. Form dùng tên Thêm/Sửa chủ đề, giải thích bản nháp, mã cố định sau công bố và thứ tự hiển thị. Tái sử dụng toàn bộ token/font ADR 0010.

So sánh: danh sách tên/mã gộp như trước khó đối chiếu thứ tự và mô tả; bảng phân cột được chọn để quản trị viên quét thông tin rõ hơn. Không thêm thẻ số liệu hoặc màu riêng vì không hỗ trợ tác vụ biên tập. Không thay API, quyền, Session/CSRF hoặc dữ liệu backend.

Đã chạy TypeScript, ESLint không cảnh báo và Vite build (101 module). Browser mock đã thử tạo nháp, công bố, sửa chủ đề công khai với mã chỉ đọc và ẩn chủ đề; xem ảnh desktop và editor mobile. Danh sách 320px và form 360px không tràn ngang trong ca đã thử. Fixture cũ có ID trùng sau khi xóa/tạo; đã sửa ID của fixture trước khi kiểm chứng, không thay đổi mã sản phẩm vì lỗi fixture. Chưa xác minh backend/MySQL thật.

Ảnh local: `.playwright-mcp/topics-redesign-desktop.png`, `topics-redesign-mobile.png`, `topics-redesign-editor-mobile.png`.


### Tinh chỉnh cột và icon chủ đề

Tách sáu cột: Chủ đề, Mã chủ đề, Mô tả, Thứ tự, Trạng thái, Thao tác. Tên cột và giá trị căn nhất quán, thứ tự căn giữa trên desktop, các ô căn giữa theo chiều dọc. Icon Chủ đề dùng bốn ô phân nhóm trong hệ Icon dùng chung; Học liệu tiếp tục dùng quyển sách. Giữ nguyên API và callback thao tác.

TypeScript, ESLint và Vite build đạt. Browser mock kiểm tra đủ sáu tiêu đề cột, icon khác nhau và không tràn ngang ở mobile 360px. Đã xem ảnh desktop 1920px: `.playwright-mcp/topics-columns-desktop.png`; ảnh mobile: `.playwright-mcp/topics-columns-mobile.png`. Bảng chuyển sang hàng có nhãn dưới 1400px để sáu cột không chật; chưa xác minh dữ liệu backend thật.


## Cải tiến Quản lý tình huống

Thay lưới chủ đề lớn bằng select có nhãn và mô tả chủ đề; giữ phân trang danh sách chủ đề hiện có. So sánh với phương án lưới cũ: bộ chọn gọn giúp danh sách tình huống hiện sớm và giữ trọng tâm ở công việc biên tập. Bảng có cột tình huống, vai giao tiếp, trình độ, trạng thái, thao tác. Dưới 1400px chuyển thành từng hàng có nhãn.

Editor thêm/sửa thay vùng danh sách trong lúc mở, gồm Thông tin chung, Vai mặc định, Biến thể vai, Trạng thái và trình độ. Desktop dùng hai cột cho trường ngắn, trường dài toàn hàng; mobile một cột. Chủ đề/phân trang chủ đề bị khóa khi đang biên tập để tránh mất nội dung. Mở editor đưa focus tới tiêu đề; hủy trả về nút mở, lưu đưa focus về tiêu đề danh sách. Không thay hợp đồng request, giới hạn 12 biến thể, tải vai hiện có, quy tắc công khai hoặc luồng xác nhận ẩn/bỏ ẩn/xóa nháp.

TypeScript, ESLint và build đã đạt. Browser mock: chọn chủ đề; mở/sửa và lưu giữ biến thể cùng A2/B1; thêm tình huống; thêm/xóa biến thể; chưa đánh dấu rà chỉ dẫn thì không gửi POST công khai, đánh dấu rồi tạo thành công; hủy về danh sách; chủ đề được mở khóa sau lưu. Editor mobile 360px không tràn ngang. Đã xem ảnh `.playwright-mcp/scenarios-layout-desktop.png` và `scenarios-add-mobile.png`; ảnh form desktop tại `scenarios-add-desktop.png`. Sau rà thị giác đã gom tên chủ đề/số lượng ngay dưới tiêu đề danh sách. Giữ token và font ADR 0010, không thêm dependency. Chưa xác minh backend/MySQL, provider thật hoặc trình đọc màn hình.


### Đồng bộ danh sách chủ đề với bảng tình huống

Bỏ mã chủ đề khỏi bảng; giữ mã trong editor/API. Bảng còn Chủ đề, Mô tả, Thứ tự, Trạng thái, Thao tác. Thu gọn cột thao tác, dành thêm chỗ cho tên/mô tả; căn giữa thứ tự/trạng thái/thao tác trên desktop. Sửa dùng bút, ẩn dùng mắt gạch, công bố dùng mắt mở và xóa nháp dùng thùng rác. Giữ tooltip, tên trợ năng kèm đối tượng, nút 44px và callback/xác nhận cũ. Mobile vẫn có nhãn từng trường.

TypeScript, ESLint và build đạt. Browser mock xác minh năm cột, icon/nút 44px, mở/hủy xác nhận ẩn, không tràn ngang ở 360px. Đã xem ảnh `.playwright-mcp/topics-balanced-icons-desktop.png`; ảnh mobile tại `topics-balanced-icons-mobile.png`. Chưa kiểm tra tích hợp backend thật.


### Bố cục thêm/sửa chủ đề độc lập

Tách trạng thái danh sách và biên tập thay vì đặt form nhỏ phía trên bảng bị vô hiệu hóa. Khi thêm/sửa chủ đề, chỉ hiển thị form căn giữa tối đa 900px trong panel; tên và mô tả toàn hàng, mã/thứ tự chia hai cột trên desktop, mobile một cột. Có nút Về danh sách chủ đề và hành động Lưu/Hủy. Giữ API và trạng thái nháp/công bố; không đổi editor các loại học liệu khác.

Browser mock đã kiểm tra bảng vắng khi biên tập, tạo nháp rồi trở lại bảng, hủy trả focus nút Thêm, quay lại trả focus nút Sửa và mã công khai vẫn chỉ đọc. Đã xem ảnh desktop 1920px/mobile 360px, không tràn ngang trong ca thử: `.playwright-mcp/topic-editor-focused-desktop.png`, `topic-editor-focused-mobile.png`. TypeScript, ESLint, Vite build và diff whitespace đạt. Chưa xác minh backend/MySQL thật.
