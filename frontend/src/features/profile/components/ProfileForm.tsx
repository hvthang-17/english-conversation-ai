import { notifyProfileChanged } from '../../../shared/lib/profile-events'
import { useState, type FormEvent } from 'react'
import { ProfileExtras } from './ProfileExtras'
import { ApiError } from '../../../shared/api/client'
import { isLevel } from '../../../shared/lib/learning-level'
import { saveProfile, type Profile } from '../api/profile-api'
import type { Topic } from '../../scenarios/api/catalog-api'
import { PageHeading } from '../../../shared/ui/PageHeading'
import { Icon } from '../../../shared/ui/Icon'
import '../styles/profile.css'
type Props = { profile: Profile | null; topics: Topic[]; learningAvailable: boolean; onSaved: (profile: Profile) => void; onSessionExpired: () => void }
export function ProfileForm({ profile, topics, learningAvailable, onSaved, onSessionExpired }: Props) {
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
      onSaved(result); notifyProfileChanged(); setMessage('Đã lưu hồ sơ.')
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) onSessionExpired()
      else { setError(e instanceof Error ? e.message : 'Không thể lưu hồ sơ. Hãy thử lại.'); if (e instanceof ApiError) setFields(e.fieldErrors) }
    } finally { setPending(false) }
  }
  function invalid(name: string) { return { 'aria-invalid': Boolean(fields[name]), 'aria-describedby': fields[name] ? `${name}-error` : undefined } }
  return <section className="profile-page" aria-labelledby="profile-title"><PageHeading id="profile-title" title="Hồ sơ học tập" description="Chọn điều bạn muốn luyện và dành một khoảng thời gian cho tiếng Anh." />
    <div className="profile-layout"><form className="profile-form" onSubmit={event => void submit(event)} aria-busy={pending}>
      <header className="profile-section-heading"><Icon name="book" /><div><h2>Việc học của bạn</h2><p>Điều chỉnh để mỗi buổi luyện vừa sức hơn.</p></div></header>
      {message ? <p role="status" className="notice">{message} {learningAvailable ? <a href="#/scenarios">Xem tình huống</a> : null}</p> : null}
      {error ? <p role="alert" className="form-error">{error}</p> : null}
      <fieldset disabled={pending} className="profile-fields"><legend className="sr-only">Thông tin học tập</legend>
        <div className="form-field"><label htmlFor="displayName">Tên hiển thị</label><input id="displayName" name="displayName" autoComplete="nickname" required maxLength={100} defaultValue={profile?.displayName ?? ''} {...invalid('displayName')} />{fields.displayName ? <small id="displayName-error">{fields.displayName}</small> : null}</div>
        <fieldset className="profile-levels" {...invalid('selectedLevel')} aria-describedby={fields.selectedLevel ? 'level-help selectedLevel-error' : 'level-help'}><legend>Trình độ muốn luyện</legend>
          <div className="profile-level-options">{[{ value: 'A2', title: 'Giao tiếp cơ bản', text: 'Tập trao đổi ngắn trong tình huống quen thuộc.' }, { value: 'B1', title: 'Mở rộng trao đổi', text: 'Tập diễn đạt ý kiến và trả lời chi tiết hơn.' }].map(level => <label key={level.value} className="profile-level-choice"><input type="radio" name="selectedLevel" value={level.value} defaultChecked={(profile?.selectedLevel ?? 'A2') === level.value} required /><span><strong>{level.value}</strong><span>{level.title}</span><small>{level.text}</small></span></label>)}</div>
          <p id="level-help" className="profile-help">Đây là mức bạn muốn luyện, không phải kết quả đánh giá. Bạn có thể đổi bất cứ lúc nào.</p>
          {fields.selectedLevel ? <small className="error" id="selectedLevel-error">{fields.selectedLevel}</small> : null}
        </fieldset>
        <div className="form-field"><label htmlFor="learningGoal">Mục tiêu giao tiếp <span className="profile-optional">(tùy chọn)</span></label><textarea id="learningGoal" name="learningGoal" rows={2} maxLength={1000} placeholder="Ví dụ: Tự tin gọi món và hỏi đường khi đi du lịch." defaultValue={profile?.learningGoal ?? ''} {...invalid('learningGoal')} />{fields.learningGoal ? <small id="learningGoal-error">{fields.learningGoal}</small> : null}</div>
        <div className="form-field"><label htmlFor="dailyGoalMinutes">Số phút luyện mỗi ngày <span className="profile-optional">(tùy chọn)</span></label><div className="profile-minutes"><input id="dailyGoalMinutes" name="dailyGoalMinutes" type="number" min={1} max={1440} step={1} defaultValue={profile?.dailyGoalMinutes ?? ''} {...invalid('dailyGoalMinutes')} /><span aria-hidden="true">phút / ngày</span></div><small>Mục tiêu cá nhân; lịch nhắc học được lưu riêng.</small>{fields.dailyGoalMinutes ? <small id="dailyGoalMinutes-error">{fields.dailyGoalMinutes}</small> : null}</div>
        <fieldset className="interest-group"><legend>Chủ đề quan tâm <span className="profile-optional">(tùy chọn)</span></legend>{topics.length ? topics.map(topic => <label key={topic.id} className="interest-choice"><input type="checkbox" name="topicIds" value={topic.id} defaultChecked={profile?.topicIds.includes(topic.id)} /><span>{topic.name}</span></label>) : <p className="profile-help">Chưa có chủ đề đang hoạt động. Bạn vẫn có thể lưu các thông tin còn lại.</p>}{fields.topicIds ? <small role="alert">{fields.topicIds}</small> : null}</fieldset>
        <div className="profile-save-row"><button className="button-primary" type="submit"><Icon name="check" />{pending ? 'Đang lưu…' : 'Lưu hồ sơ'}</button></div>
      </fieldset>
    </form><ProfileExtras avatarUrl={profile?.avatarUrl ?? null} onSessionExpired={onSessionExpired} /></div>
  </section>
}
