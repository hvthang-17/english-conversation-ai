# Quy chuẩn cải tiến giao diện

Ngày thiết lập: 25/11/2026. Áp dụng cho mọi thay đổi giao diện mới và cải tiến màn hình hiện có. Tài liệu này là chuẩn thực hiện và nghiệm thu, cụ thể hóa [ADR 0010](../decisions/0010-interface-design-system.md) theo [skill frontend-design](../../agents/skills/frontend-design/SKILL.md). Không phải báo cáo giao diện đã đạt chuẩn.

## 1. Mục tiêu và thứ tự ưu tiên

Thiết kế cho người học A2–B1 muốn thực hành giao tiếp trong tình huống đời thường: dễ bắt đầu, bớt ngại sai, hiểu góp ý và biết luyện gì tiếp theo. Chất lượng đẹp được đánh giá qua bản sắc, phân cấp, typography, nhịp bố cục và độ hoàn thiện tương tác; không tính bằng số hiệu ứng hay hình trang trí.

Thứ tự bắt buộc: đúng hành vi sản phẩm và bảo mật → dễ đọc, dễ thao tác, có khả năng tiếp cận → nhất quán → bản sắc và tinh chỉnh thẩm mỹ. Không đổi nghiệp vụ để làm bố cục đẹp hơn. Nếu có mâu thuẫn, đối chiếu [yêu cầu](../product/requirements.md), [luồng người dùng](../product/user-flows.md) và ADR liên quan trước khi sửa. Quy chuẩn này không mở rộng phạm vi tính năng, không cấp quyền gọi dịch vụ AI thật.

## 2. Định hướng thị giác duy nhất

**Không gian luyện nói tự tin**: sáng, điềm tĩnh, thân thiện và trưởng thành. Dấu ấn là nhịp trao đổi giữa hai người trong bối cảnh cụ thể: câu hỏi, lượt đáp, vai trò và mục tiêu giao tiếp. Dùng khoảng trắng để người học tập trung vào câu mình muốn nói, teal để dẫn hành động và đánh dấu lượt của người học.

- Điểm nhấn chính của trang giới thiệu là một đoạn hội thoại mẫu ngắn có ngữ cảnh, chẳng hạn gọi món: “What would you like?” / “I'd like a sandwich, please.” Gắn nhãn “Ví dụ hội thoại”; không giả làm buổi thật, chat trực tiếp hay kết quả cá nhân. Nếu có tương tác minh họa, dùng dữ liệu tĩnh và nêu rõ.
- Sau đăng nhập, điểm nhấn là tác vụ học hiện tại: chọn bài, nghe một câu, trả lời hoặc đọc góp ý. Không đặt hero quảng bá trong không gian học.
- Mỗi màn hình có một vùng thị giác chủ đạo. Các vùng phụ yên tĩnh hơn bằng kích thước chữ, khoảng cách và nền trung tính; không cạnh tranh bằng nhiều màu nhấn.
- Minh họa nếu cần phải mô tả tình huống thật của bài học, cùng phong cách xuyên suốt. Không dùng robot, quả cầu AI, ảnh stock trang trí, huy hiệu thành tích giả hoặc waveform giả như dấu ấn mặc định.
- Không dùng gradient trang trí, glassmorphism, nền tối neon, nền kem kết hợp serif/đất nung, nhãn viết hoa giãn chữ, emoji thay icon hoặc lưới thẻ giống nhau cho mọi loại nội dung. Ngoại lệ phải có lý do gắn với tác vụ và ghi trong bản thiết kế.

### So sánh bố cục trước khi chọn

Hai phương án tham chiếu dưới đây dùng để tránh vô thức quay về mẫu dashboard chung. Chọn B cho không gian học; A chỉ phù hợp một phần nhỏ khi cần giới thiệu trước đăng nhập.

```text
A. Giới thiệu chiếm phần đầu        B. Tác vụ học dẫn bố cục
+---------------------------+      +---------+---------------------------+
| Hero lớn, slogan          |      | Menu    | Tên bài / vai / mục tiêu  |
| Các số liệu và thẻ ngang  |      | ổn định | Nội dung học              |
| Nội dung học ở dưới      |      |         | Thao tác tiếp theo        |
+---------------------------+      +---------+---------------------------+
```

