import { useEffect, useRef, useState, type FormEvent } from 'react'
import { ApiError, getJson, mutateJson } from '../../../shared/api/client'
import { PageHeading } from '../../../shared/ui/PageHeading'
import { Icon } from '../../../shared/ui/Icon'
import { SessionFeedback } from '../../feedback/components/SessionFeedback'
import { VoiceTurn } from './VoiceTurn'
import { useSpeech } from '../../../shared/hooks/useSpeech'
import type { PracticeMode } from '../types'
import { AssistantMessage } from './AssistantMessage'
import { MessageExplanation } from './MessageExplanation'
import '../styles/practice-chat.css'

type Operation = { id: string; task: string; status: string; errorCode: string | null; retryable: boolean; retryNotBefore: string | null }
type PronunciationWord = {
  word: string; accuracy: number | null; errorCode: string | null
  phonemes: { expectedIpa: string; accuracy: number | null }[]
}
type Pronunciation = {
  status: string; errorCode?: string | null; overall: number | null; accuracy: number | null; fluency: number | null
  recognizedText?: string | null; words?: PronunciationWord[]
}

const wordNotes: Record<string, string> = {
  MISPRONUNCIATION: 'Cần luyện phát âm',
  UNEXPECTEDBREAK: 'Ngắt không đúng chỗ',
  MISSINGBREAK: 'Thiếu chỗ ngắt',
  MONOTONE: 'Giọng đều',
}

function PronunciationDetails({ result }: { result: Pronunciation }) {
  return <details className="pronunciation-details">
    <summary>Xem chi tiết phát âm</summary>
    {result.recognizedText ? <p>Azure nghe được <span lang="en">{result.recognizedText}</span></p> : null}
    {result.words?.length ? <ol aria-label="Điểm phát âm từng từ">
      {result.words.map((word, index) => <li key={index}>
        <strong lang="en">{word.word}</strong>
        {word.accuracy !== null ? <> · {word.accuracy}/100</> : null}
        {word.errorCode && wordNotes[word.errorCode] ? <> · {wordNotes[word.errorCode]}</> : null}
        {word.phonemes.length ? <p>Âm vị IPA {word.phonemes.map(sound =>
          `/${sound.expectedIpa}/${sound.accuracy === null ? '' : ` ${sound.accuracy}/100`}`).join(' · ')}</p> : null}
      </li>)}
    </ol> : <p>Lượt này chưa có dữ liệu chấm theo từ.</p>}
  </details>
}

function pronunciationScores(result: Pronunciation) {
  return [
    result.overall === null ? null : `Tổng ${result.overall}/100`,
    result.accuracy === null ? null : `Chính xác ${result.accuracy}/100`,
    result.fluency === null ? null : `Trôi chảy ${result.fluency}/100`,
  ].filter(Boolean).join(' · ')
}

function pronunciationStatus(result: Pronunciation) {
  if (result.status === 'PENDING' || result.status === 'RUNNING') return 'Đang chấm phát âm…'
  if (result.status === 'INSUFFICIENT_EVIDENCE') return 'Azure chưa nhận được đủ tiếng nói để chấm. Hãy thử ở lượt tiếp theo.'
  switch (result.errorCode) {
    case 'AZURE_QUOTA': return 'Đã chạm hạn mức chấm phát âm. Hãy thử sau.'
    case 'AUDIO_UNAVAILABLE':
    case 'PROCESSING_EXPIRED': return 'Bản ghi không còn khả dụng để chấm. Bạn có thể thu mới ở lượt tiếp theo.'
    case 'AZURE_INVALID_RESULT': return 'Azure trả kết quả chấm thiếu hoặc sai định dạng.'
    case 'AZURE_REJECTED':
    case 'AZURE_SDK_UNAVAILABLE':
    case 'AZURE_UNAVAILABLE': return 'Dịch vụ chấm phát âm chưa xử lý được yêu cầu.'
    default: return result.status === 'UNKNOWN' ? 'Chưa xác định được kết quả từ Azure.' : 'Lượt này chưa có điểm phát âm.'
  }
}
type Practice = {
  id: string; status: string; mode: PracticeMode; title: string; selectedLevel: string; learnerRole: string; aiRole: string
  communicationGoal: string; contextDescription: string; learnerTurns: number; maxLearnerTurns: number
  pronunciationAvailable: boolean
  messages: { id: string; sender: string; content: string; pronunciation?: Pronunciation | null }[]
  turns: { clientRequestId: string; requestId: string; turnNo: number; status: string }[]
  operation: Operation | null; hint: string | null; hintAlreadyConsumed: boolean
}

