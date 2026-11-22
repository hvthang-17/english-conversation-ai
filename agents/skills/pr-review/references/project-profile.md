# Project profile: English Conversation AI

Lớp áp dụng nội bộ cho skill nguồn PyTorch. Đọc AGENTS.md ở gốc, yêu cầu và kiến trúc trước review. SKILL.md và checklist nguồn được giữ nguyên để dễ cập nhật.

## Phạm vi và công cụ
- React/TypeScript/Vite, Java/Spring Boot, MySQL/Flyway và Gemini server-side. Hiện có scaffold HTTP, chưa có nghiệp vụ; không báo thiếu implementation ngoài phạm vi PR là lỗi.
- Dùng repo/base/head thật từ yêu cầu hoặc remote. Ví dụ pytorch/pytorch và main không phải cấu hình dự án.
- Không có Git/PR/gh hoặc quyền đọc thì review diff được cung cấp, nêu giới hạn. Không giả tạo kết quả CI hoặc maintainer permission.
- Chỉ đăng review/comment ngoài chat khi người dùng yêu cầu rõ. Cài skill không cấp quyền đăng.
- Nếu có và được phép dùng subagent thì có thể phân chia review theo nguồn; nếu không, tự thực hiện các lượt kiểm tra và nêu giới hạn. Không giả định Agent tool luôn tồn tại.

## Ánh xạ checklist
Áp dụng correctness, security, API compatibility, chất lượng test, concurrency và bằng chứng file:line. Xác minh tác động thực, gộp finding trùng, phân biệt blocking/non-blocking. Không suy diễn bug từ style hoặc giả định chưa kiểm tra.

Không áp dụng CUDA, tensors, autograd, torch/aten, OpInfo, quy tắc log thư viện Python, thời hạn deprecation, ciflow labels hoặc runner naming riêng PyTorch. Không tạo các file/hạ tầng đó trong dự án.

Thay bằng các kiểm tra:
- Spring Security: role, ownership, không tin userId client để cấp quyền; không rò key/token/mật khẩu.
- REST: DTO, validation, lỗi, phân trang và tương thích frontend.
- MySQL/JPA/Flyway: migration, ràng buộc, query, transaction, cạnh tranh và chính sách xóa.
- Hội thoại: thứ tự, chống trùng, lỗi AI, không giữ transaction trong lời gọi mạng.
- Gemini: key server-side, giới hạn usage/context, output validation, prompt injection, transcript xác nhận và dọn audio.
- React: trạng thái bất đồng bộ, cleanup micro, accessibility, không render HTML AI thô hoặc chứa secret trong VITE_*.
- Kiểm thử: theo docs/development/testing.md và công cụ thực có; không suy ra test đã chạy từ tên file hoặc coi mock là live test.

Không có finding thì báo không phát hiện vấn đề cùng phạm vi đã kiểm tra; không khẳng định mọi rủi ro đã loại trừ.
