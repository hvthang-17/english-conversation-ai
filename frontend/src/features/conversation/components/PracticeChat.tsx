import { useEffect, useRef, useState, type FormEvent } from 'react'
import { ApiError, getJson, mutateJson } from '../../../shared/api/client'
import { SessionFeedback } from '../../feedback/components/SessionFeedback'
import { VoiceTurn } from '../voice/VoiceTurn'
import { useSpeech } from '../voice/useSpeech'
import type { PracticeMode } from '../practiceMode'
import { AssistantMessage } from './AssistantMessage'
import './practice-chat.css'

type Operation = { id: string; task: string; status: string; errorCode: string | null; retryable: boolean; retryNotBefore: string | null }
type Practice = {
  id: string; status: string; mode: PracticeMode; title: string; selectedLevel: string; learnerRole: string; aiRole: string
  communicationGoal: string; contextDescription: string; learnerTurns: number; maxLearnerTurns: number
  messages: { id: string; sender: string; content: string }[]
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
  const [capturing, setCapturing] = useState(false)
  const [keyboardOpen, setKeyboardOpen] = useState(false)
  const [confirmMode, setConfirmMode] = useState(false)
  const keyboardField = useRef<HTMLTextAreaElement>(null)
  const speech = useSpeech()
  const draftKey = useRef<{ text: string; id: string } | null>(null)
  const hintKey = useRef<string | null>(null)
  const busy = data?.operation?.status === 'RUNNING' || data?.operation?.status === 'PENDING'

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
    const timer = busy ? setInterval(load, 1500) : undefined
    return () => { clearInterval(timer); controller.abort() }
  }, [id, refresh, busy, pending, onSessionExpired])

  async function action(path: string, title: string, body?: unknown, method: 'POST' | 'PATCH' = 'POST') {
    if (pending) return null
    setPending(title); setError('')
    try {
      const next = practice(await mutateJson(`practice-sessions/${id}/${path}`, body, method))
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
  const canSend = active && !busy && !pending && opening?.status === 'SUCCEEDED' &&
    latest?.status !== 'FAILED' && data.learnerTurns < data.maxLearnerTurns && operation?.status !== 'UNKNOWN'
  const canHint = canSend

  return <section className="practice-chat" aria-labelledby="practice-title">
    {announcement}
    <a href="#/scenarios">← Tình huống</a>
    <h2 id="practice-title">{data.title} · {data.selectedLevel}</h2>
    <p className="topic-label">{data.mode === 'VOICE' ? 'Luyện nghe và nói' : 'Chat văn bản'}</p>
    <p><strong>Vai của bạn</strong> {data.learnerRole} · <strong>Vai AI</strong> {data.aiRole}</p>
    <p><strong>Mục tiêu</strong> {data.communicationGoal}</p>
    <details><summary>Bối cảnh</summary><p>{data.contextDescription}</p></details>
    <p>{data.learnerTurns}/{data.maxLearnerTurns} lượt người học</p>
    <ol className="practice-transcript" aria-label="Tin nhắn hội thoại">
      {data.messages.map(line => line.sender === 'LEARNER'
        ? <li key={line.id} className="practice-learner"><strong>Bạn</strong><p lang="en">{line.content}</p></li>
        : <AssistantMessage key={line.id} id={line.id} content={line.content} role={data.aiRole}
          index={assistantMessages.indexOf(line) + 1} listenFirst={listenFirst} capturing={capturing} speech={speech} />)}
    </ol>
    {audioUnavailable ? <p role="status">{speech.speechError || 'Trình duyệt chưa hỗ trợ nghe câu AI.'}
      {listenFirst ? ' Bạn có thể mở phụ đề trên từng câu hoặc chuyển sang chat văn bản.' : ' Bạn có thể đọc câu trả lời trên màn hình.'}</p> : null}
    {listenFirst && (audioUnavailable || confirmMode) ? <div className="notice">
      {confirmMode ? <>
        <p>Chuyển sang chat văn bản cho phần còn lại của buổi? Tất cả câu AI sẽ hiển thị bằng chữ.</p>
        <div className="actions">
          <button type="button" disabled={Boolean(pending) || voiceBusy || capturing}
            onClick={() => void action('mode', 'Đang đổi chế độ…', { mode: 'TEXT' }, 'PATCH').then(next => {
              if (next?.mode === 'TEXT') { speech.stop(); setConfirmMode(false); setKeyboardOpen(false) }
            })}>Xác nhận chuyển</button>
          <button type="button" disabled={Boolean(pending)} onClick={() => setConfirmMode(false)}>Tiếp tục nghe và nói</button>
        </div>
      </> : <button type="button" disabled={Boolean(pending) || voiceBusy || capturing}
        onClick={() => setConfirmMode(true)}>Chuyển sang chat văn bản</button>}
    </div> : null}
    {error ? <p role="alert" className="form-error">{error}</p> : null}
    {pending || busy ? <p role="status">{pending || 'AI đang xử lý…'}</p> : null}
    {active && needsAction && operation && !busy ? <div className="notice">
      <p>{operation.status === 'UNKNOWN' ? 'Chưa biết Gemini đã xử lý yêu cầu trước hay chưa. Gọi lại có thể dùng thêm quota.' : operationError(operation.errorCode)}</p>
      {operation.retryable && operation.status === 'UNKNOWN' ? <label><input type="checkbox" checked={ack} onChange={event => setAck(event.target.checked)} /> Tôi đồng ý gọi lại</label> : null}
      {operation.retryable ? <button disabled={Boolean(pending) || operation.status === 'UNKNOWN' && !ack}
        onClick={() => { void action(`operations/${operation.id}/retry`, 'Đang thử lại…', { acknowledgeUnknown: ack }).then(() => setAck(false)) }}>Thử lại phản hồi</button> : null}
      {operation.id !== opening?.requestId ? <button disabled={Boolean(pending)}
        onClick={() => void action(`operations/${operation.id}/skip`, 'Đang bỏ qua…')}>Bỏ qua phản hồi</button> : null}
    </div> : null}
    {hint ? <aside className="notice" role="status"><strong>Gợi ý</strong><p lang="en">{hint}</p></aside> : null}
    {active ? <>
      {listenFirst ? <VoiceTurn key={id} sessionId={id} disabled={!canSend || keyboardOpen}
        onSessionExpired={onSessionExpired} onBusy={setVoiceBusy} onRecording={speech.stop}
        onCapture={setCapturing} onUseKeyboard={() => setKeyboardOpen(true)}
        onConfirmed={next => { setData(practice(next)); setHint(''); setKeyboardOpen(false) }} /> : null}
      {listenFirst && !keyboardOpen ? <button type="button" className="practice-keyboard" disabled={capturing || voiceBusy}
        onClick={() => setKeyboardOpen(true)}>Nhập câu trả lời bằng bàn phím</button> : null}
      {!listenFirst || keyboardOpen ? <form onSubmit={event => void send(event)}>
        <div className="form-field"><label htmlFor="practice-message">Câu tiếng Anh của bạn</label>
          <textarea id="practice-message" ref={keyboardField} value={draft} onChange={event => setDraft(event.target.value)}
            disabled={Boolean(pending)} maxLength={1000} rows={3} required />
        </div>
        <div className="actions"><button disabled={!canSend || voiceBusy || capturing || !draft.trim()}>Gửi câu</button>
          {listenFirst ? <button type="button" disabled={Boolean(pending)} onClick={() => setKeyboardOpen(false)}>Đóng ô gõ</button> : null}
        </div>
      </form> : null}
      <div className="actions practice-session-actions">
        <button type="button" disabled={!canHint || voiceBusy || capturing} onClick={() => void getHint()}>Xin gợi ý</button>
        <button type="button" disabled={Boolean(pending) || busy || voiceBusy}
          onClick={() => { speech.stop(); void action('finish', 'Đang kết thúc…') }}>Kết thúc buổi</button>
        <button type="button" disabled={Boolean(pending)} onClick={() => { setError(''); setRefresh(r => r + 1) }}>Tải lại trạng thái</button>
      </div>
      {data.learnerTurns >= data.maxLearnerTurns ? <p>Đã đủ 20 lượt. Bạn có thể kết thúc buổi.</p> : null}
    </> : <p role="status" className="notice">{data.status === 'COMPLETED' ? 'Đã hoàn thành buổi luyện.' : 'Đã đóng buổi luyện.'}</p>}
    {data.status === 'COMPLETED' ? <SessionFeedback id={id} onSessionExpired={onSessionExpired} /> : null}
  </section>
}
