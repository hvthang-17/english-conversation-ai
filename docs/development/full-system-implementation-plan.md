# Kế hoạch triển khai từ đầu — 10 nhóm chức năng

Kế hoạch giả định bắt đầu từ database rỗng. Mọi task ở trạng thái chưa thực hiện trong kế hoạch này. Thiết kế dữ liệu gồm **46 bảng**, tính cả hai bảng Spring Session, theo [database.md](../architecture/database.md). Stack gồm React, TypeScript, Vite, Java Spring Boot, MySQL và Flyway. Đối tượng là học viên tự chọn A2/B1 và quản trị viên.

## Phạm vi và thứ tự

G4 dùng **Azure Speech Text to Speech Free (F0)**. G5 dùng **Azure Pronunciation Assessment** để chấm theo mẫu. G6 dùng Azure UNSCRIPTED mặc định chấm lượt VOICE đủ điều kiện khi tính năng được bật. [ADR 0016](../decisions/0016-g6-direct-voice-submission.md) [ADR 0014](../decisions/0014-azure-speech-tts-g4.md) · [ADR 0015](../decisions/0015-azure-pronunciation-assessment-g5.md)

- Triển khai theo đúng thứ tự **1 → 10**. Hội thoại AI ở nhóm 6 là luồng demo trọng tâm. Kiểm tra sớm các rủi ro Gemini, TTS, micro và Azure ở giai đoạn 0 để giảm khả năng phát hiện muộn. Các thử nghiệm này chưa mở tính năng cho người dùng.
- Bản đầu dùng TTS đọc văn bản cho từ vựng, mẫu câu, hội thoại mẫu, đề nghe và shadowing. IPA là phiên âm hiển thị và có thể hỗ trợ cách đọc nếu dịch vụ tương thích. Quản trị học liệu bản đầu quản lý kịch bản, lời thoại, bản dịch, IPA và mã giọng. Audio, ảnh và video học liệu tải sẵn thuộc đợt mở rộng riêng sau nghiệm thu 46 bảng. Ảnh đại diện người dùng và file thu âm tạm có vòng đời riêng.
- Gemini dự kiến phục vụ hội thoại, chép lời và phản hồi nội dung. G5 dùng Azure Pronunciation Assessment `SCRIPTED` cho bài có câu tham chiếu. G6 chấm lời nói tự do bằng Azure mặc định khi dịch vụ chấm được bật. Chỉ gửi dữ liệu hư cấu khi thử thật trong phạm vi được cấp. Điểm kỹ năng là phản hồi luyện tập theo thang đo được công bố.
- G1 thiết lập quyền admin và công cụ khởi tạo tối thiểu. Dùng seed được rà soát để G2–G6 chạy trước màn hình quản trị đầy đủ ở G10. G1 bàn giao trang quản lý dữ liệu và luồng xóa, sau đó từng nhóm cập nhật danh mục dữ liệu, ownership và xử lý xóa của phần mình.

## Ước lượng và mốc

| Mốc | Công việc | Phụ thuộc trực tiếp | Giờ-người |
| --- | --- | --- | ---: |
| P0 | Nền tảng, hợp đồng kỹ thuật và kiểm chứng rủi ro | Thiết kế 46 bảng | 26 |
| G1 | Tài khoản và hồ sơ | P0 | 48 |
| G2 | Khám phá bài học | G1 | 34 |
| G3 | Từ, mẫu câu, hội thoại mẫu và ôn tập | G2, TTS | 34 |
| G4 | Luyện nghe | G2–G3, TTS | 36 |
| G5 | Phát âm và shadowing | G1–G3, micro và Azure | 50 |
| G6 | Hội thoại AI | G1–G5, Gemini | 72 |
| G7 | Cá nhân hóa | G1–G6 có dữ liệu kết quả | 30 |
| G8 | Tiến độ và động lực | G1–G7 | 38 |
| G9 | Hỗ trợ và phản hồi | G1, G2, G6, hộp thư G8 | 29 |
| G10 | Quản trị | G1–G9 | 47 |
| E2E | Dùng thử, sửa lỗi và bàn giao | G1–G10 | 32 |
| | **Tổng trước dự phòng** | | **476** |
| | **Dự phòng 72 giờ** | | **548** |

**Đã chốt đủ 10 nhóm cho bản bàn giao.** Không dùng hạn nộp để cắt phạm vi hoặc làm điều kiện đóng mốc. Ước lượng hiện tại là **548 giờ-người** gồm dự phòng, hiệu chỉnh sau P0 và G3. Với hai người × 5 giờ/ngày × 5 ngày/tuần, con số này tương đương khoảng **11 tuần** ở công suất danh nghĩa. Mức bàn giao chấm phát âm Azure được chốt sau khi xem bằng chứng P0. Các mốc G1–G10 dùng để kiểm tra tiến độ và chất lượng từng phần.

Phân công đề xuất. Người A phụ trách schema, API, quyền và tích hợp dịch vụ. Người B phụ trách UI, nội dung A2/B1 và kiểm thử trình duyệt. Hai người thống nhất hợp đồng API trước khi chia việc, đổi vai rà soát tại mỗi mốc và có thể chuẩn bị seed, wireframe và fixture song song. Thứ tự đóng nghiệm thu vẫn là G1–G10.

## Cách chạy kiểm thử và ghi nghiệm thu

Mỗi task có ba phần **Cách thực hiện**, **Kiểm thử** và **Điều kiện đạt**. Dùng mã task để đặt tên ca kiểm thử hoặc đối chiếu kết quả. Ví dụ `G6.4-01` có thể chỉ ca tải lại khi đang chép lời.

| Loại kiểm thử | Dùng để xác minh | Bằng chứng cần ghi |
| --- | --- | --- |
| Unit | Validation, cách tính điểm, quy tắc lộ trình, thời gian và chuyển trạng thái | Fixture đầu vào, giá trị mong đợi, kết quả chạy và tên test |
| API và MySQL | Migration, transaction, UNIQUE/FK, owner, session, retry, xóa và xử lý đồng thời | Lệnh chạy, phiên bản MySQL, dữ liệu trước/sau và HTTP status khi liên quan |
| Trình duyệt | Luồng học viên/admin, form, phục hồi trạng thái, keyboard và lỗi hiển thị | Phiên bản trình duyệt, bước thực hiện và ảnh hoặc test report khi phù hợp |
| Thiết bị thật | Quyền mic, ghi âm, codec, phát, dừng ghi và thoát màn hình | Hệ điều hành, thiết bị, trình duyệt, định dạng và kết quả từng ca |
| Provider giả lập hoặc thật | Ánh xạ kết quả, timeout, quota và chất lượng phản hồi | Chế độ chạy, task/provider, cỡ mẫu, latency/usage khi có. Dữ liệu hư cấu và không ghi khóa API |
| Rà nội dung và dùng thử | Đúng vai, độ khó, lỗi cần sửa, phát âm và khả năng hoàn thành nhiệm vụ | Phiếu chấm, cỡ mẫu, số người hoàn thành trên số người thử và lỗi quan sát được |

Ghi mỗi ca theo mẫu dưới đây. Ca lỗi phổ biến, retry, quyền và xóa được ưu tiên tự động hóa ở lớp có thể tái lập ổn định. Kiểm tra chất lượng giọng, nội dung và thiết bị cần bằng chứng tương ứng.

| Mã ca | Tiền điều kiện và dữ liệu | Bước chạy | Mong đợi | Thực tế | Trạng thái | Bằng chứng |
| --- | --- | --- | --- | --- | --- | --- |
| G6.4-01 | Buổi VOICE, bản ghi đang chép, fixture chưa trả kết quả | Tải lại, cho fixture trả nháp, sửa rồi xác nhận | Khôi phục đúng bản ghi và tạo đúng một lượt sau xác nhận | Chưa chạy | Chưa chạy | Điền kết quả khi thực hiện |

Dùng trạng thái **Đạt**, **Không đạt**, **Chưa chạy** hoặc **Chờ điều kiện**. Đóng mốc khi các ca bắt buộc đạt và có bằng chứng. Nếu thiếu thiết bị, quyền gọi hoặc cấu hình, ghi ca chờ cùng điều kiện để chạy lại. Vẫn bàn giao phần kiểm thử độc lập đã hoàn tất.

## P0 — Nền tảng và kiểm chứng sớm

