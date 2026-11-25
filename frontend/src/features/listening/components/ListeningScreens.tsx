import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { ApiError, apiUrl, mutateJson } from '../../../shared/api/client'
import { Icon } from '../../../shared/ui/Icon'
import { PageHeading } from '../../../shared/ui/PageHeading'
import { Pagination } from '../../../shared/ui/Pagination'
import { reportError } from '../../../shared/api/errors'
import { useApiQuery } from '../../../shared/hooks/useApiQuery'
import { type Page, type SessionGuard } from '../../../shared/types/api'
import {
  attemptPath, publicationPath, startExercise, writeAnswers, type Answer, type Attempt, type Exercise,
  type History, type Question, type Result, type Version,
} from '../api/listening-api'
import '../styles/listening.css'

const statusLabels = { IN_PROGRESS: 'Đang làm', SUBMITTED: 'Đã nộp', ABANDONED: 'Đã bỏ dở' }

export function LessonListening({ lessonId, onSessionExpired }: SessionGuard & { lessonId: string }) {
  const { data, error, reload } = useApiQuery<Exercise[]>(`lessons/${lessonId}/listening`, onSessionExpired)
  return <section className="listening-section" aria-labelledby="lesson-listening-title">
    <h2 id="lesson-listening-title">Luyện nghe</h2>
    <p><a href="#/listening">Lịch sử luyện nghe</a></p>
    {error && <LoadError error={error} reload={reload} />}
    {!error && !data && <p role="status">Đang tải đề nghe…</p>}
    {data?.length === 0 && <p>Chưa có đề nghe công khai cho bài này.</p>}
    {data && data.map(exercise => <article key={exercise.id} className="listening-card">
      <h3>{exercise.title}</h3>
      <p>{exercise.instructions}</p>
      <StartButton exerciseId={exercise.id} onSessionExpired={onSessionExpired}>Bắt đầu hoặc tiếp tục</StartButton>
    </article>)}
  </section>
}

function StartButton({ exerciseId, onSessionExpired, children }: SessionGuard & { exerciseId: string; children: string }) {
  const request = useRef('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  async function open() {
    setPending(true)
    setError('')
    request.current ||= crypto.randomUUID()
    try {
      const attempt = await startExercise(exerciseId, request.current)
      window.location.hash = `/listening/attempts/${attempt.id}`
    } catch (failure) {
      setError(reportError(failure, onSessionExpired))
    } finally {
      setPending(false)
    }
  }
  return <div>
    <button type="button" className="button-primary" disabled={pending} onClick={() => void open()}><Icon name="volume" size={20} />{pending ? 'Đang mở…' : children}</button>
    {error && <p role="alert">{error}</p>}
  </div>
}

export function ListeningAttemptScreen({ id, onSessionExpired }: SessionGuard & { id: string }) {
  const { data, error, reload } = useApiQuery<Attempt>(`${attemptPath}/${id}`, onSessionExpired)
  return <section className="listening-section">
    <a className="back-link" href="#/listening"><Icon name="back" />Về luyện nghe</a>
    {error && <LoadError error={error} reload={reload} />}
    {!error && !data && <p role="status">Đang tải lượt luyện nghe…</p>}
    {data && <AttemptForm initial={data} reload={reload} onSessionExpired={onSessionExpired} />}
  </section>
}

