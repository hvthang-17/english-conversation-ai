import { useEffect, useState } from 'react'
import { ApiError } from '../../../shared/api/client'
import { isLevel, type Level } from '../../../shared/lib/learning-level'
import { getScenarios, type ScenarioPage, type Topic } from '../api/catalog-api'
type Props = { defaultLevel: Level; topics: Topic[]; location: string; onSessionExpired: () => void }
export function ScenarioList({ defaultLevel, topics, location, onSessionExpired }: Props) {
  const query = new URLSearchParams(location.split('?')[1] ?? '')
  const suggested = query.get('level')
  const [level, setLevel] = useState<Level>(isLevel(suggested) ? suggested : defaultLevel)
  const [topicId, setTopicId] = useState(query.get('topicId') ?? '')
  const [page, setPage] = useState(0)
  const [retry, setRetry] = useState(0)
  return <section aria-labelledby="catalog-title"><h2 id="catalog-title">Chọn một cuộc trò chuyện</h2><p>Tìm tình huống gần với nhu cầu của bạn. Đọc vai trò và mục tiêu trước khi chọn bài.</p>
    <div className="catalog-filters"><div className="form-field"><label htmlFor="catalog-level">Trình độ</label><select id="catalog-level" value={level} onChange={e => { if (isLevel(e.target.value)) { setLevel(e.target.value); setPage(0) } }}><option value="A2">A2</option><option value="B1">B1</option></select></div><div className="form-field"><label htmlFor="catalog-topic">Chủ đề</label><select id="catalog-topic" value={topicId} onChange={e => { setTopicId(e.target.value); setPage(0) }}><option value="">Tất cả chủ đề</option>{topics.map(topic => <option value={topic.id} key={topic.id}>{topic.name}</option>)}</select></div></div>
    <ScenarioResults key={`${level}:${topicId}:${page}:${retry}`} level={level} topicId={topicId} page={page} onPage={setPage} onRetry={() => setRetry(r => r + 1)} onSessionExpired={onSessionExpired} />
  </section>
}
function ScenarioResults({ level, topicId, page, onPage, onRetry, onSessionExpired }: { level: Level; topicId: string; page: number; onPage: (page: number) => void; onRetry: () => void; onSessionExpired: () => void }) {
  const [data, setData] = useState<ScenarioPage | null>(null)
  const [error, setError] = useState('')
  useEffect(() => {
    const controller = new AbortController()
    getScenarios(level, topicId, page, controller.signal).then(setData).catch((e: unknown) => { if (controller.signal.aborted) return; if (e instanceof ApiError && e.status === 401) onSessionExpired(); else setError(e instanceof Error ? e.message : 'Không thể tải tình huống.') })
    return () => controller.abort()
  }, [level, topicId, page, onSessionExpired])
  if (error) return <div role="alert"><p>{error}</p><button onClick={onRetry}>Thử tải lại</button></div>
  if (!data) return <p role="status">Đang tải tình huống…</p>
  if (!data.content.length) return <p role="status">Chưa có tình huống phù hợp. Thử chọn chủ đề hoặc trình độ khác.</p>
  return <><p className="result-count" role="status">{data.totalElements} tình huống phù hợp · Trình độ {level}</p><div className="scenario-grid">{data.content.map(item => <article className="scenario-card" key={item.id}><p className="topic-label">{item.topicName} · {level}</p><h3><a href={`#/scenarios/${item.id}?level=${level}`}>{item.title}</a></h3><p>{item.description}</p><a className="lesson-link" href={`#/scenarios/${item.id}?level=${level}`}>Xem vai trò và mục tiêu <span aria-hidden="true">→</span></a></article>)}</div><nav className="pagination" aria-label="Phân trang tình huống"><button disabled={page === 0} onClick={() => onPage(page - 1)}>Trang trước</button><span>Trang {page + 1} / {Math.ceil(data.totalElements / data.size)}</span><button disabled={(page + 1) * data.size >= data.totalElements} onClick={() => onPage(page + 1)}>Trang sau</button></nav></>
}
