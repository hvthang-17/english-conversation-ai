# ADR 0010: Thống nhất hệ thống giao diện

## Trạng thái

Đã chốt hướng thiết kế ngày 23/11/2026. Cập nhật trạng thái ngày 25/11/2026 qua khảo sát mã: đã có bộ token, font local và app shell trong frontend; chưa xác minh mức áp dụng toàn bộ màn hình, trực quan hoặc tương phản trên trình duyệt. Quyết định này không thay đổi nghiệp vụ, API hay schema.

## Bối cảnh

Sản phẩm dành cho người học A2–B1 cần môi trường thực hành dễ tiếp cận, giảm ngại giao tiếp và giúp tìm thấy thao tác tiếp theo. CSS hiện tại dùng teal trong không gian học nhưng xanh lá riêng ở đăng nhập; màu, viền và trạng thái được khai báo rải rác. Phần giới thiệu lớn đang nằm trên cả không gian học sau đăng nhập.

Hướng được chọn là **Không gian luyện nói tự tin**: sáng, thân thiện và trưởng thành; cuộc hội thoại là điểm nhấn, các thành phần hỗ trợ giữ đơn giản. Áp dụng xuyên suốt đăng nhập, hồ sơ, catalog, hội thoại, nhận xét, ôn tập, lịch sử, thống kê và quản trị.

## Quyết định

### Theme và màu sắc

Triển khai light theme trước. Dark theme để sau, không nằm trong phạm vi thay đổi giao diện đầu tiên. Giữ teal hiện có và thống nhất đăng nhập theo cùng hệ màu.

| Token | Giá trị | Vai trò |
| --- | --- | --- |
| `background` | `#F3F7F8` | Nền ứng dụng |
| `surface` | `#FFFFFF` | Nội dung, biểu mẫu, hộp thoại |
| `text` | `#193747` | Nội dung và tiêu đề chính |
| `text-muted` | `#516875` | Mô tả và thông tin phụ |
| `primary` | `#17687A` | Hành động chính, liên kết, mục được chọn |
| `primary-soft` | `#E5F1F4` | Nền vùng được chọn và tin nhắn người học |

Màu ngữ nghĩa: thành công `#14664A`, lỗi/nguy hiểm `#A42C36`, cảnh báo `#865400` trên nền vàng nhạt, focus `#A65D00`. Màu viền, nền cảnh báo và các trạng thái hover/pressed/disabled sẽ được xác định khi triển khai, kiểm tra theo từng cặp màu thực tế. Không dùng màu làm tín hiệu duy nhất; luôn có chữ hoặc biểu tượng phù hợp.

Các giá trị dùng chung phải tập trung thành CSS custom properties thay vì lặp mã màu trong từng tính năng. Không tự thêm UI kit hoặc dependency để áp dụng bộ token.

### Typography

- Chọn **Be Vietnam Pro** cho giao diện tiếng Việt và hội thoại tiếng Anh; fallback `"Segoe UI", sans-serif`. Kiểm tra nguồn phân phối, giấy phép và bộ ký tự tiếng Việt trước khi đưa font vào ứng dụng.
- Tiêu đề giới thiệu 40–48px/700; tiêu đề trang 28–32px/700; tiêu đề nhóm 20–24px/600. Điều chỉnh theo màn hình nhỏ.
- Nội dung và hội thoại 16–18px/400; nhãn và nút 14–16px/600; chú thích 14px/400.
- Căn trái, giãn dòng khoảng 1,6; nội dung hội thoại khoảng 60–70 ký tự mỗi dòng, thông thường không vượt 80 ký tự.
- Dùng sentence case, tránh nhãn viết hoa toàn bộ, nhãn trang trí và tô riêng một từ trong tiêu đề.

### Bố cục

Trước đăng nhập giữ lời giới thiệu ngắn và biểu mẫu rõ ràng. Sau đăng nhập bỏ phần giới thiệu lớn, đưa nội dung học vào vùng nhìn đầu tiên.

Desktop dùng điều hướng bên trái với vị trí ổn định; thanh trên chứa thương hiệu và tài khoản. Mobile chuyển sang menu gọn, ưu tiên nội dung và vùng trả lời khi luyện tập. Điều hướng quản trị vẫn chỉ xuất hiện theo quyền hiện hành.

