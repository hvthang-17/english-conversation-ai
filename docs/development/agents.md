# Agent tooling

Tài liệu quản lý danh mục skill, cách lựa chọn và cập nhật. Quy tắc làm việc nằm trong [AGENTS.md](../../AGENTS.md).

## Shared installation

Nội dung chính nằm trong [agents/skills/](../../agents/skills/): bốn skill nội bộ chuyển nguyên nội dung từ vị trí cũ và năm skill upstream theo nguồn người dùng chọn. Tài nguyên nguồn được giữ cùng SKILL.md; không cài global hoặc thêm dependency ứng dụng.

Trên máy Windows hiện tại, `.agents/skills` là junction tới `agents/skills`, không phải bản sao. Junction được gitignore vì chứa đường dẫn máy local; sau clone xem [setup](setup.md#skill-repo-local) để tạo lại hoặc yêu cầu agent đọc trực tiếp SKILL.md. Chưa có CLAUDE.md/.claude/skills.

Client có thể nhận skill ở lượt tiếp theo; nếu chưa liệt kê, mở phiên mới và kiểm tra cơ chế khám phá của client. Tool/MCP là đặc tính môi trường, không phải cam kết dự án.

## Quy chuẩn giao diện của dự án

Khi thiết kế hoặc triển khai UI, đọc [Quy chuẩn cải tiến giao diện](interface-design-standard.md) và [ADR 0010](../decisions/0010-interface-design-system.md) trước. Skill frontend-design cung cấp phương pháp; quy chuẩn dự án chốt hướng áp dụng, token, màn hình và điều kiện nghiệm thu. Không thay theme theo ví dụ của skill.

## Choose by task

| Task | Installed skill | Scope in this project |
| --- | --- | --- |
| Thiết kế màn hình/hướng thị giác | [frontend-design](../../agents/skills/frontend-design/SKILL.md) | Bố cục, phân cấp, typography, tương tác; giữ brief/theme đã chốt |
| Viết/refactor React | [vercel-react-best-practices](../../agents/skills/vercel-react-best-practices/SKILL.md) | Rule React/client phù hợp phiên bản; không áp dụng Next.js/RSC/server actions/next/dynamic cho Vite |
| Review UI/accessibility | [web-design-guidelines](../../agents/skills/web-design-guidelines/SKILL.md) | Fetch hướng dẫn chính thức mới khi review; dùng công cụ sẵn có, báo nếu offline |
| Triển khai tương tác React | [implement-frontend-feature](../../agents/skills/implement-frontend-feature/SKILL.md) | API, trạng thái, thu âm và accessibility |
| Nghiệp vụ backend | [implement-backend-feature](../../agents/skills/implement-backend-feature/SKILL.md) | Java/Spring Boot, DTO, bảo mật và transaction |
| Schema hoặc query | [change-database](../../agents/skills/change-database/SKILL.md) | MySQL/JPA/Flyway và tác động dữ liệu |
| Hội thoại/chép lời/phản hồi AI | [integrate-gemini](../../agents/skills/integrate-gemini/SKILL.md) | API, prompt, output, usage và audio |
| Điều tra bug/test lỗi | [systematic-debugging](../../agents/skills/systematic-debugging/SKILL.md) | Tái hiện, truy nguyên, kiểm chứng giả thuyết, sửa và xác minh |
| Review/re-review PR hoặc diff | [pr-review](../../agents/skills/pr-review/SKILL.md) | Nguồn PyTorch, bắt buộc đọc [project profile](../../agents/skills/pr-review/references/project-profile.md) để áp dụng đúng dự án |

Skill bổ trợ nhau, không phải checklist nạp toàn bộ. Đọc rule/reference liên quan, giữ stack và quyết định dự án. Ví dụ upstream không cấp quyền thêm dependency, dữ liệu sản phẩm giả, tối ưu suy đoán hoặc triển khai ngoài yêu cầu.

`systematic-debugging` nhắc test-driven-development và verification-before-completion nhưng hai skill đó chưa cài; thực hiện tái hiện/kiểm thử/xác minh theo [testing](testing.md), không tự cài cả pack. Script Bash find-polluter.sh dùng npm test và ví dụ TypeScript có import riêng upstream: giữ làm tài liệu nguồn, chưa chạy/xác minh tương thích với dự án. Không dùng trực tiếp cho Maven/PowerShell. Chỉ log metadata đã che dữ liệu dù ví dụ debugging nói log đầu vào/đầu ra; không log secret, audio hoặc hội thoại thô.

`pr-review` lấy từ PyTorch, không phải workflow Lyreo của mẫu ban đầu. Không áp dụng CUDA/tensor/autograd, đường dẫn torch/aten, chính sách CI/runner và deprecation riêng PyTorch. Không dùng repo pytorch/pytorch trong ví dụ để review dự án này. Posting window trong upstream không cấp quyền đăng review: mặc định trả kết quả trong chat; chỉ đăng GitHub khi được yêu cầu rõ.

Chưa cài shadcn, Workers/Wrangler hoặc pack khác. Không suy ra cài đặt từ tên được nhắc tới. Không commit MCP cá nhân; dùng tài liệu chính thức khi công cụ tham chiếu không có.

## Provenance

Nội dung nguồn được kiểm tra ở staging trước khi cài vào vị trí chính. SKILL.md và tài nguyên upstream giữ nguyên byte; project profile là bổ sung nội bộ. [Lockfile](../../agents/skills.lock.json) lưu source/ref/path, số file và SHA-256 cây nguồn để đối chiếu.

| Installed skill | Source revision | License/notice |
| --- | --- | --- |
| frontend-design | [anthropics/skills @ e4bb5d9](https://github.com/anthropics/skills/tree/e4bb5d960d87126ff3b3a8af1a104b7db67560ba/skills/frontend-design) | [LICENSE.txt](../../agents/skills/frontend-design/LICENSE.txt), Apache-2.0 |
| vercel-react-best-practices | [vercel-labs/agent-skills @ 1a2c79d](https://github.com/vercel-labs/agent-skills/tree/1a2c79d4e6c4a2283dc2c614b6538cbe1ed5ca62/skills/react-best-practices) | SKILL.md khai báo MIT; revision này không có LICENSE/NOTICE tại root hoặc thư mục skill |
| web-design-guidelines | [vercel-labs/agent-skills @ 1a2c79d](https://github.com/vercel-labs/agent-skills/tree/1a2c79d4e6c4a2283dc2c614b6538cbe1ed5ca62/skills/web-design-guidelines) | Không có license riêng trong skill hoặc LICENSE/NOTICE tại root; không tự gán MIT |
| systematic-debugging | [obra/superpowers @ 22f0b46](https://github.com/obra/superpowers/tree/22f0b463e7a5e579904dd6211a520321035526f7/skills/systematic-debugging) | [LICENSE](../../agents/licenses/obra--superpowers--LICENSE), MIT |
| pr-review | [pytorch/pytorch @ 944193e](https://github.com/pytorch/pytorch/tree/944193e1530b030dd5937343557378dc8912e692/.agents/skills/pr-review) | [LICENSE](../../agents/licenses/pytorch--pytorch--LICENSE), [NOTICE](../../agents/licenses/pytorch--pytorch--NOTICE) từ root |
| implement-frontend-feature | Nội bộ, đường dẫn ở bảng chọn task | Theo lịch sử Git khi commit |
| implement-backend-feature | Nội bộ, đường dẫn ở bảng chọn task | Theo lịch sử Git khi commit |
| change-database | Nội bộ, đường dẫn ở bảng chọn task | Theo lịch sử Git khi commit |
| integrate-gemini | Nội bộ, đường dẫn ở bảng chọn task | Theo lịch sử Git khi commit |

Không tạo giấy phép thay upstream khi chưa có. Giấy phép toàn repository chưa chọn; giấy phép skill không tự áp dụng cho mã ứng dụng hoặc skill nội bộ.

Đã cài bằng helper install-skill-from-github.py của skill-installer, chỉ định repo/ref/path/dest vào staging local rồi chuyển tới agents/skills. Anthropic/obra dùng archive; Vercel/PyTorch dùng Git sparse checkout khi archive lỗi chuỗi chứng chỉ. Vẫn xác minh TLS qua kho chứng chỉ Windows; không sửa cấu hình Git global. Folder nguồn react-best-practices được đặt tên cài vercel-react-best-practices để khớp frontmatter.

## Update process

1. Chọn skill và revision cụ thể; đọc SKILL.md, tài nguyên và giấy phép thay đổi.
2. Nội bộ: đồng bộ yêu cầu/kiến trúc khi cần. Upstream: cài vào staging mới bằng helper hỗ trợ đích repo-local; không chạy script chỉ vì đã tải về.
3. Review diff, giữ license/notice, bảo toàn project profile. Nếu phải patch upstream, ghi rõ patch và lý do; không sửa âm thầm.
4. Kiểm tra frontmatter, tên folder, tài nguyên và hash; cập nhật lockfile cùng bảng nguồn trong cùng thay đổi.
5. Kiểm tra link dự án và junction local. Không thay toàn bộ pack, cài global hoặc tạo bản sao Claude ngoài yêu cầu.
