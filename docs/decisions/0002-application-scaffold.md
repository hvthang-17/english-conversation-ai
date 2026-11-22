# ADR 0002: Khởi tạo cấu trúc ứng dụng

## Trạng thái
Chấp nhận cho scaffold. Nghiệp vụ vẫn theo yêu cầu sản phẩm, chưa được triển khai bởi quyết định này.

## Quyết định
- Java 21, Spring Boot 4.1.2 từ danh sách stable của Spring Initializr, Maven Wrapper 3.3.4 / Maven 3.10.1. Base package vn.englishconversation.
- React 19.3.1, Vite 8.3.8, TypeScript 5.9.3, Node 24.19+ trong nhánh 24, npm lockfile. Chọn TS5.9 để tương thích peer range của typescript-eslint 8.73.
- Package backend theo nghiệp vụ, mỗi module chưa có code dùng package-info; thêm phân lớp khi triển khai. Không tạo hàng loạt class/controller/repository rỗng.
- Frontend app/features/shared; CSS cơ bản, chưa cần router/state library/UI kit/Tailwind.
- Health HTTP là endpoint duy nhất công khai. DenyAll còn lại; không tạo tài khoản mặc định. AUTH-01 chưa triển khai; cơ chế hiện đã chốt Session JDBC tại ADR 0004 sau bước scaffold.
- Profile bootstrap không persistence để kiểm tra cấu trúc local/test; default dùng MySQL/JPA/Flyway. Không dùng bootstrap cho production hoặc chứng minh DB hoạt động.
- Compose chỉ chạy MySQL 8.4 local, bind localhost; không tự tạo container hóa ứng dụng hoặc production deployment.
- Chưa tạo schema nghiệp vụ: thêm SQL versioned cùng tính năng đầu tiên; tránh migration rỗng hoặc bảng không có mục đích sản phẩm.

## Hệ quả
Có thể biên dịch, kiểm thử API và kiểm tra HTTP frontend/backend trước khi cài MySQL. Cần môi trường MySQL để kiểm tra persistence riêng. Trang khởi tạo nêu rõ chưa có chức năng học tập và không dùng dữ liệu giả. Maven/npm cần kết nối mạng trong lần tải dependency đầu.

## Nguồn tham chiếu phiên bản
- [Spring Initializr metadata](https://start.spring.io/metadata/client)
- [Spring Boot 4.1 requirements](https://docs.spring.io/spring-boot/4.1/system-requirements.html)
- [Spring Boot testing](https://docs.spring.io/spring-boot/4.1/reference/testing/spring-boot-applications.html)
- [Vite guide](https://vite.dev/guide/)
- [Node release schedule](https://nodejs.org/en/about/previous-releases)

Dependency thực tế nằm trong pom.xml và package-lock.json; kiểm tra đúng phiên bản thay vì suy ra latest từ tài liệu online.
