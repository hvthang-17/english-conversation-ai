import { useState, type FormEvent } from 'react'
import { ApiError } from '../../../shared/api/client'
import { isLevel } from '../../../shared/lib/learning-level'
import { saveProfile, type Profile } from '../api/profile-api'
import type { Topic } from '../../scenarios/api/catalog-api'
type Props = { profile: Profile | null; topics: Topic[]; onSaved: (profile: Profile) => void; onSessionExpired: () => void }
export function ProfileForm({ profile, topics, onSaved, onSessionExpired }: Props) {
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [fields, setFields] = useState<Record<string, string>>({})
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (pending) return
    const values = new FormData(event.currentTarget)
    const level = values.get('selectedLevel'); if (!isLevel(level)) return
    setPending(true); setError(''); setMessage(''); setFields({})
    try {
      const minutes = String(values.get('dailyGoalMinutes') ?? '')
      const result = await saveProfile({ displayName: String(values.get('displayName') ?? ''), selectedLevel: level, learningGoal: String(values.get('learningGoal') ?? '') || null, dailyGoalMinutes: minutes ? Number(minutes) : null, topicIds: values.getAll('topicIds').map(String) })
      onSaved(result); setMessage('Đã lưu hồ sơ. Bạn có thể chọn bài theo trình độ vừa thiết lập.')
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) onSessionExpired()
      else { setError(e instanceof Error ? e.message : 'Không thể lưu hồ sơ. Hãy thử lại.'); if (e instanceof ApiError) setFields(e.fieldErrors) }
    } finally { setPending(false) }
  }
  function invalid(name: string) { return { 'aria-invalid': Boolean(fields[name]), 'aria-describedby': fields[name] ? `${name}-error` : undefined } }
  return <section aria-labelledby="profile-title"><h2 id="profile-title">Hồ sơ học tập</h2><p>Chọn trình độ bạn muốn luyện. A2/B1 là lựa chọn cá nhân, không phải kết quả đánh giá năng lực.</p>
    {message ? <p role="status" className="notice">{message} <a href="#/scenarios">Xem tình huống</a></p> : null}
    {error ? <p role="alert" className="form-error">{error}</p> : null}
    <form onSubmit={event => void submit(event)} aria-busy={pending}><fieldset disabled={pending} className="profile-fields"><legend className="sr-only">Thông tin học tập</legend>
      <div className="form-field"><label htmlFor="displayName">Tên hiển thị</label><input id="displayName" name="displayName" autoComplete="nickname" required maxLength={100} defaultValue={profile?.displayName ?? ''} {...invalid('displayName')} />{fields.displayName ? <small id="displayName-error">{fields.displayName}</small> : null}</div>
      <div className="form-field"><label htmlFor="selectedLevel">Trình độ muốn luyện</label><select id="selectedLevel" name="selectedLevel" defaultValue={profile?.selectedLevel ?? 'A2'} {...invalid('selectedLevel')}><option value="A2">A2 — Giao tiếp cơ bản</option><option value="B1">B1 — Mở rộng trao đổi</option></select>{fields.selectedLevel ? <small id="selectedLevel-error">{fields.selectedLevel}</small> : null}</div>
      <div className="form-field"><label htmlFor="learningGoal">Mục tiêu giao tiếp (tùy chọn)</label><textarea id="learningGoal" name="learningGoal" rows={3} maxLength={1000} defaultValue={profile?.learningGoal ?? ''} {...invalid('learningGoal')} />{fields.learningGoal ? <small id="learningGoal-error">{fields.learningGoal}</small> : null}</div>
      <div className="form-field"><label htmlFor="dailyGoalMinutes">Mục tiêu phút mỗi ngày (tùy chọn)</label><input id="dailyGoalMinutes" name="dailyGoalMinutes" type="number" min={1} max={1440} step={1} defaultValue={profile?.dailyGoalMinutes ?? ''} {...invalid('dailyGoalMinutes')} /><small>Đây là mục tiêu cá nhân; chưa có nhắc học tự động.</small>{fields.dailyGoalMinutes ? <small id="dailyGoalMinutes-error">{fields.dailyGoalMinutes}</small> : null}</div>
      <fieldset className="interest-group"><legend>Chủ đề quan tâm (có thể bỏ trống)</legend>{topics.length ? topics.map(topic => <label key={topic.id} className="interest-choice"><input type="checkbox" name="topicIds" value={topic.id} defaultChecked={profile?.topicIds.includes(topic.id)} /><span>{topic.name}</span></label>) : <p>Chưa có chủ đề đang hoạt động.</p>}{fields.topicIds ? <small role="alert">{fields.topicIds}</small> : null}</fieldset>
      <button type="submit">{pending ? 'Đang lưu…' : 'Lưu hồ sơ'}</button>
    </fieldset></form>
  </section>
}
