import { useRef, useState } from 'react'
import { mutateJson } from '../../../shared/api/client'
import { reportError } from '../../../shared/api/errors'
import { useApiQuery } from '../../../shared/hooks/useApiQuery'
import { type Card, type ReviewStatus } from '../types'
import { type Page, type SessionGuard } from '../../../shared/types/api'
import { Pagination } from '../../../shared/ui/Pagination'
import { PageHeading } from '../../../shared/ui/PageHeading'
import { Icon } from '../../../shared/ui/Icon'
import '../styles/review.css'

export function ReviewCards({ onSessionExpired }: SessionGuard) {
  const [filter, setFilter] = useState('NEEDS_REVIEW')
  const [page, setPage] = useState(0)
  const [notice, setNotice] = useState('')
  const query = useApiQuery<Page<Card>>(`learning/cards?page=${page}&size=12${filter ? '&status='+filter : ''}`, onSessionExpired)
  return <section aria-labelledby="review-title" className="learning-panel">
    <PageHeading id="review-title" title="Ôn bằng thẻ ghi nhớ" description="Thử nhớ nghĩa trước khi mở đáp án. Đánh dấu để biết điều bạn cần luyện lại." />
    <div className="learning-toolbar"><div className="form-field">
      <label htmlFor="review-status">Trạng thái thẻ</label>
      <select id="review-status" value={filter} onChange={event => { setFilter(event.target.value); setPage(0) }}>
        <option value="NEEDS_REVIEW">Cần ôn</option>
        <option value="REMEMBERED">Đã nhớ</option>
        <option value="">Tất cả</option>
      </select>
    </div>{query.data ? <p role="status">{query.data.total} thẻ trong danh sách</p> : null}</div>
    {notice ? <p className="notice" role="status">{notice}</p> : null}
    {query.error ? <div role="alert"><p>{query.error}</p><button onClick={query.reload}>Thử tải lại</button></div> :
      !query.data ? <p role="status">Đang tải thẻ…</p> : <>
        {query.data.items.length === 0 ? <div className="empty-state"><Icon name="cards" /><h2>Chưa có thẻ trong mục này</h2><p>Lưu từ và mẫu câu từ phần nhận xét cuối buổi để ôn lại tại đây.</p><a href="#/scenarios">Chọn tình huống luyện tập</a></div> : null}
        <ul className="learning-list flashcard-grid">{query.data.items.map(card => <li key={card.id}>
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

  return <article className="flashcard">
    <span className={`status-badge ${card.status === 'REMEMBERED' ? 'status-success' : ''}`}>{card.status === 'REMEMBERED' ? 'Đã nhớ' : 'Cần ôn'}</span>
    <h2 lang="en">{card.expression}</h2>
    <button className="button-primary" aria-expanded={revealed} aria-controls={`card-answer-${card.id}`} onClick={() => setRevealed(value => !value)}>
      <Icon name="cards" size={20} />{revealed ? 'Ẩn đáp án' : 'Xem đáp án'}
    </button>
    <div className="flashcard-answer" id={`card-answer-${card.id}`} hidden={!revealed}>
      <p>{card.meaningVi}</p>
      {card.exampleEn ? <p lang="en">{card.exampleEn}</p> : null}
      <div className="actions">
        <button disabled={pending} onClick={() => void answer('NEEDS_REVIEW')}>Cần ôn</button>
        <button className="button-primary" disabled={pending} onClick={() => void answer('REMEMBERED')}><Icon name="check" />Đã nhớ</button>
      </div>
    </div>
    {error ? <p role="alert">{error}</p> : null}
  </article>
}
