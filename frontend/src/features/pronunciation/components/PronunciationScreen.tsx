import { useEffect, useRef, useState } from 'react'
import { ApiError, getJson, mutateForm } from '../../../shared/api/client'
import { useSpeech } from '../../../shared/hooks/useSpeech'
import { usePronunciationRecorder } from '../hooks/usePronunciationRecorder'
import { PageHeading } from '../../../shared/ui/PageHeading'
import { Icon } from '../../../shared/ui/Icon'
import '../styles/pronunciation.css'

type Drill = { id: number; lessonId: number; lessonTitle: string; level: string; referenceText: string; locale: string; mode: 'READ_ALOUD' | 'SHADOWING' }
type Catalog = { drills: Drill[]; scoringAvailable: boolean }
type Phoneme = { expectedIpa: string; accuracy: number | null }
type Word = { word: string; accuracy: number | null; errorCode: string | null; phonemes: Phoneme[] }
type Attempt = {
  id: number; drillId: number; drillMode: Drill['mode']; status: string; errorCode: string | null
  referenceText: string; locale: string; recognizedText: string | null; words: Word[]
  overall: number | null; accuracy: number | null; fluency: number | null; completeness: number | null
}
type History = { attempts: Attempt[]; page: number; hasNext: boolean }

const api = 'pronunciation'
const inProgress = (status: string) => status === 'PENDING' || status === 'RUNNING'
const modeName = (mode: Drill['mode']) => mode === 'SHADOWING' ? 'Nói đuổi' : 'Đọc theo mẫu'

function statusText(status: string, error: string | null) {
  if (status === 'PENDING' && error === 'AZURE_QUOTA') return 'Dịch vụ đang bận, sẽ tự thử lại một lần.'
  if (status === 'PENDING') return 'Đang chờ chấm.'
  if (status === 'RUNNING') return 'Azure đang chấm bản ghi.'
  if (status === 'SUCCEEDED') return 'Đã chấm.'
  if (status === 'INSUFFICIENT_EVIDENCE') return 'Chưa đủ tiếng nói để chấm. Hãy thu lại ở nơi yên tĩnh.'
  if (status === 'UNKNOWN') return 'Chưa xác định được kết quả. Bạn có thể thu một bản mới.'
  if (error === 'AZURE_QUOTA') return 'Dịch vụ đã chạm hạn mức. Hãy thử sau.'
  return 'Chấm thất bại. Bạn có thể nghe mẫu và thu một bản mới.'
}

function showFailure(failure: unknown, onExpired: () => void) {
  if (failure instanceof ApiError && failure.status === 401) onExpired()
  return failure instanceof Error ? failure.message : 'Có lỗi khi xử lý yêu cầu.'
}

