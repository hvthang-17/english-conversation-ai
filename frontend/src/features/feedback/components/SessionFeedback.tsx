import { useEffect, useState } from 'react'
import { ApiError, getJson, mutateJson } from '../../../shared/api/client'

type Correction = {
  itemType: 'ERROR' | 'IMPROVEMENT'
  originalText: string
  suggestedText: string
  explanationVi: string
}
type Suggestion = { itemType: string; expression: string; meaningVi: string; exampleEn: string }
type Review = {
  status: 'NOT_STARTED' | 'PENDING' | 'RUNNING' | 'FAILED' | 'UNKNOWN' | 'SUCCEEDED'
  errorCode: string | null
  retryable: boolean
  attempts: number
  result: {
    summary: string; strengths: string[]; goalAssessment: string; nextPractice: string
    corrections: Correction[]; suggestions: Suggestion[]
  } | null
  suggestionIds: string[]
}

export function SessionFeedback({ id, onSessionExpired }: { id: string; onSessionExpired: () => void }) {
  const [review, setReview] = useState<Review | null>(null)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const [refresh, setRefresh] = useState(0)
  const [acknowledged, setAcknowledged] = useState(false)
  const path = `practice-sessions/${id}/feedback`
  const running = review?.status === 'RUNNING' || review?.status === 'PENDING'

  useEffect(() => {
    if (pending) return
    const controller = new AbortController()
    let timer: ReturnType<typeof setTimeout> | undefined
    async function load() {
      try {
        const next = await getJson(path, controller.signal) as Review
        if (controller.signal.aborted) return
        setReview(next)
        if (next.status === 'RUNNING' || next.status === 'PENDING') timer = setTimeout(() => void load(), 1800)
      } catch (failure) {
        if (controller.signal.aborted) return
        if (failure instanceof ApiError && failure.status === 401) onSessionExpired()
        else setError('Chưa tải được trạng thái nhận xét.')
      }
    }
    void load()
    return () => { controller.abort(); clearTimeout(timer) }
  }, [path, refresh, pending, onSessionExpired])

  async function generate(retry: boolean) {
    if (pending || running) return
    setPending(true); setError('')
    try {
      const next = await mutateJson(retry ? `${path}/retry` : path,
        retry ? { acknowledgeUnknown: acknowledged } : undefined) as Review
      setReview(next); setAcknowledged(false)
    } catch (failure) {
      if (failure instanceof ApiError && failure.status === 401) onSessionExpired()
      else setError(failure instanceof Error ? failure.message : 'Chưa tạo được nhận xét.')
    } finally { setPending(false) }
  }

  const result = review?.result
  const errors = result?.corrections.filter(item => item.itemType === 'ERROR') ?? []
  const improvements = result?.corrections.filter(item => item.itemType === 'IMPROVEMENT') ?? []
  return <section aria-labelledby="feedback-title" aria-busy={pending || running} className="session-feedback">
    <h2 id="feedback-title">Nhận xét sau buổi luyện</h2>
    {error ? <div role="alert"><p>{error}</p><button onClick={() => { setError(''); setRefresh(value => value + 1) }}>Tải lại trạng thái</button></div> : null}
    {pending || running ? <p role="status">Đang tạo nhận xét…</p> : null}
    {!review && !error && !pending ? <p role="status">Đang tải nhận xét…</p> : null}
    {review?.status === 'NOT_STARTED' && !pending ? <>
      <p>Gemini sẽ đọc nội dung buổi luyện để nhận xét và gợi ý cách diễn đạt.</p>
      <button onClick={() => void generate(false)}>Tạo nhận xét</button>
    </> : null}
    {review && !pending && (review.status === 'UNKNOWN' || review.status === 'FAILED') ? <div className="notice">
      <p role="status">{review.status === 'UNKNOWN' ? 'Chưa rõ kết quả lần gọi trước. Thử lại có thể dùng thêm quota.' :
        review.errorCode === 'FEEDBACK_INVALID' ? 'Kết quả chưa đối chiếu được với hội thoại.' : 'Chưa nhận được kết quả phân tích.'}</p>
      {review.retryable && review.status === 'UNKNOWN' ? <label><input type="checkbox" checked={acknowledged}
        onChange={event => setAcknowledged(event.target.checked)} /> Tôi muốn gọi lại Gemini</label> : null}
      {review.retryable ? <button disabled={review.status === 'UNKNOWN' && !acknowledged}
        onClick={() => void generate(true)}>Thử tạo lại nhận xét</button> : <p>Hiện đã hết lượt thử lại hoặc cần kiểm tra cấu hình.</p>}
    </div> : null}
    {result ? <>
      <p>{result.summary}</p>
      <h3>Mục tiêu giao tiếp</h3><p>{result.goalAssessment}</p>
      {result.strengths.length > 0 ? <><h3>Điểm tốt</h3><ul>{result.strengths.map((item, index) => <li key={index}>{item}</li>)}</ul></> : null}
      <h3>Câu cần sửa</h3>
      {errors.length ? <Corrections items={errors} /> : <p>Không có lỗi cần sửa được ghi nhận trong buổi này.</p>}
      {improvements.length ? <><h3>Cách diễn đạt tự nhiên hơn</h3><Corrections items={improvements} /></> : null}
      {result.suggestions.length ? <><h3>Từ và mẫu câu nên ôn</h3><ul>{result.suggestions.map((item, index) =>
        <li key={review?.suggestionIds[index] ?? index}><strong lang="en">{item.expression}</strong> · {item.meaningVi}
          <p lang="en">{item.exampleEn}</p>
          {review?.suggestionIds[index] ? <SaveSuggestion id={review.suggestionIds[index]} onSessionExpired={onSessionExpired} /> : null}
        </li>)}</ul></> : null}
      <h3>Bước luyện tiếp theo</h3><p>{result.nextPractice}</p>
    </> : null}
  </section>
}

function SaveSuggestion({ id, onSessionExpired }: { id: string; onSessionExpired: () => void }) {
  const [state, setState] = useState<'idle' | 'saving' | 'saved'>('idle')
  const [error, setError] = useState('')
  async function save() {
    if (state !== 'idle') return
    setState('saving'); setError('')
    try {
      await mutateJson(`learning/cards/from-suggestion/${id}`)
      setState('saved')
    } catch (failure) {
      setState('idle')
      if (failure instanceof ApiError && failure.status === 401) onSessionExpired()
      else setError(failure instanceof Error ? failure.message : 'Chưa lưu được thẻ.')
    }
  }
  if (state === 'saved') return <p role="status">Đã lưu vào <a href="#/review">thẻ ôn tập</a>.</p>
  return <><button disabled={state === 'saving'} onClick={() => void save()}>
    {state === 'saving' ? 'Đang lưu…' : 'Lưu thẻ ôn tập'}
  </button>{error ? <p role="alert">{error}</p> : null}</>
}

function Corrections({ items }: { items: Correction[] }) {
  return <ul className="feedback-corrections">{items.map((item, index) => <li key={index}>
    <dl><div><dt>Câu gốc</dt><dd lang="en">{item.originalText}</dd></div>
      <div><dt>Gợi ý</dt><dd lang="en">{item.suggestedText}</dd></div></dl>
    <p>{item.explanationVi}</p>
  </li>)}</ul>
}
