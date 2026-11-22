# Backend

Java 21, Spring Boot 4.1.2, Maven Wrapper 3.3.4 tải Maven 3.10.1; base package `vn.englishconversation`.

## Cấu trúc

```text
backend/
├── .mvn/wrapper/maven-wrapper.properties
├── mvnw / mvnw.cmd
├── pom.xml
└── src/
    ├── main/
    │   ├── java/vn/englishconversation/
    │   │   ├── EnglishConversationApplication.java
    │   │   ├── config/
    │   │   │   ├── SecurityConfig.java
    │   │   │   └── BootstrapSecurityConfig.java
    │   │   ├── common/
    │   │   │   ├── dto/ApiErrorResponse.java
    │   │   │   └── exception/{ApiException,ApiErrors}.java
    │   │   ├── auth/
    │   │   │   ├── controller/AuthController.java
    │   │   │   ├── service/AccountService.java
    │   │   │   ├── dto/{CredentialsRequest,ChangePasswordRequest,CsrfResponse}.java
    │   │   │   ├── security/{AccountPrincipal,AccountStateFilter}.java
    │   │   │   ├── bootstrap/AdminBootstrap.java
    │   │   │   └── AccountPrincipal.java  # lớp đọc phiên JDBC cũ
    │   │   ├── user/
    │   │   │   ├── entity/UserAccount.java
    │   │   │   ├── repository/UserRepository.java
    │   │   │   └── dto/UserResponse.java
    │   │   ├── admin/
    │   │   │   ├── controller/AccountAdminController.java
    │   │   │   └── dto/UpdateAccountAccessRequest.java
    │   │   ├── system/{controller,dto}/
    │   │   ├── profile/{controller,service,repository,entity,dto}/
    │   │   ├── scenario/{controller,service,repository,dto}/
    │   │   ├── conversation/
    │   │   ├── ai/
    │   │   ├── feedback/
    │   │   ├── review/
    │   │   └── statistics/
    │   └── resources/
    │       ├── application.yaml
    │       ├── application-bootstrap.yaml
    │       ├── application-production.yaml
    │       └── db/migration/{V1__users,V2__spring_session,V3__profiles_and_catalog,V4__starter_catalog}.sql
    └── test/java/vn/englishconversation/
        ├── auth/{AccountRulesTest,SessionMysqlTest,ProductionCookieMysqlTest}.java
        ├── profile/ProfileCatalogMysqlTest.java
        ├── config/EnvImportTest.java
        └── system/HealthEndpointTest.java
```

Cấu trúc chia theo **nghiệp vụ trước, lớp bên trong sau**. DTO request/response nằm ở dto/, tách khỏi controller/service; DTO tài khoản dùng chung thuộc user/dto. Chỉ tạo lớp/thư mục khi có trách nhiệm thực tế; không bắt buộc interface/ServiceImpl cho mọi service.

`auth/AccountPrincipal.java` cũ là record deprecated chỉ phục vụ deserialize phiên JDBC đã lưu trước refactor. `readResolve` chuyển nó thành `auth.security.AccountPrincipal`; login mới chỉ tạo type mới. Không xóa lớp tương thích khi còn phiên từ bản cũ; thời điểm bỏ phải tính cả phiên cũ có thể còn được truy cập và làm mới, không chỉ thời gian deploy + 30 phút. Chỉ rollout một chiều trong lần thay đổi này; rollback về bản không biết package mới cần kết thúc các phiên mới hoặc giữ khả năng đọc chúng.

Module chưa có nghiệp vụ dùng package-info.java để ghi trách nhiệm. Khi có chức năng thực, thêm controller/, service/, repository/, entity/ và dto/ trong module tương ứng; không tạo interface/class rỗng hàng loạt. Endpoint health không cần service hoặc repository vì không có nghiệp vụ/persistence.

## Lệnh tại backend/

```powershell
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=bootstrap"
```

macOS/Linux dùng `./mvnw` thay `.\mvnw.cmd`; nếu cần, cấp quyền executable cho wrapper hoặc chạy `sh ./mvnw`.

`bootstrap` tắt datasource/JPA/Flyway để chạy scaffold local. Mặc định kết nối MySQL; đặt datasource trong root .env theo [setup](../docs/development/setup.md) rồi chạy `spring-boot:run` không kèm profile. Không sử dụng bootstrap cho production.

## API và bảo mật

`GET /api/v1/system/health` công khai, trả `status`, `application`, `timestamp` UTC; đây là process liveness, không phải DB/provider readiness. Default profile đã có [API tài khoản](../docs/api/auth.md), Spring Session JDBC 4.1.1, CSRF và phân quyền admin tài khoản; route chưa triển khai denyAll. Không có Gemini runtime client. Profile bootstrap vẫn chỉ có health.

Test thường xác minh quy tắc/hash schema provenance và HTTP health; test HTTP/JDBC thật chạy qua [script P1](../scripts/phase1/test-mysql.ps1) trên database MySQL mới. Xem [kết quả và admin bootstrap](../docs/development/phase1/README.md). Xem [kiến trúc](../docs/architecture/backend.md) trước khi phát triển tính năng.

## Tự đọc .env khi chạy Maven

Từ thư mục backend, `mvn spring-boot:run` hoặc `.\mvnw.cmd spring-boot:run` tự đọc root `.env` qua spring.config.import của Spring Boot, không cần dependency dotenv. Tìm `../.env` trước, `./.env` sau (nếu có, file trong thư mục chạy ưu tiên hơn). File có thể không tồn tại; môi trường triển khai vẫn cung cấp biến như thường lệ. Biến môi trường terminal ưu tiên hơn file; mở terminal mới hoặc xóa biến cũ khi chuyển cấu hình.

Định dạng là Java properties: mỗi dòng `KEY=value`, không thêm dấu nháy bao quanh giá trị, `export`, dấu gạch chéo cuối dòng hoặc comment cuối giá trị. Password có backslash cần escape theo properties. Root .env tiếp tục bị Git ignore; không copy vào resources hoặc đóng gói JAR. Script PowerShell cũ vẫn dùng được cho các file KEY=value đơn giản.

Hồ sơ và catalog đã triển khai ở [P2](../docs/development/phase2/README.md), API tại [profile-catalog](../docs/api/profile-catalog.md). Catalog dùng JDBC projection công khai; hồ sơ dùng JPA, cập nhật sở thích JDBC trong cùng transaction. Chạy scripts/phase2/test-mysql.ps1 cho toàn bộ suite và đường nâng cấp V2→V4.
