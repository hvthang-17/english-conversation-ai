import { useEffect, useState } from 'react'
import { Icon } from '../../../shared/ui/Icon'
import '../styles/feedback.css'
import { ApiError, getJson, mutateJson } from '../../../shared/api/client'

type Correction = {
  itemType: 'ERROR' | 'IMPROVEMENT'
  category: string
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
  repeatedErrors?: { ruleCode: string; category: string; messageCount: number; example: Correction }[]
}

const ruleLabels: Record<string, string> = {
  ARTICLE: 'Mạo từ', TENSE: 'Thì của động từ', AGREEMENT: 'Hòa hợp chủ ngữ và động từ',
  VERB_FORM: 'Dạng động từ',
  WORD_ORDER: 'Trật tự từ', PREPOSITION: 'Giới từ', WORD_CHOICE: 'Chọn từ', COLLOCATION: 'Kết hợp từ',
}
const categoryLabels: Record<string, string> = { GRAMMAR: 'Ngữ pháp', VOCABULARY: 'Từ vựng', EXPRESSION: 'Diễn đạt' }

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
    <header className="feedback-heading"><Icon name="book" /><div><h2 id="feedback-title">Nhận xét sau buổi luyện</h2><p>Nhìn lại điều bạn đã làm tốt và chọn một điều để luyện tiếp.</p></div></header>
    {error ? <div role="alert"><p>{error}</p><button onClick={() => { setError(''); setRefresh(value => value + 1) }}>Tải lại trạng thái</button></div> : null}
    {pending || running ? <p className="feedback-progress" role="status">Đang tạo nhận xét… Bạn có thể đọc lại hội thoại trong lúc chờ.</p> : null}
    {!review && !error && !pending ? <p role="status">Đang tải nhận xét…</p> : null}
    {review?.status === 'NOT_STARTED' && !pending ? <div className="feedback-start">
      <p>Gemini sẽ đọc nội dung buổi luyện để nhận xét và gợi ý cách diễn đạt.</p>
      <button className="button-primary" type="button" onClick={() => void generate(false)}><Icon name="lightbulb" />Tạo nhận xét</button>
    </div> : null}
    {review && !pending && (review.status === 'UNKNOWN' || review.status === 'FAILED') ? <div className="notice">
      <p role="status">{review.status === 'UNKNOWN' ? 'Chưa rõ kết quả lần gọi trước. Thử lại có thể dùng thêm hạn mức AI.' :
        review.errorCode === 'FEEDBACK_INVALID' ? 'Kết quả chưa đối chiếu được với hội thoại.' : 'Chưa nhận được kết quả phân tích.'}</p>
      {review.retryable && review.status === 'UNKNOWN' ? <label><input type="checkbox" checked={acknowledged}
        onChange={event => setAcknowledged(event.target.checked)} /> Tôi muốn gọi lại Gemini</label> : null}
      {review.retryable ? <button type="button" disabled={review.status === 'UNKNOWN' && !acknowledged}
        onClick={() => void generate(true)}>Thử tạo lại nhận xét</button> : <p>Hiện chưa thể thử lại. Bạn vẫn có thể đọc lại hội thoại và quay lại sau.</p>}
    </div> : null}
    {result ? <div className="feedback-result">
      <p className="feedback-summary">{result.summary}</p>
      <div className="feedback-overview"><section className="feedback-overview-section" aria-labelledby="feedback-goal"><h3 id="feedback-goal"><Icon name="target" />Mục tiêu giao tiếp</h3><p>{result.goalAssessment}</p></section>
      {result.strengths.length > 0 ? <section className="feedback-overview-section" aria-labelledby="feedback-strengths"><h3 id="feedback-strengths"><Icon name="check" />Điểm tốt</h3><ul>{result.strengths.map((item, index) => <li key={index}>{item}</li>)}</ul></section> : null}</div>
      {review?.repeatedErrors?.length ? <details className="feedback-repeated"><summary>Lỗi lặp trong buổi</summary><p className="feedback-help">Những nhóm lỗi xuất hiện ở nhiều câu trong buổi này.</p><ul>{review.repeatedErrors.map(group => <li key={group.ruleCode}>
        <div className="feedback-repeated-heading"><strong>{ruleLabels[group.ruleCode] ?? categoryLabels[group.category] ?? group.ruleCode}</strong><span>{group.messageCount} câu</span></div>
        <dl><div><dt>Câu gốc</dt><dd lang="en">{group.example.originalText}</dd></div><div><dt>Gợi ý sửa</dt><dd lang="en">{group.example.suggestedText}</dd></div></dl>
      </li>)}</ul></details> : null}
      <section className="feedback-section" aria-labelledby="feedback-errors"><h3 id="feedback-errors">Câu cần sửa</h3><p className="feedback-help">Đối chiếu câu bạn đã nói với gợi ý và đọc lý do.</p>
        {errors.length ? <Corrections items={errors} /> : <p className="feedback-no-errors"><Icon name="check" />Không có lỗi cần sửa được ghi nhận trong buổi này.</p>}
      </section>
      {improvements.length ? <section className="feedback-section" aria-labelledby="feedback-improvements"><h3 id="feedback-improvements">Cách diễn đạt tự nhiên hơn</h3><p className="feedback-help">Gợi ý để mở rộng cách nói, không phải lỗi bắt buộc phải sửa.</p><Corrections items={improvements} improvement /></section> : null}
      {result.suggestions.length ? <section className="feedback-section" aria-labelledby="feedback-suggestions"><h3 id="feedback-suggestions">Từ và mẫu câu nên ôn</h3><p className="feedback-help">Lưu điều bạn muốn dùng lại trong cuộc trò chuyện tiếp theo.</p><ul className="feedback-suggestions">{result.suggestions.map((item, index) =>
        <li key={review?.suggestionIds[index] ?? index}><h4 lang="en">{item.expression}</h4><p>{item.meaningVi}</p><p className="feedback-example" lang="en">{item.exampleEn}</p>
          {review?.suggestionIds[index] ? <SaveSuggestion id={review.suggestionIds[index]} onSessionExpired={onSessionExpired} /> : null}
        </li>)}</ul></section> : null}
      <section className="feedback-next" aria-labelledby="feedback-next-title"><h3 id="feedback-next-title"><Icon name="target" />Bước luyện tiếp theo</h3><p>{result.nextPractice}</p></section>
    </div> : null}
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
  if (state === 'saved') return <p className="feedback-saved" role="status"><Icon name="check" size={20} />Đã lưu vào <a href="#/review">thẻ ôn tập</a>.</p>
  return <><button type="button" disabled={state === 'saving'} onClick={() => void save()}>
    <Icon name="plus" size={20} />{state === 'saving' ? 'Đang lưu…' : 'Lưu thẻ ôn tập'}
  </button>{error ? <p role="alert">{error}</p> : null}</>
}

function Corrections({ items, improvement = false }: { items: Correction[]; improvement?: boolean }) {
  return <ul className={`feedback-corrections${improvement ? ' feedback-improvement-list' : ''}`}>{items.map((item, index) => <li key={index}>
    {categoryLabels[item.category] ? <span className="status-badge">{categoryLabels[item.category]}</span> : null}
    <dl><div><dt>Câu gốc</dt><dd lang="en">{item.originalText}</dd></div>
      <div><dt>{improvement ? 'Cách nói gợi ý' : 'Gợi ý sửa'}</dt><dd lang="en">{item.suggestedText}</dd></div></dl>
    <p>{item.explanationVi}</p>
  </li>)}</ul>
}
