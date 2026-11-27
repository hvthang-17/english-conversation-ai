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
  const selectedRole = data?.roles.find(role => role.code === roleCode) ?? data?.roles[0]
  const startKeys = useRef(new Map<string, string>())
  const [retry, setRetry] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    getScenario(id, level, controller.signal).then(setData).catch((e: unknown) => { if (controller.signal.aborted) return; if (e instanceof ApiError && e.status === 401) onSessionExpired(); else setError(e instanceof Error ? e.message : 'Không thể tải bài. Thử tải lại hoặc chọn bài khác.') })
    return () => controller.abort()
  }, [id, level, retry, onSessionExpired])
  async function start() {
    if (starting || !selectedRole) return
    const choice = `${mode}:${selectedRole.code}`
    const key = startKeys.current.get(choice) ?? crypto.randomUUID()
    startKeys.current.set(choice, key)
    setStarting(true)
    setStartError('')
    try {
      const result = await mutateJson('practice-sessions', { scenarioId: id, level, mode, roleCode: selectedRole.code, clientRequestId: key }) as { id?: string }
      if (!result?.id || !/^[1-9][0-9]*$/.test(result.id)) throw new Error('Không nhận được mã buổi luyện.')
      if (window.location.hash === location) window.location.hash = `/practice/${result.id}`
    } catch (failure) {
      if (failure instanceof ApiError && failure.status === 401) onSessionExpired()
      else setStartError(failure instanceof Error ? failure.message : 'Không mở được buổi luyện.')
    } finally { setStarting(false) }
  }
  if (error) return <section role="alert"><h2>Chưa mở được tình huống</h2><p>{error}</p><a href="#/scenarios">Chọn bài khác</a> <button onClick={() => { setError(''); setRetry(r => r + 1) }}>Thử lại</button></section>
  if (!data || !selectedRole) return <p role="status">Đang tải bối cảnh bài luyện…</p>
  const roles = data.roles
  const learnerRoles = [...new Set(roles.map(role => role.learnerRole))]
  const partnerRoles = [...new Set(roles.map(role => role.aiRole))]
  function selectRole(field: 'learnerRole' | 'aiRole', value: string) {
    const other = field === 'learnerRole' ? 'aiRole' : 'learnerRole'
    const matching = roles.filter(role => role[field] === value)
    const next = matching.find(role => role[other] === selectedRole?.[other]) ?? matching[0]
    if (!next) return
    setRoleCode(next.code)
    setStartError('')
  }
  return <section className="scenarios-page scenario-detail" aria-labelledby="lesson-title"><a className="back-link" href={`#/scenarios?level=${level}`}><Icon name="back" />Quay lại tình huống</a><p className="scenario-detail-meta">{data.topicName} <span className="level-badge">{level}</span></p><PageHeading id="lesson-title" title={data.title} description={data.description} />
    <div className="lesson-layout"><div className="lesson-main"><div className="lesson-context"><h2>Bối cảnh cuộc trò chuyện</h2><p>{selectedRole.contextDescription}</p></div>
    {roles.length > 1 ? <fieldset className="role-grid scenario-role-options" disabled={starting} aria-describedby="scenario-role-hint">
      <legend>Chọn vai hội thoại</legend>
      <label className="form-field">Vai của bạn
        <select value={selectedRole.learnerRole} onChange={event => selectRole('learnerRole', event.target.value)}>
          {learnerRoles.map(role => <option key={role}>{role}</option>)}
        </select>
      </label>
      <label className="form-field">Người đối thoại
        <select value={selectedRole.aiRole} onChange={event => selectRole('aiRole', event.target.value)}>
          {partnerRoles.map(role => <option key={role}>{role}</option>)}
        </select>
      </label>
      <p className="hint" id="scenario-role-hint">Khi đổi một vai, hệ thống chọn vai còn lại phù hợp với tình huống.</p>
    </fieldset> :
      <dl className="role-grid"><div><dt>Vai của bạn</dt><dd>{selectedRole.learnerRole}</dd></div><div><dt>Người đối thoại</dt><dd>{selectedRole.aiRole}</dd></div></dl>}
    <div className="lesson-goal"><h2><Icon name="target" />Mục tiêu giao tiếp</h2><p aria-live="polite">{selectedRole.communicationGoal}</p></div></div>
    <aside className="lesson-start" aria-labelledby="scenario-start-title" aria-busy={starting}><h2 id="scenario-start-title">Bắt đầu cuộc trò chuyện</h2>{practiceAvailable ? <p className="scenario-start-description">Chọn cách bạn muốn trả lời trong buổi luyện này.</p> : null}
    {practiceAvailable ? <><fieldset className="practice-mode-options" disabled={starting}>
      <legend>Chế độ hội thoại</legend>
      <label><input type="radio" name="practice-mode" checked={mode === 'TEXT'}
        onChange={() => { setMode('TEXT'); setStartError('') }} />
        <span><strong>Chat văn bản</strong><small>Đọc lời của người đối thoại và trả lời bằng văn bản.</small></span></label>
      <label><input type="radio" name="practice-mode" checked={mode === 'VOICE'}
        onChange={() => { setMode('VOICE'); setStartError('') }} />
        <span><strong>Luyện nghe và nói</strong><small>Nghe người đối thoại, sau đó ghi âm câu trả lời.</small></span></label>
    </fieldset>
    <button className="button-primary" disabled={starting} onClick={() => void start()}><Icon name={mode === 'VOICE' ? 'microphone' : 'conversation'} />{starting ? 'Đang tạo buổi luyện…' : 'Bắt đầu luyện tập'}</button>
    {starting ? <p role="status">Đang tạo buổi và chờ câu mở đầu…</p> : null}
    {startError ? <p role="alert" className="form-error">{startError}</p> : null}</> : null}
    {!practiceAvailable ? <p className="notice">Luyện hội thoại hiện chưa khả dụng. Bạn vẫn có thể đọc bối cảnh và mục tiêu của tình huống.</p> : null}
    </aside></div>
  </section>
}
