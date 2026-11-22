import { useEffect, useRef, useState } from 'react'
import { ApiError, mutateJson } from '../../../shared/api/client'
import { isLevel } from '../../../shared/lib/learning-level'
import { getScenario, type ScenarioDetail as Detail } from '../api/catalog-api'
import type { PracticeMode } from '../../conversation/practiceMode'
export function ScenarioDetail({ location, onSessionExpired }: { location: string; onSessionExpired: () => void }) {
  const id = location.split('?')[0]?.split('/')[2] ?? ''
  const requested = new URLSearchParams(location.split('?')[1] ?? '').get('level')
  const level = isLevel(requested) ? requested : 'A2'
  const [data, setData] = useState<Detail | null>(null)
  const [error, setError] = useState('')
  const [starting, setStarting] = useState(false)
  const [startError, setStartError] = useState('')
  const [mode, setMode] = useState<PracticeMode>('TEXT')
  const startKeys = useRef<Partial<Record<PracticeMode, string>>>({})
  const [retry, setRetry] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    getScenario(id, level, controller.signal).then(setData).catch((e: unknown) => { if (controller.signal.aborted) return; if (e instanceof ApiError && e.status === 401) onSessionExpired(); else setError(e instanceof Error ? e.message : 'Không thể tải bài. Thử tải lại hoặc chọn bài khác.') })
    return () => controller.abort()
  }, [id, level, retry, onSessionExpired])
  async function start() {
    if (starting) return
    const key = startKeys.current[mode] ??= crypto.randomUUID()
    setStarting(true)
    setStartError('')
    try {
      const result = await mutateJson('practice-sessions', { scenarioId: id, level, mode, clientRequestId: key }) as { id?: string }
      if (!result?.id || !/^[1-9][0-9]*$/.test(result.id)) throw new Error('Không nhận được mã buổi luyện.')
      if (window.location.hash === location) window.location.hash = `/practice/${result.id}`
    } catch (failure) {
      if (failure instanceof ApiError && failure.status === 401) onSessionExpired()
      else setStartError(failure instanceof Error ? failure.message : 'Không mở được buổi luyện.')
    } finally { setStarting(false) }
  }
  if (error) return <section role="alert"><h2>Chưa mở được tình huống</h2><p>{error}</p><a href="#/scenarios">Chọn bài khác</a> <button onClick={() => { setError(''); setRetry(r => r + 1) }}>Thử lại</button></section>
  if (!data) return <p role="status">Đang tải bối cảnh bài luyện…</p>
  return <section aria-labelledby="lesson-title"><a href={`#/scenarios?level=${level}`}>← Quay lại tình huống</a><p className="topic-label">{data.topicName} · Trình độ {level}</p><h2 id="lesson-title">{data.title}</h2><p>{data.description}</p>
    <div className="lesson-context"><h3>Bối cảnh</h3><p>{data.contextDescription}</p></div><dl className="role-grid"><div><dt>Vai của bạn</dt><dd>{data.learnerRole}</dd></div><div><dt>Vai người đối thoại</dt><dd>{data.aiRole}</dd></div></dl><h3>Mục tiêu giao tiếp</h3><p>{data.communicationGoal}</p>
    <p className="hint">Nội dung hội thoại được gửi tới Google Gemini để tạo phản hồi. Hãy dùng thông tin hư cấu khi luyện tập.</p>
    <fieldset className="practice-mode-options" disabled={starting}>
      <legend>Chế độ hội thoại</legend>
      <label><input type="radio" name="practice-mode" checked={mode === 'TEXT'}
        onChange={() => { setMode('TEXT'); setStartError('') }} />
        <span><strong>Chat văn bản</strong><small>Đọc câu AI và trả lời bằng văn bản.</small></span></label>
      <label><input type="radio" name="practice-mode" checked={mode === 'VOICE'}
        onChange={() => { setMode('VOICE'); setStartError('') }} />
        <span><strong>Luyện nghe và nói</strong><small>Nghe câu AI trước, sau đó ghi câu trả lời.</small></span></label>
    </fieldset>
    <button disabled={starting} onClick={() => void start()}>Bắt đầu hội thoại</button>
    {starting ? <p role="status">Đang tạo buổi và chờ câu mở đầu…</p> : null}
    {startError ? <p role="alert" className="form-error">{startError}</p> : null}
  </section>
}
