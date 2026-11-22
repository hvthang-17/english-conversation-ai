# Chuẩn bị thử Gemini và cấu hình key

## Trạng thái và quyền sử dụng
Chủ dự án xác nhận **chưa gọi API thật, chỉ chuẩn bị mã thử và hướng dẫn**. Số request Gemini đã thực hiện: **0**. Không tự bật billing, chọn paid tier hoặc suy ra quyền gọi từ việc có key.

Đã có bản thử Java 21 độc lập tại [experiments/phase0/gemini](../../../experiments/phase0/gemini/) dùng REST Interactions API, store=false, không background, không tools và không tự retry. Không thêm Gemini dependency vào backend runtime. Build/unit tests và dry-run đã đạt; đây không phải bằng chứng provider/model thật hoạt động.

## Lấy API key
1. Mở [trang API keys của Google AI Studio](https://aistudio.google.com/apikey), đăng nhập Google.
2. Chọn Create API key; chọn/tạo Google Cloud project theo giao diện. Nếu project đang có chưa hiện, import project qua mục Projects rồi quay lại API keys.
3. Đặt tên dễ nhận biết cho key dùng phát triển. Không bật billing chỉ để hoàn thành hướng dẫn này; quota/model khả dụng còn phụ thuộc project và gói thực tế.
4. Copy key vào dòng GEMINI_API_KEY trong file **.env ở gốc repository**, đã được tạo local. Không tạo dòng GEMINI_API_KEY thứ hai; không đưa key vào frontend/.env, VITE_*, chat, screenshot hoặc Git.
5. Giữ PHASE0_ALLOW_LIVE=false. File .env không tự được Spring Boot nạp; các script phase0 nạp biến vào đúng tiến trình. Key hiện chưa được backend runtime sử dụng.

Ví dụ tên biến, không phải credential thật:

```dotenv
GEMINI_API_KEY=YOUR_KEY_HERE
GEMINI_CHAT_MODEL=
GEMINI_AUDIO_MODEL=
GEMINI_FEEDBACK_MODEL=
PHASE0_ALLOW_LIVE=false
```

Không dùng Get-Content .env để chia sẻ cấu hình. Kiểm tra trạng thái mà không in giá trị:

```powershell
. ./scripts/phase0/local-env.ps1
$localConfig = Read-ProjectEnv -Path './.env'
if ([string]::IsNullOrWhiteSpace($localConfig['GEMINI_API_KEY'])) {
    'GEMINI_API_KEY chưa được cấu hình'
} else {
    'GEMINI_API_KEY đã được cấu hình; không hiển thị giá trị'
}
```

Tạo key không đồng nghĩa đã xác minh key sử dụng được. Nếu lộ key, revoke/rotate ở nhà cung cấp; không chỉ xóa dòng khỏi Git.

## Chạy offline

```powershell
./scripts/phase0/create-synthetic-audio.ps1
./scripts/phase0/run-gemini-probe.ps1
```

Yêu cầu JDK 21/Maven Wrapper và kết nối tải dependency lần đầu. Synthetic audio tạo bằng Windows SAPI, không micro người học. run-gemini-probe.ps1 từ chối tham số Live theo quyền hiện tại.

Dry-run chuẩn bị 4 case: CHAT_A2, CHAT_B1, FEEDBACK, TRANSCRIPTION. Report .runtime/phase0/gemini-dry-run.json chỉ chứa metadata, status PREPARED_NOT_EXECUTED, không latency/usage/cost bịa. Mô hình để NOT_SELECTED nếu chưa chọn. 8 unit test kiểm tra live gate, payload giới hạn/stateless, schema/source, bỏ thought output và giới hạn file; không gọi mạng provider.

## Phương án tích hợp đang đánh giá

| Phương án | Căn cứ hiện có | Chưa chứng minh |
| --- | --- | --- |
| Java REST / Interactions | Prototype biên dịch được, header Api-Revision 2026-05-20, inline audio và structured output theo tài liệu chính thức | Live request, quyền truy cập, độ trễ, tương thích model/project |
| Google GenAI Java SDK | Đã kiểm metadata Maven Central, release quan sát 2.6.0; thư viện chính thức | Chưa biên dịch spike SDK hoặc so sánh với REST |

Candidate để thử sau: gemini-3.5-flash-lite cho chi phí/độ trễ và gemini-3.8-flash cho chất lượng phản hồi. Tài liệu mô tả hai model nhận audio/text, nhưng không chứng minh project người dùng có quota hoặc chất lượng phù hợp. Không tự chốt model hoặc fallback sang model có phí khác khi lỗi.

Probe giới hạn 4 request/lần, max output 512 cho chat/audio, 2048 cho feedback, connect timeout 10 giây/request timeout 60 giây, không retry tự động. Các giá trị này là cấu hình thăm dò, chưa phải cấu hình production được đo. Request/output limit **không phải hard billing cap**; chưa có phép tính giá đáng tin nên cost giữ NULL.

## Điều kiện trước live test sau này
Phải có yêu cầu mới cho phép gọi, project/gói và ngân sách/hạn mức rõ, key môi trường, model đã kiểm tra chính thức, fixture không dữ liệu cá nhân, cùng quyền lưu bằng chứng đánh giá phù hợp. Wrapper vẫn chặn Live cho tới khi phạm vi được sửa có chủ đích. Nếu lỗi quota/auth/timeout dừng, không tự retry hoặc đổi gói. store=false không bảo đảm Google không giữ log an toàn hoặc xử lý dữ liệu theo điều khoản.

## Nguồn kiểm tra
- [API keys](https://ai.google.dev/gemini-api/docs/api-key)
- [Billing và quota](https://ai.google.dev/gemini-api/docs/billing)
- [Interactions REST reference](https://aistudio.google.com/api-reference/interactions-api/create-interaction)
- [Audio](https://ai.google.dev/gemini-api/docs/audio), [structured output](https://ai.google.dev/gemini-api/docs/structured-output)
- [Java SDK](https://github.com/googleapis/java-genai), [metadata Maven Central](https://repo.maven.apache.org/maven2/com/google/genai/google-genai/maven-metadata.xml)
- [3.5 Flash-Lite](https://aistudio.google.com/docs/models/gemini-3.5-flash-lite), [3.8 Flash](https://aistudio.google.com/docs/models/gemini-3.8-flash)