Tự phản biện: màu teal và sidebar riêng lẻ vẫn có thể giống dashboard phổ thông. Vì vậy, bản sắc phải xuất hiện trong nội dung và phân cấp: vai giao tiếp rõ, hội thoại làm trung tâm, gợi ý tách khỏi lượt nói, góp ý dẫn tới ôn tập. Không giải quyết sự chung chung bằng thêm hiệu ứng.

## 3. Bộ token và typography

Nguồn giá trị triển khai dùng chung là `frontend/src/app/styles/global.css`. Khi cải tiến, tái sử dụng CSS custom properties; feature CSS chỉ sở hữu bố cục hoặc trạng thái riêng. Không tạo bộ màu cạnh tranh trong từng tính năng.

### Màu nền tảng

| Token hiện hành | Giá trị chuẩn | Công dụng |
| --- | --- | --- |
| `--background` | `#F3F7F8` | Nền ứng dụng, khoảng nghỉ |
| `--surface` | `#FFFFFF` | Vùng đọc, biểu mẫu, lớp nổi |
| `--text` | `#193747` | Tiêu đề, nội dung |
| `--text-muted` | `#516875` | Mô tả, metadata vẫn cần đọc được |
| `--primary` | `#17687A` | CTA chính, liên kết, lựa chọn |
| `--primary-soft` | `#E5F1F4` | Lượt người học, vùng đang chọn |

Token hỗ trợ: `--primary-hover: #105365`, `--surface-muted: #F7FAFB`, `--border: #D4E2E7`, `--control-border: #7B929B`. Viền nhẹ chỉ chia vùng; viền điều khiển phải đủ rõ để nhận diện thao tác. Thành công dùng `--success: #14664A` trên `--success-soft: #EDF7F1`; nguy hiểm dùng `--danger: #A42C36` trên `--danger-soft: #FFF1F2`; cảnh báo dùng `--warning: #865400` trên `--warning-soft: #FFF6DE`; focus dùng `--focus: #A65D00`.

Không suy ra mọi tổ hợp token đều đạt tương phản. Đo các cặp thực tế ở cả hover, selected, focus, error và nền chồng lớp. Không dùng opacity của cả vùng để tạo chữ phụ; disabled không được làm biến mất thông tin giải thích cần thiết.

### Chữ

Một font **Be Vietnam Pro** cho cả tiếng Việt và tiếng Anh; fallback `"Segoe UI", sans-serif`. Dùng các weight đã có 400/600/700, không yêu cầu weight giả. Giữ font local, kiểm tra giấy phép và dấu tiếng Việt khi đổi asset; không tải font từ bên thứ ba trên trang chứa token khôi phục tài khoản.

| Vai trò | Cỡ chữ / weight | Giãn dòng |
| --- | --- | --- |
| Tiêu đề giới thiệu trước đăng nhập | 40–48px / 700; mobile 28–32px | 1,2–1,3 |
| Tiêu đề trang | 28–32px / 700; mobile 24–28px | 1,3 |
| Tiêu đề nhóm | 20–24px / 600 | 1,35 |
| Tiêu đề mục nhỏ | 18px / 600 | 1,4 |
| Nội dung, câu hội thoại | 16–18px / 400 | 1,6–1,7 |
| Nút, nhãn trường | 14–16px / 600 | 1,5 |
| Chú thích, metadata | 14px / 400 | 1,5–1,6 |

Dùng rem cho chữ; không chặn người dùng phóng to. Căn trái nội dung học và form, không căn đều hai lề. Đoạn đọc dài 60–70ch, dưới 80ch; không kéo transcript hết màn hình rộng. Không dùng chữ nhỏ để ép thông tin vừa thẻ. Dấu tiếng Việt không bị cắt; tránh tracking âm mạnh gây dính chữ. Sentence case; không tô riêng một từ trong tiêu đề để trang trí. Tiếng Việt cho điều hướng/hướng dẫn, tiếng Anh cho nội dung luyện, đánh dấu `lang="en"` cho đoạn tiếng Anh khi triển khai.

