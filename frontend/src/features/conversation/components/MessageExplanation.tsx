import { useEffect, useId, useRef, useState } from 'react'
import { Icon } from '../../../shared/ui/Icon'
import { ApiError, getJson, mutateJson } from '../../../shared/api/client'

type Explanation = {
  status: 'NOT_STARTED' | 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'UNKNOWN'
  errorCode: string | null; retryable: boolean; retryNotBefore: string | null
  simpleEnglish: string | null; meaningVi: string | null
}

export function MessageExplanation({ sessionId, messageId, disabled, onBusy, onSessionExpired }: {
  sessionId: string; messageId: string; disabled: boolean
  onBusy: (value: boolean) => void; onSessionExpired: () => void
}) {
  const [data, setData] = useState<Explanation | null>(null)
  const [open, setOpen] = useState(false)
  const [pending, setPending] = useState(false)
  const [review, setReview] = useState(0)
  const [meaning, setMeaning] = useState(false)
  const [error, setError] = useState('')
  const key = useRef<string | null>(null)
  const mutation = useRef<AbortController | null>(null)
  const panel = useId()
  const path = `practice-sessions/${sessionId}/messages/${messageId}/explanation`
  const running = data?.status === 'RUNNING' || data?.status === 'PENDING'
  const working = pending || running

  useEffect(() => {
    onBusy(working)
    return () => onBusy(false)
  }, [working, onBusy])

  useEffect(() => () => mutation.current?.abort(), [])

  useEffect(() => {
    if (pending) return
    const controller = new AbortController()
    let timer: ReturnType<typeof setTimeout> | undefined
    async function load() {
      try {
        const next = await getJson(path, controller.signal) as Explanation
        if (controller.signal.aborted) return
        setData(next)
        if (next.status === 'RUNNING' || next.status === 'PENDING')
          timer = setTimeout(() => void load(), 1500)
      } catch (failure) {
        if (controller.signal.aborted) return
        if (failure instanceof ApiError && failure.status === 401) onSessionExpired()
        else setError('Chưa tải được trạng thái giải thích.')
      }
    }
    void load()
    return () => { controller.abort(); clearTimeout(timer) }
  }, [path, pending, review, onSessionExpired])

  async function generate(retry: boolean) {
    if (mutation.current || running || disabled) return
    const controller = new AbortController()
    mutation.current = controller
    key.current ??= crypto.randomUUID()
    setPending(true); setError(''); setOpen(true)
    try {
      const next = await mutateJson(path, {
        clientRequestId: key.current, retry, acknowledgeUnknown: retry && data?.status === 'UNKNOWN',
      }, 'POST', controller.signal) as Explanation
      if (!controller.signal.aborted) setData(next)
    } catch (failure) {
      if (controller.signal.aborted) return
      if (failure instanceof ApiError && failure.status === 401) onSessionExpired()
      else setError(failure instanceof Error ? failure.message : 'Chưa giải thích được câu AI.')
    } finally {
      mutation.current = null
      if (!controller.signal.aborted) setPending(false)
    }
  }

  return <div className="message-explanation">
    <button className="button-quiet" type="button" aria-controls={panel} aria-expanded={open}
      disabled={!open && ((!data && !error) || (disabled && data?.status === 'NOT_STARTED'))}
      onClick={() => {
        if (!open && data?.status === 'NOT_STARTED' && !error) void generate(false)
        else setOpen(value => !value)
      }}><Icon name="lightbulb" size={20} />{open ? 'Đóng giải thích' : 'Giải thích câu này'}</button>
    <div className="message-explanation-panel" id={panel} hidden={!open} aria-busy={working}>
      {open ? <>
        {working || (!data && !error) ? <p role="status">{working ? 'Đang giải thích…' : 'Đang tải giải thích…'}</p> : null}
        {error ? <div><p role="alert">{error}</p><button type="button" onClick={() => {
          setError(''); setReview(value => value + 1)
        }}>Tải lại trạng thái giải thích</button></div> : null}
        {data?.status === 'NOT_STARTED' && !pending ? <button type="button" disabled={disabled}
          onClick={() => void generate(false)}>Tạo giải thích</button> : null}
        {!pending && (data?.status === 'FAILED' || data?.status === 'UNKNOWN') ? <>
          <p role="status">{data.status === 'UNKNOWN' ? 'Chưa rõ kết quả lần trước. Gọi lại có thể dùng thêm hạn mức.' :
            data.errorCode === 'EXPLANATION_INVALID' ? 'Kết quả giải thích chưa hợp lệ.' : 'Chưa nhận được giải thích.'}</p>
          {data.retryable ? <button type="button" disabled={disabled} onClick={() => void generate(true)}>
            {data.status === 'UNKNOWN' ? 'Đồng ý gọi lại để giải thích' : 'Thử giải thích lại'}</button> : null}
        </> : null}
        {data?.status === 'SUCCEEDED' ? <>
          <p><strong>Tiếng Anh đơn giản</strong></p>
          <p lang="en">{data.simpleEnglish}</p>
          <button type="button" aria-expanded={meaning} onClick={() => setMeaning(value => !value)}>
            {meaning ? 'Ẩn nghĩa tiếng Việt' : 'Xem nghĩa tiếng Việt'}</button>
          {meaning ? <p lang="vi">{data.meaningVi}</p> : null}
        </> : null}
      </> : null}
    </div>
  </div>
}
