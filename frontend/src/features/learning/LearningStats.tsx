import { useLearningData, type SessionGuard, type Statistics } from './learningApi'

const categories: Record<string, string> = { GRAMMAR: 'Ngữ pháp', VOCABULARY: 'Từ vựng', EXPRESSION: 'Diễn đạt' }

export function LearningStats({ onSessionExpired }: SessionGuard) {
  const { data, error, reload } = useLearningData<Statistics>('learning/stats', onSessionExpired)
  return <section aria-labelledby="stats-title" className="learning-panel">
    <h2 id="stats-title">Hoạt động học tập</h2>
    {error ? <div role="alert"><p>{error}</p><button onClick={reload}>Thử tải lại</button></div> :
      !data ? <p role="status">Đang tải thống kê…</p> : <>
        <dl className="learning-metrics">
          <div><dt>Buổi hoàn thành</dt><dd>{data.completedSessions}</dd></div>
          <div><dt>Lượt người học</dt><dd>{data.learnerTurns}</dd></div>
          <div><dt>Ngày có luyện</dt><dd>{data.practiceDays}</dd></div>
        </dl>
        <h3>Những ngày luyện gần đây</h3>
        <p>Ngày luyện được tính theo giờ Việt Nam.</p>
        {data.recentDays.length ? <ul>{data.recentDays.map(day => <li key={day.label}><time dateTime={day.label}>
          {day.label.split('-').reverse().join('/')}</time> · {day.count} lượt</li>)}</ul> : <p>Chưa có lượt luyện tập.</p>}
        <h3>Chủ đề đã luyện</h3>
        {data.topics.length ? <ul>{data.topics.map(topic => <li key={topic.topic}>{topic.topic} · {topic.completedSessions} buổi hoàn thành · {topic.learnerTurns} lượt</li>)}</ul> : <p>Chưa có hoạt động theo chủ đề.</p>}
        <h3>Nhóm lỗi trong nhận xét</h3>
        {data.errorGroups.length ? <ul>{data.errorGroups.map(group => <li key={group.label}>{categories[group.label] ?? group.label} · {group.count} lỗi</li>)}</ul> : <p>Chưa có lỗi đã ghi nhận.</p>}
        <h3>Bài luyện gợi ý</h3>
        {data.recommendations.length ? <ul>{data.recommendations.map(item => <li key={item.scenarioId}>
          <a href={`#/scenarios/${item.scenarioId}?level=${item.level}`}>{item.title} · {item.level}</a> · {item.reason}
        </li>)}</ul> : <p>Chưa có bài phù hợp với trình độ hiện tại.</p>}
      </>}
  </section>
}
