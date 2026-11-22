import { LearningWorkspace } from '../../app/LearningWorkspace'
import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { ApiError, getJson, mutateJson } from '../../shared/api/client'
type User = { id: string; email: string; role: 'LEARNER' | 'ADMIN'; status: 'ACTIVE' | 'LOCKED' }
function userResponse(value: unknown): User {
  const u = value as Partial<User> | null
  if (!u || typeof u.id !== 'string' || typeof u.email !== 'string' || !['LEARNER', 'ADMIN'].includes(u.role ?? '') || !['ACTIVE', 'LOCKED'].includes(u.status ?? '')) throw new Error('Phản hồi tài khoản không hợp lệ.')
  return u as User
}
function route() { const path = window.location.hash.split('?')[0]; return path === '#/register' ? 'register' : path === '#/account' ? 'account' : path === '#/admin' ? 'admin' : path === '#/profile' ? 'profile' : path === '#/scenarios' ? 'scenarios' : path === '#/review' ? 'review' : path === '#/history' ? 'history' : path === '#/stats' ? 'stats' : /^#\/scenarios\/[1-9][0-9]*$/.test(path ?? '') ? 'scenario' : /^#\/practice\/[1-9][0-9]*$/.test(path ?? '') ? 'practice' : 'login' }
export function AuthApp() {
  const [page, setPage] = useState(route)
  const [location, setLocation] = useState(window.location.hash)
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)
  const [pending, setPending] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [fields, setFields] = useState<Record<string, string>>({})
  useEffect(() => {
    const onHash = () => { setLocation(window.location.hash); setPage(route()); setError(''); setFields({}) }
    window.addEventListener('hashchange', onHash)
    const controller = new AbortController()
    getJson('auth/me', controller.signal).then(value => {
      const account = userResponse(value)
      setUser(account)
      if (route() === 'login' || route() === 'register')
        window.location.hash = account.role === 'ADMIN' ? '/admin' : '/scenarios'
    }).catch((e: unknown) => {
      if (controller.signal.aborted) return
      if (!(e instanceof ApiError && e.status === 401)) setError('Không thể kết nối máy chủ. Hãy tải lại trang để thử lại.')
      if (['account', 'admin', 'profile', 'scenarios', 'scenario', 'practice', 'review', 'history', 'stats'].includes(route())) { window.location.hash = '/login'; setMessage('Vui lòng đăng nhập để tiếp tục.') }
    }).finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => { controller.abort(); window.removeEventListener('hashchange', onHash) }
  }, [])
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (pending) return
    const form = event.currentTarget
    const data = new FormData(form)
    const mode = user ? 'password' : page === 'register' ? 'register' : 'login'
    setError(''); setMessage(''); setFields({})
    if (mode !== 'login' && data.get('newPassword') !== data.get('confirmation')) { setFields({ confirmation: 'Mật khẩu xác nhận chưa khớp.' }); return }
    setPending(true)
    try {
      if (mode === 'password') {
        await mutateJson('auth/password', { currentPassword: data.get('currentPassword'), newPassword: data.get('newPassword') })
        setUser(null); window.location.hash = '/login'; setMessage('Đã đổi mật khẩu và kết thúc mọi phiên. Vui lòng đăng nhập lại.')
      } else if (mode === 'register') {
        await mutateJson('auth/register', { email: data.get('email'), password: data.get('newPassword') })
        window.location.hash = '/login'; setMessage('Tạo tài khoản thành công. Bạn có thể đăng nhập.')
      } else {
        const account = userResponse(await mutateJson('auth/login', { email: data.get('email'), password: data.get('password') }))
        setUser(account)
        window.location.hash = account.role === 'ADMIN' ? '/admin' : '/profile'
      }
      form.reset()
    } catch (e) {
      if (e instanceof ApiError) {
        setFields(e.fieldErrors)
        if (e.status === 401 && mode === 'password') { setUser(null); window.location.hash = '/login'; setMessage('Phiên đã hết hạn hoặc bị thu hồi. Vui lòng đăng nhập lại.') }
        else setError(e.message)
      } else setError('Không thể hoàn tất yêu cầu. Kiểm tra kết nối rồi thử lại; nếu đã gửi đăng ký, hãy thử đăng nhập.')
    } finally { setPending(false) }
  }
  async function logout() {
    setPending(true); setError(''); setMessage('')
    try { await mutateJson('auth/logout'); setUser(null); window.location.hash = '/login'; setMessage('Đã đăng xuất khỏi thiết bị này.') }
    catch (e) { if (e instanceof ApiError && e.status === 401) { setUser(null); window.location.hash = '/login'; setMessage('Phiên đã kết thúc. Vui lòng đăng nhập lại.') } else setError(e instanceof Error ? e.message : 'Không thể đăng xuất. Vui lòng thử lại.') }
    finally { setPending(false) }
  }
  async function refresh() {
    setPending(true); setError('')
    try { setUser(userResponse(await getJson('auth/me'))) }
    catch (e) { if (e instanceof ApiError && e.status === 401) { setUser(null); window.location.hash = '/login'; setMessage('Phiên đã hết hạn hoặc bị thu hồi. Vui lòng đăng nhập lại.') } else setError('Không thể tải tài khoản. Vui lòng thử lại.') }
    finally { setPending(false) }
  }
  const onSessionExpired = useCallback(() => { setUser(null); window.location.hash = '/login'; setMessage('Phiên đã hết hạn hoặc bị thu hồi. Vui lòng đăng nhập lại.') }, [])
  if (loading) return <p role="status">Đang kiểm tra phiên đăng nhập…</p>
  if (user && page !== 'account') return <LearningWorkspace key={user.id} page={page} location={location} email={user.email}
    accountId={user.id} isAdmin={user.role === 'ADMIN'} onSessionExpired={onSessionExpired} onLogout={() => void logout()} pending={pending} actionError={error} />
  const registering = page === 'register' && !user
  function field(name: string, label: string, type: string, autocomplete: string, minLength?: number) {
    return <div className="form-field"><label htmlFor={name}>{label}</label><input id={name} name={name} type={type} autoComplete={autocomplete} required maxLength={type === 'email' ? 254 : 128} minLength={minLength} aria-invalid={Boolean(fields[name])} aria-describedby={fields[name] ? `${name}-error` : undefined} />{fields[name] ? <small id={`${name}-error`}>{fields[name]}</small> : null}</div>
  }
  return <section className="auth-panel" aria-labelledby="auth-title" aria-busy={pending}>
    <div><p className="eyebrow">{user ? 'TÀI KHOẢN CỦA BẠN' : 'BẮT ĐẦU HÀNH TRÌNH'}</p><h2 id="auth-title">{user ? 'Chào mừng bạn trở lại.' : registering ? 'Tạo tài khoản' : 'Đăng nhập'}</h2>
    <p>{user ? `Bạn đang đăng nhập với ${user.email}.` : 'Luyện tập đều đặn, tự tin giao tiếp từng ngày.'}</p></div>
    {message ? <p role="status" className="notice">{message}</p> : null}
    {error ? <p role="alert" className="form-error">{error}</p> : null}
    {user ? <div className="account-summary"><p>Vai trò: {user.role === 'ADMIN' ? 'Quản trị viên' : 'Người học'}</p><nav aria-label="Không gian học tập"><a href="#/profile">Hồ sơ học tập</a><a href="#/scenarios">Chọn tình huống</a></nav><div className="actions"><button type="button" disabled={pending} onClick={() => void refresh()}>Kiểm tra phiên</button><button type="button" disabled={pending} onClick={() => void logout()}>Đăng xuất</button></div><h3>Đổi mật khẩu</h3><p>Sau khi đổi, bạn cần đăng nhập lại trên mọi thiết bị.</p></div> : <nav aria-label="Tài khoản"><a href="#/login" aria-current={!registering ? 'page' : undefined}>Đăng nhập</a><a href="#/register" aria-current={registering ? 'page' : undefined}>Đăng ký</a></nav>}
    <form key={user ? 'password' : page} onSubmit={event => void submit(event)}>
      <fieldset disabled={pending}>
      {user ? field('currentPassword', 'Mật khẩu hiện tại', 'password', 'current-password') : field('email', 'Email', 'email', 'email')}
      {user || registering ? <>{field('newPassword', 'Mật khẩu mới (12–128 ký tự)', 'password', 'new-password', 12)}{field('confirmation', 'Nhập lại mật khẩu mới', 'password', 'new-password', 12)}</> : field('password', 'Mật khẩu', 'password', 'current-password')}
      <button className="primary" type="submit">{pending ? 'Đang xử lý…' : user ? 'Đổi mật khẩu' : registering ? 'Tạo tài khoản' : 'Đăng nhập'}</button>
      </fieldset>
    </form>
    <p className="auth-note">{user ? 'Phiên tự hết hạn sau 30 phút không hoạt động.' : 'Chưa hỗ trợ quên mật khẩu hoặc xác minh email trong phiên bản này.'}</p>
  </section>
}
