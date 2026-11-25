export const sections = {
  overview: { title: 'Tổng quan quản trị', label: 'Tổng quan', icon: 'chart', description: 'Theo dõi hoạt động học tập và chọn công việc cần xử lý.' },
  users: { title: 'Quản lý tài khoản', label: 'Tài khoản', icon: 'profile', description: 'Tìm người dùng, quản lý quyền truy cập và trạng thái tài khoản.' },
  topics: { title: 'Quản lý chủ đề', label: 'Chủ đề', icon: 'topics', description: 'Thêm, sửa, công bố, ẩn và xóa chủ đề nháp.' },
  materials: { title: 'Quản lý học liệu', label: 'Học liệu', icon: 'book', description: 'Biên tập bài học và nội dung luyện giao tiếp A2–B1.' },
  scenarios: { title: 'Quản lý tình huống', label: 'Tình huống', icon: 'dialogue', description: 'Chọn chủ đề để quản lý vai, bối cảnh và mục tiêu giao tiếp.' },
  audit: { title: 'Nhật ký quản trị', label: 'Nhật ký quản trị', icon: 'shield', description: 'Đối chiếu các thay đổi và tài khoản thực hiện.' },
  ai: { title: 'Tác vụ AI', label: 'Tác vụ AI', icon: 'history', description: 'Theo dõi trạng thái, nhà cung cấp và thời gian xử lý tác vụ.' },
  tickets: { title: 'Yêu cầu hỗ trợ', label: 'Yêu cầu hỗ trợ', icon: 'conversation', description: 'Chức năng dự kiến.' },
  announcements: { title: 'Thông báo quản trị', label: 'Thông báo quản trị', icon: 'book', description: 'Chức năng dự kiến.' },
  notifications: { title: 'Thông báo người dùng', label: 'Thông báo người dùng', icon: 'conversation', description: 'Chức năng dự kiến.' },
} as const

export type AdminSection = keyof typeof sections

export const navigation = [
  { label: 'Điều hành', keys: ['overview', 'users'] },
  { label: 'Nội dung học tập', keys: ['topics', 'materials', 'scenarios'] },
  { label: 'Giám sát', keys: ['audit', 'ai'] },
  { label: 'Đang phát triển', keys: ['tickets', 'announcements', 'notifications'] },
] as const
