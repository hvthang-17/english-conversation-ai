import { useApiQuery } from '../../../../shared/hooks/useApiQuery'
import type { SessionGuard } from '../../../../shared/types/api'
import { Icon } from '../../../../shared/ui/Icon'
import type { AdminSection } from '../../model/navigation'
import '../styles/overview.css'

const learningMetrics = [
  ['learners', 'Tài khoản người học'], ['lockedAccounts', 'Tài khoản đang khóa'],
  ['visibleTopics', 'Chủ đề đang mở'], ['visibleScenarios', 'Tình huống công khai'],
  ['completedPractices', 'Buổi luyện hoàn thành'],
] as const
const aiMetrics = [
  ['aiAttempts24h', 'Tổng số yêu cầu'], ['aiSucceeded24h', 'Thành công'],
  ['aiFailed24h', 'Thất bại'], ['aiUnknown24h', 'Chưa rõ kết quả'],
] as const
type Overview = Record<typeof learningMetrics[number][0] | typeof aiMetrics[number][0], number>

export function AdminOverview({ onSessionExpired, onNavigate }: SessionGuard & { onNavigate: (section: AdminSection) => void }) {
  const { data, error, reload } = useApiQuery<Overview>('admin/overview', onSessionExpired)
  if (error) return <div className="admin-panel"><p role="alert">{error}</p><button onClick={reload}>Tải lại số liệu</button></div>
  if (!data) return <p className="loading-state" role="status">Đang tải số liệu vận hành…</p>
  const render = (entries: readonly (readonly [keyof Overview, string])[]) => <dl className="admin-metrics">
    {entries.map(([key, label]) => <div key={key}><dt>{label}</dt><dd>{data[key].toLocaleString('vi-VN')}</dd></div>)}
  </dl>
  return <div className="admin-overview">
    <section className="admin-panel" aria-labelledby="admin-work-title">
      <div className="admin-panel-heading"><h2 id="admin-work-title">Công việc quản lý</h2></div>
      <div className="admin-work-links">
        <button onClick={() => onNavigate('materials')}><Icon name="book" size={24} /><span><strong>Biên tập học liệu</strong><small>Bài học, từ và mẫu câu, bài nghe, phát âm</small></span><Icon name="chevron" /></button>
        <button onClick={() => onNavigate('scenarios')}><Icon name="dialogue" size={24} /><span><strong>Quản lý tình huống</strong><small>Vai giao tiếp, bối cảnh và chỉ dẫn theo A2–B1</small></span><Icon name="chevron" /></button>
        <button onClick={() => onNavigate('users')}><Icon name="profile" size={24} /><span><strong>Quản lý tài khoản</strong><small>Tìm người dùng và cập nhật quyền truy cập</small></span><Icon name="chevron" /></button>
      </div>
    </section>
    <section className="admin-panel" aria-labelledby="admin-learning-title">
      <div className="admin-panel-heading"><h2 id="admin-learning-title">Hoạt động học tập</h2><button onClick={reload}>Tải lại số liệu</button></div>
      {render(learningMetrics)}
    </section>
    <section className="admin-panel" aria-labelledby="admin-ai-title">
      <div className="admin-panel-heading"><div><h2 id="admin-ai-title">Yêu cầu AI</h2><p>Trong 24 giờ qua</p></div>
        <button onClick={() => onNavigate('ai')}>Xem tác vụ AI</button></div>
      {render(aiMetrics)}
    </section>
  </div>
}