export function PronunciationScreen({ onSessionExpired, lessonId }: {
  onSessionExpired: () => void; lessonId?: string
}) {
  const [catalog, setCatalog] = useState<Catalog | null>(null)
  const [history, setHistory] = useState<History | null>(null)
  const [page, setPage] = useState(0)
  const [reload, setReload] = useState(0)
  const [drillId, setDrillId] = useState<number>()
  const [attemptId, setAttemptId] = useState<number>()
  const [catalogError, setCatalogError] = useState('')
  const [historyError, setHistoryError] = useState('')
  const mic = usePronunciationRecorder()
  const speech = useSpeech()
  const preview = useRef<HTMLAudioElement>(null)
  const expired = useRef(onSessionExpired)
  useEffect(() => { expired.current = onSessionExpired }, [onSessionExpired])

  useEffect(() => {
    const action = new AbortController()
    getJson(`${api}/drills`, action.signal).then(value => {
      setCatalog(value as Catalog)
    }).catch(failure => {
      if (!action.signal.aborted) setCatalogError(showFailure(failure, () => expired.current()))
    })
    return () => action.abort()
  }, [reload])

  useEffect(() => {
    const action = new AbortController()
    getJson(`${api}/attempts?page=${page}`, action.signal).then(value => {
      setHistory(value as History)
    }).catch(failure => {
      if (!action.signal.aborted) setHistoryError(showFailure(failure, () => expired.current()))
    })
    return () => action.abort()
  }, [page, reload])

  const drills = catalog?.drills.filter(item => !lessonId || item.lessonId.toString() === lessonId) ?? []
  const drill = drills.find(item => item.id === drillId)
  function refresh() {
    setCatalogError('')
    setHistoryError('')
    setReload(value => value + 1)
  }

  return <section className="pronunciation-screen" aria-labelledby="pronunciation-title">
    <PageHeading id="pronunciation-title" title="Luyện phát âm" description="Nghe một câu mẫu, thử đọc bằng giọng của bạn và luyện lại từng từ." />
    <div className="pronunciation-layout"><div className="pronunciation-main"><div className="pronunciation-picker">
    {lessonId ? <p><a href="#/pronunciation">Xem tất cả bài phát âm</a></p> : null}
    {catalogError ? <p role="alert">{catalogError} <button type="button" onClick={refresh}>Tải lại</button></p> : null}
    {!catalog && !catalogError ? <p role="status">Đang tải bài phát âm…</p> : null}
    {catalog ? <>
      <label className="pronunciation-picker-label">Chọn bài luyện
        <select value={drillId ?? ''} onChange={event => {
          speech.stop()
          mic.discard()
          setAttemptId(undefined)
          setDrillId(event.currentTarget.value ? Number(event.currentTarget.value) : undefined)
        }}>
          <option value="">Chọn câu bạn muốn luyện</option>
          {drills.map(item => <option key={item.id} value={item.id}>
            {item.lessonTitle} ({item.level}) — {modeName(item.mode)}: {item.referenceText.slice(0, 38)}
          </option>)}
        </select>
      </label>
      {!drills.length ? <p>Chưa có bài phát âm khả dụng.</p> : null}
      {!drill && drills.length > 0 ? <p className="pronunciation-help">Chọn một bài để nghe mẫu và bắt đầu. Bạn có thể nghe lại trước khi thu âm.</p> : null}
    </> : null}</div>
      {drill && catalog ? <DrillRecorder key={drill.id} drill={drill} scoringAvailable={catalog.scoringAvailable} mic={mic} speech={speech} preview={preview}
        onSessionExpired={() => expired.current()} onSubmitted={id => {
          setAttemptId(id)
          setPage(0)
          refresh()
        }} /> : null}

    {attemptId !== undefined ? <AttemptResult key={attemptId} id={attemptId}
      onSessionExpired={() => expired.current()} onFinished={refresh}
      speech={speech} canPlay={mic.phase === 'idle'} onPlay={() => preview.current?.pause()} /> : null}
    </div><aside className="pronunciation-history" aria-labelledby="pronunciation-history-title">
      <header className="pronunciation-section-heading"><Icon name="history" /><h2 id="pronunciation-history-title">Lịch sử phát âm</h2></header><p className="pronunciation-help">Mở một lượt để xem lại nhận xét.</p>
      {historyError ? <p role="alert">{historyError} <button type="button" onClick={refresh}>Tải lại</button></p> : null}
      {!history && !historyError ? <p role="status">Đang tải lịch sử…</p> : null}
      {history?.attempts.length === 0 ? <p className="pronunciation-history-empty">Chưa có lượt chấm trên trang này. Nghe mẫu và gửi một bản ghi để bắt đầu.</p> : null}
      {history?.attempts.length ? <ul className="pronunciation-history-list">{history.attempts.map(item => <li key={item.id}>
        <button type="button" aria-pressed={attemptId === item.id} onClick={() => setAttemptId(item.id)}>
          <span lang="en-US">{item.referenceText.slice(0, 54)}</span><small>{statusText(item.status, item.errorCode)}</small>
        </button>
      </li>)}</ul> : null}
      <nav className="pronunciation-pagination" aria-label="Phân trang lịch sử phát âm"><button type="button" disabled={page === 0} onClick={() => setPage(page - 1)}>Trang trước</button>{' '}
      <button type="button" disabled={!history?.hasNext || page !== history.page} onClick={() => setPage(page + 1)}>Trang sau</button></nav>
    </aside></div>
  </section>
}

