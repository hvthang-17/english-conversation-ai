import { useState, type FormEvent } from 'react'
import { mutateJson } from '../../shared/api/client'
import { Pagination } from '../learning/Pagination'
import { reportError, useLearningData, type Page, type SessionGuard } from '../learning/learningApi'
import { CatalogAdmin } from './CatalogAdmin'
import './admin.css'

type Account = { id: string; email: string; role: 'LEARNER' | 'ADMIN'; status: 'ACTIVE' | 'LOCKED' }
type Overview = Record<typeof metrics[number][0], number>
const metrics = [
  ['learners', 'Tài khoản người học'],
  ['lockedAccounts', 'Tài khoản đang khóa'],
  ['visibleTopics', 'Chủ đề đang mở'],
  ['visibleScenarios', 'Tình huống công khai'],
  ['completedPractices', 'Buổi luyện hoàn thành'],
  ['aiAttempts24h', 'Lần gọi AI'],
  ['aiSucceeded24h', 'Thành công'],
  ['aiFailed24h', 'Thất bại'],
  ['aiUnknown24h', 'Chưa rõ kết quả'],
] as const

export function AdminWorkspace({ accountId, onSessionExpired }: SessionGuard & { accountId: string }) {
  const [section, setSection] = useState<'overview' | 'users' | 'catalog'>('overview')
  return <section aria-labelledby="admin-title" className="admin-workspace">
    <h2 id="admin-title">Quản trị hệ thống</h2>
    <div className="actions" role="group" aria-label="Mục quản trị">
      <button type="button" aria-pressed={section === 'overview'} onClick={() => setSection('overview')}>Tổng quan</button>
      <button type="button" aria-pressed={section === 'users'} onClick={() => setSection('users')}>Tài khoản</button>
      <button type="button" aria-pressed={section === 'catalog'} onClick={() => setSection('catalog')}>Nội dung luyện tập</button>
    </div>
    {section === 'overview' ? <AdminMetrics onSessionExpired={onSessionExpired} /> : null}
    {section === 'users' ? <AdminAccounts accountId={accountId} onSessionExpired={onSessionExpired} /> : null}
    {section === 'catalog' ? <CatalogAdmin onSessionExpired={onSessionExpired} /> : null}
  </section>
}

function AdminMetrics({ onSessionExpired }: SessionGuard) {
  const { data, error, reload } = useLearningData<Overview>('admin/overview', onSessionExpired)
  if (error) return <div><p role="alert">{error}</p><button onClick={reload}>Tải lại số liệu</button></div>
  if (!data) return <p role="status">Đang tải số liệu vận hành…</p>
  const render = (entries: typeof metrics[number][]) => <dl className="admin-metrics">
    {entries.map(([key, label]) => <div key={key}><dt>{label}</dt><dd>{data[key].toLocaleString('vi-VN')}</dd></div>)}
  </dl>
  return <section>
    <h3>Hoạt động hệ thống</h3>
    {render(metrics.slice(0, 5))}
    <h3>Yêu cầu AI trong 24 giờ qua</h3>
    {render(metrics.slice(5))}
    <button onClick={reload}>Tải lại số liệu</button>
  </section>
}

function AdminAccounts({ accountId, onSessionExpired }: SessionGuard & { accountId: string }) {
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const [target, setTarget] = useState<Account | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const users = useLearningData<Page<Account>>(
    `admin/users?${new URLSearchParams({ email: query, page: String(page) })}`, onSessionExpired)

  function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const email = String(new FormData(event.currentTarget).get('email') ?? '').trim()
    if (query === email && page === 0) users.reload()
    setQuery(email); setPage(0); setTarget(null); setError(''); setNotice('')
  }

  async function change() {
    if (!target || busy) return
    setBusy(true); setError(''); setNotice('')
    const status = target.status === 'ACTIVE' ? 'LOCKED' : 'ACTIVE'
    try {
      await mutateJson(`admin/users/${target.id}/access`, { status }, 'PATCH')
      setNotice(status === 'LOCKED' ? 'Đã khóa tài khoản và thu hồi phiên.' : 'Đã mở tài khoản. Người dùng có thể đăng nhập lại.')
      setTarget(null); users.reload()
    } catch (failure) { setError(reportError(failure, onSessionExpired)) }
    finally { setBusy(false) }
  }

  return <section aria-labelledby="admin-users">
    <h3 id="admin-users">Tìm tài khoản</h3>
    <form onSubmit={search}>
      <div className="form-field"><label htmlFor="admin-email-query">Email hoặc một phần email</label>
        <input type="search" id="admin-email-query" name="email" maxLength={254} /></div>
      <button disabled={busy}>Tìm tài khoản</button>
    </form>
    {notice ? <p role="status">{notice}</p> : null}
    {error ? <p role="alert">{error}</p> : null}
    {target ? <div className="notice">
      <p>{target.status === 'ACTIVE' ? 'Khóa và thu hồi toàn bộ phiên của' : 'Cho phép đăng nhập lại với'} <strong>{target.email}</strong>?</p>
      <div className="actions">
        <button disabled={busy} onClick={() => void change()}>Xác nhận {target.status === 'ACTIVE' ? 'khóa' : 'mở'}</button>
        <button disabled={busy} onClick={() => { setTarget(null); setError('') }}>Hủy</button>
      </div>
    </div> : null}
    {users.error ? <div><p role="alert">{users.error}</p><button onClick={users.reload}>Tải lại</button></div> :
      !users.data ? <p role="status">Đang tải tài khoản…</p> : <>
        {!users.data.items.length ? <p>Không tìm thấy tài khoản.</p> : null}
        <ul className="learning-list">{users.data.items.map(account => <li key={account.id}>
          <strong>{account.email}</strong>
          <p>{account.role === 'ADMIN' ? 'Quản trị viên' : 'Người học'} · {account.status === 'ACTIVE' ? 'Đang hoạt động' : 'Đang khóa'}</p>
          {account.id === accountId ? <span>Tài khoản của bạn</span> :
            <button disabled={busy} onClick={() => { setTarget(account); setError(''); setNotice('') }}>
              {account.status === 'ACTIVE' ? 'Khóa' : 'Mở khóa'}<span className="sr-only"> {account.email}</span>
            </button>}
        </li>)}</ul>
        <Pagination {...users.data} onPage={next => { setPage(next); setTarget(null) }} disabled={busy} />
      </>}
  </section>
}