### Không gian và hình khối

- Thang khoảng cách: 4, 8, 12, 16, 24, 32, 48px tương ứng các token `--space-*` đã có. 4–8px liên kết icon/nhãn; 12–16px trong nhóm; 24–32px giữa nhóm; 48px giữa phần lớn. Không dùng cùng một gap cho mọi cấp.
- Bo góc theo vai trò: control 8px, panel 12px, dialog 16px. Pill dành cho trạng thái ngắn, không biến mọi nút và khối thành viên thuốc.
- Không đổ bóng cho mọi card. Chỉ dùng bóng nhẹ cho menu, popover, dialog hoặc vùng thực sự nổi; viền và khoảng trắng đủ để tổ chức nội dung thường.
- Icon dùng hệ `shared/ui/Icon.tsx`, cùng nét và tỷ lệ, cỡ 20–24px thông thường. Icon trang trí ẩn với công nghệ hỗ trợ; nút chỉ icon phải có tên truy cập rõ.

## 4. Bố cục và màn hình nhỏ

- Desktop từ 1024px: sidebar mở rộng 256px, thu gọn 80px, topbar 76px theo token hiện có; vùng làm việc tối đa 1420px gồm padding 24–56px. Không ép vùng đọc rộng theo container.
- 768–1023px: chuyển menu gọn khi sidebar làm nội dung chật; vùng học tối đa hai cột nếu mỗi cột vẫn đọc tốt. Dưới 768px: một cột, padding 16px, form và CTA đủ rộng; menu không chiếm chỗ vùng trả lời.
- Breakpoint phục vụ nội dung; nếu chọn mốc khác, ghi lý do và kiểm tra quanh mốc. Giữ nguyên vị trí tương đối của tiêu đề, trạng thái và hành động qua các màn hình cùng loại.
- Catalog dùng lưới khi các bài là những đối tượng ngang cấp; lịch sử dùng danh sách; thống kê dùng phân cấp số liệu; transcript dùng chuỗi lượt. Không bọc card trong card chỉ để tạo viền.
- Form đơn tối đa khoảng 480px; transcript khoảng 680–780px. Panel phụ chỉ xuất hiện khi có nội dung hữu ích; trên mobile chuyển xuống dưới hoặc mở theo yêu cầu.
- Composer ghim nếu cần nhưng không che tin nhắn cuối, lỗi, focus hay nút khi bàn phím ảo mở. Tính safe area trên thiết bị phù hợp. Không cố định chiều cao bằng viewport khiến nội dung bị cắt.
- 320px không cuộn ngang toàn trang. URL, tên bài và câu dài phải xuống dòng; chỉ bảng quản trị thực sự cần thiết mới có vùng cuộn riêng có nhãn.

```text
Desktop luyện tập                     Mobile luyện tập
+---------+------------------------+  +----------------------------+
| Menu    | Bài / vai / chế độ     |  | Menu       Tên bài         |
|         | AI: câu hỏi           |  | Vai / chế độ / tiến độ     |
|         |       Bạn: lượt đáp   |  | AI: câu hỏi                |
|         | Gợi ý (mở khi cần)    |  |       Bạn: lượt đáp        |
|         | Trạng thái / trả lời  |  | Trạng thái / trả lời       |
+---------+------------------------+  +----------------------------+
```

## 5. Quy chuẩn theo nhóm màn hình

