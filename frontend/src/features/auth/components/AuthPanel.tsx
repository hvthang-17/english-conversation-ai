import type { FormEvent } from 'react'
import type { User } from '../model/user'
import { AuthField } from './AuthField'
import { AccountLifecycleActions } from './AccountLifecycleActions'
import '../styles/auth.css'

type Props = {
  page: string
  user: User | null
  pending: boolean
  message: string
  error: string
  fields: Record<string, string>
  submit: (event: FormEvent<HTMLFormElement>) => Promise<void>
  refresh: () => Promise<void>
  onSessionExpired: () => void
  onDeleted: () => void
}

export function AuthPanel({ page, user, pending, message, error, fields, submit, refresh, onSessionExpired, onDeleted }: Props) {
  const registering = page === 'register' && !user
  function field(name: string, label: string, type: 'email' | 'password', autocomplete: string, minLength?: number) {
    return <AuthField name={name} label={label} type={type} autoComplete={autocomplete} minLength={minLength} error={fields[name]} hint={name === 'newPassword' ? 'Từ 12 đến 128 ký tự.' : undefined} />
  }
  return <section className={`auth-panel${user ? ' account-panel' : ''}`} aria-labelledby="auth-title" aria-busy={pending}>
    <div className="auth-heading">{user ? <h1 id="auth-title">Tài khoản của bạn</h1> : <h1 id="auth-title" tabIndex={-1}>{registering ? 'Tạo tài khoản' : 'Chào mừng bạn trở lại'}</h1>}
    <p>{user ? user.email : registering ? 'Chọn bài học và bắt đầu luyện giao tiếp.' : 'Đăng nhập để tiếp tục luyện tập.'}</p></div>
    {message ? <p role="status" className="notice">{message}</p> : null}
    {error ? <p role="alert" className="form-error">{error}</p> : null}
    {user ? <div className="account-summary"><p className="status-badge">{user.role === 'ADMIN' ? 'Quản trị viên' : 'Người học'}</p><div className="actions"><button className="button-secondary" type="button" disabled={pending} onClick={() => void refresh()}>Kiểm tra phiên</button></div><h2>Đổi mật khẩu</h2><p>Sau khi đổi, bạn cần đăng nhập lại trên mọi thiết bị.</p></div> : <nav className="segmented-nav" aria-label="Tài khoản"><a href="#/login" aria-current={!registering ? 'page' : undefined}>Đăng nhập</a><a href="#/register" aria-current={registering ? 'page' : undefined}>Đăng ký</a></nav>}
    <form className="auth-form" key={user ? 'password' : page} onSubmit={event => void submit(event)}>
      <fieldset disabled={pending}>
      {user ? field('currentPassword', 'Mật khẩu hiện tại', 'password', 'current-password') : field('email', 'Email', 'email', 'email')}
      {user || registering ? <>{field('newPassword', registering ? 'Mật khẩu' : 'Mật khẩu mới', 'password', 'new-password', 12)}{field('confirmation', 'Nhập lại mật khẩu mới', 'password', 'new-password', 12)}</> : field('password', 'Mật khẩu', 'password', 'current-password')}
      <button className="primary auth-submit" type="submit">{pending ? 'Đang xử lý…' : user ? 'Đổi mật khẩu' : registering ? 'Tạo tài khoản' : 'Đăng nhập'}</button>
      </fieldset>
    </form>
    {user ? <AccountLifecycleActions onSessionExpired={onSessionExpired} onDeleted={onDeleted} /> : !registering ? <p className="auth-help"><a href="#/forgot-password">Quên mật khẩu?</a></p> : null}
  </section>
}
