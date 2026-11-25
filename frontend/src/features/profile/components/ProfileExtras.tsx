import { notifyProfileChanged } from '../../../shared/lib/profile-events'
import { useEffect, useState, type FormEvent } from 'react'
import { Icon } from '../../../shared/ui/Icon'
import '../styles/profile.css'
import { ApiError, getJson, mutateJson, uploadForm } from '../../../shared/api/client'

type Schedule = { days: number[]; localTime: string; timeZone: string; enabled: boolean; emailReminderEnabled: boolean; emailReminderAvailable: boolean; nextOccurrenceAt: string | null }
function schedule(value: unknown): Schedule | null {
  if (value === null) return null
  const s = value as Partial<Schedule>
  if (!s || !Array.isArray(s.days) || !s.days.every(d => Number.isInteger(d) && d >= 1 && d <= 7) || typeof s.localTime !== 'string' || typeof s.timeZone !== 'string' || typeof s.enabled !== 'boolean' || typeof s.emailReminderEnabled !== 'boolean' || typeof s.emailReminderAvailable !== 'boolean' || !(s.nextOccurrenceAt === null || typeof s.nextOccurrenceAt === 'string')) throw new Error('Dữ liệu lịch học không hợp lệ.')
  return s as Schedule
}
export function ProfileExtras({ avatarUrl, onSessionExpired }: { avatarUrl: string | null; onSessionExpired: () => void }) {
  const [revision, setRevision] = useState(0)
  const [hasAvatar, setHasAvatar] = useState(Boolean(avatarUrl))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  function report(failure: unknown) { if (failure instanceof ApiError && failure.status === 401) onSessionExpired(); else setError(failure instanceof Error ? failure.message : 'Không thể lưu thay đổi.') }
  async function avatar(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (busy) return
    const form = event.currentTarget; const data = new FormData(form); const file = data.get('file')
    if (!(file instanceof File) || !file.size || file.size > 2 * 1024 * 1024) { setError('Chọn ảnh tối đa 2 MiB.'); return }
    setBusy(true); setError(''); setNotice('')
    try { await uploadForm('profile/me/avatar', data); notifyProfileChanged(); setHasAvatar(true); setRevision(r => r + 1); form.reset(); setNotice('Đã cập nhật ảnh đại diện.') }
    catch (failure) { report(failure) } finally { setBusy(false) }
  }
  async function removeAvatar() {
    setBusy(true); setError(''); setNotice('')
    try { await mutateJson('profile/me/avatar', undefined, 'DELETE'); notifyProfileChanged(); setHasAvatar(false); setNotice('Đã xóa ảnh đại diện.') }
    catch (failure) { report(failure) } finally { setBusy(false) }
  }
  return <div className="profile-extras">
    <section className="profile-extra-card" aria-labelledby="avatar-title" aria-busy={busy}>
      <header className="profile-section-heading"><Icon name="profile" /><div><h2 id="avatar-title">Ảnh đại diện</h2><p>Một hình ảnh quen thuộc cho góc học của bạn.</p></div></header>
      <div className="profile-avatar-row">{hasAvatar ? <img className="profile-avatar" src={`/api/v1/profile/me/avatar?v=${revision}`} alt="Ảnh đại diện của bạn" width={72} height={72} /> : <span className="profile-avatar-placeholder" aria-hidden="true"><Icon name="profile" size={32} /></span>}<p id="avatar-help" className="profile-help">JPEG, PNG hoặc WebP. Tối đa 2 MiB và 4096 × 4096 pixel. Ảnh được thu nhỏ và bỏ metadata.</p></div>
      <form onSubmit={event => void avatar(event)}><fieldset disabled={busy}><div className="form-field"><label htmlFor="avatar-file">Chọn ảnh mới</label><input id="avatar-file" name="file" type="file" aria-describedby="avatar-help" accept="image/jpeg,image/png,image/webp" required /></div><div className="actions"><button className="button-primary" type="submit">{busy ? 'Đang lưu…' : 'Lưu ảnh'}</button>{hasAvatar ? <button className="button-quiet" type="button" onClick={() => void removeAvatar()}>Xóa ảnh</button> : null}</div></fieldset></form>
      {error ? <p role="alert">{error}</p> : null}{notice ? <p role="status" className="notice">{notice}</p> : null}
    </section>
    <ScheduleForm onSessionExpired={onSessionExpired} />
  </div>
}
function ScheduleForm({ onSessionExpired }: { onSessionExpired: () => void }) {
  const [data, setData] = useState<Schedule | null>(null)
  const [loaded, setLoaded] = useState(false)
  const [retry, setRetry] = useState(0)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  useEffect(() => {
    const controller = new AbortController()
    getJson('profile/me/practice-schedule', controller.signal).then(value => { setData(schedule(value)); setLoaded(true) }).catch((failure: unknown) => { if (controller.signal.aborted) return; if (failure instanceof ApiError && failure.status === 401) onSessionExpired(); else setError(failure instanceof Error ? failure.message : 'Không thể tải lịch.') })
    return () => controller.abort()
  }, [retry, onSessionExpired])
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (busy) return
    const values = new FormData(event.currentTarget)
    setBusy(true); setError(''); setNotice('')
    try {
      const result = schedule(await mutateJson('profile/me/practice-schedule', { days: values.getAll('days').map(Number), localTime: values.get('localTime'), timeZone: values.get('timeZone'), enabled: values.has('enabled'), emailReminderEnabled: values.has('emailReminderEnabled') }, 'PUT'))
      setData(result); setNotice('Đã lưu lịch học dự kiến. Lịch không tự tạo buổi luyện.')
    } catch (failure) { if (failure instanceof ApiError && failure.status === 401) onSessionExpired(); else setError(failure instanceof Error ? failure.message : 'Không thể lưu lịch.') }
    finally { setBusy(false) }
  }
  return <section className="profile-extra-card" aria-labelledby="schedule-title" aria-busy={busy}><header className="profile-section-heading"><Icon name="history" /><div><h2 id="schedule-title">Lịch luyện tập</h2><p>Dành một khoảng thời gian cho tiếng Anh mỗi tuần.</p></div></header>
    {error ? <p role="alert">{error}</p> : null}{!loaded ? <>{!error ? <p role="status" className="profile-help">Đang tải lịch…</p> : null}{error ? <button onClick={() => { setError(''); setRetry(r => r + 1) }}>Thử tải lại</button> : null}</> : <>
      {notice ? <p role="status" className="notice">{notice}</p> : null}
      <form key={retry} onSubmit={event => void save(event)}><fieldset disabled={busy}><legend className="sr-only">Thiết lập lịch</legend>
        <fieldset className="schedule-days"><legend>Ngày luyện trong tuần</legend>{['Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy', 'Chủ nhật'].map((day, index) => <label key={day}><input name="days" type="checkbox" value={index + 1} defaultChecked={data?.days.includes(index + 1)} /><span className="sr-only">{day}</span><span aria-hidden="true">{index === 6 ? 'CN' : `T${index + 2}`}</span></label>)}</fieldset>
        <div className="form-field"><label htmlFor="schedule-time">Giờ bắt đầu</label><input id="schedule-time" name="localTime" type="time" required defaultValue={data?.localTime ?? '20:00'} /></div>
        <div className="form-field"><label htmlFor="schedule-zone">Múi giờ</label><input id="schedule-zone" name="timeZone" list="schedule-zones" maxLength={64} required defaultValue={data?.timeZone ?? 'Asia/Ho_Chi_Minh'} /><datalist id="schedule-zones"><option value="Asia/Ho_Chi_Minh" /><option value="Asia/Bangkok" /><option value="Asia/Tokyo" /><option value="Europe/London" /><option value="America/New_York" /></datalist></div>
        <label className="profile-setting"><input name="enabled" type="checkbox" defaultChecked={data?.enabled ?? true} />Bật lịch luyện tập</label>
        <label className="profile-setting"><input name="emailReminderEnabled" type="checkbox" defaultChecked={data?.emailReminderEnabled ?? false} />Nhắc tôi qua email tài khoản khi đến giờ</label>
        <p className="profile-help">Lịch không tự tạo buổi luyện. Email chỉ được gửi khi dịch vụ nhắc học hoạt động; bạn có thể tắt bất cứ lúc nào.</p>
        {data && !data.emailReminderAvailable ? <p className="notice">Bạn có thể lưu tùy chọn nhắc học. Việc gửi thư sẽ hoạt động khi dịch vụ nhắc học được bật.</p> : null}
        <button type="submit" className="button-primary">{busy ? 'Đang lưu…' : 'Lưu lịch dự kiến'}</button>
      </fieldset></form>
      {data?.nextOccurrenceAt ? <p className="profile-next-session">Lần luyện dự kiến tiếp theo: <time dateTime={data.nextOccurrenceAt}>{new Intl.DateTimeFormat('vi-VN', { dateStyle: 'full', timeStyle: 'short', timeZone: data.timeZone }).format(new Date(data.nextOccurrenceAt))}</time> ({data.timeZone}).</p> : null}
    </>}
  </section>
}