| Nhóm | Phân cấp và cách trình bày bắt buộc |
| --- | --- |
| Giới thiệu / đăng nhập / khôi phục | Giới thiệu ngắn và hội thoại mẫu cạnh form trên desktop; mobile ưu tiên form. Mỗi form có tiêu đề, label, trợ giúp và CTA cụ thể. Không lặp hero ở mọi bước khôi phục. |
| Chủ đề / bài học / tình huống | Tên bài, ngữ cảnh đời thường, A2/B1, vai và mục tiêu nói rõ trước khi bắt đầu. Tìm kiếm/lọc nằm gần kết quả; có số kết quả thật và cách bỏ lọc. Yêu thích và Học sau là hai thao tác phân biệt. |
| Học liệu / sổ tay | Từ hoặc mẫu câu tiếng Anh nổi bật, nghĩa và ví dụ đọc được; nút nghe gắn đúng câu. Bản dịch mở theo quy tắc tính năng; trạng thái đã lưu rõ, không chỉ đổi icon. |
| Luyện nghe | Player và câu hỏi là trọng tâm; trạng thái tải/phát/dừng/lỗi rõ. Không lộ lời thoại, đáp án hoặc giải thích trước thời điểm nghiệp vụ cho phép. |
| Luyện phát âm theo mẫu | Câu mẫu, nghe mẫu, ghi âm, kết quả theo thứ tự tác vụ. Ghi rõ phạm vi kết quả; không diễn giải thành chứng nhận CEFR hoặc chấm mọi năng lực. |
| Hội thoại TEXT / VOICE | Nhãn người nói, AI nền trung tính, người học nền teal nhạt. Gợi ý/giải thích ngoài transcript. VOICE ẩn phụ đề từng câu theo hợp đồng, kể cả với screen reader; thu âm là CTA chính. TEXT ưu tiên ô trả lời và gửi. |
| Thu âm và xử lý | Phân biệt đang ghi, thời lượng, đã dừng, nghe lại, đang gửi/chép lời, cần xác nhận và lỗi theo luồng hiện hành. Chỉ hiện điều khiển hợp lệ cho trạng thái; thông báo bước tiếp theo. Không dùng animation ngụ ý đang ghi khi microphone chưa hoạt động. |
| Nhận xét cuối buổi | Tóm tắt ngắn, câu gốc, phần cần sửa, cách nói tự nhiên hơn và giải thích tiếng Việt. Không tô đỏ toàn bộ bài; không có lỗi là trạng thái hợp lệ. Đặt “Lưu để ôn” cạnh nội dung lưu được. |
| Ôn tập / quiz | Một mục tiêu nhận thức mỗi lượt; từ/câu là trọng tâm. Trước khi lật hoặc nộp không lộ đáp án qua chữ phụ hoặc tên truy cập. Phản hồi đúng/sai có chữ và hướng đi tiếp. |
| Lịch sử / thống kê | Danh sách dễ quét theo ngày, bài và trạng thái. Biểu đồ chỉ khi có câu hỏi cần trả lời, có nhãn/đơn vị và dữ liệu thay thế đọc được. Không biến số lượt thành điểm năng lực hoặc tạo dữ liệu cho đẹp. |
| Hồ sơ / tài khoản | Chia nhóm học tập, lịch, tài khoản và dữ liệu; hành động nguy hiểm tách cuối. Giải thích đúng phạm vi xóa, trạng thái đang xử lý và khả năng thử lại. |
| Quản trị | Mật độ cao hơn nhưng cùng token/control. Bảng có tiêu đề cột, filter, phân trang và trạng thái; hành động theo hàng rõ đối tượng. Không thêm quyền hoặc lộ hội thoại riêng qua thiết kế. |

## 6. Component, trạng thái và câu chữ

