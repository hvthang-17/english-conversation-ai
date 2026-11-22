# Quy ước lập trình

## Chung
Tên định danh tiếng Anh; hướng dẫn và giải thích tiếng Việt. Ưu tiên cấu trúc sẵn có, thay đổi tập trung, không refactor rộng ngoài yêu cầu. Đọc dependency và tài liệu chính thức theo phiên bản trước khi dùng API chưa rõ.

Không sao chép quy tắc chung vào nhiều file. Khi tài liệu và code khác nhau, chỉ ra phần khác và xác định ý định thay đổi. Không thêm dữ liệu sản phẩm giả để che việc API chưa tồn tại; fixture và mock phải tách khỏi runtime.

## Java
Tên class PascalCase, method/field camelCase, package lowercase. Constructor injection; DTO tách entity; kiểm tra input tại biên; exception có ngữ nghĩa và handler tập trung. Tránh catch bỏ qua lỗi và repository query không giới hạn. Chia package theo nghiệp vụ, sau đó controller/service/repository/entity/dto tùy lớp thực có; auth/security và auth/bootstrap cho bảo mật/khởi tạo. DTO request/response là file riêng, không đặt nested record trong controller/service. Không tạo ServiceImpl nếu chưa cần nhiều implementation. Khi đổi package của kiểu serializable đang nằm trong Session JDBC, phải có kế hoạch tương thích dữ liệu phiên và kiểm thử.

## TypeScript
Bật strict khi khởi tạo; tránh any không có lý do. Component PascalCase, hook useX. API client dùng chung; không rải endpoint/logic xác thực trong UI. Kiểu phía client không thay thế validation server. Dọn effect, media stream và speech playback đúng vòng đời.

## SQL
Tên bảng/cột snake_case; constraint/index có tên rõ. Chỉ tạo migration sau khi schema đã được thống nhất. Seed tình huống tách dữ liệu người dùng, có thể chạy lại theo cách có kiểm soát; không seed admin với mật khẩu cố định.