function AttemptForm({ initial, reload, onSessionExpired }: SessionGuard & { initial: Attempt; reload: () => void }) {
  const [attempt, setAttempt] = useState(initial)
  const [answers, setAnswers] = useState(initial.answers)
  const [dirty, setDirty] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [conflict, setConflict] = useState(false)
  const [pending, setPending] = useState(false)
  const [confirmAbandon, setConfirmAbandon] = useState(false)
  const editable = attempt.status === 'IN_PROGRESS'

  async function save(submit: boolean) {
    setPending(true)
    setNotice('')
    setError('')
    try {
      const saved = await writeAnswers(attempt.id, attempt.draftVersion, answers, submit)
      setAttempt(saved)
      setAnswers(saved.answers)
      setDirty(false)
      setNotice(submit ? 'Đã nộp bài.' : 'Đã lưu nháp trên máy chủ.')
    } catch (failure) {
      if (failure instanceof ApiError && failure.status === 409) setConflict(true)
      setError(reportError(failure, onSessionExpired))
    } finally {
      setPending(false)
    }
  }

  async function abandon() {
    setPending(true)
    setError('')
    try {
      await mutateJson(`${attemptPath}/${attempt.id}/abandon`)
      reload()
    } catch (failure) {
      if (failure instanceof ApiError && failure.status === 409) setConflict(true)
      setError(reportError(failure, onSessionExpired))
    } finally {
      setPending(false)
    }
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!pending && !conflict) void save(true)
  }

  return <>
    <PageHeading id="listening-attempt-title" title={attempt.exercise.title} description={attempt.exercise.instructions} />
    <div className="listening-attempt-meta"><span className="level-badge">{attempt.exercise.level}</span><span className="status-badge">{statusLabels[attempt.status]}</span></div>
    <div className={attempt.status === 'ABANDONED' ? 'listening-attempt-closed' : 'listening-attempt-layout'}>
    {attempt.status !== 'ABANDONED' && <ListeningAudio key={attempt.id} id={attempt.id} />}
    <div className="listening-answer-area">
    {error && <p role="alert">{error}</p>}
    {conflict && <button type="button" onClick={reload}>Tải lại bản đã lưu</button>}
    {notice && <p className="notice" role="status">{notice}</p>}
    {editable && <form className="listening-form" onSubmit={submit}>
      <header className="listening-question-heading"><h2>Câu hỏi bài nghe</h2><p className="hint">Bạn có thể nghe lại và lưu nháp để tiếp tục sau. Câu chưa trả lời được tính 0 điểm.</p></header>
      {attempt.questions.map((question, index) => <QuestionInput key={question.code} question={question}
        index={index} value={answers[question.code]} disabled={pending || conflict} change={value => {
          setDirty(true)
          setNotice('')
          setAnswers(previous => ({ ...previous, [question.code]: value }))
        }} />)}
      <p role="status">{dirty ? 'Có thay đổi chưa lưu.' : ''}</p>
      <div className="actions listening-submit-actions">
        <button type="button" disabled={pending || conflict || !dirty} onClick={() => void save(false)}>Lưu nháp</button>
        <button type="submit" className="button-primary" disabled={pending || conflict}>{pending ? 'Đang xử lý…' : 'Nộp bài'}</button>
        <button type="button" disabled={pending || conflict} onClick={() => setConfirmAbandon(value => !value)} aria-expanded={confirmAbandon}>Bỏ dở</button>
      </div>
      {confirmAbandon && <div className="notice">
        <p>Bạn sẽ kết thúc lượt này và có thể mở lượt mới.</p>
        <div className="actions"><button type="button" className="button-danger" disabled={pending || conflict} onClick={() => void abandon()}>Xác nhận bỏ dở</button><button type="button" disabled={pending} onClick={() => setConfirmAbandon(false)}>Tiếp tục làm bài</button></div>
      </div>}
    </form>}
    {attempt.result && <ListeningResult result={attempt.result} questions={attempt.questions} />}
    {attempt.status === 'ABANDONED' && <p className="notice">Lượt này đã bỏ dở. Bạn có thể bắt đầu một lượt mới.</p>}
    {!editable && <StartButton exerciseId={attempt.exercise.id} onSessionExpired={onSessionExpired}>Làm lại</StartButton>}
    </div></div>
  </>
}

function ListeningAudio({ id }: { id: string }) {
  const audio = useRef<HTMLAudioElement>(null)
  const [rate, setRate] = useState(1)
  const [failed, setFailed] = useState(false)
  const src = apiUrl(`${attemptPath}/${id}/audio`)
  return <section className="listening-card listening-player" aria-labelledby="listening-audio-title"><header className="listening-panel-heading"><Icon name="volume" /><h2 id="listening-audio-title">Nghe tình huống</h2></header><p className="hint">Nghe lại khi cần. Chọn tốc độ phù hợp trước khi trả lời.</p>
    <audio ref={audio} src={src} controls preload="metadata" aria-label="Phát audio bài nghe"
      onLoadedMetadata={event => { event.currentTarget.playbackRate = rate }} onError={() => setFailed(true)} />
    <div className="listening-player-actions"><label>Tốc độ phát <select value={rate} onChange={event => {
      const next = Number(event.target.value)
      setRate(next)
      if (audio.current) audio.current.playbackRate = next
    }}><option value={1}>1×</option><option value={0.75}>0,75×</option></select></label>
    <button type="button" onClick={() => {
      if (audio.current) { audio.current.currentTime = 0; void audio.current.play().catch(() => setFailed(true)) }
    }} disabled={failed}><Icon name="history" size={20} />Nghe lại từ đầu</button></div>
    {failed && <div role="alert"><p>Chưa phát được audio. Bạn vẫn có thể lưu nháp.</p>
      <button type="button" onClick={() => { setFailed(false); audio.current?.load() }}>Thử tải lại audio</button></div>}
  </section>
}

