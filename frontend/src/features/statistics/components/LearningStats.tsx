import { useApiQuery } from '../../../shared/hooks/useApiQuery'
import { type SessionGuard } from '../../../shared/types/api'
import { type Statistics } from '../types'
import { PageHeading } from '../../../shared/ui/PageHeading'
import { Icon } from '../../../shared/ui/Icon'
import '../styles/statistics.css'

const categories: Record<string, string> = { GRAMMAR: 'Ngữ pháp', VOCABULARY: 'Từ vựng', EXPRESSION: 'Diễn đạt' }

export function LearningStats({ onSessionExpired }: SessionGuard) {
  const { data, error, reload } = useApiQuery<Statistics>('learning/stats', onSessionExpired)
  const maximumTurns = Math.max(1, ...(data?.recentDays.map(day => day.count) ?? []))
  return <section aria-labelledby="stats-title" className="learning-panel">
    <PageHeading id="stats-title" title="Hoạt động học tập" description="Nhìn lại hoạt động đã lưu của bạn. Các con số phản ánh việc luyện tập, không phải điểm đánh giá năng lực." />
    {error ? <div role="alert"><p>{error}</p><button onClick={reload}>Thử tải lại</button></div> :
      !data ? <p className="loading-state" role="status">Đang tải thống kê…</p> : <>
        <dl className="learning-metrics">
          <div><dt><Icon name="check" />Buổi hoàn thành</dt><dd>{data.completedSessions.toLocaleString('vi-VN')}</dd></div>
          <div><dt><Icon name="conversation" />Lượt trả lời của bạn</dt><dd>{data.learnerTurns.toLocaleString('vi-VN')}</dd></div>
          <div><dt><Icon name="history" />Ngày có luyện tập</dt><dd>{data.practiceDays.toLocaleString('vi-VN')}</dd></div>
        </dl>
        <div className="stats-layout">
          <section className="stats-section" aria-labelledby="recent-days-title"><h2 id="recent-days-title">Những ngày luyện gần đây</h2>
            <p className="hint">Ngày luyện được tính theo giờ Việt Nam.</p>
            {data.recentDays.length ? <ul className="activity-days">{data.recentDays.map(day => <li key={day.label}>
              <time dateTime={day.label}>{day.label.split('-').reverse().join('/')}</time>
              <meter min={0} max={maximumTurns} value={day.count} aria-label={`Số lượt ngày ${day.label}`} />
              <span>{day.count} lượt</span>
            </li>)}</ul> : <p className="empty-copy">Chưa có lượt luyện tập. Một cuộc trò chuyện ngắn là đủ để bắt đầu.</p>}
          </section>
          <section className="stats-section" aria-labelledby="error-groups-title"><h2 id="error-groups-title">Nhóm lỗi trong nhận xét</h2>
            <p className="hint">Dùng nhận xét để chọn điều cần luyện thêm.</p>
            {data.errorGroups.length ? <ul className="stat-rows">{data.errorGroups.map(group => <li key={group.label}><span>{categories[group.label] ?? group.label}</span><strong>{group.count} lỗi</strong></li>)}</ul> : <p className="empty-copy">Chưa có lỗi được ghi nhận.</p>}
          </section>
          <section className="stats-section" aria-labelledby="practiced-topics-title"><h2 id="practiced-topics-title">Chủ đề đã luyện</h2>
            {data.topics.length ? <ul className="stat-rows">{data.topics.map(topic => <li key={topic.topic}><strong>{topic.topic}</strong><span>{topic.completedSessions} buổi hoàn thành, {topic.learnerTurns} lượt</span></li>)}</ul> : <p className="empty-copy">Chưa có hoạt động theo chủ đề.</p>}
          </section>
          <section className="stats-section" aria-labelledby="recommended-title"><h2 id="recommended-title">Cuộc trò chuyện tiếp theo</h2>
            {data.recommendations.length ? <ul className="recommendation-list">{data.recommendations.map(item => <li key={item.scenarioId}>
              <span className="level-badge">{item.level}</span><a href={`#/scenarios/${item.scenarioId}?level=${item.level}`}>{item.title}</a><p>{item.reason}</p>
            </li>)}</ul> : <p className="empty-copy">Chưa có bài phù hợp với trình độ hiện tại. <a href="#/profile">Xem hồ sơ học tập</a></p>}
          </section>
        </div>
      </>}
  </section>
}
