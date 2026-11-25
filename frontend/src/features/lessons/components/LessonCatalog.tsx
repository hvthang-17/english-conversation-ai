import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { ApiError } from '../../../shared/api/client'
import { Icon } from '../../../shared/ui/Icon'
import { PageHeading } from '../../../shared/ui/PageHeading'
import { Pagination } from '../../../shared/ui/Pagination'
import type { Topic } from '../../scenarios/api/catalog-api'
import { getLesson, getLessons, setBookmark, type AvailableLesson, type BookmarkKind, type Lesson, type LessonPage } from '../api/lesson-api'
import { LessonMaterialsPanel } from './LessonMaterialsPanel'
import { LessonListening } from '../../listening/components/ListeningScreens'
import '../styles/lesson-catalog.css'

type SessionProps = { onSessionExpired: () => void }
const marks = [
  { kind: 'FAVORITE', label: 'Yêu thích', field: 'favorite' },
  { kind: 'SAVE_FOR_LATER', label: 'Học sau', field: 'savedForLater' },
] as const

function apiError(failure: unknown, onSessionExpired: () => void) {
  if (failure instanceof ApiError && failure.status === 401) {
    onSessionExpired()
    return ''
  }
  return failure instanceof Error ? failure.message : 'Không thể hoàn tất. Vui lòng thử lại.'
}

function BookmarkControls({ item, onSessionExpired, onChanged }: SessionProps & {
  item: Lesson
  onChanged: () => void
}) {
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')

  async function change(kind: BookmarkKind, marked: boolean) {
    setPending(true)
    setError('')
    try {
      await setBookmark(item.id, kind, marked)
      onChanged()
    } catch (failure) {
      setError(apiError(failure, onSessionExpired))
    } finally {
      setPending(false)
    }
  }

  return <div className="lesson-bookmarks" aria-busy={pending}>
    {marks.filter(mark => item.available || item[mark.field]).map(mark =>
      <button key={mark.kind} type="button" disabled={pending} aria-pressed={item[mark.field]} aria-label={item.available ? `${mark.label}: ${item.title}` : `Bỏ dấu ${mark.label.toLowerCase()}`}
        onClick={() => void change(mark.kind, !item[mark.field])}>
        <Icon name={mark.kind === 'FAVORITE' ? 'heart' : 'bookmark'} size={20} />{item.available ? item[mark.field] ? mark.kind === 'FAVORITE' ? 'Đã yêu thích' : 'Đã lưu học sau' : mark.label : `Bỏ dấu ${mark.label.toLowerCase()}`}
      </button>)}
    {error ? <p role="alert">{error}</p> : null}
  </div>
}

export function LessonCatalog({ location, topics, saved, onSessionExpired }: SessionProps & {
  location: string
  topics: Topic[]
  saved: boolean
}) {
  const query = useMemo(() => new URLSearchParams(location.split('?')[1] ?? ''), [location])
  const kind: BookmarkKind | null = saved ? query.get('kind') === 'SAVE_FOR_LATER' ? 'SAVE_FOR_LATER' : 'FAVORITE' : null
  const [result, setResult] = useState<LessonPage | null>(null)
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    getLessons(query, kind, controller.signal).then(value => {
      if (controller.signal.aborted) return
      if (saved && value.items.length === 0 && value.page > 0) {
        const lastPage = Math.max(0, Math.ceil(value.total / value.size) - 1)
        window.location.hash = `/saved-lessons?kind=${kind}&page=${lastPage}`
        return
      }
      setResult(value)
    }).catch(failure => {
      if (!controller.signal.aborted) setError(apiError(failure, onSessionExpired))
    })
    return () => controller.abort()
  }, [kind, onSessionExpired, query, revision, saved])

  function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const next = new URLSearchParams()
    for (const name of ['q', 'level', 'topicId']) {
      const value = String(form.get(name) ?? '').trim()
      if (value) next.set(name, value)
    }
    window.location.hash = next.size ? `/lessons?${next}` : '/lessons'
  }

  function changePage(page: number) {
    const next = new URLSearchParams(query)
    next.set('page', String(page))
    window.location.hash = `/${saved ? 'saved-lessons' : 'lessons'}?${next}`
  }

  function reload() {
    setResult(null)
    setError('')
    setRevision(value => value + 1)
  }

  return <section className="lesson-library" aria-labelledby="lesson-library-title">
    <PageHeading id="lesson-library-title" title={saved ? 'Bài đã lưu' : 'Khám phá bài học'} description={saved ? 'Những bài bạn muốn quay lại. Yêu thích và Học sau được lưu riêng.' : 'Chọn một chủ đề gần với cuộc sống, học cách diễn đạt rồi thử một cuộc trò chuyện.'} />
    {saved ? <nav className="lesson-saved-tabs" aria-label="Loại dấu đã lưu">
      {marks.map(mark => <a key={mark.kind} className="button-link" href={`#/saved-lessons?kind=${mark.kind}`}
        aria-current={kind === mark.kind ? 'page' : undefined}>{mark.label}</a>)}
    </nav> : <form className="lesson-filters" aria-label="Tìm và lọc bài học" onSubmit={search}>
      <label className="form-field">Tìm bài học
        <input name="q" type="search" placeholder="Tên bài hoặc điều bạn muốn học" defaultValue={query.get('q') ?? ''} maxLength={120} />
      </label>
      <label className="form-field">Chủ đề
        <select name="topicId" defaultValue={query.get('topicId') ?? ''}>
          <option value="">Tất cả chủ đề</option>
          {topics.map(topic => <option key={topic.id} value={topic.id}>{topic.name}</option>)}
        </select>
      </label>
      <label className="form-field">Cấp độ
        <select name="level" defaultValue={query.get('level') ?? ''}>
          <option value="">Tất cả cấp độ</option><option>A2</option><option>B1</option>
        </select>
      </label>
      <div className="lesson-filter-actions">
        <button type="submit" className="button-primary"><Icon name="book" size={20} />Tìm bài</button>
        {query.size > 0 ? <a href="#/lessons">Xóa bộ lọc</a> : null}
      </div>
    </form>}
    {error ? <div role="alert"><p>{error}</p><button type="button" onClick={reload}>Thử lại</button></div> : null}
    {!result && !error ? <p role="status">Đang tải bài học…</p> : null}
    {result ? <>
      <p className="lesson-result-count" role="status">{result.total} bài học{saved ? ' đã lưu' : ' phù hợp'}</p>
      {result.items.length ? <ul className="lesson-results" aria-label="Danh sách bài học">
        {result.items.map(item => <li key={item.id} className="lesson-result">
          <div>
            {item.available ? <>
              <div className="lesson-result-topic"><span>{item.topicName}</span><span className="level-badge">{item.level}</span></div>
              <h2><a href={`#/lessons/${item.id}`}>{item.title}</a></h2>
              <p>{item.summary}</p>
              <p className="lesson-card-objective"><Icon name="target" size={20} /><span>{item.learningObjective}</span></p>
            </> : <><span className="status-badge status-warning">Không còn khả dụng</span><h2>Không thể mở bài này</h2><p className="hint">Bạn vẫn có thể bỏ dấu đã lưu của bài này.</p></>}
          </div>
          <BookmarkControls item={item} onSessionExpired={onSessionExpired} onChanged={reload} />
        </li>)}
      </ul> : <div className="empty-state lesson-empty"><Icon name="book" size={32} /><h2>{saved ? 'Chưa có bài trong danh sách này' : 'Không tìm thấy bài học phù hợp'}</h2><p>{saved ? 'Đánh dấu bài yêu thích hoặc lưu để học sau khi khám phá bài học.' : 'Thử từ khóa ngắn hơn hoặc bỏ bớt bộ lọc.'}</p><a href="#/lessons">{saved ? 'Khám phá bài học' : 'Xem tất cả bài học'}</a></div>}
      <Pagination page={result.page} size={result.size} total={result.total} onPage={changePage} />
    </> : null}
  </section>
}

