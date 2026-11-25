# Backend

Java 21, Spring Boot 4.1.2, Maven Wrapper 3.3.4 tải Maven 3.10.1; base package `vn.englishconversation`.

## Cấu trúc

Xem [quy ước cấu trúc Spring Boot và cách đặt file mới](../docs/development/coding-conventions.md#cấu-trúc-backend-spring-boot).

Bản dựng G6 mặc định dùng profile `g6` với Flyway V1–V19 và Hibernate validate khi chạy Maven. Dùng schema rỗng hoặc schema đã có lịch sử của dòng mới. [Cách chạy với MySQL phát triển](../docs/development/setup.md#chạy-tài-khoản-hồ-sơ-và-catalog).

```text
backend/
├── .mvn/wrapper/, mvnw, mvnw.cmd
├── pom.xml
└── src/
    ├── main/
    │   ├── java/
    │   │   ├── db/migration/               # Java migration Flyway V10, V12
    │   │   └── vn/englishconversation/
    │   │       ├── EnglishConversationApplication.java
    │   │       ├── config/
    │   │       ├── common/{dto,exception,repository}/
    │   │       ├── auth/{bootstrap,controller,dto,job,security,service}/
    │   │       │   └── AccountPrincipal.java  # tại auth/, đọc Session JDBC cũ
    │   │       ├── user/{dto,entity,repository}/
    │   │       ├── profile/{controller,dto,entity,repository,service}/
    │   │       ├── scenario/{controller,dto,repository,service}/
    │   │       ├── lesson/{controller,dto,repository,service}/
    │   │       ├── learning/{controller,dto,repository,service}/
    │   │       ├── listening/{controller,dto,repository,service}/
    │   │       ├── pronunciation/{controller,dto,job,repository,service}/
    │   │       ├── conversation/{controller,dto,service}/
    │   │       ├── feedback/{controller,dto,repository,service}/
    │   │       ├── ai/{config,provider}/
    │   │       ├── voice/{dto,service}/
    │   │       ├── mail/{job,provider}/
    │   │       ├── admin/{controller,dto,repository,service}/
    │   │       ├── system/{controller,dto}/
    │   │       └── review/, statistics/    # package-info, chưa có lớp thực thi
    │   └── resources/
    │       ├── application.yaml, application-<profile>.yaml
    │       ├── content/
    │       └── db/migration/               # SQL migration, giữ nguyên tên và nội dung
    └── test/java/vn/englishconversation/   # Kiểm thử theo nhóm nghiệp vụ
```

Cấu trúc chia theo **nghiệp vụ trước, lớp bên trong sau**. DTO request/response nằm ở dto/, tách khỏi controller/service; DTO tài khoản dùng chung thuộc user/dto. Chỉ tạo lớp/thư mục khi có trách nhiệm thực tế; không bắt buộc interface/ServiceImpl cho mọi service.

`auth/AccountPrincipal.java` cũ là record deprecated chỉ phục vụ deserialize phiên JDBC đã lưu trước refactor. `readResolve` chuyển nó thành `auth.security.AccountPrincipal`; login mới chỉ tạo type mới. Không xóa lớp tương thích khi còn phiên từ bản cũ; thời điểm bỏ phải tính cả phiên cũ có thể còn được truy cập và làm mới, không chỉ thời gian deploy + 30 phút. Chỉ rollout một chiều trong lần thay đổi này; rollback về bản không biết package mới cần kết thúc các phiên mới hoặc giữ khả năng đọc chúng.

`mail/provider` chứa `MailGateway` và adapter SMTP, `mail/job` chứa worker gửi thư. `voice/dto` chứa hợp đồng HTTP, `voice/service` chứa xử lý WAV và lưu tạm âm thanh. Giữ tên bean, annotation, lịch chạy, cấu hình và hợp đồng HTTP khi đổi package. Java migration tiếp tục ở `db.migration` để Flyway khám phá đúng vị trí.

Module chưa có nghiệp vụ dùng package-info.java để ghi trách nhiệm. Khi có chức năng thực, thêm controller/, service/, repository/, entity/ và dto/ trong module tương ứng; không tạo interface/class rỗng hàng loạt. Endpoint health không cần service hoặc repository vì không có nghiệp vụ/persistence.

## Lệnh tại backend/

```powershell
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=bootstrap"
.\mvnw.cmd spring-boot:run
```

macOS/Linux dùng `./mvnw` thay `.\mvnw.cmd`; nếu cần, cấp quyền executable cho wrapper hoặc chạy `sh ./mvnw`.

Đặt datasource trong `.env` ở gốc repo để chạy với MySQL. Chỉ định `-Dspring-boot.run.profiles=bootstrap` khi cần chạy health độc lập. [Thiết lập](../docs/development/setup.md#chạy-tài-khoản-và-hồ-sơ-g1).

## API và bảo mật

`GET /api/v1/system/health` công khai, trả trạng thái tiến trình và timestamp UTC. `GET /api/v1/system/features` cho frontend biết phạm vi giao diện đang khả dụng. Profile `g6` mở tài khoản Session JDBC, hồ sơ, catalog, sổ tay, luyện nghe, luyện phát âm và hội thoại. [API phát âm](../docs/api/pronunciation.md). [API tài khoản](../docs/api/auth.md) · [API khám phá bài học](../docs/api/lessons.md) · [API học liệu và sổ tay](../docs/api/lesson-materials.md) · [API luyện nghe](../docs/api/listening.md).

Chạy kiểm thử P0 theo [biên bản bản dựng sạch](../docs/development/phase0/README.md). Xem [kiến trúc](../docs/architecture/backend.md) trước khi phát triển tính năng.

## Tự đọc .env khi chạy Maven

Từ thư mục backend, `mvn spring-boot:run` hoặc `.\mvnw.cmd spring-boot:run` tự đọc root `.env` qua spring.config.import của Spring Boot, không cần dependency dotenv. Tìm `../.env` trước, `./.env` sau (nếu có, file trong thư mục chạy ưu tiên hơn). File có thể không tồn tại; môi trường triển khai vẫn cung cấp biến như thường lệ. Biến môi trường terminal ưu tiên hơn file; mở terminal mới hoặc xóa biến cũ khi chuyển cấu hình.

Định dạng là Java properties: mỗi dòng `KEY=value`, không thêm dấu nháy bao quanh giá trị, `export`, dấu gạch chéo cuối dòng hoặc comment cuối giá trị. Password có backslash cần escape theo properties. Root .env tiếp tục bị Git ignore; không copy vào resources hoặc đóng gói JAR. Script PowerShell cũ vẫn dùng được cho các file KEY=value đơn giản.

Hồ sơ và catalog có [hợp đồng API tham chiếu](../docs/api/profile-catalog.md). Nghiệm thu runtime bản dựng sạch theo G1/G2 của [kế hoạch](../docs/development/full-system-implementation-plan.md).

## Vòng đời tài khoản và email (G1)

V4 tạo các bảng vòng đời tài khoản trên schema của dòng V1 mới. Người học đăng nhập được ngay sau đăng ký. Hệ thống dùng liên kết email một lần cho khôi phục mật khẩu.

| Biến môi trường | Mặc định / ý nghĩa |
| --- | --- |
| MAIL_ENABLED | false; bật sau khi cấu hình và kiểm tra dịch vụ gửi |
| MAIL_HOST / MAIL_PORT | host rỗng, port 587 |
| MAIL_USERNAME / MAIL_PASSWORD | credential SMTP chỉ ở server |
| MAIL_FROM | địa chỉ gửi của hệ thống, bắt buộc khi bật |
| MAIL_STARTTLS | true; giữ TLS khi kết nối dịch vụ thật |
| APP_PUBLIC_URL | http://localhost:5173; đặt URL HTTPS frontend khi triển khai, không có query/fragment |
| MAIL_OUTBOX_KEY | Base64 của 32 byte ngẫu nhiên; bắt buộc khi bật SMTP, AES-256-GCM cho token tạm trong outbox |
| MAIL_POLL_MS | 60000; chu kỳ quét gửi thư khôi phục |
| AVATAR_DIRECTORY | .runtime/avatars; thư mục riêng ngoài webroot |
| ACCOUNT_CLEANUP_MS | 300000; job xóa tài khoản và cleanup |

Không dùng khóa mẫu trong test để triển khai; không in khóa/password hoặc link token. Tạo khóa bằng CSPRNG và lưu trong secret môi trường. Khi đổi MAIL_OUTBOX_KEY: tạm dừng phát hành mail và worker, hủy outbox token chưa gửi/vô hiệu token tương ứng, thay khóa đồng bộ mọi instance, rồi bật lại và cho gửi lại liên kết. Chưa hỗ trợ keyring/rotation trong lúc vẫn gửi hàng đợi cũ; không đổi khóa đơn lẻ trên một instance. Khóa không được nằm chung backup database.

Để thử local có thể dùng SMTP sink chỉ nghe loopback, MAIL_STARTTLS=false và không credential; kiểm thử tự động đã dùng SMTP local/fake sender, không gửi email thật. Thiếu cấu hình SMTP thì request reset vẫn trả thông báo chung, UI giải thích phụ thuộc cấu hình; không giả báo đã gửi. Backend có thể lưu lịch khi SMTP tắt nhưng emailReminderAvailable=false.

API yêu cầu khôi phục luôn trả thông báo chung khi nhận đầu vào hợp lệ. Token đặt lại mật khẩu hết hạn sau 30 phút và dùng một lần. Yêu cầu gửi lại vô hiệu liên kết cũ. Đặt lại hoặc đổi mật khẩu thu hồi mọi phiên và vô hiệu token đặt lại còn lại. Avatar JPEG/PNG/WebP được chuẩn hóa PNG tối đa 512 px và lưu trong thư mục riêng. File cũ được dọn sau khi thay ảnh. File mồ côi trên một giờ được quét lại. Giới hạn HTTP multipart là 2 MiB/file và 3 MiB/request.

Xóa tài khoản trả 202 sau khi chặn truy cập và thu hồi phiên. Job giữ reference trong DB cho đến khi dọn audio/avatar thành công; lỗi dọn file giữ RETRY, không báo hoàn tất giả. Không xóa catalog; gỡ actor và ẩn danh target của audit USER. Tombstone hoàn tất giữ 8 ngày để bao phủ backup tối đa 7 ngày. Trước restore phải xuất và giữ các tombstone mới hơn backup ở nơi riêng, áp lại danh sách subject_user_id cần xóa trước mở dịch vụ, không phục hồi phiên cũ. Quy trình restore thật chưa nghiệm thu; không coi riêng job xóa là bằng chứng backup đã xử lý đúng. Chỉ xóa dữ liệu học tập vẫn giữ avatar/lịch/tài khoản.

Có bốn thread scheduler để SMTP không độc chiếm tác vụ cleanup audio. Đặt thư mục avatar/audio trên storage bền vững dùng chung nếu chạy nhiều instance; triển khai hiện tại chưa nghiệm thu storage phân tán. Worker dùng claim/lease, unique delivery, tối đa 3 lần và cửa sổ nhắc 30 phút. SMTP chấp nhận không đảm bảo inbox; kết quả không rõ sau crash có thể gây trùng thư.

Dependency mới: spring-boot-starter-mail theo BOM Spring Boot 4.1.2 (Apache-2.0), TwelveMonkeys imageio-webp 3.13.1 (BSD-3-Clause, đọc WebP trên Java 21). Nguồn: [Spring Mail](https://docs.spring.io/spring-boot/reference/io/email.html), [TwelveMonkeys](https://github.com/haraldk/TwelveMonkeys/tree/twelvemonkeys-3.13.1), [giấy phép](https://github.com/haraldk/TwelveMonkeys/blob/twelvemonkeys-3.13.1/LICENSE.txt). Không thêm thư viện UI hoặc JWT.
