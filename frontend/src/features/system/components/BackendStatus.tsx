import { useEffect, useState } from 'react'
import { getBackendHealth } from '../api/system-api'

type ConnectionState = { status: 'loading' } | { status: 'connected' } | { status: 'error'; message: string }

export function BackendStatus() {
  const [attempt, setAttempt] = useState(0)
  const [connection, setConnection] = useState<ConnectionState>({ status: 'loading' })

  useEffect(() => {
    const controller = new AbortController()
    const timeout = window.setTimeout(() => controller.abort(new Error('Kết nối quá thời gian chờ.')), 10000)
    let disposed = false

    getBackendHealth(controller.signal).then(() => {
      if (!disposed) setConnection({ status: 'connected' })
    }).catch((error: unknown) => {
      if (!disposed) setConnection({ status: 'error', message: error instanceof Error ? error.message : 'Không thể kết nối máy chủ.' })
    }).finally(() => window.clearTimeout(timeout))

    return () => {
      disposed = true
      window.clearTimeout(timeout)
      controller.abort()
    }
  }, [attempt])

  function retry() {
    setConnection({ status: 'loading' })
    setAttempt((value) => value + 1)
  }

  return (
    <div className="connection">
      <h3>Kết nối máy chủ</h3>
      <div role="status" aria-live="polite">
        {connection.status === 'loading' ? <p>Đang kiểm tra kết nối…</p> : null}
        {connection.status === 'connected' ? <p className="connected">Đã kết nối backend.</p> : null}
        {connection.status === 'error' ? <><p className="error">Chưa kết nối được backend.</p><p>{connection.message}</p></> : null}
      </div>
      <button type="button" onClick={retry} disabled={connection.status === 'loading'}>Kiểm tra lại</button>
      <p className="hint">Trạng thái này chỉ kiểm tra HTTP, chưa kiểm tra MySQL hoặc Gemini.</p>
    </div>
  )
}