export function LessonDetail({ id, materialsAvailable, listeningAvailable, pronunciationAvailable, onSessionExpired }: SessionProps & { id: string; materialsAvailable: boolean; listeningAvailable: boolean; pronunciationAvailable?: boolean }) {
  const [item, setItem] = useState<AvailableLesson | null>(null)
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    getLesson(id, controller.signal).then(value => {
      if (!controller.signal.aborted) setItem(value)
    }).catch(failure => {
      if (!controller.signal.aborted) setError(apiError(failure, onSessionExpired))
    })
    return () => controller.abort()
  }, [id, onSessionExpired, revision])

  function reload() {
    setItem(null)
    setError('')
    setRevision(value => value + 1)
  }

  return <section className="lesson-library lesson-detail-page">
    <a className="back-link" href="#/lessons"><Icon name="back" />Về khám phá bài học</a>
    {error ? <div role="alert"><p>{error}</p><button type="button" onClick={reload}>Thử lại</button></div> : null}
    {!item && !error ? <p role="status">Đang tải bài học…</p> : null}
    {item ? <>
      <div className="lesson-result-topic"><span>{item.topicName}</span><span className="level-badge">{item.level}</span></div>
      <PageHeading id="lesson-detail-title" title={item.title} description={item.summary} />
      <div className="lesson-detail-overview"><section className="lesson-detail-content" aria-labelledby="lesson-objective-title">
        <h2 id="lesson-objective-title"><Icon name="target" />Mục tiêu bài học</h2>
        <p>{item.learningObjective}</p><p className="hint">Học từ và mẫu câu, nghe cách dùng trong hội thoại, rồi thử diễn đạt theo cách của bạn.</p>
      </section><aside className="lesson-study-actions" aria-label="Lựa chọn học tập">
        <h2>Thực hành theo bài</h2>
        {item.scenarioId && item.scenarioTitle ? <a className="lesson-practice-link" href={`#/scenarios/${item.scenarioId}?level=${item.level}`}><Icon name="conversation" /><span><strong>Luyện hội thoại</strong><small>{item.scenarioTitle}</small></span><Icon name="chevron" size={20} /></a> : null}
        {pronunciationAvailable ? <a className="lesson-practice-link" href={`#/pronunciation?lesson=${id}`}><Icon name="microphone" /><span><strong>Luyện phát âm</strong><small>Nghe và đọc theo câu mẫu của bài.</small></span><Icon name="chevron" size={20} /></a> : null}
        <BookmarkControls item={item} onSessionExpired={onSessionExpired} onChanged={reload} />
      </aside></div>
      {materialsAvailable ? <LessonMaterialsPanel id={id} onSessionExpired={onSessionExpired} /> : null}
      {listeningAvailable ? <LessonListening lessonId={id} onSessionExpired={onSessionExpired} /> : null}
    </> : null}
  </section>
}
