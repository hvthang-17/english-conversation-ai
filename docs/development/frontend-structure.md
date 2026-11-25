# Quy tắc tổ chức frontend

Áp dụng cho React + TypeScript + Vite trong dự án. Chia theo nghiệp vụ trước, theo trách nhiệm bên trong mỗi nghiệp vụ; giữ import trực tiếp và chỉ tạo thư mục khi có mã thực. Cấu trúc này triển khai nguyên tắc đã có tại [kiến trúc frontend](../architecture/frontend.md), không yêu cầu thêm thư viện router, state hoặc UI.

## Cấu trúc hiện tại

```text
frontend/
├── public/fonts/                 # Font local, URL tĩnh được giữ nguyên
├── index.html                    # HTML entry
├── vite.config.ts
├── eslint.config.js
├── tsconfig*.json
└── src/
    ├── main.tsx                  # Mount React và global CSS
    ├── vite-env.d.ts
    ├── app/
    │   ├── App.tsx               # Phiên, capability và điều phối trang
    │   ├── routing/routes.ts     # Nhận diện hash route, nhóm trang và trang đầu
    │   ├── layouts/              # AppFrame, HeaderAccount, PublicLayout
    │   ├── workspace/            # Ghép màn hình LearningWorkspace
    │   └── styles/               # Token/reset/global và app shell
    ├── features/
    │   ├── auth/                 # Biểu mẫu tài khoản, khôi phục, kiểu User
    │   ├── profile/              # Hồ sơ, avatar và lịch học
    │   ├── lessons/              # Catalog, chi tiết và học liệu
    │   ├── scenarios/            # Tình huống và chọn vai
    │   ├── conversation/         # Hội thoại, lượt ghi âm và giải thích
    │   ├── feedback/             # Nhận xét buổi luyện
    │   ├── learning/             # Sổ tay và quản lý dữ liệu học tập
    │   ├── review/               # Ôn thẻ từ phản hồi
    │   ├── history/              # Lịch sử hội thoại
    │   ├── statistics/           # Thống kê hoạt động
    │   ├── listening/            # Bài nghe, lượt làm và lịch sử nghe
    │   ├── pronunciation/        # Luyện phát âm và hook ghi âm riêng
    │   ├── admin/                # Quản trị
    │   └── system/               # Trạng thái hệ thống
    └── shared/
        ├── api/                  # HTTP/CSRF và thông báo lỗi dùng chung
        ├── hooks/                # useApiQuery, useSpeech
        ├── lib/                  # Level, sự kiện hồ sơ, audio/recording
        ├── styles/               # CSS trình bày dùng ở nhiều feature
        ├── types/                # Page, SessionGuard
        └── ui/                   # Brand, Icon, PageHeading, Pagination
```

Trong một feature:

| Vị trí | Trách nhiệm |
| --- | --- |
| `components/*.tsx` | Màn hình và thành phần UI của feature. Tên PascalCase. |
| `api/*-api.ts` | Hàm HTTP, kiểm tra phản hồi và hợp đồng riêng của API. Dùng shared client. |
| `hooks/use*.ts` | Hook có vòng đời hoặc trạng thái riêng của feature. |
| `model/*.ts` | Quy tắc, kiểm tra dữ liệu hoặc mô hình nghiệp vụ tách khỏi UI khi cần. |
| `types.ts` | Kiểu dùng giữa nhiều file trong feature, khi việc tách giúp làm rõ nơi sở hữu. Kiểu chỉ dùng một component được giữ cùng component. |
| `styles/*.css` | CSS riêng của feature, được import từ component sử dụng. |
| `README.md` | Trách nhiệm, phụ thuộc và giới hạn kiểm chứng. |

Feature lớn có thể chia tiếp theo chức năng quản lý trước, rồi theo trách nhiệm. `admin` dùng `overview/`, `accounts/`, `materials/`, `scenarios/`, `activity/`; khung ghép chung trong `admin/components` và cấu hình điều hướng trong `admin/model`. Không ép mọi module có đủ API/hook/types nếu không có nhu cầu. [Cây admin và trách nhiệm](../../frontend/src/features/admin/README.md).

## Ranh giới phụ thuộc