```text
+----------------+--------------------------------------+
| Logo                                 Tài khoản        |
+----------------+--------------------------------------+
| Tình huống     | Tên tình huống                        |
| Ôn tập         | Trình độ và vai của người học         |
| Lịch sử        |                                      |
| Thống kê       | Nội dung chính của màn hình           |
| Hồ sơ          |                                      |
+----------------+--------------------------------------+
```

Catalog ưu tiên tên tình huống, vai trò và mục tiêu nói. Trong hội thoại, câu AI dùng nền trung tính, câu người học dùng teal nhạt; có nhãn người nói và gợi ý nằm ngoài transcript. Giữ nguyên hai chế độ và quy tắc ẩn phụ đề theo [ADR 0009](0009-practice-modes.md); thiết kế không được làm lộ câu AI trong chế độ nghe.

### Component và tương tác

| Thành phần | Quy tắc |
| --- | --- |
| Nút | Cao tối thiểu 44px; một hành động chính nổi bật trong mỗi vùng thao tác |
| Nút phụ | Nền trắng, viền rõ; dùng cho gợi ý, quay lại, hủy |
| Thao tác xóa | Kiểu nguy hiểm riêng, giải thích đúng phạm vi dữ liệu bị xóa |
| Input | Label phía trên; trợ giúp và lỗi ngay dưới trường |
| Bo góc | 8px cho nút/input; 12px cho khối nội dung; 16px cho hộp thoại |
| Khoảng cách | Thang 4, 8, 12, 16, 24, 32, 48px |
| Đổ bóng | Dành cho lớp nổi như menu và hộp thoại |
| Chuyển động | Nhẹ khi thao tác làm thay đổi trạng thái; tôn trọng reduced motion |

Quản trị có thể tăng mật độ thông tin nhưng dùng cùng màu, nút và biểu mẫu. Không chia mọi nội dung thành các thẻ giống nhau hoặc dùng gradient, chuyển động để trang trí.

Ngôn ngữ hướng dẫn bằng tiếng Việt, hội thoại bằng tiếng Anh. Dùng tên hành động cụ thể và nhất quán như “Chọn tình huống”, “Bắt đầu luyện tập”, “Lưu để ôn”. Trạng thái tải, lỗi và trống phải hướng dẫn được bước tiếp theo.

Nhận xét phân biệt “Cần sửa” với “Cách nói tự nhiên hơn”; không biến mọi góp ý thành lỗi. Thống kê phản ánh hoạt động đã lưu, không trình bày thành điểm năng lực hoặc chứng nhận.

## Quy chuẩn thực hiện

Từ ngày 25/11/2026, mọi cải tiến UI áp dụng [Quy chuẩn cải tiến giao diện](../development/interface-design-standard.md). Tài liệu đó cụ thể hóa token, bố cục responsive, từng nhóm màn hình, trạng thái, khả năng tiếp cận và điều kiện nghiệm thu; giữ nguyên hướng thiết kế của ADR này.

## Hệ quả và kiểm chứng khi triển khai

- Cần thay các giá trị CSS rải rác bằng token chung và cập nhật cấu trúc bố cục sau đăng nhập; giữ hành vi API và dữ liệu hiện hành.
- Kiểm tra font, dấu tiếng Việt, tương phản chữ thông thường tối thiểu 4,5:1 và chữ lớn tối thiểu 3:1; kiểm tra thêm viền điều khiển, focus và trạng thái tương tác phù hợp.
- Kiểm tra bàn phím, focus nhìn thấy, bố cục mobile, nội dung dài, reduced motion và các trạng thái tải/lỗi/trống trên các màn hình liên quan.
- Xem ảnh chụp trình duyệt để nghiệm thu tính nhất quán; không coi việc chốt tài liệu là bằng chứng giao diện đã triển khai hoặc đã đạt accessibility.
- Không cần migration, thay đổi backend hoặc gọi Gemini thật cho việc áp dụng thiết kế này.

[Kiến trúc frontend](../architecture/frontend.md) · [Yêu cầu sản phẩm](../product/requirements.md) · [Kiểm thử](../development/testing.md)