function QuestionInput({ question, index, value, disabled, change }: {
  question: Question; index: number; value: Answer | undefined; disabled: boolean; change: (value: Answer) => void
}) {
  const name = useId()
  const ordered = Array.isArray(value) ? value : []
  return <fieldset className="listening-card listening-question" disabled={disabled}>
    <legend><span className="listening-question-number">Câu {index + 1}</span>{question.prompt}</legend>
    {question.type === 'MULTIPLE_CHOICE' && question.options.map(option => <label key={option.code} className="listening-choice">
      <input type="radio" name={name} checked={option.code === value} onChange={() => change(option.code)} /> {option.text}
    </label>)}
    {question.type === 'REORDER' && <>
      <p>Bấm từng lựa chọn theo thứ tự bạn nghe được.</p>
      <ol aria-label="Thứ tự đã chọn">{ordered.map(code => <li key={code}>{optionLabel(question, code)}</li>)}</ol>
      <div className="actions">
        {question.options.map(option => <button key={option.code} type="button" disabled={ordered.includes(option.code)}
          onClick={() => change([...ordered, option.code])}>{option.text}</button>)}
        {ordered.length > 0 && <button type="button" onClick={() => change(ordered.slice(0, -1))}>Bỏ mục cuối</button>}
      </div>
    </>}
    {(question.type === 'FILL_BLANK' || question.type === 'DICTATION') && <label className="form-field">
      Đáp án câu {index + 1}
      <textarea rows={question.type === 'DICTATION' ? 3 : 2} maxLength={400} lang="en"
        value={typeof value === 'string' ? value : ''} onChange={event => change(event.target.value)} />
    </label>}
  </fieldset>
}

function optionLabel(question: Question | undefined, answer: string) {
  return question?.options.find(option => option.code === answer)?.text ?? answer
}

function ListeningResult({ result, questions }: { result: Result; questions: Question[] }) {
  return <section className="listening-card listening-result" aria-labelledby="listening-result">
    <header className="listening-result-heading"><div><h2 id="listening-result">Kết quả bài nghe</h2><p>Đọc lại lời thoại và xem giải thích từng câu.</p></div><p className="listening-score"><strong>{result.score}/{result.maxScore}</strong><span>điểm</span></p></header>
    <div className="listening-transcript"><h3>Lời thoại</h3><p lang="en">{result.transcriptEn}</p>{result.translationVi && <details><summary>Xem bản dịch</summary><p>{result.translationVi}</p></details>}</div>
    <ol className="listening-result-list">
      {result.details.map(detail => {
        const question = questions.find(item => item.code === detail.questionCode)
        const render = (values: string[]) => values.map(value => optionLabel(question, value))
          .join(question?.type === 'REORDER' ? ' → ' : ' / ')
        const response = detail.answerValue
        return <li key={detail.questionCode}>
          <div className="listening-result-row"><h3>{question?.prompt}</h3><span className={`status-badge ${detail.pointsAwarded > 0 ? 'status-success' : 'status-warning'}`}>{detail.pointsAwarded > 0 ? 'Đúng' : 'Chưa đúng'} ({detail.pointsAwarded}/1)</span></div>
          <p><strong>Bạn trả lời</strong> {response ? render(Array.isArray(response) ? response : [response]) : 'Bỏ trống'}</p>
          <p><strong>Đáp án</strong> {render(detail.accepted)}</p>
          <p>{detail.explanationVi}</p>
        </li>
      })}
    </ol>
  </section>
}

