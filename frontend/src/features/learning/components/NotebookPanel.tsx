import { useId, useRef, useState, type FormEvent } from 'react'
import { ApiError } from '../../../shared/api/client'
import { Icon } from '../../../shared/ui/Icon'
import '../styles/learning.css'
import { PageHeading } from '../../../shared/ui/PageHeading'
import { reportError } from '../../../shared/api/errors'
import { useApiQuery } from '../../../shared/hooks/useApiQuery'
import { type Page, type SessionGuard } from '../../../shared/types/api'
import { type ReviewStatus } from '../../review/types'
import { deleteCard, notebookPath, openQuiz, submitReview, type NotebookCard, type Quiz, type ReviewAttempt } from '../api/notebook-api'
import { Pagination } from '../../../shared/ui/Pagination'

const labels: Record<ReviewStatus, string> = { NEEDS_REVIEW: 'Cần ôn', REMEMBERED: 'Đã nhớ' }

export function NotebookPanel({ onSessionExpired }: SessionGuard) {
  const [filter, setFilter] = useState('')
  const [page, setPage] = useState(0)
  const [deleted, setDeleted] = useState('')
  const result = useApiQuery<Page<NotebookCard>>(`${notebookPath}?page=${page}${filter ? `&status=${filter}` : ''}`, onSessionExpired)
  const data = result.data

  function afterDelete() {
    setDeleted('Đã xóa thẻ và lịch sử ôn của thẻ.')
    if (page > 0 && data?.items.length === 1) setPage(page - 1)
    else result.reload()
  }

  return <section className="notebook" aria-labelledby="notebook-title">
    <PageHeading id="notebook-title" title="Sổ tay ôn tập" description="Nhớ lại từ và mẫu câu đã lưu. Lật thẻ hoặc thử chọn nghĩa trước khi xem đáp án." action={<a className="button-link" href="#/lessons"><Icon name="book" />Khám phá bài học</a>} />
    <div className="learning-toolbar">
    <label>Trạng thái thẻ <select value={filter} onChange={event => { setFilter(event.target.value); setPage(0) }}>
      <option value="">Tất cả thẻ</option>
      {Object.entries(labels).map(([status, label]) => <option key={status} value={status}>{label}</option>)}
    </select></label>{data ? <p role="status">{data.total} thẻ trong danh sách</p> : null}</div>
    {deleted ? <p className="notice" role="status">{deleted}</p> : null}
    {result.error ? <div role="alert"><p>{result.error}</p><button type="button" onClick={result.reload}>Thử tải lại sổ tay</button></div> : null}
    {!data && !result.error ? <p role="status">Đang tải sổ tay…</p> : null}
    {data ? <>
      {data.items.length > 0 ? <ul className="notebook-list">{data.items.map(card => <NotebookEntry key={card.id}
        card={card} onDeleted={afterDelete} onSessionExpired={onSessionExpired} />)}</ul>
        : <p className="empty-state" role="status">{filter ? 'Chưa có thẻ trong trạng thái này.' : 'Chưa có thẻ. Hãy lưu từ hoặc mẫu câu trong một bài học.'}</p>}
      <Pagination page={page} size={data.size} total={data.total} onPage={setPage} />
    </> : null}
  </section>
}

