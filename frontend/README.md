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
    │   └── styles/global.css
    ├── features/
    │   ├── system/{api,components}/
    │   ├── auth/
    │   ├── profile/
    │   ├── scenarios/
    │   ├── conversation/
    │   ├── feedback/
    │   ├── review/
    │   ├── history/
    │   ├── statistics/
    │   └── admin/
    └── shared/
        ├── api/client.ts
        ├── ui/
        └── lib/
```

Feature chưa triển khai chỉ có README ghi trách nhiệm. Thêm api/, components/, hooks/, types.ts khi có nhu cầu thực; không tạo component hoặc API giả để lấp chỗ trống. app/ sở hữu ghép màn hình và style; shared/ chỉ chứa phần dùng chung.

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

Trang hiện tại có đăng ký/login/đổi mật khẩu/logout và current user qua API thật, route hash /login, /register, /account. API client lấy CSRF mới trước mỗi mutation, xử lý lỗi JSON; không lưu session trong browser storage. Chưa có bài luyện. Chưa cài UI kit, Tailwind, thư viện router hoặc framework test frontend; xem [kiến trúc](../docs/architecture/frontend.md).

Giai đoạn 2 có ProfileForm, ScenarioList/Detail và LearningWorkspace; route #/profile, #/scenarios, #/scenarios/{id}?level=A2. API GET topic/list/detail và PUT profile/me có Session/CSRF; chưa tạo buổi khi chọn bài. Xem [P2](../docs/development/phase2/README.md).
