import { useEffect, useRef, useState } from 'react'
import { ApiError, getJson, mutateForm, mutateJson } from '../../../shared/api/client'
import { maxRecordingSeconds, recordingType, toWave } from './wave'

type Draft = {
  id: string; status: 'TRANSCRIBING' | 'READY' | 'CONFIRMED' | 'FAILED' | 'EXPIRED'
  transcript: string | null; expiresAt: string | null; errorCode: string | null
  retryable: boolean; unknown: boolean; retryAt: string | null
}
type Recording = { blob: Blob; url: string; key: string }
type Capture = { recorder: MediaRecorder; stream: MediaStream; timer: number }

function parseDraft(value: unknown) {
  const draft = value as Partial<Draft> | null
  if (!draft || typeof draft.id !== 'string' || typeof draft.status !== 'string')
    throw new Error('Phản hồi bản chép lời không hợp lệ.')
  return draft as Draft
}

function recordingError(failure: unknown) {
  if (failure instanceof DOMException && ['NotAllowedError', 'SecurityError'].includes(failure.name))
    return 'Bạn chưa cấp quyền micro. Hãy kiểm tra quyền của trang hoặc nhập văn bản.'
  if (failure instanceof DOMException && ['NotFoundError', 'NotReadableError'].includes(failure.name))
    return 'Không truy cập được micro. Hãy kiểm tra thiết bị hoặc nhập văn bản.'
  return 'Không thể ghi âm trên thiết bị này. Bạn vẫn có thể nhập văn bản.'
}

function draftError(draft: Draft) {
  if (draft.unknown) return 'Chưa rõ Gemini đã xử lý bản ghi hay chưa. Bấm thử lại có thể dùng thêm hạn mức.'
  if (draft.errorCode === 'NO_SPEECH') return 'Gemini chưa nghe rõ lời nói. Hãy ghi lại hoặc nhập văn bản.'
  if (draft.errorCode === 'PROVIDER_RATE_LIMIT') return 'Gemini đang giới hạn lượt gọi. Hãy đợi trước khi thử lại.'
  return 'Chưa chép được lời từ bản ghi. Bạn có thể nhập văn bản.'
}

function release(capture: Capture) {
  window.clearInterval(capture.timer)
  capture.stream.getTracks().forEach(track => track.stop())
}

