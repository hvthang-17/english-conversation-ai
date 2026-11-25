# Frontend

React 19.3.1, TypeScript 5.9.3, Vite 8.3.8; npm và package-lock.json. Node 24.19+ trong nhánh 24, có .nvmrc. TypeScript chưa nâng lên 7 vì typescript-eslint được chọn chỉ hỗ trợ dưới 6.1.

## Cấu trúc

```text
frontend/
├── package.json / package-lock.json
├── index.html
├── vite.config.ts
├── eslint.config.js
├── tsconfig*.json
├── .env.example
└── src/
    ├── main.tsx
    ├── app/
    │   ├── App.tsx
    │   ├── layouts/
    │   ├── routing/routes.ts
    │   ├── workspace/LearningWorkspace.tsx
    │   └── styles/
    ├── features/
    │   ├── system/{api,components}/
    │   ├── auth/
    │   ├── profile/
    │   ├── scenarios/
    │   ├── conversation/
    │   ├── feedback/
    │   ├── learning/
    │   ├── lessons/
    │   ├── listening/
    │   ├── pronunciation/
    │   ├── review/
    │   ├── history/
    │   ├── statistics/
    │   └── admin/
    └── shared/
        ├── api/client.ts
        ├── api/errors.ts
        ├── hooks/
        ├── types/
        ├── styles/
        ├── ui/
        └── lib/
```

Mỗi feature chia `api/`, `components/`, `hooks/`, `model/`, `styles/`, `types.ts` theo nhu cầu thực; không tạo thư mục rỗng hoặc API giả. `app/` sở hữu phiên, điều phối trang và layout; `shared/` chỉ chứa phần dùng chung. Feature không import app; shared không import feature/app, được kiểm tra bằng ESLint. Xem [quy tắc cấu trúc frontend](../docs/development/frontend-structure.md).

## Lệnh tại frontend/

```powershell
npm ci
npm run dev
npm run typecheck
npm run lint
npm run build
npm run preview
```

Dev: http://127.0.0.1:5173; preview build: http://127.0.0.1:4173. Vite dev proxy `/api` tới http://localhost:8080. Preview không có proxy backend, vì vậy trạng thái kết nối có thể lỗi nếu chưa có reverse proxy chung; không coi preview là cấu hình production.

Có thể sao chép .env.example thành .env.local trong frontend để đổi VITE_API_BASE_URL hoặc DEV_API_TARGET. VITE_* là công khai; không đặt khóa Gemini vào đây. Root .env dành cho Compose, không tự nạp vào Vite frontend.

Ứng dụng có đăng ký/login/đổi mật khẩu/logout và current user qua API, route hash /login, /register, /account. Các feature bài học, hội thoại, luyện nghe và phát âm được ghép theo capability từ backend. API client lấy CSRF mới trước mỗi mutation, xử lý lỗi JSON; không lưu session trong browser storage. Chưa cài UI kit, Tailwind, thư viện router hoặc framework test frontend; xem [kiến trúc](../docs/architecture/frontend.md).

Giai đoạn 2 có ProfileForm, ScenarioList/Detail và LearningWorkspace; route #/profile, #/scenarios, #/scenarios/{id}?level=A2. API GET topic/list/detail và PUT profile/me có Session/CSRF; chưa tạo buổi khi chọn bài. Xem [Kế hoạch G2](../docs/development/full-system-implementation-plan.md#g2--quản-lý-và-khám-phá-nội-dung).

Ở G3, mở `#/lessons/{id}` để học từ, mẫu câu và hội thoại mẫu. Trang `#/notebook` dùng flashcard, quiz chọn nghĩa và lịch sử ôn. Hội thoại đọc bằng giọng tiếng Anh cục bộ của trình duyệt. [Nghiệm thu G3](../docs/development/acceptance/g3-learning-acceptance.md).