- Trong mỗi vùng thao tác chỉ một CTA chính; nút phụ trung tính, thao tác nguy hiểm có kiểu riêng. Tên nút nói đúng kết quả: “Bắt đầu luyện tập”, “Gửi câu trả lời”, “Dừng ghi âm”, “Lưu để ôn”. Giữ từ vựng xuyên suốt nút, tiêu đề và thông báo.
- Control có min-height 44px; mục tiêu chạm độc lập tối thiểu 44 × 44px. Liên kết trong đoạn văn giữ dấu hiệu liên kết; không biến mọi link thành nút. Hover không làm dịch layout, không là cách duy nhất để lộ thao tác.
- Input có label thật phía trên, trợ giúp và lỗi gắn với trường; placeholder chỉ minh họa. Lỗi form tổng hợp khi cần và giữ dữ liệu đã nhập. Cho phép paste và password manager; không thêm bước nhập để trang trí.
- Dialog chỉ cho quyết định cần tập trung: tiêu đề rõ, focus vào trong, trả focus khi đóng, có đường thoát phù hợp; kiểm soát focus và nền theo ngữ nghĩa dialog. Không dùng toast làm nơi duy nhất báo lỗi cần xử lý.
- Loading nói rõ đang làm gì; chặn gửi trùng nhưng giữ nội dung. Skeleton chỉ phản ánh hình dạng nội dung dự kiến, không làm giả tiến độ hoặc dữ liệu.
- Empty phân biệt chưa có dữ liệu, không có kết quả lọc và dữ liệu không còn khả dụng. Mỗi trường hợp có hướng đi phù hợp như “Chọn bài học” hoặc “Xóa bộ lọc”.
- Error nêu việc không hoàn tất và cách khắc phục; thử lại không tạo lượt trùng. Không đổ lỗi, xin lỗi máy móc, lộ stack trace hay tên cấu hình nội bộ cho người học.
- Success phản ánh kết quả đã được xác nhận; chỉ thông báo “Đã lưu” khi lưu thành công. Không dùng lời khen ngẫu nhiên, streak hay số liệu chưa có trong nghiệp vụ.
- Chuyển trạng thái 120–200ms khi cần làm rõ thay đổi; không autoplay, parallax, card bay lên hàng loạt hoặc hover phóng to. `prefers-reduced-motion` loại bỏ chuyển động không thiết yếu; trạng thái vẫn hiểu được khi tắt animation.

## 7. Khả năng tiếp cận là điều kiện nghiệm thu

- Chữ thường tương phản ít nhất 4,5:1; chữ lớn ít nhất 3:1. Thành phần UI và tín hiệu focus cần thiết đạt 3:1 với nền liền kề; không dùng riêng màu để truyền đạt trạng thái.
- Dùng semantic HTML, thứ bậc heading hợp lý, một tiêu đề chính cho nội dung trang, skip link và tên truy cập đúng. Thứ tự DOM theo thứ tự đọc; không đảo bằng CSS khiến bàn phím đi sai hướng.
- Mọi luồng ảnh hưởng thao tác được bằng bàn phím, focus nhìn thấy và không bị lớp ghim che. Menu/dialog có trạng thái mở/đóng và quản lý focus; thông báo bất đồng bộ qua live region phù hợp, không đọc lại toàn transcript.
- Kiểm tra zoom 200% và reflow ở bề rộng tương đương 320 CSS px, nội dung tiếng Việt có dấu, câu tiếng Anh dài và font fallback. Không dựa vào screenshot desktop để kết luận accessibility.
- Không tự phát âm thanh; nút phát/dừng có tên và trạng thái rõ. Không lộ phụ đề/đáp án đang ẩn qua DOM truy cập, tooltip hay live region.

## 8. Quy trình áp dụng bắt buộc

1. **Đọc và khảo sát:** đọc tài liệu này, ADR 0010, luồng liên quan; tìm component, token, CSS và API đang có. Ghi màn hình/phạm vi thay đổi, hành động chính, vấn đề nhìn thấy và điều cần giữ theo nghiệp vụ. Giữ thay đổi sẵn có của người dùng.
2. **Lập bản thiết kế ngắn trước code:** nêu mục tiêu người học, điểm nhấn duy nhất, token dùng lại, phân cấp chữ, bố cục desktop/mobile bằng wireframe, các trạng thái và câu chữ chính. So sánh ít nhất hai cách bố trí khi thay cấu trúc lớn.
3. **Tự phản biện theo brief:** trả lời “Bỏ tên sản phẩm đi, giao diện này còn thể hiện việc luyện giao tiếp A2–B1 không?”; loại chi tiết chung chung và ghi điều đã sửa cùng lý do. Đối chiếu thứ tự ưu tiên ở mục 1 rồi mới triển khai. Đây là bước tự rà soát, không yêu cầu thêm vòng xin phép cho việc đã được giao.
4. **Triển khai từ nền tảng:** token → shared component → màn hình. Tái sử dụng Brand, Icon, PageHeading và control hiện có trước khi tạo mới. Feature selector có phạm vi rõ; tránh selector toàn cục mới, specificity chồng chéo, `!important` và override cuối file để chữa lỗi dây chuyền. Không thêm UI kit/dependency chỉ để đổi diện mạo.
5. **Kiểm chứng:** chạy lệnh phù hợp trong [testing](testing.md); xem giao diện thật và chụp desktop/mobile khi công cụ cho phép. Kiểm tra đủ trạng thái liên quan bằng fixture/mock tách runtime, không gọi AI thật để làm đẹp ảnh nghiệm thu. Không chụp dữ liệu riêng tư.
6. **Tinh chỉnh và bàn giao:** xem ảnh, bỏ trang trí không có nhiệm vụ, sửa lệch baseline, nhịp spacing, độ dài dòng và phân cấp CTA. Ghi màn hình đã kiểm tra, kích thước, trạng thái, kết quả, phần chưa xác minh và ảnh minh chứng. Nếu chưa có browser thì ghi rõ, không tuyên bố đã nghiệm thu trực quan.

