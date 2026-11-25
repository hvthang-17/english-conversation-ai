import { useEffect, useState, type FormEvent } from 'react'
import { AuthField } from './AuthField'
import { Icon } from '../../../shared/ui/Icon'
import { ApiError, mutateJson } from '../../../shared/api/client'

export function RecoveryPanel({ mode, onReset }: { mode: 'forgot-password' | 'reset-password'; onReset: () => void }) {
  const [token] = useState(() => {
    const params = new URLSearchParams(window.location.hash.split('?')[1] ?? '')
    const value = params.get('token') ?? ''
    return value
  })
  useEffect(() => { if (token) window.history.replaceState(null, '', `${window.location.pathname}${window.location.search}#/${mode}`) }, [token, mode])
  const [pending, setPending] = useState(false)
  const [done, setDone] = useState(false)
  const [error, setError] = useState('')
  const [fields, setFields] = useState<Record<string, string>>({})
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (pending || done) return
    const values = new FormData(event.currentTarget)
    if (mode === 'reset-password' && values.get('newPassword') !== values.get('confirmation')) { setFields({ confirmation: 'Mật khẩu xác nhận chưa khớp.' }); document.getElementById('confirmation')?.focus(); return }
    setPending(true); setError(''); setFields({})
    try {
      if (mode === 'forgot-password') await mutateJson('auth/password-reset/request', { email: values.get('email') })
      else await mutateJson('auth/password-reset/confirm', { token, newPassword: values.get('newPassword') })
      setDone(true)
      if (mode === 'reset-password') onReset()
    } catch (failure) { if (failure instanceof ApiError) setFields(failure.fieldErrors); setError(failure instanceof Error ? failure.message : 'Không thể xử lý liên kết. Hãy thử lại.') }
    finally { setPending(false) }
  }
  const title = mode === 'forgot-password' ? 'Quên mật khẩu' : 'Đặt lại mật khẩu'
  return <section className="auth-panel" aria-labelledby="auth-title" aria-busy={pending}>
    <div className="auth-heading"><span className="auth-recovery-icon"><Icon name="shield" /></span><h1 id="auth-title" tabIndex={-1}>{done ? mode === 'forgot-password' ? 'Kiểm tra hộp thư của bạn' : 'Mật khẩu đã được đổi' : title}</h1><p>{done ? mode === 'forgot-password' ? 'Mở liên kết trong email để chọn mật khẩu mới.' : 'Đăng nhập bằng mật khẩu mới để tiếp tục luyện tập.' : mode === 'forgot-password' ? 'Nhập email bạn đã dùng để đăng ký. Chúng tôi sẽ hướng dẫn bạn đặt lại mật khẩu.' : 'Chọn mật khẩu mới để tiếp tục luyện tập.'}</p></div>
    {error ? <p role="alert" className="form-error">{error}</p> : null}
    {done ? <p role="status" className="notice">{mode === 'forgot-password' ? 'Nếu tài khoản đủ điều kiện, hệ thống sẽ gửi liên kết đặt lại mật khẩu. Hãy kiểm tra hộp thư đến và thư rác.' : 'Đã đổi mật khẩu và kết thúc mọi phiên. Vui lòng đăng nhập lại.'}</p> : mode !== 'forgot-password' && !token ? <p role="alert">Liên kết thiếu mã xác nhận. Hãy yêu cầu liên kết mới.</p> : <form className="auth-form" onSubmit={event => void submit(event)}><fieldset disabled={pending}>
      {mode === 'forgot-password' ? <AuthField name="email" label="Email tài khoản" type="email" autoComplete="email" error={fields.email} /> : <>
        <AuthField name="newPassword" label="Mật khẩu mới" autoComplete="new-password" minLength={12} error={fields.newPassword} hint="Từ 12 đến 128 ký tự. Tránh dùng lại mật khẩu ở nơi khác." />
        <AuthField name="confirmation" label="Nhập lại mật khẩu mới" autoComplete="new-password" minLength={12} error={fields.confirmation} />
      </>}
      <button className="button-primary auth-submit" type="submit">{pending ? 'Đang xử lý…' : mode === 'forgot-password' ? 'Gửi liên kết khôi phục' : 'Đặt lại mật khẩu'}</button>
    </fieldset></form>}
    <nav className="auth-recovery-links" aria-label="Khôi phục tài khoản"><a className={done && mode === 'reset-password' ? 'button-link button-primary' : 'back-link'} href="#/login"><Icon name="back" />Về đăng nhập</a>{mode === 'reset-password' ? <a href="#/forgot-password">Yêu cầu liên kết mới</a> : null}</nav>
  </section>
}
