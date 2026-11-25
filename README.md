# English Conversation AI

Đồ án: **Xây dựng website hỗ trợ luyện giao tiếp tiếng Anh tích hợp trí tuệ nhân tạo**.

Người học A2–B1 luyện tình huống bằng văn bản hoặc ghi âm theo lượt, nhận phản hồi cuối buổi, lưu nội dung ôn tập và theo dõi hoạt động.

## Trạng thái

Bản dựng hiện tại mặc định chạy profile `g6` với MySQL local. Profile `bootstrap` kiểm tra tiến trình HTTP, còn `bootstrap,p0-db` tái lập mốc kiểm chứng V1 trên schema riêng của dòng Flyway mới. [Kế hoạch 10 nhóm](docs/development/full-system-implementation-plan.md).

P0 đã kiểm chứng MySQL V1, API nền, frontend, audio Chrome Windows, TTS local và probe AI offline. Edge, Android và thử provider thật còn chờ. [Biên bản P0](docs/development/phase0/README.md).

## Công nghệ

- React 19.3.1 + TypeScript 5.9.3 + Vite 8.3.8, Node 24 và npm; hiện dùng CSS cơ bản.
- Java 21 + Spring Boot 4.1.2; Spring Security, Spring Data JPA/Hibernate và Maven Wrapper.
- MySQL 8.4 qua portable local hoặc Compose; Flyway V1 users, V2 Spring Session 4.1.1, V3 hồ sơ/catalog và V4 bộ 8 bài.
- Xác thực đã chọn Session cookie + Spring Security + Spring Session JDBC/MySQL; đã triển khai đăng nhập và CSRF. ERD gồm 20 bảng, trong đó 2 bảng phiên do thư viện quản lý.
- Gemini API được gọi từ backend khi bật hội thoại.

## Bắt đầu

- [Kế hoạch G6](docs/development/full-system-implementation-plan.md#g6--luyện-hội-thoại-với-ai)

- [Mục lục tài liệu](docs/index.md)
- [Kế hoạch triển khai 10 nhóm chức năng](docs/development/full-system-implementation-plan.md)
- [Giai đoạn 0: kết quả, MySQL, Gemini/key và micro](docs/development/phase0/README.md)
- [Yêu cầu sản phẩm](docs/product/requirements.md)
- [Kiến trúc](docs/architecture/overview.md)
- [ERD và chính sách dữ liệu](docs/architecture/database.md)
- [Từ điển 46 bảng](docs/architecture/database.md#3-từ-điển-46-bảng-và-dữ-liệu-mẫu) và [luồng dữ liệu](docs/architecture/database.md#6-luồng-dữ-liệu)
- [Thiết lập môi trường](docs/development/setup.md)
- [Hướng dẫn Codex](AGENTS.md)
- [Danh mục skill](docs/development/agents.md)

## Chạy nhanh G2

Thiết lập kết nối MySQL trong `.env` ở gốc repo. Mở terminal tại `backend/` và chạy:

```powershell
mvn spring-boot:run
```

Terminal thứ hai, tại frontend, với Node 24 đang hoạt động:

```powershell
npm ci
npm run dev
```

Mở http://127.0.0.1:5173 để khám phá bài học, nghe hội thoại mẫu, ôn bằng sổ tay, luyện nghe và luyện phát âm. Maven mặc định chọn profile `g6` và chạy Flyway đến V18. Vào **Tình huống** để luyện hội thoại. [Cấu hình G6](docs/development/acceptance/g6-conversation-acceptance.md). [Cấu hình và nghiệm thu G5](docs/development/acceptance/g5-pronunciation-acceptance.md). Để chạy riêng health mà không kết nối MySQL, dùng `mvn spring-boot:run "-Dspring-boot.run.profiles=bootstrap"`. [Hướng dẫn công khai đề nghe](docs/api/listening.md).

Chi tiết cấu trúc trong [frontend/README.md](frontend/README.md), [backend/README.md](backend/README.md) và cách chạy MySQL trong [setup](docs/development/setup.md).