export function ListeningHistory({ isAdmin, onSessionExpired }: SessionGuard & { isAdmin: boolean }) {
  const [exercisePage, setExercisePage] = useState(0)
  const [page, setPage] = useState(0)
  const { data: catalog, error: catalogError, reload: reloadCatalog } =
    useApiQuery<Page<Exercise>>(`listening/exercises?page=${exercisePage}`, onSessionExpired)
  const { data, error, reload } = useApiQuery<Page<History>>(`${attemptPath}?page=${page}`, onSessionExpired)
  useEffect(() => {
    function onVisible() {
      if (document.visibilityState === 'visible') reloadCatalog()
    }
    document.addEventListener('visibilitychange', onVisible)
    return () => document.removeEventListener('visibilitychange', onVisible)
  }, [reloadCatalog])

  return <section className="listening-section" aria-labelledby="listening-title">
    <PageHeading id="listening-title" title="Luyện nghe" description="Lắng nghe những tình huống quen thuộc, trả lời và kiểm tra điều bạn hiểu." />
    <section aria-labelledby="listening-catalog-title">
      <div className="listening-section-heading"><h2 id="listening-catalog-title">Chọn một bài nghe</h2><Icon name="volume" /></div>
      {catalogError && <LoadError error={catalogError} reload={reloadCatalog} />}
      {!catalog && !catalogError && <p role="status">Đang tải đề nghe…</p>}
      {catalog && <>
        {catalog.total === 0 && <p className="empty-state" role="status">Chưa có đề nghe công khai. Hãy quay lại khi có bài mới.</p>}
        <div className="listening-catalog-grid">{catalog.items.map(exercise => <article className="listening-card" key={exercise.id}>
          <span className="level-badge">{exercise.level}</span><h3>{exercise.title}</h3>
          <p>{exercise.instructions}</p>
          <StartButton exerciseId={exercise.id} onSessionExpired={onSessionExpired}>Bắt đầu hoặc tiếp tục</StartButton>
        </article>)}</div>
        <Pagination page={exercisePage} size={catalog.size} total={catalog.total} onPage={setExercisePage} />
      </>}
    </section>
    <section className="listening-section" aria-labelledby="listening-history-title">
      <h2 id="listening-history-title">Lịch sử luyện nghe</h2>
      {error && <LoadError error={error} reload={reload} />}
      {!error && !data && <p role="status">Đang tải lịch sử…</p>}
      {data && <>
        {data.items.length === 0 && <p className="empty-state" role="status">Chưa có lượt luyện nghe. Chọn một bài nghe phía trên để bắt đầu.</p>}
        <ul className="listening-history">{data.items.map(item => <li key={item.id} className="listening-card">
          <div className="listening-history-copy"><a href={`#/listening/attempts/${item.id}`}>{item.exercise.title}</a><time dateTime={item.startedAt}>{new Date(item.startedAt).toLocaleString('vi-VN')}</time></div>
          <div className="listening-history-meta"><span className="level-badge">{item.exercise.level}</span><span className="status-badge">{statusLabels[item.status]}</span>{item.score !== null && <strong>{item.score}/{item.maxScore} điểm</strong>}</div>
        </li>)}</ul>
        <Pagination page={page} size={data.size} total={data.total} onPage={setPage} />
      </>}
    </section>
    {isAdmin && <PublishExercises onSessionExpired={onSessionExpired} onPublished={() => {
      setExercisePage(0)
      reloadCatalog()
    }} />}
  </section>
}

function PublishExercises({ onSessionExpired, onPublished }: SessionGuard & { onPublished: () => void }) {
  const { data, error, reload } = useApiQuery<Version[]>(publicationPath, onSessionExpired)
  const [selected, setSelected] = useState<Version>()
  const [pending, setPending] = useState(false)
  const [message, setMessage] = useState('')
  const [failure, setFailure] = useState('')
  async function publish(version: Version) {
    setPending(true)
    setFailure('')
    setMessage('')
    try {
      await mutateJson(`${publicationPath}/${version.id}/publish`)
      setMessage(`Đã công khai ${version.title}.`)
      setSelected(undefined)
      reload()
      onPublished()
    } catch (error) {
      setFailure(reportError(error, onSessionExpired))
    } finally {
      setPending(false)
    }
  }
  return <section className="listening-section" aria-labelledby="listening-publish">
    <h2 id="listening-publish">Công khai đề nghe</h2>
    {error && <LoadError error={error} reload={reload} />}
    {message && <p role="status">{message}</p>}
    {failure && <p role="alert">{failure}</p>}
    {!data && !error && <p role="status">Đang tải các phiên bản đề…</p>}
    {data?.filter(version => version.status === 'DRAFT').map(version => <div key={version.id} className="listening-card">
      <h3>{version.title} · {version.level}</h3>
      {selected?.id === version.id ? <>
        <p>Azure Speech có thể nhận một yêu cầu tạo audio. Khi công khai, phiên bản trước đó của bài sẽ được ẩn.</p>
        <div className="actions">
          <button type="button" className="button-primary" disabled={pending} onClick={() => void publish(version)}>
            {pending ? 'Đang chuẩn bị…' : 'Xác nhận công khai'}
          </button>
          <button type="button" disabled={pending} onClick={() => setSelected(undefined)}>Hủy</button>
        </div>
      </> : <button type="button" disabled={pending} onClick={() => setSelected(version)}>Chuẩn bị công khai</button>}
    </div>)}
    {data && data.every(version => version.status !== 'DRAFT') && <p>Không có phiên bản nháp.</p>}
  </section>
}

function LoadError({ error, reload }: { error: string; reload: () => void }) {
  return <div role="alert"><p>{error}</p><button type="button" onClick={reload}>Thử tải lại</button></div>
}
