# Quy ước lập trình

## Chung
Tên định danh tiếng Anh; hướng dẫn và giải thích tiếng Việt. Ưu tiên cấu trúc sẵn có, thay đổi tập trung, không refactor rộng ngoài yêu cầu. Đọc dependency và tài liệu chính thức theo phiên bản trước khi dùng API chưa rõ.

Không sao chép quy tắc chung vào nhiều file. Khi tài liệu và code khác nhau, chỉ ra phần khác và xác định ý định thay đổi. Không thêm dữ liệu sản phẩm giả để che việc API chưa tồn tại; fixture và mock phải tách khỏi runtime.

## Java

### Cấu trúc backend Spring Boot

Dự án dùng bố cục Maven và **chia package theo nghiệp vụ trước, theo trách nhiệm bên trong mỗi nghiệp vụ**. Đặt `EnglishConversationApplication.java` có `@SpringBootApplication` ở package gốc `vn.englishconversation`. Các thành phần nghiệp vụ nằm trong package con. Spring Boot khuyến nghị đặt lớp khởi động ở package gốc để giới hạn phạm vi quét. [Spring Boot — Structuring Your Code](https://docs.spring.io/spring-boot/reference/using/structuring-your-code.html) · [Apache Maven — Standard Directory Layout](https://maven.apache.org/guides/introduction/introduction-to-the-standard-directory-layout.html)

```text
backend/
├── pom.xml                         # Dependency và build
├── .mvn/wrapper/
├── mvnw, mvnw.cmd
└── src/
    ├── main/
    │   ├── java/vn/englishconversation/
    │   │   ├── EnglishConversationApplication.java
    │   │   ├── config/             # Cấu hình Spring cấp ứng dụng
    │   │   ├── common/             # Thành phần xuyên suốt dùng chung
    │   │   ├── auth/               # Xác thực, phiên và vòng đời tài khoản
    │   │   ├── profile/            # Hồ sơ học viên
    │   │   ├── scenario/           # Catalog tình huống
    │   │   ├── conversation/       # Buổi luyện và hội thoại
    │   │   └── listening/          # Ví dụ module sẽ thêm khi triển khai luyện nghe
    │   │       ├── controller/ListeningController.java
    │   │       ├── service/ListeningService.java
    │   │       ├── repository/ListeningAttemptRepository.java
    │   │       ├── entity/ListeningAttempt.java    # Khi dùng JPA
    │   │       └── dto/
    │   │           ├── SubmitListeningRequest.java
    │   │           └── ListeningAttemptResponse.java
    │   └── resources/
    │       ├── application.yaml
    │       ├── application-<profile>.yaml           # Khi môi trường cần cấu hình riêng
    │       └── db/migration/V<version>__<description>.sql
    └── test/
        ├── java/vn/englishconversation/listening/
        │   ├── ListeningServiceTest.java
        │   └── ListeningMysqlTest.java
        └── resources/                             # Fixture và cấu hình test khi cần
```

Cây trên minh họa vị trí đặt file mới. Các lớp thực tế được tạo theo nhu cầu của tính năng. Xem [cây mã nguồn hiện tại](../../backend/README.md#cấu-trúc).

### Trách nhiệm của từng package

| Package | Trách nhiệm và ví dụ |
| --- | --- |
| `<feature>/controller` | Nhận request HTTP, đọc principal, chạy validation đầu vào bằng `@Valid`, gọi service và trả DTO. Ví dụ `ListeningController` nhận bài nộp. |
| `<feature>/service` | Điều phối nghiệp vụ, kiểm tra owner/trạng thái, quyết định transaction và dùng repository hoặc gateway. Ví dụ `ListeningService` kiểm tra đề thuộc lượt làm rồi chấm điểm. |
| `<feature>/repository` | Chứa truy vấn và thao tác lưu/đọc của nghiệp vụ. Dùng JPA repository hoặc lớp lưu trữ JDBC tùy dữ liệu. API danh sách có giới hạn và thứ tự ổn định. |
| `<feature>/entity` | Ánh xạ dữ liệu JPA. Module chỉ dùng JDBC có thể đọc projection hoặc kiểu dữ liệu nội bộ phù hợp. |
| `<feature>/dto` | Hợp đồng vào/ra của API. Mỗi request/response là một file riêng. Controller trả DTO để kiểm soát dữ liệu công khai. |
| `<feature>/job`, `<feature>/provider` | Đặt tác vụ định kỳ, xử lý hàng đợi hoặc adapter dịch vụ ngoài tại module chịu trách nhiệm. Ví dụ `auth/job` cho dọn tài khoản và `ai/provider` cho Gemini. |
| `config`, `common` | `config` đặt cấu hình Spring cấp ứng dụng. `common/exception` xử lý lỗi chung và `common/dto` giữ hợp đồng lỗi. SQL và quy tắc riêng của tính năng nằm ở module sở hữu. |

### Quy tắc khi thêm file

- Luồng HTTP thông thường là **Controller → Service → Repository**. Service gọi adapter riêng khi cần Gemini, Azure, TTS hoặc SMTP. Với thao tác có provider, ghi trạng thái trong transaction ngắn, gọi provider ngoài transaction và kiểm tra lại trạng thái khi lưu kết quả.
- Tên package viết thường. Tên class dùng `PascalCase`, method và field dùng `camelCase`. Một public type có file cùng tên. Dùng constructor injection. Service có thể là một class cụ thể cho tới khi nghiệp vụ cần nhiều cách triển khai.
- Đặt tính năng vào module đang sở hữu dữ liệu hoặc nghiệp vụ. Giữ `auth/security` cho principal và filter, `auth/bootstrap` cho khởi tạo admin. Khi đổi package của kiểu đã được serialize vào Session JDBC, kiểm tra khả năng đọc phiên và có phương án tương thích hoặc thu hồi phiên.
- Cấu hình mặc định chung ở `application.yaml`. Các giá trị phụ thuộc môi trường đặt qua profile hoặc biến môi trường. Mật khẩu và API key chỉ lấy từ cấu hình ngoài mã nguồn. DDL nằm trong `db/migration` và được quản lý qua Flyway.
- Test trong `src/test/java` dưới cùng base package và nhóm nghiệp vụ tương ứng. Dùng unit test cho quy tắc nghiệp vụ, HTTP test cho validation/quyền và MySQL test cho FK, UNIQUE, transaction hoặc query đặc thù. Đặt fixture và cấu hình chỉ dùng khi test trong `src/test/resources` khi cần.

## Quy tắc cải tiến giao diện

Mọi thay đổi UI phải đọc và áp dụng [Quy chuẩn cải tiến giao diện](interface-design-standard.md) trước khi viết code. Bản thiết kế ngắn, tự phản biện theo đối tượng A2–B1 và checklist nghiệm thu trong quy chuẩn là một phần của công việc; không chỉ đổi màu hoặc thêm hiệu ứng.

## TypeScript
Bật strict khi khởi tạo; tránh any không có lý do. Component PascalCase, hook useX. API client dùng chung; không rải endpoint/logic xác thực trong UI. Kiểu phía client không thay thế validation server. Dọn effect, media stream và speech playback đúng vòng đời.

Tổ chức file theo [quy tắc cấu trúc frontend](frontend-structure.md): `app` ghép trang và layout, `features` chia theo nghiệp vụ và trách nhiệm, `shared` độc lập. Import trực tiếp, không tạo barrel export hoặc thư mục rỗng. CSS riêng đặt trong `styles/` của module sở hữu và được import rõ ràng; cập nhật import và tài liệu khi di chuyển file. ESLint chặn import ngược tầng.

## SQL
Tên bảng/cột snake_case; constraint/index có tên rõ. Chỉ tạo migration sau khi schema đã được thống nhất. Seed tình huống tách dữ liệu người dùng, có thể chạy lại theo cách có kiểm soát; không seed admin với mật khẩu cố định.
