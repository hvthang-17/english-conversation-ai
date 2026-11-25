import { useRef, useState, type FormEvent } from 'react'
import { reportError } from '../../../../shared/api/errors'
import { useApiQuery } from '../../../../shared/hooks/useApiQuery'
import type { Page, SessionGuard } from '../../../../shared/types/api'
import { Pagination } from '../../../../shared/ui/Pagination'
import { updateAccountAccess } from '../api/accounts-api'
import type { Account } from '../types'
import '../styles/accounts.css'

export function AdminAccounts({ accountId, onSessionExpired }: SessionGuard & { accountId: string }) {
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const [target, setTarget] = useState<Account | null>(null)
  const [changeRole, setChangeRole] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const searchForm = useRef<HTMLFormElement>(null)
  const confirmation = useRef<HTMLDivElement>(null)
  const trigger = useRef<HTMLButtonElement | null>(null)
  const users = useApiQuery<Page<Account>>(
    `admin/users?${new URLSearchParams({ email: query, page: String(page) })}`, onSessionExpired)

  function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const email = String(new FormData(event.currentTarget).get('email') ?? '').trim()
    if (query === email && page === 0) users.reload()
    setQuery(email); setPage(0); setTarget(null); setError(''); setNotice('')
  }

  function select(account: Account, role: boolean, button: HTMLButtonElement) {
    trigger.current = button
    setChangeRole(role); setTarget(account); setError(''); setNotice('')
    requestAnimationFrame(() => confirmation.current?.focus())
  }

  function cancel() { setTarget(null); setError(''); requestAnimationFrame(() => trigger.current?.focus()) }

  async function change() {
    if (!target || busy) return
    setBusy(true); setError(''); setNotice('')
    const status = target.status === 'ACTIVE' ? 'LOCKED' : 'ACTIVE'
    try {
      await updateAccountAccess(target.id, changeRole ? { role: target.role === 'ADMIN' ? 'LEARNER' : 'ADMIN' } : { status })
      setNotice(changeRole ? 'Đã đổi quyền và thu hồi phiên.' : status === 'LOCKED' ? 'Đã khóa tài khoản và thu hồi phiên.' : 'Đã mở tài khoản. Người dùng có thể đăng nhập lại.')
      setTarget(null); users.reload()
      requestAnimationFrame(() => searchForm.current?.querySelector('input')?.focus())
    } catch (failure) { setError(reportError(failure, onSessionExpired)) }
    finally { setBusy(false) }
  }

  return <section className="admin-panel" aria-label="Danh sách tài khoản">
    <form ref={searchForm} className="admin-account-search" onSubmit={search}>
      <div className="form-field"><label htmlFor="admin-email-query">Email hoặc một phần email</label>
        <input type="search" id="admin-email-query" name="email" maxLength={254} placeholder="Ví dụ: learner@example.com" disabled={busy} /></div>
      <button className="primary" disabled={busy}>Tìm tài khoản</button>
      {query ? <button type="button" disabled={busy} onClick={() => { searchForm.current?.reset(); setQuery(''); setPage(0); setTarget(null); setError(''); setNotice('') }}>Xóa tìm kiếm</button> : null}
    </form>
    {notice ? <p className="admin-success" role="status">{notice}</p> : null}
    {target ? <div ref={confirmation} className="admin-confirmation" tabIndex={-1} aria-labelledby="account-confirm-title">
      <h2 id="account-confirm-title">{changeRole ? 'Xác nhận đổi quyền' : target.status === 'ACTIVE' ? 'Xác nhận khóa tài khoản' : 'Xác nhận mở khóa'}</h2>
      <p>{changeRole ? (target.role === 'ADMIN' ? 'Chuyển sang Người học và thu hồi phiên của' : 'Cấp quyền Quản trị viên và thu hồi phiên của') : target.status === 'ACTIVE' ? 'Khóa và thu hồi toàn bộ phiên của' : 'Cho phép đăng nhập lại với'} <strong>{target.email}</strong>?</p>
      {error ? <p role="alert">{error}</p> : null}
      <div className="actions">
        <button className={changeRole || target.status === 'ACTIVE' ? 'button-danger' : 'primary'} disabled={busy} onClick={() => void change()}>
          {busy ? 'Đang cập nhật…' : 'Xác nhận ' + (changeRole ? 'đổi quyền' : target.status === 'ACTIVE' ? 'khóa' : 'mở khóa')}
        </button><button disabled={busy} onClick={cancel}>Hủy</button>
      </div>
    </div> : null}
    {users.error ? <div><p role="alert">{users.error}</p><button onClick={users.reload}>Tải lại tài khoản</button></div>
      : !users.data ? <p className="loading-state" role="status">Đang tải tài khoản…</p> : <>
        <div className="admin-panel-heading"><h2>Tài khoản</h2><p role="status">{users.data.total.toLocaleString('vi-VN')} kết quả{query ? ` cho “${query}”` : ''}</p></div>
        {!users.data.items.length ? <div className="admin-empty"><h3>Không tìm thấy tài khoản</h3><p>Thử một phần email khác hoặc xóa tìm kiếm để xem danh sách.</p></div> :
          <table className="admin-accounts-table">
            <caption className="sr-only">Tài khoản và quyền truy cập</caption>
            <thead><tr><th scope="col">Tài khoản</th><th scope="col">Vai trò</th><th scope="col">Trạng thái</th><th scope="col">Thao tác</th></tr></thead>
            <tbody>{users.data.items.map(account => <tr key={account.id}>
              <th scope="row"><strong>{account.email}</strong><span className="admin-account-id">ID {account.id}</span></th>
              <td data-label="Vai trò">{account.role === 'ADMIN' ? 'Quản trị viên' : 'Người học'}</td>
              <td data-label="Trạng thái"><span className={`status-badge ${account.status === 'ACTIVE' ? 'status-success' : 'status-danger'}`}>{account.status === 'ACTIVE' ? 'Đang hoạt động' : 'Đang khóa'}</span></td>
              <td>{account.id === accountId ? <span className="admin-own-account">Tài khoản của bạn</span> : <div className="admin-row-actions">
                <button disabled={busy || target !== null} onClick={event => select(account, false, event.currentTarget)}>
                  {account.status === 'ACTIVE' ? 'Khóa' : 'Mở khóa'}<span className="sr-only"> {account.email}</span>
                </button><button disabled={busy || target !== null} onClick={event => select(account, true, event.currentTarget)}>
                  Đổi quyền<span className="sr-only"> {account.email}</span>
                </button></div>}</td>
            </tr>)}</tbody>
          </table>}
        <Pagination {...users.data} onPage={next => { setPage(next); setTarget(null) }} disabled={busy || target !== null} />
      </>}
  </section>
}
