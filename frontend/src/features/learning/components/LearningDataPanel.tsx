import { useState } from 'react'
import { PageHeading } from '../../../shared/ui/PageHeading'
import { Icon } from '../../../shared/ui/Icon'
import '../styles/learning.css'
import { ApiError, mutateJson } from '../../../shared/api/client'

type Props = {
  learningAvailable: boolean
  lessonCatalogAvailable: boolean
  lessonMaterialsAvailable: boolean
  listeningAvailable: boolean
  pronunciationAvailable: boolean
  onSessionExpired: () => void
}

export function LearningDataPanel({ learningAvailable, lessonCatalogAvailable, lessonMaterialsAvailable, listeningAvailable, pronunciationAvailable, onSessionExpired }: Props) {
  const [confirming, setConfirming] = useState(false)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')

  async function deleteData() {
    setPending(true)
    setError('')
    setMessage('')
    try {
      await mutateJson('learning/data', undefined, 'DELETE')
      setConfirming(false)
      setMessage('Đã xóa dữ liệu luyện tập. Hồ sơ, ảnh đại diện và lịch học vẫn được giữ.')
    } catch (failure) {
      if (failure instanceof ApiError && failure.status === 401) onSessionExpired()
      else setError(failure instanceof Error ? failure.message : 'Không thể xóa dữ liệu luyện tập.')
    } finally {
      setPending(false)
    }
  }

  return <section className="learning-data-page" aria-labelledby="learning-data-title">
    <PageHeading id="learning-data-title" title="Dữ liệu học tập của tôi" description="Xem lại nội dung đã lưu và chủ động quản lý dữ liệu luyện tập của bạn." />
    <section className="learning-data-links" aria-labelledby="learning-data-content-title"><h2 id="learning-data-content-title">Nội dung của bạn</h2>
      <ul>
        {learningAvailable ? <><li><a href="#/history"><Icon name="history" /><span><strong>Buổi luyện và phản hồi</strong><small>Đọc lại những cuộc trò chuyện đã lưu.</small></span><Icon name="chevron" size={20} /></a></li><li><a href="#/review"><Icon name="cards" /><span><strong>Thẻ ôn tập</strong><small>Từ và mẫu câu từ nhận xét buổi luyện.</small></span><Icon name="chevron" size={20} /></a></li></> : null}
        {lessonCatalogAvailable ? <li><a href="#/saved-lessons"><Icon name="bookmark" /><span><strong>Bài đã lưu</strong><small>Yêu thích và Học sau của bạn.</small></span><Icon name="chevron" size={20} /></a></li> : null}
        {lessonMaterialsAvailable ? <li><a href="#/notebook"><Icon name="book" /><span><strong>Sổ tay và lịch sử ôn</strong><small>Học liệu bạn muốn luyện lại.</small></span><Icon name="chevron" size={20} /></a></li> : null}
        {listeningAvailable ? <li><a href="#/listening"><Icon name="volume" /><span><strong>Luyện nghe</strong><small>Bản nháp và kết quả bài nghe.</small></span><Icon name="chevron" size={20} /></a></li> : null}
        {pronunciationAvailable ? <li><a href="#/pronunciation"><Icon name="microphone" /><span><strong>Luyện phát âm</strong><small>Các lượt chấm theo câu mẫu.</small></span><Icon name="chevron" size={20} /></a></li> : null}
      </ul><p className="hint">Thông tin cá nhân và lịch học nằm trong <a href="#/profile">Hồ sơ học tập</a>.</p>
    </section>
    {error ? <p role="alert">{error}</p> : null}
    {message ? <p className="notice" role="status">{message}</p> : null}
    <section className="learning-data-danger" aria-labelledby="learning-data-delete-title"><h2 id="learning-data-delete-title">Xóa dữ liệu luyện tập</h2><p>Chỉ thực hiện khi bạn muốn bắt đầu lại. Thao tác này không thể hoàn tác.</p>
    {confirming ? <div className="notice notice-danger">
      <p>{learningAvailable
        ? 'Hệ thống sẽ xóa buổi luyện, phản hồi, thẻ đã lưu và lịch sử ôn tập. '
        : lessonMaterialsAvailable ? 'Sổ tay và lịch sử ôn sẽ được xóa. ' : null}{lessonCatalogAvailable ? 'Yêu thích và Học sau sẽ được xóa. ' : null}{listeningAvailable ? 'Bản nháp và kết quả luyện nghe sẽ được xóa. ' : null}{pronunciationAvailable ? 'Bản ghi tạm và kết quả phát âm sẽ được xóa. ' : null}Tài khoản, hồ sơ, ảnh đại diện và lịch học được giữ.</p>
      <div className="actions">
        <button type="button" className="button-danger" disabled={pending} onClick={() => void deleteData()}>{pending ? 'Đang xóa…' : 'Xác nhận xóa dữ liệu luyện tập'}</button>
        <button type="button" disabled={pending} onClick={() => setConfirming(false)}>Hủy</button>
      </div>
    </div> : <button className="button-danger" type="button" aria-expanded={confirming} onClick={() => { setConfirming(true); setMessage('') }}>Xóa dữ liệu luyện tập</button>}
    <p>Để xóa toàn bộ tài khoản, mở <a href="#/account">Tài khoản</a>.</p>
    </section>
  </section>
}