function practice(value: unknown): Practice {
  const result = value as Partial<Practice> | null
  if (!result || typeof result.id !== 'string' || typeof result.title !== 'string' ||
      (result.mode !== 'TEXT' && result.mode !== 'VOICE') ||
      !Array.isArray(result.messages) || !Array.isArray(result.turns)) {
    throw new Error('Phản hồi buổi luyện không hợp lệ.')
  }
  return result as Practice
}

function operationError(code: string | null) {
  if (code === 'PROVIDER_RATE_LIMIT') return 'Gemini đang giới hạn lượt gọi. Hãy chờ rồi thử lại.'
  if (code === 'PROVIDER_AUTH') return 'Dịch vụ hội thoại chưa xác thực được. Hãy báo quản trị viên.'
  return 'Chưa nhận được phản hồi phù hợp từ AI.'
}

export function PracticeChat({ id, onSessionExpired }: { id: string; onSessionExpired: () => void }) {
  const [data, setData] = useState<Practice | null>(null)
  const [error, setError] = useState('')
  const [pending, setPending] = useState('')
  const [refresh, setRefresh] = useState(0)
  const [draft, setDraft] = useState('')
  const [hint, setHint] = useState('')
  const [ack, setAck] = useState(false)
  const [voiceBusy, setVoiceBusy] = useState(false)
  const [explainBusy, setExplainBusy] = useState(false)
  const [capturing, setCapturing] = useState(false)
  const [keyboardOpen, setKeyboardOpen] = useState(false)
  const keyboardField = useRef<HTMLTextAreaElement>(null)
  const speech = useSpeech()
  const draftKey = useRef<{ text: string; id: string } | null>(null)
  const hintKey = useRef<string | null>(null)
  const busy = data?.operation?.status === 'RUNNING' || data?.operation?.status === 'PENDING'

  const scoring = data?.messages.some(line => line.pronunciation?.status === 'PENDING' || line.pronunciation?.status === 'RUNNING') ?? false
  const scored = data?.messages.flatMap(line => {
    const scores = line.pronunciation?.status === 'SUCCEEDED' ? pronunciationScores(line.pronunciation) : ''
    return scores ? [{ id: line.id, content: line.content, scores }] : []
  }) ?? []

  useEffect(() => {
    if (keyboardOpen) keyboardField.current?.focus()
  }, [keyboardOpen])

  useEffect(() => {
    if (pending) return
    const controller = new AbortController()
    function load() {
      getJson(`practice-sessions/${id}`, controller.signal).then(practice).then(next => {
        if (!controller.signal.aborted) setData(next)
      }).catch((failure: unknown) => {
        if (controller.signal.aborted) return
        if (failure instanceof ApiError && failure.status === 401) onSessionExpired()
        else setError(failure instanceof Error ? failure.message : 'Không tải được buổi luyện.')
      })
    }
    if (!busy) load()
    const timer = busy || scoring ? setInterval(load, 1500) : undefined
    return () => { clearInterval(timer); controller.abort() }
  }, [id, refresh, busy, scoring, pending, onSessionExpired])

  async function action(path: string, title: string, body?: unknown) {
    if (pending) return null
    setPending(title); setError('')
    try {
      const next = practice(await mutateJson(`practice-sessions/${id}/${path}`, body))
      setData(next)
      if (next.hint) setHint(next.hint)
      if (next.hintAlreadyConsumed) setHint(previous => previous || 'Gợi ý trước đã được tạo. Bạn có thể yêu cầu gợi ý mới.')
      return next
    } catch (failure) {
      if (failure instanceof ApiError && failure.status === 401) onSessionExpired()
      else setError(failure instanceof Error ? failure.message : 'Không hoàn tất được thao tác.')
      return null
    } finally { setPending('') }
  }

  async function send(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const text = draft.trim()
    if (!canSend || voiceBusy || capturing || !text) return
    if (draftKey.current?.text !== text) draftKey.current = { text, id: crypto.randomUUID() }
    const key = draftKey.current.id
    const next = await action('turns', 'Đang gửi câu và chờ AI…', { content: text, clientRequestId: key })
    if (next?.turns.some(turn => turn.clientRequestId === key)) {
      setDraft(''); setHint(''); setKeyboardOpen(false); draftKey.current = null
    }
  }

  async function getHint() {
    hintKey.current ??= crypto.randomUUID()
    setHint('')
    const next = await action('hints', 'Đang chờ gợi ý…', { clientRequestId: hintKey.current })
    if (next) hintKey.current = null
  }

  const assistantMessages = data?.messages.filter(line => line.sender === 'ASSISTANT') ?? []
  const listenFirst = data?.status === 'ACTIVE' && data.mode === 'VOICE'
  const announcement = <p role="status" aria-atomic="true" className="practice-new-line">
    {listenFirst && assistantMessages.length ? `Có câu AI mới · Câu ${assistantMessages.length}` : ''}
  </p>
  if (!data) return <section className="practice-chat">{announcement}{error ? <><p role="alert">{error}</p><button onClick={() => { setError(''); setRefresh(r => r + 1) }}>Tải lại</button></> : <p role="status">Đang tải buổi luyện…</p>}</section>
  const operation = data.operation
  const active = data.status === 'ACTIVE'
  const audioUnavailable = !speech.supported || Boolean(speech.speechError)
  const opening = data.turns.find(turn => turn.turnNo === 0)
  const latest = data.turns.at(-1)
  const needsAction = operation?.status === 'FAILED' || operation?.status === 'UNKNOWN'
  const canSend = active && !busy && !pending && !explainBusy && opening?.status === 'SUCCEEDED' &&
    latest?.status !== 'FAILED' && data.learnerTurns < data.maxLearnerTurns && operation?.status !== 'UNKNOWN'
  const canHint = canSend

  return <section className="practice-chat" aria-labelledby="practice-title">
    {announcement}
    <a className="back-link" href="#/scenarios"><Icon name="back" />Về tình huống</a>
    <PageHeading id="practice-title" title={data.title} />
    <div className="practice-meta"><span className="level-badge">{data.selectedLevel}</span><span className="status-badge">{data.mode === 'VOICE' ? 'Luyện nghe và nói' : 'Chat văn bản'}</span><span className="practice-turn-count">{data.learnerTurns}/{data.maxLearnerTurns} lượt người học</span></div>
    <div className="practice-layout"><aside className="practice-context" aria-labelledby="practice-context-title"><h2 id="practice-context-title">Vai và mục tiêu</h2>
      <dl className="practice-roles"><div><dt>Vai của bạn</dt><dd>{data.learnerRole}</dd></div><div><dt>Người đối thoại</dt><dd>{data.aiRole}</dd></div></dl>
      <div className="practice-goal"><h3><Icon name="target" size={20} />Mục tiêu</h3><p>{data.communicationGoal}</p></div>
      <details><summary>Bối cảnh tình huống</summary><p>{data.contextDescription}</p></details>
    </aside><div className="practice-main">
    <ol className="practice-transcript" aria-label="Tin nhắn hội thoại">
      {data.messages.map(line => line.sender === 'LEARNER'
        ? <li key={line.id} className="practice-learner"><strong className="practice-speaker">Bạn</strong><p lang="en">{line.content}</p>
            {line.pronunciation ? <p>{line.pronunciation.status === 'SUCCEEDED'
              ? pronunciationScores(line.pronunciation) || 'Chưa đủ dữ liệu chấm'
              : pronunciationStatus(line.pronunciation)}</p> : null}
            {line.pronunciation?.status === 'SUCCEEDED'
              ? <PronunciationDetails result={line.pronunciation} /> : null}
          </li>
        : <AssistantMessage key={line.id} id={line.id} content={line.content} role={data.aiRole}
          index={assistantMessages.indexOf(line) + 1} listenFirst={listenFirst} capturing={capturing} speech={speech}>
          {active && assistantMessages.at(-1)?.id === line.id ? <MessageExplanation sessionId={id} messageId={line.id}
            disabled={busy || Boolean(pending) || voiceBusy || capturing}
            onBusy={setExplainBusy} onSessionExpired={onSessionExpired} /> : null}
        </AssistantMessage>)}
    </ol>
    {audioUnavailable ? <p role="status">{speech.speechError || 'Trình duyệt chưa hỗ trợ nghe câu AI.'}
      {listenFirst ? ' Bạn có thể mở phụ đề trên từng câu.' : ' Bạn có thể đọc câu trả lời trên màn hình.'}</p> : null}
    {error ? <p role="alert" className="form-error">{error}</p> : null}
    {pending || busy ? <p className="practice-processing" role="status">{pending || 'AI đang xử lý…'}</p> : null}
    {active && needsAction && operation && !busy ? <div className="notice">
      <p>{operation.status === 'UNKNOWN' ? 'Chưa biết Gemini đã xử lý yêu cầu trước hay chưa. Gọi lại có thể dùng thêm hạn mức AI.' : operationError(operation.errorCode)}</p>
      {operation.retryable && operation.status === 'UNKNOWN' ? <label><input type="checkbox" checked={ack} onChange={event => setAck(event.target.checked)} /> Tôi đồng ý gọi lại</label> : null}
      {operation.retryable ? <button disabled={Boolean(pending) || operation.status === 'UNKNOWN' && !ack}
        onClick={() => { void action(`operations/${operation.id}/retry`, 'Đang thử lại…', { acknowledgeUnknown: ack }).then(() => setAck(false)) }}>Thử lại phản hồi</button> : null}
      {operation.id !== opening?.requestId ? <button disabled={Boolean(pending)}
        onClick={() => void action(`operations/${operation.id}/skip`, 'Đang bỏ qua…')}>Bỏ qua phản hồi</button> : null}
    </div> : null}
    {hint ? <aside className="notice practice-hint" role="status"><strong><Icon name="lightbulb" size={20} />Gợi ý khi bí câu</strong><p lang="en">{hint}</p></aside> : null}
    {active ? <>
      {listenFirst ? <VoiceTurn key={id} sessionId={id} pronunciationAvailable={data.pronunciationAvailable} disabled={!canSend || keyboardOpen}
        onSessionExpired={onSessionExpired} onBusy={setVoiceBusy} onRecording={speech.stop}
        onCapture={setCapturing} onUseKeyboard={() => setKeyboardOpen(true)}
        onConfirmed={next => { setData(practice(next)); setHint(''); setKeyboardOpen(false) }} /> : null}
      {listenFirst && !keyboardOpen ? <button type="button" className="practice-keyboard" disabled={capturing || voiceBusy}
        onClick={() => setKeyboardOpen(true)}>Nhập câu trả lời bằng bàn phím</button> : null}
      {!listenFirst || keyboardOpen ? <form className="practice-composer" onSubmit={event => void send(event)}>
        <div className="form-field"><label htmlFor="practice-message">Câu tiếng Anh của bạn</label>
          <textarea id="practice-message" ref={keyboardField} value={draft} onChange={event => setDraft(event.target.value)}
            disabled={Boolean(pending)} maxLength={1000} rows={3} lang="en" placeholder="Viết câu trả lời của bạn…" required />
        </div>
        <div className="actions"><button className="button-primary" disabled={!canSend || voiceBusy || capturing || !draft.trim()}><Icon name="send" size={20} />Gửi câu</button>
          {listenFirst ? <button type="button" disabled={Boolean(pending)} onClick={() => setKeyboardOpen(false)}>Đóng ô gõ</button> : null}
        </div>
      </form> : null}
      <div className="actions practice-session-actions">
        <button type="button" disabled={!canHint || voiceBusy || capturing} onClick={() => void getHint()}><Icon name="lightbulb" size={20} />Xin gợi ý</button>
        <button type="button" disabled={Boolean(pending) || busy || voiceBusy || explainBusy}
          onClick={() => { speech.stop(); void action('finish', 'Đang kết thúc…') }}>Kết thúc buổi</button>
        <button className="button-quiet" type="button" disabled={Boolean(pending)} onClick={() => { setError(''); setRefresh(r => r + 1) }}>Tải lại trạng thái</button>
      </div>
      {data.learnerTurns >= data.maxLearnerTurns ? <p>Đã đủ 20 lượt. Bạn có thể kết thúc buổi.</p> : null}
    </> : <p role="status" className="notice">{data.status === 'COMPLETED' ? 'Đã hoàn thành buổi luyện.' :
      'Buổi luyện đã đóng. Để hoàn thành, cần ít nhất một lượt bạn gửi câu và AI phản hồi thành công. Bạn vẫn có thể xem lại hội thoại đã lưu.'}</p>}
    </div></div>
    {data.status === 'COMPLETED' && (scored.length > 0 || scoring) ? <section aria-label="Tổng kết phát âm">
      <h2>Phát âm</h2>
      {scoring ? <p role="status">Một số lượt vẫn đang được chấm.</p> : null}
      <p>{scored.length} lượt có điểm phát âm.</p>
      {scored.length ? <ol>{scored.map(line => <li key={line.id}><span lang="en">{line.content}</span> · {line.scores}</li>)}</ol> : null}
    </section> : null}
    {data.status === 'COMPLETED' ? <SessionFeedback id={id} onSessionExpired={onSessionExpired} /> : null}
  </section>
}
