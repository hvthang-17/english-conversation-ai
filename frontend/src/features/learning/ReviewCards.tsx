import { useRef, useState } from 'react'
import { mutateJson } from '../../shared/api/client'
import { reportError, useLearningData, type Card, type Page, type ReviewStatus, type SessionGuard } from './learningApi'
import { Pagination } from './Pagination'

export function ReviewCards({ onSessionExpired }: SessionGuard) {
  const [filter, setFilter] = useState('NEEDS_REVIEW')
  const [page, setPage] = useState(0)
  const [notice, setNotice] = useState('')
  const query = useLearningData<Page<Card>>(`learning/cards?page=${page}&size=12${filter ? '&status='+filter : ''}`, onSessionExpired)
  return <section aria-labelledby="review-title" className="learning-panel">
    <h2 id="review-title">Ôn bằng thẻ ghi nhớ</h2>
    <div className="form-field">
      <label htmlFor="review-status">Trạng thái thẻ</label>
      <select id="review-status" value={filter} onChange={event => { setFilter(event.target.value); setPage(0) }}>
        <option value="NEEDS_REVIEW">Cần ôn</option>
        <option value="REMEMBERED">Đã nhớ</option>
        <option value="">Tất cả</option>
      </select>
    </div>
    {notice ? <p role="status">{notice}</p> : null}
    {query.error ? <div role="alert"><p>{query.error}</p><button onClick={query.reload}>Thử tải lại</button></div> :
      !query.data ? <p role="status">Đang tải thẻ…</p> : <>
        {query.data.items.length === 0 ? <p>Chưa có thẻ trong mục này. Bạn có thể lưu từ và mẫu câu từ phần nhận xét cuối buổi.</p> : null}
        <ul className="learning-list">{query.data.items.map(card => <li key={card.id}>
          <Flashcard card={card} onSessionExpired={onSessionExpired} onAnswered={answer => {
            setNotice(answer === 'REMEMBERED' ? 'Đã đánh dấu thẻ là đã nhớ.' : 'Đã chuyển thẻ về cần ôn.')
            if (page > 0 && filter && answer !== filter && query.data?.items.length === 1) setPage(page - 1)
            else query.reload()
          }} />
        </li>)}</ul>
        <Pagination page={page} size={query.data.size} total={query.data.total} onPage={setPage} />
      </>}
  </section>
}

function Flashcard({ card, onSessionExpired, onAnswered }: SessionGuard & {
  card: Card; onAnswered: (answer: ReviewStatus) => void
}) {
  const [revealed, setRevealed] = useState(false)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const retry = useRef<{ result: ReviewStatus; clientRequestId: string } | null>(null)
  async function answer(result: ReviewStatus) {
    if (pending) return
    if (retry.current?.result !== result) retry.current = { result, clientRequestId: crypto.randomUUID() }
    setPending(true); setError('')
    try {
      await mutateJson(`learning/cards/${card.id}/attempts`, retry.current)
      retry.current = null
      onAnswered(result)
    } catch (failure) { setError(reportError(failure, onSessionExpired)) }
    finally { setPending(false) }
  }

  return <article>
    <h3 lang="en">{card.expression}</h3>
    <p>{card.status === 'REMEMBERED' ? 'Đã nhớ' : 'Cần ôn'}</p>
    <button aria-expanded={revealed} aria-controls={`card-answer-${card.id}`} onClick={() => setRevealed(value => !value)}>
      {revealed ? 'Ẩn đáp án' : 'Xem đáp án'}
    </button>
    <div id={`card-answer-${card.id}`} hidden={!revealed}>
      <p>{card.meaningVi}</p>
      {card.exampleEn ? <p lang="en">{card.exampleEn}</p> : null}
      <div className="actions">
        <button disabled={pending} onClick={() => void answer('NEEDS_REVIEW')}>Cần ôn</button>
        <button disabled={pending} onClick={() => void answer('REMEMBERED')}>Đã nhớ</button>
      </div>
    </div>
    {error ? <p role="alert">{error}</p> : null}
  </article>
}