function DrillRecorder({ drill, scoringAvailable, onSubmitted, onSessionExpired, mic, speech, preview }: {
  drill: Drill; scoringAvailable: boolean; onSubmitted: (id: number) => void; onSessionExpired: () => void
  mic: ReturnType<typeof usePronunciationRecorder>; speech: ReturnType<typeof useSpeech>
  preview: React.RefObject<HTMLAudioElement | null>
}) {
  const [posting, setPosting] = useState(false)
  const [error, setError] = useState('')
  const upload = useRef<AbortController | null>(null)
  useEffect(() => () => upload.current?.abort(), [])
  const busy = mic.phase !== 'idle'
  const tooLong = Boolean(mic.recording && mic.recording.seconds > 30)
  const sampleLines = drill.referenceText.split(/(?<=[.!?])\s+/)

  async function submit() {
    if (!mic.recording || upload.current || tooLong) return
    speech.stop()
    preview.current?.pause()
    const action = new AbortController()
    upload.current = action
    setPosting(true)
    setError('')
    const body = new FormData()
    body.set('requestId', mic.recording.key)
    body.set('audio', mic.recording.wav, 'recording.wav')
    try {
      const result = await mutateForm(`${api}/drills/${drill.id}/attempts`, body, action.signal) as Attempt
      if (action.signal.aborted) return
      mic.discard()
      onSubmitted(result.id)
    } catch (failure) {
      if (!action.signal.aborted) setError(showFailure(failure, onSessionExpired))
    } finally {
      if (!action.signal.aborted) {
        upload.current = null
        setPosting(false)
      }
    }
  }

  return <section className="pronunciation-practice" aria-label="Bài luyện đã chọn">
    <div className="pronunciation-drill-meta"><span className="level-badge">{drill.level}</span><span>{drill.lessonTitle}</span></div>
    <h2>{modeName(drill.mode)}</h2>
    <p>{drill.mode === 'SHADOWING' ? 'Nghe mẫu, dừng phát rồi thu âm lời bạn vừa nghe.' : 'Đọc từ hoặc đoạn dưới đây bằng giọng của bạn.'}</p>
    <blockquote className="pronunciation-sample" lang="en-US">{drill.referenceText}</blockquote>
    <div className="pronunciation-actions">
      <button type="button" disabled={busy || posting} onClick={() => {
        preview.current?.pause()
        speech.play('sample', drill.referenceText)
      }}><Icon name="volume" />Nghe mẫu</button>
      <button type="button" disabled={busy || posting} onClick={() => {
        preview.current?.pause()
        speech.play('sample', drill.referenceText, 0.75)
      }}>Nghe chậm</button>
      {speech.playing === 'sample' ? <button type="button" onClick={speech.stop}>Dừng phát mẫu</button> : null}
    </div>
    {sampleLines.length > 1 ? <details className="pronunciation-line-details"><summary>Nghe từng câu mẫu</summary><ol className="pronunciation-sample-lines" aria-label="Nghe từng câu mẫu">{sampleLines.map((line, index) =>
      <li key={index}><span lang="en-US">{line}</span>{' '}
        <button type="button" disabled={busy || posting} onClick={() => {
          preview.current?.pause()
          speech.play('sample', line)
        }}>Nghe câu {index + 1}</button>
      </li>)}</ol></details> : null}
    {speech.speechError ? <p role="status">{speech.speechError}</p> : null}
    <div className="pronunciation-recording-area"><h3><Icon name="microphone" />Bản ghi của bạn</h3><p className="pronunciation-help">Có thể thu tối đa 60 giây. Để gửi chấm, hãy giữ bản ghi trong 30 giây.</p>
    {!mic.supported ? <p role="status">Trình duyệt này chưa hỗ trợ thu âm.</p> : null}
    {mic.phase === 'recording' ? <p className="pronunciation-recording-status"><span>Đang thu</span><span role="timer" aria-live="off">{mic.seconds} / 60 giây</span></p> : null}
    {mic.phase === 'permission' ? <p role="status">Đang chờ quyền micro…</p> : null}
    {mic.phase === 'converting' ? <p role="status">Đang chuẩn bị bản nghe lại…</p> : null}
    {mic.error ? <p role="alert">{mic.error}</p> : null}
    <div className="pronunciation-actions">
      {!busy && mic.supported ? <button className="button-primary" type="button" disabled={posting} onClick={() => {
        speech.stop()
        preview.current?.pause()
        setError('')
        void mic.start()
      }}><Icon name="microphone" />{mic.recording ? 'Thu lại' : 'Bắt đầu thu'}</button> : null}
      {mic.phase === 'recording' ? <button className="button-danger" type="button" onClick={mic.stop}><Icon name="close" />Dừng thu</button> : null}
      {busy || mic.recording ? <button type="button" disabled={posting} onClick={() => {
        preview.current?.pause()
        mic.discard()
        setError('')
      }}>Hủy bản ghi</button> : null}
    </div>
    {mic.recording ? <div className="pronunciation-preview">
      <p>Nghe lại bản ghi của bạn</p>
      <audio ref={preview} controls preload="metadata" src={mic.recording.url} onPlay={speech.stop} />
      {tooLong ? <p role="alert">Bản ghi dài hơn 30 giây. Hãy thu lại một đoạn ngắn hơn để gửi chấm.</p> : null}
      <p>Khi bấm gửi, bản ghi được chuyển đến Microsoft Azure để chấm. Tệp tạm được dọn sau khi xử lý, với hạn lưu 1 giờ.</p>
      <button className="button-primary" type="button" disabled={!scoringAvailable || posting || tooLong} onClick={() => void submit()}>
        {posting ? 'Đang gửi…' : error ? 'Gửi lại bản này' : 'Gửi chấm'}
      </button>
    </div> : null}
    {!scoringAvailable ? <p role="status">Chấm phát âm chưa khả dụng. Bạn vẫn có thể nghe mẫu, thu âm và nghe lại trên thiết bị.</p> : null}
    {error ? <p role="alert">{error}</p> : null}
    </div>
  </section>
}