Kết quả chạy, các ca đang chờ và lệnh tái lập được ghi trong [biên bản P0 bản dựng sạch](phase0/README.md#kết-quả-p0-của-bản-dựng-sạch-46-bảng).

- [x] `P0.1` Tạo cấu trúc backend/frontend, cấu hình môi trường local, test và triển khai, health endpoint và hướng dẫn chạy. Đưa cấu hình mẫu vào repo, cấp secret qua môi trường.
  - **Cách thực hiện.** Tách cấu hình local/test, khai báo biến bắt buộc, health endpoint và hướng dẫn lệnh chạy.
  - **Kiểm thử.** Khởi động từ checkout mới với cấu hình mẫu. Gọi health khi đủ cấu hình và chạy lại khi thiếu biến bắt buộc.
  - **Điều kiện đạt.** Frontend mở được, health phản ánh trạng thái backend và lỗi cấu hình chỉ nêu tên biến, không in giá trị secret.
- [x] `P0.2` Khởi tạo dòng Flyway mới từ [đặc tả 46 bảng](../architecture/database.md) trên schema phát triển rỗng. Ngày 25/11/2026, V1 và restart đã đạt tại `english_conversation`. [Bằng chứng MySQL](phase0/mysql.md).
  - **Cách thực hiện.** Đích cấu hình là `english_conversation` trên `127.0.0.1:3306`. Dùng dòng migration mới từ `V1`, sắp DDL theo FK, Hibernate validate, schema Spring Session theo dependency và seed mẫu tách riêng. Thao tác dọn dữ liệu cũ ở schema đích cần xác nhận riêng. [ADR 0012](../decisions/0012-system-database-scope.md#đích-mysql-phát-triển-cho-p02).
  - **Kiểm thử.** Ghi host, cổng, UUID và schema. Khi đích sẵn sàng, kiểm tra lịch sử Flyway có V1 mới, restart không áp lại V1, cùng kiểm tra Unicode, UTC, UNIQUE/FK/CHECK. Bằng chứng cũ ghi rõ probe đã chạy trên `english_conversation_dev`.
  - **Điều kiện đạt.** Schema đích dùng dòng migration mới, ứng dụng trỏ đúng qua restart và V1 tạo đủ bảng nền. Đối chiếu đủ 46 bảng nghiệp vụ và Session ở E2E.
- [x] `P0.3` Thiết lập API JSON, định dạng lỗi, validation, phân trang, CSRF cho SPA và phương án same-origin/proxy. Chuẩn bị fixture tài khoản, bài học và provider giả lập có success, timeout, quota, kết quả sai cấu trúc. Kiểm tra cookie Session JDBC theo môi trường ở G1 và phân trang trên catalog thật ở G2.
  - **Cách thực hiện.** Định nghĩa envelope lỗi, mã nghiệp vụ, phân trang, luồng lấy CSRF và hợp đồng provider giả lập.
  - **Kiểm thử.** Gửi request sai kiểu, thiếu CSRF cho thao tác bảo vệ, dữ liệu trang đầu/cuối và kích hoạt fixture success, timeout, quota, JSON sai.
  - **Điều kiện đạt.** Client phân biệt được lỗi; CSRF chặn mutation, phân trang ổn định và fixture kích hoạt được từng kết quả.
- [ ] `P0.4` Kiểm chứng micro, thu âm, phát và encoding trên Chrome/Edge Windows và Chrome Android. Ghi phiên bản, quyền mic và ca từ chối quyền. Kiểm tra sớm TTS theo câu, hai giọng mẫu, tốc độ, độ trễ và hủy phát. Safari có thể được khảo sát nếu được đưa vào phạm vi.
  - **Cách thực hiện.** Dựng màn hình thử thu âm, phát lại, TTS nhiều giọng, đổi tốc độ và hủy phát.
  - **Kiểm thử.** Thử trực tiếp trên Chrome Windows, Edge Windows và Chrome Android. Cấp/từ chối quyền, thu, nghe lại và rời màn hình khi đang phát.
  - **Điều kiện đạt.** Biên bản ghi thiết bị, trình duyệt, codec và kết quả. Khi từ chối quyền có phản hồi rõ và mic dừng sau khi thoát.
- [ ] `P0.5` Chuẩn bị probe Gemini và Azure bằng bản ghi/kịch bản hư cấu. Trước khi gọi thật, xác nhận quyền, model/locale, nguồn audio tương thích, chi phí và trần gọi. Ghi căn cứ cho provider, timeout, quota, chính sách lỗi và thông báo xử lý audio.
  - **Cách thực hiện.** Định nghĩa probe cho CHAT, chép lời, FEEDBACK và Azure chấm phát âm, có hạn thời gian và ghi usage đã lọc.
  - **Kiểm thử.** Chạy fixture lỗi, thiếu trường và timeout. Nếu được cấp phạm vi, thử với dữ liệu hư cấu và ghi model, locale, số lần gọi, độ trễ.
  - **Điều kiện đạt.** Probe có kết quả tái lập. Với Azure, ghi riêng khả năng chấm đọc mẫu, nói tự do, kết quả từng từ/âm vị, ngữ điệu và ca thiếu dữ liệu. Dùng bằng chứng đó để chốt mức bàn giao G5/G6. Probe thật chưa được phép hoặc chưa chạy có trạng thái chờ riêng.
- [x] `P0.6` Tạo bộ kiểm tra build/typecheck/lint, unit, API–MySQL và trình duyệt. Lập bộ nội dung mẫu, mẫu số đo độ trễ/tỷ lệ hoàn thành và cách thu ý kiến khoảng 10 người dùng thử. Xác định quy trình dọn audio, transcript, log và sao lưu theo chính sách đã chốt.
  - **Cách thực hiện.** Định nghĩa lệnh kiểm tra, dữ liệu fixture, đồng hồ thử TTL, mẫu phiếu đánh giá và cách ghi kết quả.
  - **Kiểm thử.** Chạy ít nhất một kiểm tra ở mỗi lớp đã thiết lập, thử fixture hết hạn và mở biểu mẫu dùng thử.
  - **Điều kiện đạt.** Có lệnh tái lập, dữ liệu hư cấu, danh sách TTL cần kiểm tra và mẫu ghi số người hoàn thành trên số người được giao việc.

**Bàn giao và nghiệm thu**

- Backend và frontend chạy local theo hướng dẫn, pipeline kiểm tra được, migration nền chạy trên MySQL rỗng. Probe có bằng chứng hoặc đánh dấu chờ môi trường/quyền gọi cụ thể. Các task có thể triển khai bằng fixture vẫn tiếp tục.

## G1 — Tài khoản và hồ sơ học viên

**Dữ liệu** `users`, `learner_profiles`, `user_topic_interests`, Spring Session JDBC, token/email outbox, lịch nhắc và job xóa. Tạo `topics` tối thiểu trước khi ghi sở thích; catalog đầy đủ ở G2.

[Báo cáo nghiệm thu và lệnh chạy G1](acceptance/g1-account-acceptance.md).

| Quyết định | Cách thực hiện |
| --- | --- |
| Tái sử dụng mã và tiếp tục dòng Flyway | Đối chiếu mã tài khoản, hồ sơ, khôi phục mật khẩu và xóa tài khoản với schema mới. V1 đã tạo `users` và `learner_profiles` trên `english_conversation`. Thêm migration cho Session JDBC và các bảng G1 còn thiếu, rồi chạy với cấu hình nghiệp vụ để nghiệm thu trên MySQL. [Bằng chứng V1](phase0/mysql.md). |
| Phiên đăng nhập | Spring Session JDBC hết hạn sau 30 phút không hoạt động và cho phép đăng nhập trên nhiều thiết bị. Đăng xuất kết thúc phiên hiện tại. Đổi hoặc đặt lại mật khẩu thu hồi mọi phiên. Kiểm tra bằng hai trình duyệt trên MySQL thật. |
| Khôi phục mật khẩu | Người học đăng nhập ngay sau khi đăng ký. Liên kết xác thực qua email chỉ dùng để đặt lại mật khẩu. Dùng Mailpit để thử đường dẫn mở đúng form, token hết hạn và chỉ dùng được một lần. Nhà gửi, tên miền và quyền SMTP thật được chốt khi chuẩn bị bật production. |
| Phạm vi xóa | Xóa dữ liệu học tập giữ tài khoản, hồ sơ, avatar và lịch. Xóa tài khoản yêu cầu mật khẩu hiện tại, chặn truy cập và thu hồi phiên ngay, sau đó chạy job dọn dữ liệu và file có thể thử lại. Mỗi nhóm chức năng bổ sung phần dọn dữ liệu của mình trước nghiệm thu. [Chính sách dữ liệu](../product/data-and-browser-proposal.md). |
| Phụ thuộc G2 và G8 | G1 tạo `topics` tối thiểu để lưu sở thích, đồng thời lưu mục tiêu số phút mỗi ngày, lịch, múi giờ và tùy chọn nhắc. G2 mở rộng catalog. G8 nghiệm thu worker gửi nhắc học. |

- [x] `G1.1` Tạo đăng ký, đăng nhập, đăng xuất, lấy người dùng hiện tại và đổi mật khẩu. Chuẩn hóa email, đặt UNIQUE, dùng PasswordEncoder, validation và lỗi thống nhất. Đăng ký công khai cấp `LEARNER`. Bootstrap admin có kiểm soát từ môi trường, không reset admin đã tồn tại.
  - **Cách thực hiện.** Tạo service đăng ký/login/đổi mật khẩu, bảng users và bootstrap admin có cấu hình riêng.
  - **Kiểm thử.** Đăng ký email khác hoa/thường và có khoảng trắng, dùng sai mật khẩu, gửi role ADMIN từ signup và chạy bootstrap hai lần.
  - **Điều kiện đạt.** Một email chuẩn hóa chỉ tạo một tài khoản, hash lưu đúng, signup công khai chỉ cấp LEARNER và admin sẵn có giữ nguyên.
- [x] `G1.2` Tích hợp Spring Security và Session JDBC, idle 30 phút, nhiều thiết bị, rotation sau đăng nhập, CSRF và cookie. Kiểm tra status/role trên request bảo vệ. Logout thu hồi phiên hiện tại; khóa, đổi quyền, đổi hoặc reset mật khẩu thực hiện thu hồi theo chính sách.
  - **Cách thực hiện.** Lưu SecurityContext qua Session JDBC, áp rotation, CSRF, timeout và kiểm tra security version/role khi cần.
  - **Kiểm thử.** Dùng hai trình duyệt và MySQL. Kiểm tra thay ID phiên, request kế tiếp, hết hạn 30 phút, logout một phiên, mất cả hai phiên sau đổi mật khẩu, khóa tài khoản và CSRF sai.
  - **Điều kiện đạt.** Logout một phiên giữ phiên kia. Phiên hết hạn, khóa hoặc cần thu hồi không truy cập được endpoint bảo vệ.
- [x] `G1.3` Hoàn thiện luồng **Quên mật khẩu** bằng liên kết gửi qua email, dùng token một lần có hạn, hash và SMTP outbox. Bỏ luồng xác minh email độc lập khỏi màn hình và hợp đồng bản đầu, đồng thời gỡ điều kiện xác minh khỏi đăng nhập, AI và bật nhắc học trong phần mã nguồn tái sử dụng.
  - **Cách thực hiện.** Phát hành token đặt lại mật khẩu, lưu hash và outbox mã hóa. Worker gửi ngoài transaction. Giới hạn yêu cầu, trả phản hồi chung và thu hồi phiên khi reset thành công. Dùng Mailpit local/staging và SMTP thật chỉ ở production khi được cấp phép.
  - **Kiểm thử.** Đăng ký rồi đăng nhập ngay, dùng fixture để gọi AI như tài khoản hợp lệ và bật nhắc học. Mở liên kết từ Mailpit để kiểm tra đúng biểu mẫu và đặt lại mật khẩu thành công. Thử yêu cầu với email không tồn tại, token hết hạn, dùng lại token, SMTP lỗi và cookie cũ trên cả hai trình duyệt sau reset. Kiểm tra thư thử chỉ vào hộp thư nội bộ.
  - **Điều kiện đạt.** Đăng ký không phát email xác minh. Tài khoản hoạt động sử dụng được các chức năng theo quyền và quota thông thường. Chỉ người có liên kết reset hợp lệ mới đặt lại được mật khẩu và mỗi token chỉ dùng một lần. Reset không tiết lộ tài khoản.
- [x] `G1.4` Tạo màn hình hồ sơ, avatar, trình độ A2/B1, mục tiêu học, sở thích chủ đề và số phút/ngày. Backend kiểm tra owner, loại/kích thước ảnh và dọn ảnh cũ hoặc file mồ côi. Thiết lập giờ, ngày luyện, múi giờ và tùy chọn nhắc. Worker gửi nhắc được hoàn thiện tại G8.
  - **Cách thực hiện.** Xây form hồ sơ và lịch, kiểm tra avatar, level, chủ đề, mục tiêu phút và giờ theo múi giờ.
  - **Kiểm thử.** Lưu rồi tải lại hồ sơ; gửi level lạ, avatar sai kiểu/quá cỡ, ngày trùng và cập nhật hồ sơ người khác.
  - **Điều kiện đạt.** Dữ liệu hợp lệ giữ nguyên sau tải lại, đầu vào sai bị chặn và ảnh thay thế được ghi nhận để dọn.
- [x] `G1.5` Tạo trang **Dữ liệu học tập của tôi** với dữ liệu của chính người dùng, xác nhận trước thao tác xóa và danh sách module sẽ bổ sung theo tiến độ. Xây hợp đồng xóa một buổi, xóa dữ liệu học tập và xóa tài khoản. Xóa tài khoản xác thực lại mật khẩu, thu hồi phiên, hủy email/lịch đang chờ và tạo job dọn DB/file có retry. Mỗi nhóm thêm phần dọn dữ liệu tương ứng trước nghiệm thu.
  - **Cách thực hiện.** Tạo danh sách dữ liệu cá nhân, ba phạm vi xóa và job chạy lại được. Đăng ký đầu việc dọn theo từng module.
  - **Kiểm thử.** Xóa dữ liệu học tập rồi xác nhận tài khoản, hồ sơ, avatar và lịch còn dùng được. Thử xóa tài khoản với mật khẩu sai và đúng, kiểm tra cookie cũ và mô phỏng dọn file lỗi rồi retry.
  - **Điều kiện đạt.** Yêu cầu sai không tạo job. Luồng hợp lệ chặn ngay truy cập, dọn đúng phạm vi, job chỉ hoàn tất khi xử lý xong.
- [x] `G1.6` Xây UI đăng ký/login/đổi mật khẩu và các màn hình cần đăng nhập. Kiểm tra form lỗi, trạng thái mất phiên, quay lại màn hình mong muốn, thông báo gửi email và quyền truy cập từ hai tài khoản.
  - **Cách thực hiện.** Nối form với API, trạng thái người dùng hiện tại và route bảo vệ, lưu đường dẫn quay lại nếu phù hợp.
  - **Kiểm thử.** Dùng trình duyệt để đăng ký/login/logout, nhập lỗi, truy cập thẳng trang bảo vệ và giả lập mất phiên khi lưu form.
  - **Điều kiện đạt.** Lỗi hiển thị đúng trường, mất phiên có trạng thái rõ, đăng nhập thành công chuyển tới đích hợp lệ và không lưu session ID trong localStorage.

**Bàn giao và nghiệm thu**

- Đăng ký email khác hoa/thường bị chặn trùng. Mật khẩu được băm, session đổi ID khi login, tồn tại qua request và hết hạn đúng. CSRF sai bị từ chối. Logout một thiết bị giữ phiên thiết bị kia; khóa hoặc xóa thu hồi theo quy tắc. Link đặt lại mật khẩu chỉ dùng một lần. Job xóa chạy lại an toàn và không để thao tác muộn tái tạo dữ liệu.

## G2 — Quản lý và khám phá nội dung

**Dữ liệu** `topics`, `scenarios`, `scenario_levels`, `scenario_role_variants`, `lessons`, `lesson_bookmarks`.

[Bằng chứng nghiệm thu và phiếu rà nội dung G2](acceptance/g2-catalog-acceptance.md).

### Quyết định trước triển khai

| Nội dung | Cách xử lý |
| --- | --- |
| Bài học và tình huống | Bài học là đơn vị tìm, lọc và lưu. Mỗi bài thuộc một chủ đề và một cấp độ A2 hoặc B1. Tình huống dùng để luyện hội thoại. Bài có thể gợi ý một tình huống hỗ trợ cùng cấp độ. |
| Chủ đề và migration | Bổ sung migration sau V5. Dùng lại ID và mã của sáu chủ đề đã nạp cho hồ sơ. Xếp bài phỏng vấn vào `WORK`. Giữ ID bài học ổn định để bảo toàn dấu trang và tham chiếu. |
| Công khai và ẩn | Chỉ hiển thị bài `ACTIVE` thuộc chủ đề đang hoạt động. Khi công khai, kiểm tra nội dung và cấp độ của tình huống gợi ý nếu có. Khi tình huống gợi ý bị ẩn sau đó, giữ bài công khai và bỏ thao tác mở tình huống. Chỉ xóa cứng bản nháp chưa có tham chiếu trong quy trình quản trị. Nội dung đã dùng chuyển sang `ARCHIVED` để giữ tham chiếu và snapshot các buổi đã luyện. |
| Tìm kiếm và lọc | Tìm từ khóa trong tên và tóm tắt, hỗ trợ tiếng Việt không dấu và không phân biệt hoa thường. Kết hợp từ khóa, chủ đề và cấp độ bằng AND. Thứ tự và khóa phân xử phải ổn định qua các trang. |
| Dấu cá nhân | `FAVORITE` và `SAVE_FOR_LATER` hoạt động độc lập. Gửi lại cùng thao tác vẫn chỉ giữ một dòng mỗi người–bài–loại. Bài bị ẩn vẫn xuất hiện dưới trạng thái **Không còn khả dụng** trong danh sách đã lưu để chủ sở hữu có thể bỏ dấu. |
| Phạm vi quản trị | G2 dùng seed có kiểm soát và dữ liệu mẫu để kiểm tra quy tắc công khai. Màn hình quản trị nội dung đầy đủ thuộc G10. |

### Tám bài mẫu ban đầu

| Bài học | Chủ đề | Cấp độ | Mục tiêu | Tình huống gợi ý và vai |
| --- | --- | --- | --- | --- |
| Giới thiệu bản thân | `INTRODUCTION` | A2 | Chào hỏi, nói tên và nêu một sở thích. | Gặp bạn mới ở câu lạc bộ. Người học là thành viên mới, AI là thành viên đón tiếp. |
| Kể về thói quen | `DAILY_LIFE` | A2 | Mô tả một ngày và hỏi về thời gian sinh hoạt. | Trò chuyện giờ nghỉ. Người học và AI là hai bạn cùng lớp. |
| Đổi món đồ đã mua | `SHOPPING` | B1 | Giải thích lý do đổi hàng và hỏi về lựa chọn thay thế. | Quầy đổi hàng. Người học là khách, AI là nhân viên. Thử thêm phương án đổi vai. |
| Hỏi đường đến nhà ga | `TRAVEL` | A2 | Hỏi đường và xác nhận một chỉ dẫn ngắn. | Đứng ở ngã tư. Người học là khách du lịch, AI là người địa phương. |
| Đặt phòng khách sạn | `TRAVEL` | B1 | Hỏi điều kiện đặt phòng, so sánh lựa chọn và xác nhận lịch. | Quầy lễ tân. Người học là khách, AI là lễ tân. |
| Thống nhất kế hoạch học nhóm | `EDUCATION` | B1 | Đề xuất lịch, giải thích một vướng mắc và thống nhất giờ họp. | Hẹn làm bài nhóm. Người học và AI là hai thành viên. |
| Ngày đầu đi làm | `WORK` | A2 | Giới thiệu ngắn với đồng nghiệp và hỏi nơi nhận hướng dẫn. | Gặp đồng nghiệp mới. Người học là nhân viên mới, AI là người hướng dẫn. |
| Phỏng vấn cơ bản | `WORK` | B1 | Tóm tắt kinh nghiệm, nêu điểm mạnh và trả lời câu hỏi tiếp nối. | Phỏng vấn vị trí thực tập. Người học là ứng viên, AI là nhà tuyển dụng. |

V7 đã có tóm tắt, mã mục tiêu, mã tình huống và bối cảnh cho từng bài. V8 đồng bộ bối cảnh và hướng dẫn cho các bài 103, 105 và 106 theo phiếu rà. Người rà tiếng Anh kiểm tra độ khó, vai và mục tiêu trước khi ký nhận nội dung.

- [x] `G2.1` Tạo catalog chủ đề, bài học và tình huống. Chốt điều kiện công khai, mã ổn định, level A2/B1, mục tiêu và tình huống gợi ý. Ghi quy tắc ẩn hoặc xóa bản nháp, giữ nguồn tham chiếu của lịch sử.
  - **Cách thực hiện.** Tạo quan hệ chủ đề–bài–tình huống–level, vòng đời nháp/công khai/ẩn và kiểm tra điều kiện công khai.
  - **Kiểm thử.** Thử công khai bài thiếu level, tình huống không hỗ trợ level của bài và ẩn nguồn đang có tham chiếu.
  - **Điều kiện đạt.** Dữ liệu sai không công khai; ẩn nguồn không làm hỏng dữ liệu tham chiếu.
- [ ] `G2.2` Biên soạn nội dung gốc theo chủ đề giới thiệu bản thân, sinh hoạt, mua sắm, du lịch, học tập, công việc và phỏng vấn. Chuẩn bị bộ tình huống A2/B1 có bối cảnh, mục tiêu và vai. **Seed, hai ca Flyway và rà nội bộ đã hoàn thành, chờ người rà tiếng Anh ký nhận.** [Phiếu rà với tám mẫu đối thoại](acceptance/g2-content-review.md).
  - **Cách thực hiện.** Tạo danh mục seed có mã ổn định, mô tả, mục tiêu, level và vai, kèm phiếu rà nội dung.
  - **Kiểm thử.** Chạy Flyway trên MySQL rỗng và trên schema G1 đã có V5. Khởi động lại để xác nhận seed không tạo bản ghi trùng và sở thích đã lưu vẫn trỏ đúng chủ đề. Rà ít nhất một bài A2, một bài B1 và độ phủ của sáu chủ đề.
  - **Điều kiện đạt.** Mã không trùng; mẫu đúng level, vai, mục tiêu và được người rà ký nhận.
- [x] `G2.3` Tạo API danh sách/chi tiết, tìm từ khóa trên bài công khai, lọc theo level/chủ đề và phân trang ổn định. Tách DTO quản trị khỏi DTO học viên để giữ hướng dẫn nội bộ của AI ở server.
  - **Cách thực hiện.** Xây truy vấn lọc/tìm có thứ tự cố định và DTO học viên tối thiểu.
  - **Kiểm thử.** Tìm `dat phong` và `ĐẶT PHÒNG`, kết hợp với chủ đề Du lịch và B1, rồi đổi sang A2 để kiểm tra lọc. Mở trang sau, gọi chi tiết bài ẩn và kiểm tra JSON học viên không chứa hướng dẫn AI.
  - **Điều kiện đạt.** Kết quả không trùng trang trên fixture cố định, không trả nội dung ẩn hay chỉ dẫn AI.
- [x] `G2.4` Tạo thao tác yêu thích và học sau độc lập, chống lưu trùng cùng loại dấu, quyền owner và trang danh sách đã lưu. Xây màn hình danh sách, bộ lọc, chi tiết bài, trạng thái tải/trống/lỗi và bố cục mobile.
  - **Cách thực hiện.** Tạo API dấu trang theo loại, nối tìm kiếm và màn hình bài đã lưu.
  - **Kiểm thử.** Hai học viên cùng lưu một bài, một người lưu cả hai loại, gửi lại thao tác và xóa một loại dấu. Sau khi ẩn bài, kiểm tra catalog loại bài và chủ sở hữu vẫn bỏ được dấu trong danh sách cá nhân.
  - **Điều kiện đạt.** Chỉ một dòng mỗi người–bài–loại, xóa một loại giữ loại kia, tài khoản khác không sửa được.

**Bàn giao và nghiệm thu**

- Chọn được bài A2/B1 theo chủ đề, mục tiêu hoặc tình huống gợi ý. Tìm kiếm/lọc trả đúng kết quả và không trả bài ẩn hay prompt nội bộ. Người học có thể đặt cả hai loại dấu trên cùng bài và chỉ quản lý dấu của mình.

## G3 — Từ vựng, mẫu câu, hội thoại mẫu và ôn tập

**Dữ liệu** `language_items`, `sample_dialogues`, `review_items`, `review_attempts`.

### Quyết định trước triển khai

| Nội dung | Quyết định |
| --- | --- |
| Số lượng và chất lượng học liệu | Dùng tám bài G2. Mỗi bài có ba từ, hai mẫu câu và một hội thoại mẫu dài 4–6 lượt. Mỗi mục có nghĩa tiếng Việt và ví dụ theo tình huống. Ưu tiên IPA `en-US` đã rà cho từ đơn. Mẫu câu có thể bỏ trống IPA nếu chưa có phiên âm đáng tin cậy. Rà một bài A2 và một bài B1 trước khi biên soạn sáu bài còn lại. |
| Nguồn phát và giọng TTS | Dùng giọng tiếng Anh cục bộ của trình duyệt cho học liệu công khai, tái sử dụng phần phát giọng hiện có. Ưu tiên `en-US` rồi dùng giọng tiếng Anh cục bộ sẵn có. `voice_key` là tùy chọn. Một giọng có thể đọc cả hai vai. Thiếu giọng thì hiện thông báo và giữ văn bản để học. TTS đọc văn bản tiếng Anh. IPA dùng để tra cứu. |
| Nghe hội thoại mẫu | Mặc định hiện lời tiếng Anh, ẩn bản dịch. Bật/tắt hai phần độc lập. Phát cả đoạn, nghe lại từng câu và đổi tốc độ giữa 1× và 0,75×. Đánh dấu câu đang phát, dừng khi đổi bài hoặc rời trang. G4 tiếp tục xử lý riêng đề nghe có chính sách giữ kín kịch bản trước khi nộp. |
| Sổ tay cá nhân | Lưu bản sao từ hoặc mẫu câu, nghĩa, ví dụ tiếng Anh và IPA. Mỗi người có một thẻ cho cùng mục nguồn. Bản sao tiếp tục dùng được khi nguồn bị ẩn hoặc xóa. Xóa thẻ xóa các lượt ôn của thẻ. Xóa toàn bộ dữ liệu học tập xóa sổ tay và lượt ôn, giữ tài khoản, hồ sơ và lịch học. |
| Flashcard và quiz chọn nghĩa | Triển khai `FLASHCARD` trước, rồi thêm một dạng `QUICK_QUIZ` chọn nghĩa tiếng Việt của từ hoặc mẫu câu tiếng Anh. Flashcard cập nhật **Cần ôn / Đã nhớ** theo lựa chọn của người học. Backend chấm quiz, lưu đúng/sai và thời điểm ôn. Chọn đáp án có nghĩa phân biệt rõ. Thiếu lựa chọn phù hợp thì cho ôn bằng flashcard. Cả hai luồng chống ghi trùng khi gửi lại. Điền từ và lịch ôn tự động thuộc các giai đoạn sau. |

- [x] `G3.1` Soạn học liệu và hội thoại mẫu theo số lượng, trường nội dung và quy trình rà đã chốt. Backend kiểm tra danh sách câu, thứ tự, giới hạn và mã giọng.
  - **Cách thực hiện.** Soạn mục từ và đoạn thoại theo cấu trúc JSON đã chốt, kiểm tra lời tiếng Anh và mã giọng.
  - **Kiểm thử.** Rà trọn một bài A2 và một bài B1, sau đó kiểm tra số lượng học liệu trên tám bài. Thử thứ tự câu trùng, câu rỗng, đoạn ngoài 4–6 lượt trong bộ mẫu và mã giọng không hợp lệ.
  - **Điều kiện đạt.** Đủ học liệu cho tám bài, nội dung hợp lệ hiện đúng thứ tự và lỗi soạn thảo được phát hiện trước công khai.
- [x] `G3.2` Xây chức năng TTS đọc từ/câu và phát nối tiếp đoạn mẫu. Bổ sung nghe lặp, nghe lại từng câu, đổi tốc độ, dừng khi đổi trang, bật/tắt bản chép lời hoặc bản dịch. Xử lý thiếu giọng, audio chưa sẵn sàng và lỗi tổng hợp; văn bản vẫn dùng được.
  - **Cách thực hiện.** Xây trạng thái phát TTS theo từng câu, đồng bộ phụ đề và hủy playback khi đổi ngữ cảnh.
  - **Kiểm thử.** Kiểm tra mặc định lời Anh hiện và bản dịch ẩn. Nghe cả đoạn, nghe lại câu thứ hai, thử tốc độ 1× và 0,75×, bật/tắt hai phần lời độc lập và điều hướng khi đang phát. Thử thiết bị chỉ có một giọng, thiếu giọng ưu tiên, thiếu giọng tiếng Anh và lỗi phát.
  - **Điều kiện đạt.** Lời/phụ đề theo đúng câu và tốc độ; âm thanh dừng khi rời màn hình, văn bản còn dùng được khi lỗi.
- [x] `G3.3` Xây sổ tay cá nhân. Lưu bản sao từ/câu, nghĩa, ví dụ và IPA để thẻ vẫn ôn được khi ẩn hoặc xóa liên kết nguồn. Chống lưu trùng theo nguồn và người học, cho xóa thẻ của mình.
  - **Cách thực hiện.** Sao chép trường ôn từ học liệu vào thẻ và dùng khóa chống trùng theo nguồn.
  - **Kiểm thử.** Lưu đồng thời hai lần, ẩn hoặc gỡ nguồn, thử xóa thẻ bằng tài khoản khác và xóa bằng chính chủ. Xác nhận các lượt ôn đi cùng thẻ được dọn và xóa dữ liệu học tập vẫn giữ tài khoản, hồ sơ, lịch học.
  - **Điều kiện đạt.** Chỉ một thẻ được tạo, bản sao vẫn ôn được khi mất nguồn và xóa kiểm tra owner.
- [x] `G3.4` Xây `FLASHCARD` tự đánh dấu cần ôn/đã nhớ trước, sau đó thêm `QUICK_QUIZ` chọn nghĩa chấm phía server. **Đã triển khai và kiểm thử API, bàn phím và cây trợ năng Chromium.**
  - **Cách thực hiện.** Ghi lượt flashcard, thời điểm ôn và trạng thái do người học chọn trong cùng giao dịch. Với quiz, ghi kết quả đúng/sai và thời điểm ôn, giữ trạng thái thẻ do người học chọn.
  - **Kiểm thử.** Ôn flashcard bằng bàn phím và trình đọc màn hình, nộp quiz đúng/sai, thử thiếu lựa chọn phù hợp, gửi lại cùng mã và truy cập thẻ khác tài khoản.
  - **Điều kiện đạt.** Flashcard lưu trạng thái tự đánh dấu. Quiz có kết quả chấm server. Gửi lại cùng mã chỉ có một lượt ôn. Khi thiếu lựa chọn phù hợp, người học vẫn ôn được bằng flashcard.

**Bàn giao và nghiệm thu**

- Một bài có thể học từ, nghe mẫu, nghe hội thoại với phụ đề theo câu, lưu từ và ôn lại. Giọng/TTS lỗi có trạng thái rõ. Flashcard và câu hỏi ngắn ghi đúng loại lượt, kết quả và chủ sở hữu.
- [Kết quả triển khai và kiểm thử G3](acceptance/g3-learning-acceptance.md).

## G4 — Luyện nghe phục vụ giao tiếp

**Dữ liệu** `listening_exercises`, `listening_attempts`.

### Quyết định trước triển khai

| Nội dung | Quyết định |
| --- | --- |
| TTS và nghe lại | Dùng Azure Speech F0, tiếng Anh Mỹ và MP3 mono 24 kHz, 48 kbps. Backend tạo audio theo phiên bản đề và tái sử dụng khi tải lại hoặc đổi thiết bị. Học viên nghe lại tự do ở 1× hoặc 0,75×, không giới hạn thời gian làm bài. Khi audio gặp lỗi, báo lỗi và cho thử lại, giữ đáp án nháp. |
| Kịch bản nhiều vai | Giữ `transcript_en` để hiển thị sau nộp. Đề nhiều vai lưu thêm `script_segments_json` gồm thứ tự, nhãn vai, lời tiếng Anh và mã giọng từng lượt. Backend kiểm tra thứ tự, nội dung và mã giọng rồi tạo SSML. Đề một giọng dùng `transcript_en` và `tts_voice_key`. |
| Tạo audio và công khai đề | Gọi Azure khi chuẩn bị công khai phiên bản đề. Chỉ chuyển sang `ACTIVE` khi nội dung hợp lệ và MP3 phát được. Lưu tệp ở thư mục riêng phía server theo ID phiên bản đề, phục vụ qua API kiểm tra quyền. Giữ tệp của đề cũ để tiếp tục lượt đang làm. Mở đề, nghe lại và làm lại dùng MP3 đã có. |
| Kịch bản và đáp án | Cả tám đề đầu dùng `AFTER_SUBMIT` và kịch bản mới so với G3. Trước khi nộp, API chỉ cung cấp audio, hướng dẫn, câu hỏi, lựa chọn công khai và bản nháp của chính người học. Sau khi nộp thành công mới trả lời thoại, bản dịch nếu có, đáp án và giải thích. |
| Bộ học liệu | Tám đề, mỗi bài G2 một đề theo cấp độ A2 hoặc B1 của bài. Bộ đề có cả câu ngắn và đoạn hội thoại, bao phủ chọn đáp án, điền từ, sắp xếp và nghe–chép. |
| Chấm điểm | Mỗi câu đúng được 1 điểm, sai hoặc bỏ trống được 0. Chọn đáp án so mã lựa chọn. Sắp xếp so toàn bộ thứ tự mã. Điền từ và nghe–chép chuẩn hóa hoa/thường, khoảng trắng và dấu câu cuối câu. Chấp nhận biến thể khi tác giả khai báo trong đáp án của câu. |
| Tiếp tục và phiên bản đề | Lưu nháp trên server và cho tiếp tục trên thiết bị khác. Mỗi người có tối đa một lượt `IN_PROGRESS` trên mỗi phiên bản đề. Mỗi lần lưu gửi `draft_version`. Nếu đã có bản mới hơn, API trả `409` và giao diện cho tải lại bản đã lưu. Nộp thành công khóa đáp án. Gửi lại cùng nội dung trả kết quả cũ, còn gửi nội dung khác trả `409`. Làm lại tạo lượt mới. Khi thay đề, lượt đang làm và lịch sử vẫn gắn với phiên bản đã mở. |

Trước khi nghiệm thu tích hợp G4.2, thử Azure Speech F0 với một câu A2 và một đoạn B1. Đo độ trễ, dung lượng, kiểm tra đủ từ, phát 1×/0,75× và phân biệt hai vai. Bản thử gửi tối đa hai yêu cầu mỗi lần chạy. [Bản thử Azure TTS](experiments/g4-azure-tts.md).

- [x] `G4.1` Soạn đề từ câu, đoạn hội thoại và tình huống A2/B1. Lưu kịch bản, giọng, chính sách hiển thị lời, bản dịch, bộ câu hỏi và đáp án. Đề đã có lượt làm được giữ nguyên nội dung ảnh hưởng chấm; khi cần sửa, tạo đề thay thế.
  - **Cách thực hiện.** Soạn đề có mã câu ổn định, đáp án theo từng loại và cơ chế thay đề đã có lượt.
  - **Kiểm thử.** Kiểm tra đủ tám đề và cả bốn dạng. Thử câu thiếu đáp án hoặc trùng mã, đoạn thoại sai thứ tự, thiếu lời hoặc có mã giọng lạ. Phát hành đề mới khi đề cũ có cả lượt đang làm và lượt đã nộp.
  - **Điều kiện đạt.** Cấu hình sai không công khai. Học viên tiếp tục được lượt cũ và lịch sử giữ đúng nội dung và điểm sau khi phát hành đề mới.
- [ ] `G4.2` Xây phát audio TTS từ backend. Với đề nghe trước, API trả audio và nội dung được phép xem; kịch bản, đáp án và giải thích chỉ công bố theo chính sách. Giữ nguồn audio nhất quán khi nghe lại trong cùng lượt. Đã triển khai và kiểm tra với Azure giả lập. Còn nghiệm thu công khai đề với tài nguyên F0 thật.
  - **Cách thực hiện.** Tạo và kiểm tra MP3 trước khi công khai đề. Tách API phát audio khỏi API đọc kết quả và kiểm tra quyền đọc theo lượt.
  - **Kiểm thử.** Thử công khai khi thiếu MP3 hoặc Azure báo lỗi. Kiểm tra quyền đọc và JSON trước nộp. Nghe ở 1× và 0,75×, tải lại, đổi thiết bị và làm lại, rồi đối chiếu tệp và số lần gọi Azure. Mở tiếp lượt của đề đã ẩn.
  - **Điều kiện đạt.** Đề thiếu audio hợp lệ giữ trạng thái nháp. Lời thoại, bản dịch, đáp án và giải thích chỉ có sau nộp. Tệp giữ nguyên theo phiên bản đề và thao tác học không gọi thêm Azure. Lỗi phát giữ đáp án nháp.
- [x] `G4.3` Xây bốn dạng chọn đáp án, điền từ, sắp xếp, nghe–chép. Kiểm tra chuẩn hóa chữ hoa/thường, khoảng trắng, biến thể được chấp nhận và dấu câu. Chấm phía server và xác thực mã câu thuộc đúng đề.
  - **Cách thực hiện.** Viết bộ chấm cho bốn loại và fixture chuẩn hóa tương ứng.
  - **Kiểm thử.** Mỗi loại thử đúng, sai, bỏ trống. Thử mã đáp án lạ, đảo thứ tự, khác biệt hoa/thường, khoảng trắng, dấu câu cuối và biến thể có hoặc chưa được khai báo. Thử mã câu thuộc đề khác.
  - **Điều kiện đạt.** Điểm từng câu khớp bảng đáp án mẫu, đầu vào khác đề bị từ chối và tổng điểm không vượt điểm tối đa.
- [x] `G4.4` Xây bắt đầu, tiếp tục, nộp, làm lại và xem lịch sử. Lưu đáp án, điểm từng câu, giải thích, tổng điểm và điểm tối đa cùng giao dịch. Xử lý nộp lặp, bỏ dở, lỗi phát và mất mạng.
  - **Cách thực hiện.** Dùng phiên bản nháp để kiểm tra ghi đồng thời. Chốt đáp án và điểm cùng giao dịch. Phân trang lịch sử theo chủ sở hữu.
  - **Kiểm thử.** Thử mở đồng thời. Cho hai thiết bị tải cùng phiên bản nháp rồi lưu lần lượt. Thử lưu sau nộp, nộp đồng thời, gửi lại cùng đáp án sau mất mạng, gửi lại với đáp án khác, làm lại, bỏ dở và đọc lịch sử bằng tài khoản khác.
  - **Điều kiện đạt.** Hai yêu cầu mở đồng thời chỉ tạo một lượt đang làm. Nháp cũ nhận `409` và không ghi đè nháp mới. Lượt đã nộp có một kết quả bất biến. Làm lại có ID mới và truy cập chéo không trả dữ liệu.

**Bàn giao và nghiệm thu**

- Có đề mẫu cho đủ bốn dạng. Kiểm thử đúng/sai/bỏ trống, gửi trùng giữ một kết quả và làm lại tạo lượt mới. Học viên xem điểm, đáp án và giải thích của mình; đề nghe trước giữ kín kịch bản và đáp án đến mốc cho phép.
- [Biên bản nghiệm thu G4](acceptance/g4-listening-acceptance.md).

## G5 — Luyện phát âm và ngữ điệu

**Dữ liệu** `pronunciation_drills`, `pronunciation_attempts`, `ai_requests`, `ai_attempts`.

### Quyết định trước triển khai

Backend dùng Speech SDK Java gọi Azure Pronunciation Assessment `SCRIPTED`, locale `en-US`, thang 0–100. Audio gửi từ trình duyệt là WAV PCM16 mono 16 kHz. [ADR 0015](../decisions/0015-azure-pronunciation-assessment-g5.md)

| Nội dung | Quyết định |
| --- | --- |
| Độ dài bài và bản ghi | Giữ trần thu âm **60 giây**. Thiết kế bài mẫu đọc khoảng **10–25 giây** và nghiệm thu từ, câu ngắn trước. Nếu bản đầu cần chấm nguyên đoạn trên 30 giây, nghiệm thu chế độ liên tục, ghép kết quả và phát hiện từ bỏ hoặc thêm. Bản đầu nhận chấm tối đa 30 giây. |
| Đọc theo mẫu và Shadowing | Dùng lại TTS của G3 để phát văn bản mẫu. Với Shadowing, học viên nghe mẫu, dừng playback rồi thu âm. Cả hai dùng cùng cơ chế chấm `SCRIPTED`. |
| Điểm cho học viên | Hiện điểm tổng, độ chính xác, độ trôi chảy, độ đầy đủ và chi tiết từ hoặc âm vị IPA khi Azure trả kết quả hợp lệ. Khi bản ghi im lặng hoặc thiếu bằng chứng, để điểm trống và cho thu lại. Giữ điểm ngữ điệu tắt đến khi kiểm chứng dữ liệu và chi phí. |
| Thứ tự migration | Nếu G5 dùng hàng đợi và nhật ký tác vụ AI, tạo `ai_requests` và `ai_attempts` trước khi thêm FK từ lượt chấm. Tạo nguồn G5 theo `drill_id`. Khi triển khai G6, bổ sung liên kết `audio_submission_id` sau khi bảng `audio_submissions` có mặt. |
| Bản thử Azure thật | Xác nhận tài nguyên F0 hỗ trợ Pronunciation Assessment trước khi gọi. Giới hạn **tối đa 6 lượt** gồm đọc đúng, sai âm mục tiêu, bỏ từ, thêm từ, im lặng và một đoạn trên 30 giây nếu cần nghiệm thu đoạn dài. Chốt hạn mức dùng thử và chi phí trước khi bật cho học viên. |

- [x] `G5.1` Soạn từ, câu và đoạn ngắn trong giới hạn thu âm. Xây `READ_ALOUD` và `SHADOWING`, phát câu mẫu bằng TTS, cho nghe lại và luyện từng phần. Giữ snapshot câu tham chiếu và locale cho lần chấm cũ.
  - **Cách thực hiện.** Tạo drill, chọn READ_ALOUD/SHADOWING và lưu snapshot khi bắt đầu chấm.
  - **Kiểm thử.** Mở mẫu từ, câu, đoạn. Kiểm tra Shadowing dừng phát mẫu trước khi thu và kết quả cũ vẫn giữ câu tham chiếu khi sửa nội dung bài.
  - **Điều kiện đạt.** Mỗi mode có hướng dẫn đúng và kết quả cũ vẫn hiện câu tham chiếu ban đầu.
- [ ] `G5.2` Đã triển khai và kiểm tra API/Chrome với micro giả lập. Còn kiểm tra G5 trên thiết bị vật lý. Xây kiểm tra micro, xin quyền khi bấm, đếm thời lượng, ghi/dừng/nghe lại/ghi lại/hủy. Chỉ upload khi học viên bấm gửi. Dừng micro khi thoát. Backend kiểm tra định dạng, codec, dung lượng, thời lượng tối đa 60 giây và file rỗng.
  - **Cách thực hiện.** Xây state ghi âm, preview và validation server trước khi tiếp nhận file.
  - **Kiểm thử.** Từ chối mic; ghi rồi hủy; thoát trang; tải file rỗng, sai codec và quá 60 giây.
  - **Điều kiện đạt.** Mic dừng đúng vòng đời, hủy không upload và file sai không tạo tác vụ chấm.
- [ ] `G5.3` Đã tích hợp SDK và kiểm chứng fixture. Còn kiểm chứng Azure F0 thật. Tích hợp nhận dạng lời nói và Azure chấm `SCRIPTED`. Hiển thị nội dung nhận dạng để đối chiếu câu mẫu. Kiểm tra điểm Azure trước khi lưu và giữ chi tiết từ, âm vị qua `detail_json`. Giữ điểm ngữ điệu tắt trong khi xác minh dữ liệu và chi phí.
  - **Cách thực hiện.** Dùng Speech SDK Java và ánh xạ lời nhận dạng, điểm và chi tiết Azure SCRIPTED sang DTO đã kiểm tra. Với chế độ liên tục, ghép kết quả theo thứ tự và đối chiếu lời nhận dạng với câu tham chiếu để xác định từ bỏ sót hoặc thêm.
  - **Kiểm thử.** Chạy fixture có đủ điểm, thiếu prosody, không nhận dạng và điểm ngoài thang. Với bản thử thật trong phạm vi đã chốt, thử mẫu ngắn để xác nhận IPA. Nếu nghiệm thu đoạn dài, dùng mẫu trên 30 giây để kiểm tra ghép đoạn, bỏ từ và thêm từ.
  - **Điều kiện đạt.** Điểm hợp lệ hiển thị đúng cấp; thiếu điểm để trống và đầu ra sai cấu trúc không được lưu thành công.
- [x] `G5.4` Ghi lịch sử lần chấm, cấu hình chấm, điểm 0–100, lỗi và độ trễ. Tạo một lần chấm logic cho một lần gửi, retry có giới hạn và dọn file tạm khi xử lý xong, hủy, lỗi hoặc hết hạn. Hiển thị từ/âm cần luyện, cho nghe mẫu và thu mới để thử lại.
  - **Cách thực hiện.** Thiết kế idempotency, trạng thái lỗi, tái thử và cleanup giữ tham chiếu tới khi dọn xong.
  - **Kiểm thử.** Gửi trùng, mô phỏng timeout, lỗi dọn file rồi retry, kết quả muộn sau xóa và mở lịch sử khi audio đã dọn.
  - **Điều kiện đạt.** Một bản gửi tạo một kết quả logic, worker muộn không ghi lại dữ liệu bị xóa và lịch sử điểm còn đọc được.
- [ ] `G5.5` Đã chuẩn bị bộ ca rà, còn chờ bằng chứng F0 và người rà. Đánh giá bằng mẫu đọc đúng, đọc thiếu, âm khó, giọng nhanh/chậm, nền ồn và không có tiếng nói. Kiểm chứng dữ liệu theo từ/âm vị và mức hiển thị với người đánh giá. So sánh điểm chỉ trong cùng chế độ, locale và phiên bản cấu hình khi phù hợp.
  - **Cách thực hiện.** Tạo bộ mẫu đọc đúng, đọc thiếu, âm mục tiêu, tốc độ khác và nhiễu để người rà chấm.
  - **Kiểm thử.** So đối chiếu phản hồi từ/âm vị với nhận xét người rà trên mẫu thật được phép, ghi ca không đủ bằng chứng.
  - **Điều kiện đạt.** Có biên bản điểm hợp lý hoặc lỗi đã ghi rõ. Mẫu thiếu bằng chứng không bị gán điểm giả.

**Bàn giao và nghiệm thu**

- Một bài đi được từ nghe mẫu đến thu âm, xem lời nhận dạng, xem điểm và luyện lại. Lỗi mic, upload trùng, timeout và dọn file có kiểm thử. Tính đúng của bản chép lời và chất lượng phản hồi phát âm được nghiệm thu riêng. Điểm ghi theo thang chấm, tránh diễn giải thành tỷ lệ phát âm đúng tuyệt đối.

[API phát âm](../api/pronunciation.md) · [Kết quả kiểm thử và ca rà F0](acceptance/g5-pronunciation-acceptance.md).

## G6 — Luyện hội thoại với AI

**Dữ liệu** `practice_sessions`, `conversation_turns`, `messages`, `audio_submissions`, `assistant_explanations`, feedback, tác vụ AI và lượt chấm phát âm.

- [ ] `G6.1` Đã triển khai và đạt kiểm thử tích hợp A2/B1 với Gemini giả lập. Còn nghiệm thu lời mở đầu bằng Gemini thật. Xây chọn chủ đề, tình huống, trình độ, vai và chế độ `TEXT`/`VOICE`. Lưu snapshot vai/mục tiêu/level. Gemini tạo lời mở đầu đúng vai. Khôi phục đúng buổi, chế độ và dữ liệu lịch sử sau tải lại.
  - **Cách thực hiện.** Tạo buổi và opening qua snapshot, xác thực tình huống, level, vai và chế độ ngay lúc tạo.
  - **Kiểm thử.** Chạy một A2 và một B1; đảo vai một tình huống; sửa catalog sau khi tạo và tải lại buổi.
  - **Điều kiện đạt.** Opening theo vai đã chọn, buổi tải lại giữ chế độ và lịch sử giữ snapshot đúng.
- [x] `G6.2` Đã triển khai và đạt kiểm thử tích hợp MySQL với Gemini giả lập. Xây CHAT nhiều lượt với ngữ cảnh đã giới hạn. Backend kiểm tra owner, trạng thái và mức giới hạn 20 lượt người học. Một lượt hội thoại xử lý mỗi buổi, cấp số lượt nguyên tử, dùng idempotency/fingerprint và giữ câu đã gửi khi AI lỗi. Lưu usage/latency và xử lý timeout, retry và kết quả chưa rõ.
  - **Cách thực hiện.** Khóa buổi khi cấp lượt, lưu câu trước khi gọi và giới hạn ngữ cảnh theo cấu hình.
  - **Kiểm thử.** Gửi đồng thời, gửi lại cùng payload và cùng mã khác payload, gửi lượt 21, mô phỏng quota và timeout.
  - **Điều kiện đạt.** Chỉ một lượt được nhận trong ca đồng thời; không trùng tin, không có lượt 21 và câu đã nhận còn khi provider lỗi.
- [x] `G6.3` Đã triển khai và kiểm tra trên Chrome với API/TTS giả lập. Xây giao diện TEXT và VOICE nghe trước. VOICE dùng TTS tiếng Anh cục bộ để phát câu AI theo thao tác người học, có nghe lại, nghe chậm và phụ đề riêng từng câu. Hỗ trợ nhập bàn phím cho một lượt trong VOICE. Người học chọn TEXT hoặc VOICE trước khi bắt đầu. Mở phụ đề một câu không mở các câu sau. Khi kết thúc, xem toàn bộ transcript.
  - **Cách thực hiện.** Tách trạng thái phụ đề theo tin AI, hỗ trợ phát/chậm/lặp và thay thế nhập văn bản.
  - **Kiểm thử.** Nghe opening, mở phụ đề, nhận câu mới, gõ thay cho một lượt, tải lại để kiểm tra chế độ và mở transcript sau kết thúc.
  - **Điều kiện đạt.** Câu mới vẫn ẩn khi đang VOICE, lượt gõ không đổi chế độ hoặc tự mở phụ đề.
- [x] `G6.4` Đã kiểm thử tích hợp MySQL và Chrome với dịch vụ giả lập. Tái dùng bộ ghi âm. Sau khi thông báo xử lý ngoài hệ thống, cho học viên bấm Gửi bản ghi. Gemini chép lời sau khi gửi. Hiển thị bản chép nháp để xem, sửa và xác nhận. Dùng nội dung đã xác nhận để tạo tin, lượt và gọi CHAT. Sau xác nhận, nếu dịch vụ chấm được bật, tự gửi audio gốc tối đa 30 giây còn hạn đến Azure UNSCRIPTED en-US bằng tác vụ độc lập. Hiện kết quả hợp lệ ở từng lượt và tổng kết.
  - **Cách thực hiện.** Tách giọng nói thành upload, chép lời, xác nhận, tạo tin và chấm tùy chọn. Giữ khóa chống gửi trùng, audio TTL 1 giờ và nháp TTL 30 phút từ READY.
  - **Kiểm thử.** Tải lại khi đang chép, khôi phục nháp còn hạn, gửi và xác nhận trùng, chép rỗng/lỗi, file hết hạn, tắt chấm và mô phỏng Azure lỗi.
  - **Điều kiện đạt.** Chỉ khi xác nhận mới tạo đúng một lượt, dùng nội dung đã sửa nếu có. Azure chỉ nhận audio sau xác nhận, khi tính năng được bật, bản ghi tối đa 30 giây và tệp còn hạn. Chép lỗi hoặc rỗng thì báo lỗi để thử lại; không tạo tin rỗng. Azure không chặn CHAT. File được dọn đúng vòng đời.
- [x] `G6.5` Đã kiểm thử tích hợp MySQL và Chrome với Gemini giả lập. Xây gợi ý khi bí và giải thích câu AI gần nhất. Kiểm tra đúng tin, đúng buổi và owner; giải thích tiếng Anh đơn giản và nghĩa Việt theo yêu cầu. Tác vụ phụ không thay transcript chính, không tính lượt và không tự mở phụ đề.
  - **Cách thực hiện.** Tạo HINT và EXPLAIN có khóa theo buổi/tin, kiểm tra câu AI mới nhất và owner.
  - **Kiểm thử.** Yêu cầu lại cùng mã, thử câu cũ hoặc khác buổi, mở tiếng Việt theo thao tác và giả lập timeout.
  - **Điều kiện đạt.** Gợi ý/giải thích giữ đúng câu, không thêm transcript, không tăng lượt và không mở phụ đề.
- [x] `G6.6` Đã kiểm thử tích hợp MySQL và Chrome với dịch vụ giả lập. Xây kết thúc và tổng kết. Buổi `COMPLETED` cần ít nhất một lượt người học đã nhận phản hồi thành công; nếu không, lưu `ABANDONED`. Gemini trả nhận xét ngữ pháp, từ vựng, diễn đạt, điểm mạnh, mục tiêu, lỗi lặp và gợi ý luyện thêm. Kiểm tra cấu trúc và câu nguồn, tách lỗi thật với cách nói cải thiện. Hiện điểm phát âm riêng khi đủ dữ liệu.
  - **Cách thực hiện.** Khóa kết thúc buổi, phân loại trạng thái và validate schema góp ý với tin học viên nguồn.
  - **Kiểm thử.** Thử buổi chỉ opening, AI lỗi lượt đầu, một lượt thành công, thành công rồi lỗi. Gửi feedback tham chiếu sai buổi hoặc đoạn trích sai.
  - **Điều kiện đạt.** Chỉ buổi có trao đổi thành công là COMPLETED; câu đã lưu được giữ và feedback sai nguồn không được chấp nhận.
- [x] `G6.7` Đã kiểm thử tích hợp MySQL và Chrome bằng fixture. Xây lịch sử phân trang, chi tiết transcript và nhận xét, lưu gợi ý vào sổ tay, xóa một buổi giữ thẻ và xóa toàn bộ dữ liệu theo chính sách. Kiểm tra kết quả AI đến muộn, retry, quota, dữ liệu cũ và quyền truy cập chéo.
  - **Cách thực hiện.** Gắn lịch sử, lưu thẻ độc lập với nguồn và thực hiện xóa theo đúng phạm vi.
  - **Kiểm thử.** Lưu thẻ, xóa một buổi khi worker còn chạy, xóa toàn bộ học tập và đọc bằng tài khoản khác.
  - **Điều kiện đạt.** Xóa một buổi giữ thẻ, xóa toàn bộ dọn cả thẻ và lượt ôn, kết quả muộn không tái tạo buổi.

**Bàn giao và nghiệm thu**

- Chạy hoàn chỉnh ít nhất một tình huống A2 và một B1 từ chọn vai đến tổng kết. Kiểm thử TEXT, VOICE, gõ thay một lượt, tải lại khi đang chép lời, lượt 21, gợi ý, giải thích, thất bại AI và xóa. Nghiệm thu đúng vai, dễ hiểu, hợp lý của góp ý và micro trên thiết bị thật. Kết quả mock chỉ chứng minh hành vi tích hợp.

[Biên bản tích hợp G6](acceptance/g6-conversation-acceptance.md).

## G7 — Cá nhân hóa lộ trình học

**Dữ liệu** `learning_plans`, `learning_plan_items`, hồ sơ, kết quả các kỹ năng và thẻ ôn.

- [ ] `G7.1` Xây bộ gợi ý theo trình độ, mục tiêu, sở thích và kết quả có dữ liệu. Ưu tiên bài chưa hoàn thành, nhóm lỗi lặp hoặc thẻ cần ôn. Với tài khoản mới, dùng mức tự chọn và sở thích. Ghi mã lý do để người học hiểu vì sao được gợi ý.
  - **Cách thực hiện.** Tạo thứ tự ưu tiên theo level, mục tiêu, sở thích, lỗi lặp và thẻ đến hạn.
  - **Kiểm thử.** So fixture tài khoản mới, hai profile khác sở thích, bài ẩn và hai loại điểm yếu khác nhau.
  - **Điều kiện đạt.** Gợi ý có lý do khớp dữ liệu, chỉ trả nội dung khả dụng và kết quả ổn định với cùng đầu vào.
- [ ] `G7.2` Chốt quy tắc gợi ý tăng/giảm độ khó dựa trên số lượt đủ bằng chứng và cùng cách chấm. Cho phép người học giữ mức đang chọn hoặc đổi mục tiêu. Khảo sát các trường hợp không đủ dữ liệu, dữ liệu chỉ có một kỹ năng và xóa lịch sử.
  - **Cách thực hiện.** Đặc tả ngưỡng dữ liệu đủ, bộ điểm có thể so sánh và lựa chọn giữ level hiện tại.
  - **Kiểm thử.** Thử thiếu mẫu, khác phiên bản chấm, dữ liệu đủ ngưỡng và bỏ qua gợi ý đổi level.
  - **Điều kiện đạt.** Thiếu bằng chứng không phát sinh khuyến nghị đổi level; học viên có thể giữ lựa chọn của mình.
- [ ] `G7.3` Tạo kế hoạch ngày hoặc tuần, thêm hoặc gợi ý mục theo ngày, sắp xếp lại, đánh dấu hoàn thành/bỏ qua và đổi mục tiêu. Kiểm tra ngày theo múi giờ, nguồn bài/thẻ và cập nhật đồng thời. Cập nhật hoặc xử lý mục nguồn đã bị gỡ.
  - **Cách thực hiện.** Lưu kế hoạch và mục, khóa phiên bản khi chỉnh; kiểm tra nguồn và ngày theo múi giờ.
  - **Kiểm thử.** Thử mục ngoài ngày, đồng thời sửa hai tab, nguồn vừa bị ẩn và thẻ của người khác.
  - **Điều kiện đạt.** Không ghi đè dữ liệu từ phiên bản cũ; nguồn sai và ngày sai bị chặn; mục mất nguồn có trạng thái rõ.
- [ ] `G7.4` Tạo màn hình Hôm nay, lộ trình và nội dung nên luyện lại. Ghép bài, thẻ và lỗi đã gặp thành hành động cụ thể. Lưu kết quả kế hoạch để G8 đếm mục hoàn thành.
  - **Cách thực hiện.** Nối màn hình Hôm nay với mục trong ngày, mở bài/thẻ và thao tác hoàn thành/bỏ qua.
  - **Kiểm thử.** Chạy qua nửa đêm địa phương, dùng ngày trống, mở mục chưa làm và đánh dấu xong.
  - **Điều kiện đạt.** Danh sách đúng ngày và trạng thái sau tải lại, dữ liệu hoàn thành đọc được cho G8.

**Bàn giao và nghiệm thu**

- Hai hồ sơ khác level/mục tiêu hoặc có điểm yếu khác nhau nhận gợi ý có lý do khác nhau. Học viên đổi mục tiêu và kế hoạch mà lịch sử cũ vẫn đọc được. Đổi ngày/múi giờ, mục nguồn bị xóa và sửa hai tab có kiểm thử.

## G8 — Tiến độ và động lực

**Dữ liệu** `lesson_completions`, `study_intervals`, `user_badges`, `notifications`, lịch nhắc và các nguồn kết quả.

- [ ] `G8.1` Chốt điều kiện hoàn thành từng bài, nhiệm vụ và chủ đề. Ghi mốc hoàn thành một lần theo người–bài, đếm nhiệm vụ từ kế hoạch. Định nghĩa tỷ lệ chủ đề với mẫu số là tập bài đủ điều kiện. Đếm buổi hoàn thành theo trạng thái và lượt từ tin học viên.
  - **Cách thực hiện.** Định nghĩa mẫu số hoàn thành và dùng bản ghi nguồn để tổng hợp.
  - **Kiểm thử.** Tạo fixture bài đủ/chưa đủ điều kiện, bài bị ẩn, buổi ABANDONED và ca ghi mốc trùng.
  - **Điều kiện đạt.** Số đếm khớp mẫu số đã công bố và mỗi người–bài chỉ có một mốc.
- [ ] `G8.2` Ghi khoảng học đã xác thực, chặn event trùng và gộp khoảng chồng giữa tab/thiết bị. Tính phút ngày, mục tiêu và streak theo múi giờ từ dữ liệu hoạt động hợp lệ. Kiểm tra qua nửa đêm, tab bỏ quên và xóa lịch sử.
  - **Cách thực hiện.** Gộp khoảng giao nhau, cắt theo ngày địa phương và áp giới hạn thời gian do server kiểm tra.
  - **Kiểm thử.** Gửi khoảng 08:05–08:07 và 08:06–08:08, khoảng qua nửa đêm, event trùng và tab bỏ quên.
  - **Điều kiện đạt.** Hai khoảng đầu cho 3 phút, mốc ngày đúng múi giờ và event trùng không tăng số phút.
- [ ] `G8.3` Xây dashboard điểm nghe, điểm nói theo rubric khi đã chốt và điểm phát âm theo thời gian. Hiển thị cỡ mẫu, thiếu dữ liệu, nhóm lỗi lặp và thay đổi trong tập điểm có thể so sánh. Thống nhất cách diễn giải xu hướng học tập.
  - **Cách thực hiện.** Tách dữ liệu chuỗi nghe/nói/phát âm theo thang và cấu hình chấm; gom lỗi thật theo câu.
  - **Kiểm thử.** Thử thiếu điểm, điểm 0, khác phiên bản, hai lỗi trên cùng một tin và lỗi lặp ở nhiều tin.
  - **Điều kiện đạt.** Thiếu điểm hiển thị riêng với 0; nhóm lỗi đếm số câu nguồn; điểm khác thang không bị gộp.
- [ ] `G8.4` Xây điểm thưởng dẫn xuất từ mốc hoàn thành, quy tắc huy hiệu và một lần trao theo người–mã. Chốt điều chỉnh hiển thị sau khi xóa dữ liệu. Kiểm thử trao đồng thời và tính lại số liệu.
  - **Cách thực hiện.** Định nghĩa luật tính điểm và cấp huy hiệu, tính lại từ dữ liệu đủ điều kiện.
  - **Kiểm thử.** Tạo hai yêu cầu hoàn thành đồng thời, thử đúng ngưỡng và xóa dữ liệu nguồn.
  - **Điều kiện đạt.** Điểm và huy hiệu chống trùng, cập nhật sau xóa theo quy tắc đã ghi.
- [ ] `G8.5` Hoàn thiện luồng nhắc học từ G1. Đổi lịch địa phương sang UTC, chống gửi trùng, xử lý lease/retry, bỏ qua lịch cũ khi người dùng đổi hoặc tắt. Tạo hộp thư thông báo kết quả có người nhận và trạng thái đọc để dùng tiếp ở G9–G10.
  - **Cách thực hiện.** Sinh lượt nhắc từ lịch phiên bản UTC, xử lý lease/retry và tạo bản ghi hộp thư theo người nhận.
  - **Kiểm thử.** Giả lập hai worker, đổi lịch khi đang chờ, SMTP lỗi, tài khoản bị khóa/chờ xóa trước giờ gửi, kiểm tra đọc notification bằng hai tài khoản.
  - **Điều kiện đạt.** Một lần nhắc hợp lệ được nhận xử lý theo khóa, lịch cũ bị loại và trạng thái đọc theo từng người.

**Bàn giao và nghiệm thu**

- Dashboard tái hiện đúng số liệu từ fixture cho nghe, nói, phát âm, thời lượng và số buổi. Kiểm tra điểm/huy hiệu chống trùng, số liệu sau xóa, nhắc đúng giờ và phạm vi notification. Người học xem được mốc đạt và nguyên nhân chênh lệch khi dữ liệu còn ít.

## G9 — Hỗ trợ và phản hồi

**Dữ liệu** `support_tickets`, `support_ticket_messages`, `notifications`, `announcements` cho màn hình đọc thông báo.

- [ ] `G9.1` Tạo form góp ý, báo lỗi kỹ thuật và báo cáo bài học, câu AI hoặc bản nhận xét. Chọn đúng một đối tượng báo cáo, xác minh quyền nguồn, cho học viên xem trích đoạn trước khi gửi và lọc thông tin nhạy cảm.
  - **Cách thực hiện.** Tách loại phiếu và validation đối tượng báo cáo, cho xem trích đoạn trước khi gửi.
  - **Kiểm thử.** Thử báo cáo 0 hoặc 2 nguồn, câu AI của người khác, phiếu góp ý không nguồn và trích đoạn cần lọc.
  - **Điều kiện đạt.** Phiếu hợp lệ lưu đúng loại/trích đoạn, nguồn sai bị chặn và phiếu thường không cần đối tượng báo cáo.
- [ ] `G9.2` Tạo lịch sử phiếu của học viên, xem trạng thái, nội dung trao đổi, gửi phản hồi bổ sung và xử lý nguồn đã bị gỡ. Phân trang và kiểm tra owner mọi endpoint. Tạo trạng thái trống/lỗi và thông báo khi có phản hồi.
  - **Cách thực hiện.** Tạo dòng thời gian phiếu, API phản hồi và xử lý trường hợp nguồn bị gỡ.
  - **Kiểm thử.** Học viên mở, gửi thêm, phân trang, xem nguồn bị gỡ và thử truy cập phiếu người khác.
  - **Điều kiện đạt.** Đúng thứ tự và trạng thái, vẫn đọc được phiếu hợp lệ khi mất nguồn, truy cập chéo bị từ chối.
- [ ] `G9.3` Hiển thị thông báo của admin theo người nhận, thời hạn và trạng thái đọc. Dùng seed/fixture thông báo cho nghiệm thu giao diện. Luồng soạn và phát thật hoàn thiện tại G10.
  - **Cách thực hiện.** Tái dùng hộp thư G8 và trạng thái đọc, cấp fixture thông báo admin theo nhóm.
  - **Kiểm thử.** Thử thông báo còn hạn, hết hạn, đã đọc, chưa đọc và thông báo thuộc người nhận khác.
  - **Điều kiện đạt.** Hộp thư chỉ hiện nội dung được phép, đánh dấu đọc không đổi trạng thái của người khác.
- [ ] `G9.4` Định nghĩa chuyển trạng thái phiếu, quyền đọc trích đoạn và hợp đồng hàng đợi xử lý để G10 tích hợp. Kiểm tra phiếu không có nguồn, nguồn khác tài khoản và xóa nội dung sau báo cáo.
  - **Cách thực hiện.** Lập bảng chuyển trạng thái và phạm vi xem dữ liệu cho học viên/admin.
  - **Kiểm thử.** Thử chuyển trạng thái không hợp lệ, phiếu kỹ thuật không có nguồn và báo cáo mất nguồn sau khi tạo.
  - **Điều kiện đạt.** API giữ quy tắc trạng thái, phiếu thường hoạt động và báo cáo cũ giữ thông tin được phép.

**Bàn giao và nghiệm thu**

- Học viên tạo được góp ý và báo cáo, theo dõi trạng thái/trao đổi và đọc thông báo. Truy cập chéo bị từ chối. Kiểm thử fixture có phản hồi và thông báo đã đọc, chưa đọc hoặc hết hạn.

## G10 — Quản trị hệ thống

**Dữ liệu** bảng quản trị và audit, catalog/học liệu, ticket, announcement, notification và metadata tác vụ AI.

- [x] `G10.1` [Đã kiểm chứng HTTP/MySQL](acceptance/g10-account-admin-acceptance.md). Xây tìm và xem tài khoản, phân trang, đổi quyền, khóa/mở. Bảo vệ endpoint theo ADMIN và trạng thái tài khoản, thu hồi phiên khi đổi quyền hoặc khóa. Chốt quy tắc bảo toàn quyền truy cập quản trị khi tác động tài khoản admin.
  - **Cách thực hiện.** Tạo danh sách người dùng có bộ lọc, mutation quyền/trạng thái và audit tương ứng.
  - **Kiểm thử.** Learner gọi API admin; admin khóa học viên có cookie cũ; thử thay quyền admin duy nhất theo quy tắc đã chốt.
  - **Điều kiện đạt.** Quyền áp server-side, cookie cũ mất quyền và thao tác không làm mất quyền quản trị ngoài quy tắc.
- [x] `G10.2` Xây CRUD và công bố/ẩn chủ đề, bài, từ/câu, hội thoại mẫu, đề nghe và bài phát âm. Validation level, transcript, dịch, IPA và cấu hình TTS. Cho xóa bản nháp chưa có tham chiếu; nội dung có lịch sử dùng ẩn hoặc lưu trữ để giữ kết quả cũ.
  - **Cách thực hiện.** Tạo form quản trị học liệu TTS, preview, công bố, ẩn và xóa bản nháp.
  - **Kiểm thử.** Thử nội dung thiếu trường, giọng sai, xóa bản nháp và ẩn đề hoặc bài đã có lịch sử.
  - **Điều kiện đạt.** Nội dung lỗi không công bố; bản nháp hợp lệ xóa được, lịch sử học cũ vẫn đọc đúng.
  - **Kết quả.** Flyway V1–V19 chạy trên MySQL 9.2 rỗng. Ca HTTP kiểm tra sáu loại nội dung, phân quyền, bản nháp có tham chiếu, IPA thiếu, giọng sai và lịch sử sau ẩn. Chrome headless kiểm tra tạo/xóa nháp và nút nghe thử bằng fixture. Ca công bố MP3 dùng Azure giả lập.
  - **Bàn giao.** Mở **Catalog / học liệu → Học liệu** bằng tài khoản ADMIN. Bật cấu hình Azure Speech để công bố đề nghe trên môi trường thật. [Hợp đồng và vòng đời học liệu](../api/admin.md#quản-lý-học-liệu-g102).
- [x] `G10.3` Quản lý tình huống, cấp độ, bối cảnh, vai, mục tiêu và chỉ dẫn AI. Kiểm tra role trước công bố, giữ snapshot cũ, rà prompt và xác minh API học viên chỉ có dữ liệu được phép.
  - **Cách thực hiện.** Tạo editor tình huống, biến thể vai và kiểm tra chỉ dẫn trước công bố.
  - **Kiểm thử.** Tạo vai trùng, tình huống thiếu level; sửa vai đã được dùng và gọi API học viên.
  - **Điều kiện đạt.** Catalog sai bị chặn; buổi cũ giữ snapshot và API học viên không chứa chỉ dẫn nội bộ.
  - **Kết quả.** Hai ca kiểm thử MySQL 9.2 và hội thoại dùng Gemini giả lập đạt. Chrome headless ở chiều rộng 390 px đã kiểm tra thêm, sửa, gỡ biến thể và xác nhận rà chỉ dẫn trước khi lưu công khai. [Hợp đồng biến thể vai](../api/admin.md#biến-thể-vai-g103).
- [ ] `G10.4` Xây hàng đợi xử lý phiếu, phân công, phản hồi, đổi trạng thái và phát notification. Admin xem dữ liệu đã lọc hoặc trích đoạn được gửi. Các API quản trị tiếp tục kiểm tra quyền riêng của transcript cá nhân.
  - **Cách thực hiện.** Nối hàng đợi với phân công, phản hồi và thông báo cho đúng chủ phiếu.
  - **Kiểm thử.** Admin nhận phiếu, thêm phản hồi, đóng, thử đọc transcript ngoài trích đoạn và thử gửi trùng notification.
  - **Điều kiện đạt.** Phiếu và audit ghi đúng actor, người học nhận cập nhật đúng một lần và quyền transcript được bảo toàn.
- [ ] `G10.5` Xây soạn, duyệt trạng thái, công bố và hết hạn thông báo. Chọn nhóm nhận theo role/level, chống tạo trùng và ghi audit theo actor/target. Kiểm tra đúng người nhận và nội dung có giới hạn.
  - **Cách thực hiện.** Thiết lập workflow soạn/công bố, tính tập người nhận và khóa chống phát lại.
  - **Kiểm thử.** Công bố cho A2, thử learner B1/admin, gọi công bố lặp và mở thông báo đã hết hạn.
  - **Điều kiện đạt.** Chỉ nhóm đích nhận thông báo, phân phối không nhân đôi và trạng thái hết hạn đúng.
- [ ] `G10.6` Xây bảng số liệu tổng hợp và nhật ký vận hành. Hiển thị người dùng, lượt học, tỷ lệ hoàn thành có mẫu số, nội dung được sử dụng, lỗi, độ trễ, quota và usage theo tác vụ/dịch vụ. Ghi audit tối thiểu cho thao tác quản trị; kiểm tra quyền và nội dung log.
  - **Cách thực hiện.** Xây query tổng hợp từ dữ liệu nguồn, mẫu số và metadata theo tác vụ/provider.
  - **Kiểm thử.** Đối chiếu fixture người dùng, lượt học, error/usage; kiểm tra audit actor/target và rà log.
  - **Điều kiện đạt.** Số liệu khớp fixture và công bố mẫu số; log không chứa key hoặc nội dung hội thoại thô.
- [ ] `G10.7` Lập hạng mục sau bản đầu cho quản lý audio, ảnh và video tải sẵn. Khi được mở phạm vi, đặc tả nguồn/giấy phép, upload, metadata, trạng thái công bố và dọn file. Nghiệm thu bản đầu dùng dữ liệu TTS, transcript và bản dịch.
  - **Cách thực hiện.** Ghi backlog riêng cho học liệu tải sẵn và liệt kê schema, quyền sử dụng và vòng đời cần thiết.
  - **Kiểm thử.** Rà phạm vi với database.md và thử tạo học liệu bản đầu chỉ dùng văn bản/TTS.
  - **Điều kiện đạt.** Bản đầu nghiệm thu không phụ thuộc upload học liệu. Hạng mục sau có điều kiện mở phạm vi.

**Bàn giao và nghiệm thu**

- Admin quản lý được người dùng, nội dung, phiếu và thông báo từ ứng dụng. Learner gọi trực tiếp API admin bị từ chối. Tài khoản khóa có cookie cũ không tiếp tục được dùng. Sửa catalog giữ đúng snapshot và điểm cũ. Audit ghi đúng người thao tác và đối tượng mà không chứa secret hoặc hội thoại thô.

## E2E — Nghiệm thu, dùng thử và bàn giao

- [ ] `E2E.1` Chạy MySQL từ rỗng, seed và xác minh quan hệ 46 bảng; chạy backend unit/integration, frontend typecheck/lint/build và kiểm thử trình duyệt. Chạy các ca nhiều tab, retry, khóa/mất phiên, xóa và worker muộn. Rà thông báo riêng tư và kiểm tra restore từ bản sao lưu phù hợp.
  - **Cách thực hiện.** Chuẩn bị môi trường sạch, dữ liệu seed, script kiểm tra và bản sao lưu thử.
  - **Kiểm thử.** Chạy full suite, dựng DB từ rỗng, kiểm tra 46 bảng nghiệp vụ/hạ tầng phiên, restore thử, chạy ca quyền và xóa.
  - **Điều kiện đạt.** Có log lệnh và kết quả. Dữ liệu sau restore khớp fixture, các ca bắt buộc đạt hoặc lỗi chặn được ghi rõ.
- [ ] `E2E.2` Chạy hành trình chính đăng ký → chọn bài → nghe/ôn → luyện phát âm → hội thoại → thấy tiến độ. Chạy hành trình admin xử lý báo cáo và phát thông báo. Ghi bằng chứng Chrome/Edge Windows và Chrome Android cho audio/voice. Phân biệt ca giả lập, thiết bị thật và provider thật trong báo cáo.
  - **Cách thực hiện.** Viết kịch bản người học và admin, ghi fixture, thiết bị và chế độ provider của từng ca.
  - **Kiểm thử.** Chạy hành trình chính trên browser mục tiêu, có một tình huống A2 và một B1; chạy admin xử lý phiếu và công bố.
  - **Điều kiện đạt.** Có bằng chứng từng bước và ma trận kết quả theo thiết bị. Ca mock, thiết bị và provider thật được ghi riêng.
- [ ] `E2E.3` Mời khoảng 10 người dùng thử với kịch bản rõ. Ghi số người, tỷ lệ hoàn thành theo từng luồng, thời gian/lỗi và phản hồi. Nhờ người đủ năng lực tiếng Anh rà khoảng 30–50 đoạn mẫu nếu đủ nguồn lực. Ghi cỡ mẫu thực tế và áp rubric đúng vai, dễ hiểu A2/B1, chép đúng ý, góp ý hợp lý và phát âm đủ bằng chứng. Đo median/P95 độ trễ theo tác vụ, ghi số mẫu và điều kiện. Theo dõi usage, ước tính chi phí khi có đơn giá và chỉ gọi provider thật trong phạm vi được cấp. Dùng [phiếu chấm và kịch bản A2/B1](chat-acceptance/evaluation.md) làm đầu vào.
  - **Cách thực hiện.** Chuẩn bị mẫu chấm, kịch bản dùng thử và biểu mẫu đo thời gian, sự cố, chất lượng.
  - **Kiểm thử.** Đo hoàn thành theo nhiệm vụ, rà mẫu với người có năng lực tiếng Anh và đo độ trễ/usage nếu được cấp quyền.
  - **Điều kiện đạt.** Báo cáo ghi số mẫu, số người, điều kiện và giới hạn. Các nhận định có bằng chứng và không suy từ mock sang kết quả thật.
- [ ] `E2E.4` Ưu tiên lỗi bảo mật, mất dữ liệu hoặc chặn luồng chính. Sửa, chạy lại ca liên quan và cập nhật hướng dẫn cài đặt, seed, tài khoản demo, vận hành, kết quả kiểm thử và kịch bản trình bày. Bàn giao danh sách các hạng mục còn chờ xác minh cùng điều kiện để đóng.
  - **Cách thực hiện.** Lập danh sách lỗi, ưu tiên chặn theo ảnh hưởng và chuẩn bị gói bàn giao có cách chạy lại.
  - **Kiểm thử.** Chạy lại ca lỗi và hồi quy liên quan, nhờ thành viên còn lại dựng demo từ hướng dẫn.
  - **Điều kiện đạt.** Không còn lỗi chặn được biết trong phạm vi bàn giao; ca chưa xét có trạng thái, người phụ trách và điều kiện đóng.

### Tiêu chuẩn đóng task

- Task có UI được kiểm tra trên trình duyệt, gồm trạng thái tải, trống, lỗi và thao tác bàn phím phù hợp. Task dữ liệu có kiểm tra quyền, ràng buộc và hành vi retry/xóa liên quan. Task thay đổi API hoặc vòng đời dữ liệu cập nhật tài liệu tương ứng.
- Chỉ đóng mốc khi tiêu chí nghiệm thu có bằng chứng chạy trên thay đổi thực tế. Ghi cấu hình, dữ liệu mẫu, môi trường và giới hạn kiểm chứng để người khác chạy lại.
