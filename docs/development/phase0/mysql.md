# MySQL local đã xác minh

## Kết quả
Đã chạy MySQL Community 8.4.13 portable ở **127.0.0.1:3307**, dữ liệu trong .runtime/phase0/mysql-data. Không cài Windows service và không thay đổi dịch vụ MySQL92 đang dừng của máy.

- Tải ZIP từ CDN MySQL chính thức qua TLS; MD5 khớp giá trị trên trang tải: 7b94489d5524dea9b27097068bea25a8.
- Tài khoản english_app giới hạn database english_conversation; credential local ngẫu nhiên ở root .env, không in ra chat hoặc commit.
- Kiểm tra bằng client: version/port/database/account, tạo bảng TEMPORARY, ghi/đọc Unicode, xóa bảng tạm; session timezone +00:00, kết nối có TLS cipher. Stop/start/restart đã xác minh giữ dữ liệu, không reinitialize. Đây là local, chưa xác minh certificate/hostname production.
- Spring Boot khởi động **không profile bootstrap**, Hikari kết nối thành công; Flyway nhận MySQL 8.4, validate 0 migration và tạo riêng flyway_schema_history; Hibernate EntityManagerFactory khởi tạo.
- GET health trả UP khi backend đang dùng datasource thật. Health tự nó vẫn chỉ là process liveness; bằng chứng DB là truy vấn client và log startup JDBC/Flyway/JPA, không phải suy ra từ health.
- Chưa tạo bảng/entity/migration nghiệp vụ, chưa kiểm thử schema 20 bảng hoặc tài khoản Session.

Metadata local tại .runtime/phase0/mysql-verification.json. Không đưa option file chứa mật khẩu hoặc raw log chưa kiểm tra vào repository.

## Chạy từ gốc repository trên PowerShell

```powershell
./scripts/phase0/start-mysql.ps1
./scripts/phase0/verify-mysql.ps1
./scripts/phase0/run-backend.ps1
```

run-backend.ps1 nạp root .env vào biến môi trường của tiến trình, không thay đổi môi trường global. Tắt backend bằng Ctrl+C.

```powershell
./scripts/phase0/stop-mysql.ps1
```

Start dùng lại data directory đã có, không xóa hoặc khởi tạo lại. Không sửa .env rồi coi credential database tự đổi. Không chạy start khi port đã bị instance khác chiếm; script kiểm tra và từ chối nếu không xác minh được.

## Máy thành viên khác
Có thể dùng Compose trong infrastructure/compose.yaml hoặc tải [MySQL 8.4 Windows ZIP](https://dev.mysql.com/downloads/mysql/8.4.html) chính thức. Đặt portable đã kiểm tra tại .runtime/phase0/tools/mysql-8.4.13-winx64 hoặc truyền ToolDirectory vào start script; script yêu cầu nhánh 8.4. Không copy .env hay data directory từ máy người khác.

Start tạo root .env với credential ngẫu nhiên chỉ nếu file chưa tồn tại. Nếu đã có file thiếu/sai giá trị, dừng để người dùng kiểm tra; không ghi đè key hoặc password. Công cụ bootstrap dùng account root loopback cho quản trị và app account có DDL trong database riêng phục vụ phát triển/Flyway; production cần mô hình tài khoản/quyền riêng.

## Giới hạn còn lại
Chưa xác minh reboot máy, backup/restore, migration nghiệp vụ hoặc production. Hai lỗi bootstrap đã được truy nguyên và sửa: root@localhost không khớp TCP khi skip-name-resolve (thêm root loopback riêng), và start ngay sau shutdown trong lúc process cũ còn giữ ibdata1 (stop script chờ process thoát). Đã test lại vòng đời; không sửa quyền file/data máy cũ. File init chứa credential được xóa sau startup thành công; nếu lần đầu lỗi phải kiểm tra/dọn file local khi hoàn tất, không công bố nội dung.
