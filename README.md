# English Conversation AI

Đồ án: **Xây dựng website hỗ trợ luyện giao tiếp tiếng Anh tích hợp trí tuệ nhân tạo**.

Người học A2–B1 luyện tình huống bằng văn bản hoặc ghi âm theo lượt, nhận phản hồi cuối buổi, lưu nội dung ôn tập và theo dõi hoạt động.

## Trạng thái

Đã triển khai tài khoản React/Spring Boot với Session JDBC/MySQL: đăng ký, đăng nhập, đổi mật khẩu, đăng xuất, CSRF, admin bootstrap và phân quyền tài khoản nền tảng. Đã có hồ sơ cá nhân và catalog 8 bài A2/B1 theo [giai đoạn 2](docs/development/phase2/README.md), với [API hồ sơ/catalog](docs/api/profile-catalog.md). Flyway V1–V5 tạo 13 bảng; xem [giai đoạn 1](docs/development/phase1/README.md) và [API auth](docs/api/auth.md). Hội thoại văn bản và HINT đã triển khai ở P3. Ôn tập và quản trị catalog thuộc các giai đoạn sau.

Giai đoạn 0 đã chuẩn bị MySQL 8.4.13 riêng trên localhost:3307 và xác minh Spring Boot kết nối thật; có probe Gemini offline, metadata micro Chrome desktop và tài liệu khảo sát. Giai đoạn 0 chỉ chạy offline. Giai đoạn 3 có bốn mẫu Gemini live với dữ liệu hư cấu. Xem [báo cáo và phần còn lại](docs/development/phase0/README.md).

## Công nghệ

- React 19.3.1 + TypeScript 5.9.3 + Vite 8.3.8, Node 24 và npm; hiện dùng CSS cơ bản.
- Java 21 + Spring Boot 4.1.2; Spring Security, Spring Data JPA/Hibernate và Maven Wrapper.
- MySQL 8.4 qua portable local hoặc Compose; Flyway V1 users, V2 Spring Session 4.1.1, V3 hồ sơ/catalog và V4 bộ 8 bài.
- Xác thực đã chọn Session cookie + Spring Security + Spring Session JDBC/MySQL; đã triển khai đăng nhập và CSRF. ERD gồm 20 bảng, trong đó 2 bảng phiên do thư viện quản lý.
- Gemini API được gọi từ backend khi bật hội thoại.

## Bắt đầu

- [Giai đoạn 3: cấu hình và chạy hội thoại](docs/development/phase3/README.md)

- [Mục lục tài liệu](docs/index.md)
- [Kế hoạch thực hiện theo giai đoạn](docs/development/implementation-plan.md)
- [Giai đoạn 0: kết quả, MySQL, Gemini/key và micro](docs/development/phase0/README.md)
- [Yêu cầu sản phẩm](docs/product/requirements.md)
- [Kiến trúc](docs/architecture/overview.md)
- [ERD và chính sách dữ liệu](docs/architecture/database.md)
- [Từ điển trường](docs/architecture/database-dictionary.md) và [workflow dữ liệu](docs/architecture/database-workflows.md)
- [Thiết lập môi trường](docs/development/setup.md)
- [Hướng dẫn Codex](AGENTS.md)
- [Danh mục skill](docs/development/agents.md)

## Chạy nhanh không cần database

Terminal thứ nhất, tại backend:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=bootstrap"
```

Terminal thứ hai, tại frontend, với Node 24 đang hoạt động:

```powershell
npm ci
npm run dev
```

Mở http://127.0.0.1:5173. Trang khởi tạo hiển thị kết nối tới backend 8080. Health chỉ kiểm tra HTTP, không kiểm tra MySQL/Gemini. Các API nghiệp vụ mặc định bị chặn.

Chi tiết cấu trúc trong [frontend/README.md](frontend/README.md), [backend/README.md](backend/README.md) và cách chạy MySQL trong [setup](docs/development/setup.md).
