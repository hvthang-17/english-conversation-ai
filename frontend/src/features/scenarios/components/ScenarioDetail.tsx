import { useEffect, useRef, useState } from 'react'
import { ApiError, mutateJson } from '../../../shared/api/client'
import { isLevel } from '../../../shared/lib/learning-level'
import { getScenario, type ScenarioDetail as Detail } from '../api/catalog-api'
import type { PracticeMode } from '../../conversation/types'
import { PageHeading } from '../../../shared/ui/PageHeading'
import { Icon } from '../../../shared/ui/Icon'
import '../styles/scenarios.css'
export function ScenarioDetail({ location, onSessionExpired, practiceAvailable }: { location: string; onSessionExpired: () => void; practiceAvailable: boolean }) {
  const id = location.split('?')[0]?.split('/')[2] ?? ''
  const requested = new URLSearchParams(location.split('?')[1] ?? '').get('level')
  const level = isLevel(requested) ? requested : 'A2'
  const [data, setData] = useState<Detail | null>(null)
  const [error, setError] = useState('')
  const [starting, setStarting] = useState(false)
  const [startError, setStartError] = useState('')
  const [mode, setMode] = useState<PracticeMode>('TEXT')
  const [roleCode, setRoleCode] = useState('DEFAULT')
  const startKeys = useRef(new Map<string, string>())
  const [retry, setRetry] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    getScenario(id, level, controller.signal).then(setData).catch((e: unknown) => { if (controller.signal.aborted) return; if (e instanceof ApiError && e.status === 401) onSessionExpired(); else setError(e instanceof Error ? e.message : 'Không thể tải bài. Thử tải lại hoặc chọn bài khác.') })
    return () => controller.abort()
  }, [id, level, retry, onSessionExpired])
  async function start() {
    if (starting || !data) return
    const choice = `${mode}:${roleCode}`
    const key = startKeys.current.get(choice) ?? crypto.randomUUID()
    startKeys.current.set(choice, key)
    setStarting(true)
    setStartError('')
    try {
      const result = await mutateJson('practice-sessions', { scenarioId: id, level, mode, roleCode, clientRequestId: key }) as { id?: string }
      if (!result?.id || !/^[1-9][0-9]*$/.test(result.id)) throw new Error('Không nhận được mã buổi luyện.')
      if (window.location.hash === location) window.location.hash = `/practice/${result.id}`
    } catch (failure) {
      if (failure instanceof ApiError && failure.status === 401) onSessionExpired()
      else setStartError(failure instanceof Error ? failure.message : 'Không mở được buổi luyện.')
    } finally { setStarting(false) }
  }
  if (error) return <section role="alert"><h2>Chưa mở được tình huống</h2><p>{error}</p><a href="#/scenarios">Chọn bài khác</a> <button onClick={() => { setError(''); setRetry(r => r + 1) }}>Thử lại</button></section>
  if (!data) return <p role="status">Đang tải bối cảnh bài luyện…</p>
  const selectedRole = data.roles.find(role => role.code === roleCode) ?? data
  return <section className="scenarios-page scenario-detail" aria-labelledby="lesson-title"><a className="back-link" href={`#/scenarios?level=${level}`}><Icon name="back" />Quay lại tình huống</a><p className="scenario-detail-meta">{data.topicName} <span className="level-badge">{level}</span></p><PageHeading id="lesson-title" title={data.title} description={roleCode === 'DEFAULT' ? data.description : undefined} />
    <div className="lesson-layout"><div className="lesson-main"><div className="lesson-context"><h2>Bối cảnh cuộc trò chuyện</h2><p>{selectedRole.contextDescription}</p></div>
    {data.roles.length > 1 ? <fieldset className="practice-mode-options scenario-role-options" disabled={starting}>
      <legend>Chọn vai của bạn</legend>
      {data.roles.map(role => <label key={role.code}>
        <input type="radio" name="practice-role" checked={role.code === roleCode}
          onChange={() => { setRoleCode(role.code); setStartError('') }} />
        <span><strong>{role.learnerRole}</strong><small>AI đóng vai {role.aiRole}</small></span>
      </label>)}
    </fieldset> : null}
    <dl className="role-grid"><div><dt>Vai của bạn</dt><dd>{selectedRole.learnerRole}</dd></div><div><dt>Người đối thoại</dt><dd>{selectedRole.aiRole}</dd></div></dl><div className="lesson-goal"><h2><Icon name="target" />Mục tiêu giao tiếp</h2><p>{selectedRole.communicationGoal}</p></div></div>
    <aside className="lesson-start" aria-labelledby="scenario-start-title" aria-busy={starting}><h2 id="scenario-start-title">Bắt đầu cuộc trò chuyện</h2>{practiceAvailable ? <p className="scenario-start-description">Chọn cách bạn muốn trả lời trong buổi luyện này.</p> : null}
    {practiceAvailable ? <><fieldset className="practice-mode-options" disabled={starting}>
      <legend>Chế độ hội thoại</legend>
      <label><input type="radio" name="practice-mode" checked={mode === 'TEXT'}
        onChange={() => { setMode('TEXT'); setStartError('') }} />
        <span><strong>Chat văn bản</strong><small>Đọc câu AI và trả lời bằng văn bản.</small></span></label>
      <label><input type="radio" name="practice-mode" checked={mode === 'VOICE'}
        onChange={() => { setMode('VOICE'); setStartError('') }} />
        <span><strong>Luyện nghe và nói</strong><small>Nghe câu AI trước, sau đó ghi câu trả lời.</small></span></label>
    </fieldset>
    <button className="button-primary" disabled={starting} onClick={() => void start()}><Icon name={mode === 'VOICE' ? 'microphone' : 'conversation'} />{starting ? 'Đang tạo buổi luyện…' : 'Bắt đầu luyện tập'}</button>
    <p className="hint">Nội dung hội thoại được gửi tới Google Gemini để tạo phản hồi. Hãy dùng thông tin hư cấu khi luyện tập.</p>
    {starting ? <p role="status">Đang tạo buổi và chờ câu mở đầu…</p> : null}
    {startError ? <p role="alert" className="form-error">{startError}</p> : null}</> : null}
    {!practiceAvailable ? <p className="notice">Luyện hội thoại hiện chưa khả dụng. Bạn vẫn có thể đọc bối cảnh và mục tiêu của tình huống.</p> : null}
    </aside></div>
  </section>
}
