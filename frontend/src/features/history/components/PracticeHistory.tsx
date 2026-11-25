import { useState } from 'react'
import { mutateJson } from '../../../shared/api/client'
import { reportError } from '../../../shared/api/errors'
import { useApiQuery } from '../../../shared/hooks/useApiQuery'
import { type HistoryEntry } from '../types'
import { type Page, type SessionGuard } from '../../../shared/types/api'
import { Pagination } from '../../../shared/ui/Pagination'
import { PageHeading } from '../../../shared/ui/PageHeading'
import { Icon } from '../../../shared/ui/Icon'
import '../styles/history.css'

const dateTime = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh' })

export function PracticeHistory({ onSessionExpired }: SessionGuard) {
  const [page, setPage] = useState(0)
  const [confirm, setConfirm] = useState<{ id?: string; title?: string } | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const query = useApiQuery<Page<HistoryEntry>>(`learning/history?page=${page}&size=12`, onSessionExpired)

  async function remove() {
    if (!confirm || busy) return
    const id = confirm.id
    setBusy(true); setError(''); setNotice('')
    try {
      await mutateJson(id ? `learning/history/${id}` : 'learning/data', undefined, 'DELETE')
      setConfirm(null)
      setNotice(id ? 'Đã xóa buổi luyện. Các thẻ đã lưu vẫn còn.' : 'Đã xóa dữ liệu học tập. Tài khoản và hồ sơ vẫn còn.')
      if (page > 0 && (!id || query.data?.items.length === 1)) setPage(id ? page - 1 : 0)
      else query.reload()
    } catch (failure) { setError(reportError(failure, onSessionExpired)) }
    finally { setBusy(false) }
  }

  return <section aria-labelledby="history-title" className="learning-panel">
    <PageHeading id="history-title" title="Nhìn lại những cuộc trò chuyện" description="Tiếp tục buổi đang luyện hoặc đọc lại hội thoại và nhận xét của bạn." action={<a className="button-link button-primary" href="#/scenarios"><Icon name="plus" />Chọn tình huống</a>} />
    {notice ? <p className="notice" role="status">{notice}</p> : null}
    {error ? <p role="alert">{error}</p> : null}
    {confirm ? <section className="notice notice-danger" aria-labelledby="delete-title">
      <h3 id="delete-title">{confirm.id ? `Xóa buổi “${confirm.title}”?` : 'Xóa toàn bộ dữ liệu học tập?'}</h3>
      <p>{confirm.id ? 'Hội thoại, nhận xét, điểm phát âm và bản ghi tạm của buổi này sẽ được dọn. Thẻ đã lưu vẫn còn trong sổ tay.' :
        'Buổi luyện, thẻ, lượt ôn, bài đã lưu, lượt luyện nghe và lượt chấm phát âm sẽ bị xóa. Tài khoản, hồ sơ, ảnh đại diện và lịch luyện tập vẫn được giữ.'}</p>
      <p>Thống kê sẽ cập nhật theo dữ liệu còn lại. Bạn không thể hoàn tác thao tác này.</p>
      <div className="actions"><button className="button-danger" disabled={busy} onClick={() => void remove()}>{busy ? 'Đang xóa…' : 'Xác nhận xóa'}</button>
        <button disabled={busy} onClick={() => setConfirm(null)}>Hủy</button></div>
    </section> : null}
    {query.error ? <div role="alert"><p>{query.error}</p><button onClick={query.reload}>Thử tải lại</button></div> :
      !query.data ? <p role="status">Đang tải lịch sử…</p> : <>
        <p className="learning-result-count" role="status">{query.data.total} buổi luyện đã lưu</p>
        {query.data.items.length === 0 ? <div className="empty-state"><Icon name="history" /><h2>Chưa có buổi luyện trong trang này</h2><p>Bắt đầu một tình huống để lưu lại cuộc trò chuyện đầu tiên.</p><a href="#/scenarios">Chọn tình huống</a></div> : null}
        <ul className="learning-list history-list">{query.data.items.map(item => <li key={item.id}>
          <div className="history-info"><span className="level-badge">{item.level}</span><h2><a href={`#/practice/${item.id}`}>{item.title}</a></h2>
          <p><time dateTime={item.startedAt}>{dateTime.format(new Date(item.startedAt))}</time><span>{item.learnerTurns} lượt trả lời</span></p></div>
          <div className="history-actions"><span className={`status-badge ${item.status === 'COMPLETED' ? 'status-success' : item.status === 'ACTIVE' ? 'status-warning' : ''}`}>{item.status === 'COMPLETED' ? 'Hoàn thành' : item.status === 'ACTIVE' ? 'Đang luyện' : 'Đã đóng'}</span>
            {item.feedbackStatus === 'SUCCEEDED' ? <span className="hint">Có nhận xét</span> : null}
          {confirm?.id === item.id ? null : <button className="button-quiet button-danger" disabled={busy} onClick={() => {
            setError(''); setConfirm({ id: item.id, title: item.title })
            requestAnimationFrame(() => document.getElementById('delete-title')?.scrollIntoView({block:'center'}))
          }}>Xóa buổi<span className="sr-only"> {item.title}</span></button>}</div>
        </li>)}</ul>
        <Pagination page={page} size={query.data.size} total={query.data.total} onPage={setPage} disabled={busy} />
      </>}
    {!confirm ? <div className="data-management"><h2>Quản lý dữ liệu học tập</h2><p>Xóa dữ liệu khi bạn muốn bắt đầu lại. Thao tác này không thể hoàn tác.</p><button className="button-danger" disabled={busy} onClick={() => { setError(''); setConfirm({}); requestAnimationFrame(() =>
      document.getElementById('delete-title')?.scrollIntoView({block:'center'})) }}>Xóa toàn bộ dữ liệu học tập</button></div> : null}
  </section>
}