- `app` được import feature và shared; feature không import `app`. Component feature nhận callback, dữ liệu qua props. App quyết định layout và màn hình nào được ghép.
- `shared` không import `app` hoặc `features`; tên API nghiệp vụ cụ thể không nằm trong hook dùng chung. `useApiQuery` nhận đường dẫn và callback hết phiên từ nơi gọi.
- Feature có thể dùng hợp đồng hoặc chức năng của feature khác khi có nhu cầu thật, ví dụ lesson dùng luyện nghe, notebook dùng trạng thái thẻ ôn. Import trực tiếp từ module sở hữu, không tạo vòng phụ thuộc hoặc chuyển toàn bộ nghiệp vụ vào shared.
- Chỉ chuyển phần sang shared khi có nhiều nơi dùng và có thể mô tả giao diện độc lập. `Pagination`, `useSpeech` và chuyển WAV là các trường hợp hiện có.
- Dùng import tương đối trực tiếp, không thêm `index.ts` xuất lại toàn feature hoặc alias mới chỉ để rút ngắn đường dẫn. Import kiểu dùng `import type` hoặc `type` trong named import.
- ESLint chặn import ngược tầng từ feature/shared. TypeScript kiểm tra đường dẫn sau khi di chuyển.

## CSS và tài nguyên

`app/styles/global.css` sở hữu font local, token, reset và quy tắc nền. Layout ứng dụng có stylesheet trong app; CSS riêng feature ở `styles/`. CSS trình bày dùng chung của danh sách, bộ lọc và control học liệu nằm trong `shared/styles/learning-content.css`; stylesheet cần nó dùng `@import` rõ ràng. Không dựa vào việc một trang khác đã import stylesheet trước đó.

Giữ selector riêng theo phạm vi feature; giữ thứ tự quy tắc và media query khi tách file để bảo toàn cascade. Font trong `public/` tiếp tục dùng URL `/fonts/...`. Tài nguyên import từ TypeScript có thể đặt cạnh module sở hữu khi phát sinh; không tạo thư mục assets rỗng.

## Khi thêm hoặc di chuyển file

1. Chọn module sở hữu theo nghiệp vụ; tìm phần đang có trước khi viết mới.
2. Di chuyển đồng thời import TypeScript, import CSS và liên kết tài liệu. Không để file chuyển tiếp chỉ xuất lại đường dẫn cũ.
3. Bảo toàn route hash, Session/CSRF, feature/role gating, callback, AbortController và vòng đời audio.
4. Chạy `npm run typecheck`, `npm run lint`, `npm run build` tại frontend. Thử các trang ảnh hưởng bằng fixture/mock; không gọi dịch vụ AI trả phí để kiểm tra việc di chuyển file.
5. Cập nhật README và kiến trúc khi thay đổi ranh giới. Báo riêng kiểm chứng tĩnh, browser mock và tích hợp backend thật.

Không đổi API, schema, dependency hoặc cách xác thực chỉ vì sắp xếp thư mục. Xem thêm [quy chuẩn giao diện](interface-design-standard.md) và [kiểm thử](testing.md).

## Kiểm chứng đợt sắp xếp ngày 25/11/2026

- TypeScript `tsc -b --pretty false`, ESLint `--max-warnings 0` và Vite build đã chạy thành công bằng Node đóng gói của môi trường, tương ứng các script trong package.json.
- Kiểm tra 77 file TypeScript/TSX/CSS: không có import tương đối không phân giải được hoặc vòng phụ thuộc module TypeScript.
- Browser dùng API mock: đăng ký hiển thị đúng biểu mẫu, hiện/ẩn mật khẩu, đăng nhập; tải catalog, bài lưu, tình huống, hồ sơ, sổ tay, ôn tập, lịch sử, thống kê, luyện nghe, phát âm, quản lý dữ liệu và tài khoản. Các trang đã thử không phát sinh lỗi JavaScript.
- Thử lỗi 500 và tải lại thành công trên thẻ ôn; 401 chuyển về đăng nhập. Sidebar thu gọn 80px, drawer mobile đóng bằng Escape. Flashcard hai cột ở 1366px và một cột ở 360px, không tràn ngang trong ca đã thử.
- Đã xem ảnh desktop/mobile: `.playwright-mcp/frontend-structure-review-desktop.png` và `.playwright-mcp/frontend-structure-auth-mobile.png` tại gốc repository. Đây là fixture kiểm thử, không phải dữ liệu sản phẩm.

Chưa chạy tích hợp backend thật hoặc kiểm tra ghi âm/provider trong đợt sắp xếp này. Không gọi Gemini, Azure hoặc dịch vụ trả phí.