function AttemptResult({ id, onSessionExpired, onFinished, speech, canPlay, onPlay }: {
  id: number; onSessionExpired: () => void; onFinished: () => void
  speech: ReturnType<typeof useSpeech>; canPlay: boolean; onPlay: () => void
}) {
  const [attempt, setAttempt] = useState<Attempt | null>(null)
  const [error, setError] = useState('')
  const [retry, setRetry] = useState(0)
  const callbacks = useRef({ onSessionExpired, onFinished })
  useEffect(() => { callbacks.current = { onSessionExpired, onFinished } }, [onSessionExpired, onFinished])

  useEffect(() => {
    const action = new AbortController()
    let timer = 0
    let wasPending = false
    async function load() {
      try {
        const value = await getJson(`${api}/attempts/${id}`, action.signal) as Attempt
        if (action.signal.aborted) return
        setAttempt(value)
        if (inProgress(value.status)) {
          wasPending = true
          timer = window.setTimeout(() => void load(), 2500)
        } else if (wasPending) {
          callbacks.current.onFinished()
        }
      } catch (failure) {
        if (!action.signal.aborted) setError(showFailure(failure, callbacks.current.onSessionExpired))
      }
    }
    void load()
    return () => { action.abort(); window.clearTimeout(timer) }
  }, [id, retry])

  return <section className="pronunciation-result" aria-label="Kết quả chấm">
    <h2>Kết quả phát âm</h2>
    {error ? <p role="alert">{error} <button type="button" onClick={() => {
      setError('')
      setRetry(retry + 1)
    }}>Tải lại kết quả</button></p> : null}
    {!attempt && !error ? <p role="status">Đang tải kết quả…</p> : null}
    {attempt ? <>
      <p role="status" className="notice">{statusText(attempt.status, attempt.errorCode)}</p>
      <div className="pronunciation-result-text"><h3>Câu mẫu</h3><p lang="en-US">{attempt.referenceText}</p></div>
      {attempt.recognizedText ? <div className="pronunciation-result-text"><h3>Lời nhận dạng</h3><p lang="en-US">{attempt.recognizedText}</p></div> : null}
      {attempt.status === 'SUCCEEDED' ? <>
        <p className="pronunciation-help">Điểm phản ánh bản ghi so với câu mẫu, không phải chứng nhận trình độ hay tỷ lệ phát âm đúng tuyệt đối.</p>
        <dl className="pronunciation-scores">
          {([['Tổng thể', attempt.overall], ['Độ chính xác', attempt.accuracy],
            ['Độ trôi chảy', attempt.fluency], ['Độ đầy đủ', attempt.completeness]] as const).map(([label, score]) =>
            <div key={label}><dt>{label}</dt><dd>{score === null ? 'Chưa có điểm' : `${score}/100`}</dd></div>)}
        </dl>
        {attempt.words.length ? <ul className="pronunciation-words">{attempt.words.map((word, index) => <li key={index}>
          <div className="pronunciation-word-heading"><strong lang="en-US">{word.word}</strong>
            {word.errorCode === 'OMISSION' ? <span className="status-badge status-warning">Bỏ sót</span> : word.errorCode === 'INSERTION' ? <span className="status-badge">Nói thêm</span> : word.errorCode === 'MISPRONUNCIATION' ? <span className="status-badge status-warning">Cần luyện thêm</span> : null}
            {word.accuracy !== null ? <span className="pronunciation-word-score">{word.accuracy}/100</span> : null}
          </div>
          <button type="button" disabled={!canPlay} onClick={() => { onPlay(); speech.play(`word-${index}`, word.word) }}><Icon name="volume" size={20} />Nghe từ <span lang="en-US">{word.word}</span></button>
          {word.phonemes.length ? <p>Âm vị {word.phonemes.map(sound =>
            `/${sound.expectedIpa}/${sound.accuracy === null ? '' : ` ${sound.accuracy}/100`}`).join(' · ')}</p> : null}
        </li>)}</ul> : null}
        {speech.playing?.startsWith('word-') ? <button type="button" onClick={speech.stop}>Dừng nghe từ</button> : null}
        {speech.speechError ? <p role="status">{speech.speechError}</p> : null}
      </> : null}
    </> : null}
  </section>
}
