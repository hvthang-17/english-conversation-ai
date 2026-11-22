import { useState } from 'react'
import { mutateJson } from '../../shared/api/client'
import { reportError, useLearningData, type HistoryEntry, type Page, type SessionGuard } from './learningApi'
import { Pagination } from './Pagination'

const dateTime = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh' })

export function PracticeHistory({ onSessionExpired }: SessionGuard) {
  const [page, setPage] = useState(0)
  const [confirm, setConfirm] = useState<{ id?: string; title?: string } | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const query = useLearningData<Page<HistoryEntry>>(`learning/history?page=${page}&size=12`, onSessionExpired)

  async function remove() {
    if (!confirm || busy) return
    const id = confirm.id
    setBusy(true); setError(''); setNotice('')
    try {
      await mutateJson(id ? `learning/history/${id}` : 'learning/data', undefined, 'DELETE')
      setConfirm(null)
      setNotice(id ? 'Đã xóa buổi luyện. Các thẻ đã lưu vẫn còn.' : 'Đã xóa các buổi luyện, thẻ và lịch sử ôn.')
      if (page > 0 && (!id || query.data?.items.length === 1)) setPage(id ? page - 1 : 0)
      else query.reload()
    } catch (failure) { setError(reportError(failure, onSessionExpired)) }
    finally { setBusy(false) }
  }

  return <section aria-labelledby="history-title" className="learning-panel">
    <h2 id="history-title">Lịch sử luyện tập</h2>
    {notice ? <p role="status">{notice}</p> : null}
    {error ? <p role="alert">{error}</p> : null}
    {confirm ? <section className="notice" aria-labelledby="delete-title">
      <h3 id="delete-title">{confirm.id ? `Xóa buổi “${confirm.title}”?` : 'Xóa toàn bộ dữ liệu học tập?'}</h3>
      <p>{confirm.id ? 'Hội thoại và nhận xét của buổi này sẽ bị xóa. Thẻ đã lưu vẫn còn trong thư viện ôn tập.' :
        'Mọi buổi luyện, hội thoại, nhận xét, thẻ và lịch sử ôn sẽ bị xóa. Tài khoản và hồ sơ học tập vẫn dùng được.'}</p>
      <p>Thống kê sẽ cập nhật theo dữ liệu còn lại. Bạn không thể hoàn tác thao tác này.</p>
      <div className="actions"><button disabled={busy} onClick={() => void remove()}>{busy ? 'Đang xóa…' : 'Xác nhận xóa'}</button>
        <button disabled={busy} onClick={() => setConfirm(null)}>Hủy</button></div>
    </section> : null}
    {query.error ? <div role="alert"><p>{query.error}</p><button onClick={query.reload}>Thử tải lại</button></div> :
      !query.data ? <p role="status">Đang tải lịch sử…</p> : <>
        {query.data.items.length === 0 ? <p>Không có buổi luyện trong trang này. <a href="#/scenarios">Chọn tình huống</a></p> : null}
        <ul className="learning-list">{query.data.items.map(item => <li key={item.id}>
          <h3><a href={`#/practice/${item.id}`}>{item.title}</a></h3>
          <p>{item.level} · {dateTime.format(new Date(item.startedAt))} · {item.learnerTurns} lượt</p>
          <p>{item.status === 'COMPLETED' ? 'Hoàn thành' : item.status === 'ACTIVE' ? 'Đang luyện' : 'Đã đóng'}
            {item.feedbackStatus === 'SUCCEEDED' ? ' · Có nhận xét' : ''}</p>
          {confirm?.id === item.id ? null : <button disabled={busy} onClick={() => {
            setError(''); setConfirm({ id: item.id, title: item.title })
            requestAnimationFrame(() => document.getElementById('delete-title')?.scrollIntoView({block:'center'}))
          }}>Xóa buổi<span className="sr-only"> {item.title}</span></button>}
        </li>)}</ul>
        <Pagination page={page} size={query.data.size} total={query.data.total} onPage={setPage} disabled={busy} />
      </>}
    {!confirm ? <button disabled={busy} onClick={() => { setError(''); setConfirm({}); requestAnimationFrame(() =>
      document.getElementById('delete-title')?.scrollIntoView({block:'center'})) }}>Xóa toàn bộ dữ liệu học tập</button> : null}
  </section>
}