### Điều kiện đạt cho mỗi đợt cải tiến

- [ ] Có bản thiết kế ngắn và lý do riêng cho tình huống học; giữ hướng ADR 0010.
- [ ] Token, chữ, icon, khoảng cách, bo góc và CTA nhất quán; không có màu riêng vô cớ.
- [ ] Màn hình đầu tiên cho biết đang học gì và thao tác tiếp theo; không cần đọc hero để tìm tác vụ.
- [ ] Có trạng thái loading/error/empty/success và trạng thái media liên quan; dữ liệu thực không bị thay bằng mẫu.
- [ ] Đã kiểm tra ít nhất 360px, 768px và 1440px; thêm 320px cho tràn ngang và hai phía breakpoint thay đổi.
- [ ] Đã kiểm tra bàn phím, focus, zoom/reflow, reduced motion và tương phản các cặp thực tế.
- [ ] Đã kiểm tra dấu tiếng Việt, nội dung dài, ẩn phụ đề/đáp án và giữ nguyên hợp đồng tính năng liên quan.
- [ ] Đã xem ảnh trước/sau cùng viewport/trạng thái khi có thể; không có clipping, overlap, whitespace vô nghĩa hoặc các vùng nhấn cạnh tranh.
- [ ] Checks phù hợp đã chạy; mọi giới hạn xác minh được ghi, không đánh dấu đạt phần chưa thử.

Thiếu tiêu chí hành vi, khả năng tiếp cận hoặc kiểm chứng liên quan thì chưa nghiệm thu toàn bộ. Đẹp hơn ở một ảnh không bù cho thao tác bị hỏng.

## 9. Lộ trình và quản lý quy chuẩn

Ưu tiên theo phụ thuộc: (1) rà token/font/control và app shell; (2) catalog, chi tiết, học liệu; (3) nghe, phát âm, hội thoại và media; (4) nhận xét, sổ tay, ôn tập, lịch sử, thống kê; (5) tài khoản/hồ sơ và quản trị. Chọn phạm vi từng đợt theo tác vụ được giao; không tự mở rộng refactor toàn ứng dụng.

Khảo sát mã ngày 25/11/2026 thấy `global.css` đã có font local, token và sidebar; `practice-chat.css` vẫn có màu trực tiếp như `#E7F1E9` cho lượt người học. Đây là điểm cần đối chiếu khi cải tiến, chưa phải kết luận về CSS cuối cùng sau cascade hay bằng chứng browser. Không đổi mã trong đợt thiết lập tài liệu này.

Tài liệu này sở hữu quy tắc chi tiết; ADR 0010 sở hữu hướng và lý do. Không sao chép toàn bộ quy chuẩn sang nhiều nơi. Khi đổi màu/font/bố cục chủ đạo, cập nhật ADR cùng chuẩn; khi thêm component/trạng thái, cập nhật chuẩn và bằng chứng liên quan trong cùng đợt. Không sửa nội dung skill upstream để áp quy tắc riêng dự án.