export function VoiceTurn({ sessionId, disabled, onConfirmed, onSessionExpired, onBusy, onRecording, onCapture, onUseKeyboard }: {
  sessionId: string; disabled: boolean; onConfirmed: (value: unknown) => void
  onSessionExpired: () => void; onBusy: (busy: boolean) => void; onRecording: () => void
  onCapture: (capturing: boolean) => void; onUseKeyboard: () => void
}) {
  const [mode, setMode] = useState<'idle' | 'permission' | 'recording' | 'stopping'>('idle')
  const [noticeAcceptedAt, acceptNotice] = useState<number | null>(null)
  const [seconds, setSeconds] = useState(0)
  const [recording, setRecording] = useState<Recording | null>(null)
  const [draft, setDraft] = useState<Draft | null>(null)
  const [transcript, setTranscript] = useState('')
  const [status, setStatus] = useState('')
  const [error, setError] = useState('')
  const [poll, setPoll] = useState(0)
  const capture = useRef<Capture | null>(null)
  const generation = useRef(0)
  const operation = useRef<AbortController | null>(null)
  const encoded = useRef<Blob | null>(null)
  const playback = useRef<HTMLAudioElement | null>(null)
  const codec = recordingType()
  const path = `practice-sessions/${sessionId}/audio`

  useEffect(() => () => {
    generation.current++
    if (capture.current) release(capture.current)
    capture.current = null
    operation.current?.abort()
    onCapture(false)
    onBusy(false)
  }, [onBusy, onCapture])

  useEffect(() => () => {
    if (recording) URL.revokeObjectURL(recording.url)
  }, [recording])

  const processingId = draft?.status === 'TRANSCRIBING' ? draft.id : null
  useEffect(() => {
    if (!processingId) return
    const controller = new AbortController()
    let timer: number
    async function refresh() {
      try {
        const next = parseDraft(await getJson(`${path}/${processingId}`, controller.signal))
        if (controller.signal.aborted) return
        if (next.status === 'TRANSCRIBING') timer = window.setTimeout(() => void refresh(), 1500)
        else { setDraft(next); setTranscript(next.transcript ?? '') }
      } catch (failure) {
        if (controller.signal.aborted) return
        if (failure instanceof ApiError && failure.status === 401) onSessionExpired()
        else setError('Không đọc được trạng thái bản ghi. Hãy tải lại trạng thái.')
      }
    }
    timer = window.setTimeout(() => void refresh(), 1000)
    return () => { controller.abort(); window.clearTimeout(timer) }
  }, [path, processingId, poll, onSessionExpired])

  const draftExpiry = draft?.status === 'READY' ? draft.expiresAt : null
  useEffect(() => {
    if (!draftExpiry) return
    const timer = window.setTimeout(() => {
      setTranscript('')
      setDraft(previous => previous?.status === 'READY'
        ? { ...previous, status: 'EXPIRED', transcript: null } : previous)
    }, Math.max(0, Date.parse(draftExpiry) - Date.now()))
    return () => window.clearTimeout(timer)
  }, [draftExpiry])

  function clear() {
    generation.current++
    if (capture.current) release(capture.current)
    capture.current = null
    encoded.current = null
    playback.current?.pause()
    setMode('idle'); setRecording(null); setDraft(null); setTranscript(''); setSeconds(0)
    onCapture(false)
  }

  function stop() {
    const current = capture.current
    if (!current || current.recorder.state !== 'recording') return
    setMode('stopping')
    current.recorder.stop()
    release(current)
  }

  async function start() {
    if (!codec || !noticeAcceptedAt || mode !== 'idle') return
    clear()
    const version = generation.current
    setError(''); setMode('permission')
    onCapture(true)
    onRecording()
    let stream: MediaStream | undefined
    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: { channelCount: 1 } })
      if (generation.current !== version) {
        stream.getTracks().forEach(track => track.stop())
        return
      }
      const recorder = new MediaRecorder(stream, { mimeType: codec, audioBitsPerSecond: 64_000 })
      const chunks: Blob[] = []
      recorder.ondataavailable = event => { if (event.data.size) chunks.push(event.data) }
      recorder.onstop = () => {
        if (generation.current !== version) return
        if (capture.current) release(capture.current)
        capture.current = null
        const blob = new Blob(chunks, { type: recorder.mimeType || codec })
        setMode('idle')
        onCapture(false)
        if (blob.size) setRecording({ blob, url: URL.createObjectURL(blob), key: crypto.randomUUID() })
        else setError('Bản ghi trống. Hãy thử lại hoặc nhập văn bản.')
      }
      recorder.onerror = () => {
        if (generation.current !== version) return
        clear()
        setError('Ghi âm bị gián đoạn. Hãy thử lại hoặc nhập văn bản.')
      }
      recorder.start()
      const started = performance.now()
      const timer = window.setInterval(() => {
        const elapsed = (performance.now() - started) / 1000
        setSeconds(Math.min(maxRecordingSeconds, Math.floor(elapsed)))
        if (elapsed >= maxRecordingSeconds) stop()
      }, 200)
      capture.current = { recorder, stream, timer }
      setMode('recording')
    } catch (failure) {
      stream?.getTracks().forEach(track => track.stop())
      if (generation.current === version) {
        setMode('idle'); setError(recordingError(failure)); onCapture(false)
      }
    }
  }

  async function act(title: string, run: (signal: AbortSignal) => Promise<void>) {
    if (operation.current) return
    const controller = new AbortController()
    operation.current = controller
    setError(''); setStatus(title); onBusy(true)
    try { await run(controller.signal) } catch (failure) {
      if (controller.signal.aborted) return
      if (failure instanceof ApiError && failure.status === 401) onSessionExpired()
      else if (failure instanceof TypeError) setError('Kết nối bị gián đoạn. Hãy kiểm tra mạng và thử lại.')
      else setError(failure instanceof Error ? failure.message : 'Chưa xử lý được bản ghi.')
    } finally {
      operation.current = null
      if (!controller.signal.aborted) { setStatus(''); onBusy(false) }
    }
  }

  function upload(retry = false) {
    if (!recording || !noticeAcceptedAt) return
    return act('Đang gửi bản ghi và chép lời…', async signal => {
      playback.current?.pause()
      const wav = encoded.current ?? await toWave(recording.blob)
      if (signal.aborted) return
      encoded.current = wav
      const form = new FormData()
      form.set('file', wav, 'recording.wav')
      form.set('clientRequestId', recording.key)
      form.set('retry', String(retry))
      const next = parseDraft(await mutateForm(path, form, signal))
      if (signal.aborted) return
      setDraft(next); setTranscript(next.transcript ?? '')
      if (next.status === 'READY') {
        setRecording(null)
        encoded.current = null
      }
    })
  }

  function confirm() {
    if (!draft || !transcript.trim()) return
    return act('Đang gửi bản chép lời và chờ AI…', async signal => {
      const next = await mutateJson(`${path}/${draft.id}/confirm`, { content: transcript.trim() }, 'POST', signal)
      if (!signal.aborted) { onConfirmed(next); clear() }
    })
  }

  function discard() {
    if (!draft || draft.status === 'EXPIRED' || draft.status === 'CONFIRMED') {
      clear(); setError('')
      return
    }
    return act('Đang hủy bản chép lời…', async signal => {
      await mutateJson(`${path}/${draft.id}`, undefined, 'DELETE', signal)
      if (!signal.aborted) clear()
    })
  }

  return <section className="voice-turn" aria-labelledby="voice-title">
    <h3 id="voice-title">Trả lời bằng giọng nói</h3>
    <details open={!noticeAcceptedAt}>
      <summary>Thông báo xử lý âm thanh</summary>
      <p>Khi bấm gửi bản ghi, máy chủ chuyển âm thanh đến Google Gemini để chép lời.
        Âm thanh gốc được xóa sau khi xử lý, với thời hạn dọn an toàn 1 giờ. Bản chép lời nháp
        hết hạn sau 30 phút. Bản đã xác nhận được lưu trong lịch sử đến khi bạn xóa buổi luyện.
        Nội dung gõ cũng được gửi đến Gemini khi bạn gửi câu.</p>
      <p><a href="https://ai.google.dev/gemini-api/terms" target="_blank" rel="noreferrer">Điều khoản dữ liệu của Gemini</a></p>
      {!noticeAcceptedAt && codec ? <div className="actions">
        <button type="button" onClick={() => acceptNotice(Date.now())}>Đã hiểu, tiếp tục</button>
        <button type="button" onClick={onUseKeyboard}>Dùng văn bản</button>
      </div> : null}
    </details>
    {!codec ? <p>Trình duyệt hoặc kết nối hiện tại chưa hỗ trợ ghi âm. Bạn vẫn có thể nhập văn bản.</p> : null}
    {noticeAcceptedAt && !recording && !draft ? <div className="actions">
      {mode === 'idle' ? <button type="button" disabled={disabled} onClick={() => void start()}>Ghi câu trả lời</button> : null}
      {mode === 'recording' ? <button type="button" onClick={stop}>Dừng ghi âm</button> : null}
      {mode !== 'idle' ? <button type="button" onClick={() => { clear(); setError('') }}>Hủy ghi âm</button> : null}
      {mode === 'recording' ? <span aria-label={`Đã ghi ${seconds} giây`}>{seconds} / {maxRecordingSeconds} giây</span> : null}
      {mode === 'permission' ? <span role="status">Đang chờ quyền micro…</span> : null}
    </div> : null}
    {noticeAcceptedAt && mode === 'idle' && !recording && !draft ? <p>Tối đa 60 giây mỗi bản ghi.</p> : null}
    {recording && !draft ? <>
      <audio ref={playback} controls src={recording.url} aria-label="Nghe lại bản ghi" />
      <div className="actions">
        <button type="button" disabled={disabled || Boolean(status)} onClick={() => void upload()}>Gửi bản ghi để chép lời</button>
        <button type="button" disabled={Boolean(status)} onClick={() => void discard()}>Bỏ bản ghi</button>
      </div>
    </> : null}
    {draft?.status === 'TRANSCRIBING' ? <p role="status">Gemini đang chép lời…</p> : null}
    {draft?.status === 'TRANSCRIBING' && error ? <button type="button" onClick={() => { setError(''); setPoll(n => n + 1) }}>Tải lại trạng thái bản ghi</button> : null}
    {draft?.status === 'READY' ? <div className="form-field">
      <label htmlFor="voice-transcript">Kiểm tra và sửa bản chép lời</label>
      <textarea id="voice-transcript" lang="en" rows={3} maxLength={1000} value={transcript}
        disabled={Boolean(status)} onChange={event => setTranscript(event.target.value)} />
      <div className="actions">
        <button type="button" disabled={disabled || Boolean(status) || !transcript.trim()} onClick={() => void confirm()}>Xác nhận và gửi vào hội thoại</button>
        <button type="button" disabled={Boolean(status)} onClick={() => void discard()}>Hủy bản chép lời</button>
      </div>
    </div> : null}
    {draft && ['FAILED', 'EXPIRED', 'CONFIRMED'].includes(draft.status) ? <div>
      <p role="status">{draft.status === 'FAILED' ? draftError(draft) :
        draft.status === 'CONFIRMED' ? 'Bản chép lời đã được gửi.' : 'Bản chép lời đã hết hạn.'}</p>
      <div className="actions">
        {draft.retryable && recording ? <button type="button" disabled={disabled || Boolean(status)}
          onClick={() => void upload(true)}>{draft.unknown ? 'Đồng ý gọi lại để chép lời' : 'Thử chép lời lại'}</button> : null}
        <button type="button" disabled={Boolean(status)} onClick={() => void discard()}>Đóng bản ghi</button>
      </div>
    </div> : null}
    {status ? <p role="status">{status}</p> : null}
    {error ? <p className="form-error" role="alert">{error}</p> : null}
  </section>
}
