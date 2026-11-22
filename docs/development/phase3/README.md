# Giai đoạn 3 — Hội thoại văn bản và gợi ý

V5 tạo practice_sessions → ai_requests → ai_attempts, conversation_turns → messages. Khóa ngoại audio được dành cho giai đoạn 6. Khởi động lại backend để Flyway áp phiên bản mới lên database đang dùng.

## Chạy hội thoại

Trong root `.env`, giữ key và model đã cấu hình, thêm hai dòng dưới đây bằng giá trị của bạn.

```dotenv
GEMINI_CHAT_ENABLED=true
CHAT_FINGERPRINT_SECRET=gia_tri_ngau_nhien_toi_thieu_32_ky_tu
```

Có thể sinh secret một lần bằng Python trong terminal local.

```powershell
python -c "import secrets; print(secrets.token_hex(32))"
```

Giữ secret trong file local bị ignore. Từ backend chạy `mvn spring-boot:run`, từ frontend chạy `npm run dev`, đăng nhập rồi chọn tình huống. Trước khi thử Free tier, dùng nội dung hư cấu. Việc thêm key không tự bật tính năng.

## Điều phối và giới hạn

- Backend lấy topic, scenario và level khả dụng tại thời điểm mở buổi rồi ghi snapshot. Transcript chỉ lưu nội dung đã được chấp nhận.
- Khóa buổi và UNIQUE bảo vệ số lượt, idempotency và mỗi bên một message/lượt. UNIQUE trên cột sinh ảo giữ tối đa một tác vụ PENDING/RUNNING/UNKNOWN của một buổi.
- MySQL advisory lock giữ qua transaction đặt chỗ để kiểm tra quota nguyên tử giữa các instance cùng schema. Provider được gọi sau commit. Mỗi lần gọi giữ một dòng attempt, kể cả UNKNOWN. Pool cần đủ cho connection giữ advisory lock và transaction.
- Mặc định 6 request/phút/người, 10 request/phút/cơ sở dữ liệu, tối đa 2 tác vụ đồng thời, timeout 30 giây và tối đa 2 attempt/tác vụ. Tổng số attempt có thể đặt qua CHAT_TOTAL_ATTEMPT_LIMIT, mặc định 0 tương ứng không đặt trần tổng. Các mức này là chốt bảo vệ ứng dụng, quota Google vẫn áp dụng riêng.
- Giới hạn mặc định 1.000 ký tự/câu người học, 2.000 ký tự/câu AI, 80.000 ký tự ngữ cảnh và 512 token đầu ra. Nếu vượt giới hạn ngữ cảnh thì từ chối trước gọi. Lease dài hơn timeout; trạng thái quá hạn được chuyển UNKNOWN khi truy cập buổi.
- Hiện dùng REST generateContent với system instruction và history tách riêng, không bật công cụ. Chỉ nhận candidate STOP và plain text hợp lệ theo giới hạn. Nhật ký kỹ thuật giữ model, prompt version, latency, usage nếu có. Chi phí USD để NULL nếu thiếu bảng giá phù hợp hoặc usage; cấu hình giá tùy chọn dùng giá trên một triệu token và mã tham chiếu.
- Preview âm thanh, chép lời, feedback và trang lịch sử chuyên biệt thuộc các giai đoạn sau.

## Kiểm tra

`scripts/phase2/test-mysql.ps1` chạy suite hiện hành qua MySQL 8.4.13 portable trên schema mới và đường nâng cấp. `python scripts/phase3/smoke-gemini.py --live` là lệnh kiểm thử riêng, gửi tối đa bốn request với hội thoại nhà hàng hư cấu. Lệnh lưu kết quả dưới .runtime/phase3 và dừng ở lỗi đầu tiên. Kết quả live là bằng chứng mẫu và không thay thế đánh giá CEFR độc lập hoặc kiểm tra trên toàn bộ catalog.

Hợp đồng tại [API buổi luyện](../../api/practice.md).

Bản thử Gemini ngày 22/11/2026 hoàn thành cả bốn request. Hai câu mở đầu giữ vai nhân viên nhà hàng, câu tiếp theo phản hồi yêu cầu gọi súp và HINT đưa mẫu câu xin trà. Độ trễ quan sát khoảng 1,7–6,8 giây. Bốn mẫu dùng tổng 376 token đầu vào và 55 token đầu ra theo phản hồi provider. Đây là kiểm tra ngữ cảnh trên mẫu nhỏ. [Kết quả và usage từng request](gemini-live.json).

Xem số liệu kỹ thuật ban đầu trong MySQL bằng truy vấn chỉ đọc sau. Truy vấn tổng hợp metadata và không cần chọn nội dung hội thoại.

```sql
SELECT r.task_type, a.model_name, a.status,
       COUNT(*) AS attempts, AVG(a.latency_ms) AS avg_latency_ms,
       SUM(a.input_tokens) AS input_tokens, SUM(a.output_tokens) AS output_tokens,
       SUM(a.estimated_cost) AS estimated_usd
FROM ai_attempts a
JOIN ai_requests r ON r.id = a.ai_request_id
GROUP BY r.task_type, a.model_name, a.status;
```

Maven verify chạy trên MySQL 8.4.13 với 33/33 kiểm thử đạt. Bốn kiểm thử P3 bao phủ luồng HTTP có Session/CSRF, owner, snapshot, 20 lượt, quota giữa hai buổi, hai thao tác đồng thời, timeout, replay, retry và metadata. Typecheck, lint và build frontend đều đạt. Chưa kiểm tra giao diện trong trình duyệt với Gemini live. [Bằng chứng kiểm thử](verification.json).
