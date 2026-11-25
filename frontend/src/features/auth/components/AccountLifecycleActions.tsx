import { useState, type FormEvent } from 'react'
import { AuthField } from './AuthField'
import { ApiError, mutateJson } from '../../../shared/api/client'

export function AccountLifecycleActions({ onDeleted, onSessionExpired }: { onDeleted: () => void; onSessionExpired: () => void }) {
  const [pending, setPending] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [error, setError] = useState('')
  async function remove(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (pending) return
    const values = new FormData(event.currentTarget)
    setPending(true); setError('')
    try { await mutateJson('auth/account', { currentPassword: values.get('deletePassword'), confirmation: values.get('confirmation') }, 'DELETE'); onDeleted() }
    catch (failure) { if (failure instanceof ApiError && failure.status === 401) onSessionExpired(); else setError(failure instanceof Error ? failure.message : 'Chưa thể xóa tài khoản.') }
    finally { setPending(false) }
  }
  return <div className="account-lifecycle" aria-busy={pending}>
    {error ? <p role="alert" className="form-error">{error}</p> : null}
    <section className="auth-danger-zone" aria-labelledby="delete-account-title"><h2 id="delete-account-title">Xóa tài khoản</h2><p>Xóa tài khoản, hồ sơ, ảnh đại diện, lịch học và mọi dữ liệu luyện tập. Thao tác không thể hoàn tác.</p>
      {!deleting ? <button type="button" className="button-danger" aria-expanded={deleting} onClick={() => setDeleting(true)}>Tôi muốn xóa tài khoản</button> : <form onSubmit={event => void remove(event)}><fieldset disabled={pending}><p>Sau xác nhận, mọi phiên sẽ kết thúc và email nhắc học bị hủy. Hệ thống tiếp tục dọn tệp; bản sao lưu cũ có thể còn tối đa 7 ngày.</p><AuthField name="deletePassword" label="Mật khẩu hiện tại" autoComplete="current-password" /><div className="form-field"><label htmlFor="delete-confirmation">Nhập DELETE để xác nhận</label><input id="delete-confirmation" name="confirmation" required pattern="DELETE" autoComplete="off" /></div><div className="actions"><button className="button-danger" type="submit">{pending ? 'Đang xử lý…' : 'Xác nhận xóa vĩnh viễn'}</button><button type="button" onClick={() => setDeleting(false)}>Hủy</button></div></fieldset></form>}
    </section>
  </div>
}