function NotebookEntry({ card, onDeleted, onSessionExpired }: SessionGuard & { card: NotebookCard; onDeleted: () => void }) {
  const id = useId()
  const [revealed, setRevealed] = useState(false)
  const [showHistory, setShowHistory] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const [pending, setPending] = useState(false)
  const [status, setStatus] = useState(card.status)
  const [quiz, setQuiz] = useState<Quiz>()
  const [choice, setChoice] = useState('')
  const [quizResult, setQuizResult] = useState<boolean | null>(null)
  const [latestAttempt, setLatestAttempt] = useState('')
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [retryStatus, setRetryStatus] = useState<ReviewStatus>()
  const flashRequest = useRef<{ status: ReviewStatus; key: string } | null>(null)

  async function run(action: () => Promise<void>) {
    setPending(true)
    setError('')
    setMessage('')
    try {
      await action()
    } catch (failure) {
      setError(reportError(failure, onSessionExpired))
    } finally {
      setPending(false)
    }
  }

  function mark(next: ReviewStatus) {
    const request = flashRequest.current ?? { status: next, key: crypto.randomUUID() }
    flashRequest.current = request
    setRetryStatus(request.status)
    void run(async () => {
      const attempt = await submitReview(card.id, { mode: 'FLASHCARD', result: request.status, clientRequestId: request.key })
      flashRequest.current = null
      setRetryStatus(undefined)
      setStatus(attempt.result ?? request.status)
      setLatestAttempt(attempt.id)
      setMessage(`Đã ghi lượt ôn: ${labels[attempt.result ?? request.status]}.`)
    })
  }

  function startQuiz() {
    setRevealed(false)
    void run(async () => {
      setQuiz(await openQuiz(card.id))
      setChoice('')
      setQuizResult(null)
    })
  }

  function grade(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!quiz || !choice || pending || quizResult !== null) return
    void run(async () => {
      try {
        const attempt = await submitReview(card.id, { mode: 'QUICK_QUIZ', optionId: choice, clientRequestId: quiz.id })
        setQuizResult(attempt.correct)
        setLatestAttempt(attempt.id)
      } catch (failure) {
        if (failure instanceof ApiError && failure.code === 'QUIZ_EXPIRED') setQuiz(undefined)
        throw failure
      }
    })
  }

  const quizOpen = quiz !== undefined && quizResult === null
  return <li className="g3-card notebook-entry" aria-labelledby={`${id}-expression`} aria-busy={pending}>
    <div className="notebook-entry-heading"><span className={`status-badge ${status === 'REMEMBERED' ? 'status-success' : ''}`}>{labels[status]}</span><span className="notebook-kind">{card.itemType === 'PHRASE' ? 'Mẫu câu' : 'Từ vựng'}</span></div><h2 id={`${id}-expression`} lang="en">{card.expression}</h2>
    <div className="material-controls">
      <button className="button-primary" type="button" aria-expanded={revealed} aria-controls={`${id}-answer`} disabled={quizOpen}
        onClick={() => setRevealed(value => !value)}><Icon name="cards" size={20} />{revealed ? 'Ẩn mặt sau' : 'Lật thẻ'}</button>
      <button type="button" disabled={pending || quizOpen || Boolean(retryStatus)} onClick={startQuiz}>
        {quizResult !== null ? 'Câu hỏi mới' : 'Quiz chọn nghĩa'}</button>
    </div>
    <div className="notebook-answer" id={`${id}-answer`} hidden={!revealed}>
      <p>{card.meaningVi}</p>
      {card.ipa ? <p className="hint">{card.ipa}</p> : null}
      {card.exampleEn ? <p lang="en">{card.exampleEn}</p> : null}
      <div className="material-controls">
        {(Object.keys(labels) as ReviewStatus[]).map(next => <button key={next} type="button"
          disabled={pending || (retryStatus !== undefined && retryStatus !== next)} onClick={() => mark(next)}>
          {retryStatus === next ? `Thử lưu lại: ${labels[next]}` : labels[next]}
        </button>)}
      </div>
    </div>
    {quiz ? <form className="notebook-quiz" onSubmit={grade}>
      <fieldset disabled={pending || quizResult !== null}>
        <legend>Chọn nghĩa tiếng Việt</legend>
        {quiz.options.map(option => <label key={option.id}><input type="radio" name={`${id}-choice`}
          value={option.id} checked={choice === option.id} onChange={() => setChoice(option.id)} required /> {option.meaningVi}</label>)}
      </fieldset>
      <div className="material-controls">
        {quizResult === null ? <button className="button-primary" type="submit" disabled={!choice || pending}>Nộp đáp án</button> : null}
        <button type="button" disabled={pending} onClick={() => { setQuiz(undefined); setQuizResult(null); setError('') }}>Đóng quiz</button>
      </div>
      {quizResult !== null ? <p className="notice" role="status">{quizResult ? 'Đúng.' : 'Chưa đúng.'} Nghĩa của thẻ là {card.meaningVi}.</p> : null}
    </form> : null}
    <div className="material-controls notebook-utilities">
      <button className="button-quiet" type="button" aria-expanded={showHistory} onClick={() => setShowHistory(value => !value)}>
        {showHistory ? 'Ẩn lịch sử ôn' : 'Lịch sử ôn'}</button>
      <button className="button-quiet button-danger" type="button" aria-expanded={confirming} disabled={pending} onClick={() => setConfirming(value => !value)}>Xóa thẻ</button>
    </div>
    {confirming ? <div className="notice notice-danger"><p>Xóa thẻ này cùng toàn bộ lượt ôn của thẻ?</p><div className="material-controls">
      <button type="button" className="button-danger" disabled={pending} onClick={() => void run(async () => {
        await deleteCard(card.id)
        onDeleted()
      })}>Xác nhận xóa thẻ</button>
      <button type="button" disabled={pending} onClick={() => setConfirming(false)}>Hủy xóa</button>
    </div></div> : null}
    {message ? <p className="notice" role="status">{message}</p> : null}
    {error ? <p role="alert">{error}</p> : null}
    {showHistory ? <AttemptHistory key={latestAttempt} cardId={card.id} onSessionExpired={onSessionExpired} /> : null}
  </li>
}

function AttemptHistory({ cardId, onSessionExpired }: SessionGuard & { cardId: string }) {
  const [page, setPage] = useState(0)
  const { data, error, reload } = useApiQuery<Page<ReviewAttempt>>(`${notebookPath}/${cardId}/attempts?page=${page}`, onSessionExpired)
  return <section className="notebook-history">
    <h3>Lịch sử ôn của thẻ</h3>
    {error ? <div role="alert"><p>{error}</p><button type="button" onClick={reload}>Thử tải lại lịch sử</button></div> : null}
    {!data && !error ? <p role="status">Đang tải lịch sử…</p> : null}
    {data ? <>
      {data.items.length ? <ul>{data.items.map(item => <li key={item.id}>
        {item.mode === 'FLASHCARD' ? `Flashcard · ${item.result ? labels[item.result] : ''}` : `Quiz · ${item.correct ? 'Đúng' : 'Sai'}`}
        {' · '}<time dateTime={item.reviewedAt}>{new Date(item.reviewedAt).toLocaleString('vi-VN')}</time>
      </li>)}</ul> : <p>Chưa có lượt ôn.</p>}
      <Pagination page={page} size={data.size} total={data.total} onPage={setPage} />
    </> : null}
  </section>
}
