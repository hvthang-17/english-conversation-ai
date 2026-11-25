import { useEffect, useState } from 'react'
import { ApiError } from '../../../shared/api/client'
import { isLevel, type Level } from '../../../shared/lib/learning-level'
import { getScenarios, type ScenarioPage, type Topic } from '../api/catalog-api'
import { PageHeading } from '../../../shared/ui/PageHeading'
import { Icon } from '../../../shared/ui/Icon'
import '../styles/scenarios.css'
type Props = { defaultLevel: Level; topics: Topic[]; location: string; onSessionExpired: () => void }
export function ScenarioList({ defaultLevel, topics, location, onSessionExpired }: Props) {
  const query = new URLSearchParams(location.split('?')[1] ?? '')
  const suggested = query.get('level')
  const [level, setLevel] = useState<Level>(isLevel(suggested) ? suggested : defaultLevel)
  const [topicId, setTopicId] = useState(query.get('topicId') ?? '')
  const [page, setPage] = useState(0)
  const [retry, setRetry] = useState(0)
  return <section className="scenarios-page" aria-labelledby="catalog-title"><PageHeading id="catalog-title" title="Hôm nay, bạn muốn trò chuyện gì?" description="Chọn một tình huống gần với cuộc sống của bạn. Đọc vai trò và mục tiêu, rồi bắt đầu luyện tập." />
    <div className="catalog-filters" role="group" aria-label="Lọc tình huống"><div className="form-field"><label htmlFor="catalog-level">Trình độ</label><select id="catalog-level" value={level} onChange={e => { if (isLevel(e.target.value)) { setLevel(e.target.value); setPage(0) } }}><option value="A2">A2 — Giao tiếp cơ bản</option><option value="B1">B1 — Mở rộng trao đổi</option></select></div><div className="form-field"><label htmlFor="catalog-topic">Chủ đề</label><select id="catalog-topic" value={topicId} onChange={e => { setTopicId(e.target.value); setPage(0) }}><option value="">Tất cả chủ đề</option>{topics.map(topic => <option value={topic.id} key={topic.id}>{topic.name}</option>)}</select></div>{topicId ? <button className="button-quiet" type="button" onClick={() => { setTopicId(''); setPage(0) }}>Bỏ lọc chủ đề</button> : null}</div>
    <ScenarioResults key={`${level}:${topicId}:${page}:${retry}`} level={level} topicId={topicId} page={page} onPage={setPage} onClear={() => { setTopicId(''); setPage(0) }} onRetry={() => setRetry(r => r + 1)} onSessionExpired={onSessionExpired} />
  </section>
}
function ScenarioResults({ level, topicId, page, onPage, onClear, onRetry, onSessionExpired }: { level: Level; topicId: string; page: number; onPage: (page: number) => void; onClear: () => void; onRetry: () => void; onSessionExpired: () => void }) {
  const [data, setData] = useState<ScenarioPage | null>(null)
  const [error, setError] = useState('')
  useEffect(() => {
    const controller = new AbortController()
    getScenarios(level, topicId, page, controller.signal).then(setData).catch((e: unknown) => { if (controller.signal.aborted) return; if (e instanceof ApiError && e.status === 401) onSessionExpired(); else setError(e instanceof Error ? e.message : 'Không thể tải tình huống.') })
    return () => controller.abort()
  }, [level, topicId, page, onSessionExpired])
  if (error) return <div role="alert"><p>{error}</p><button onClick={onRetry}>Thử tải lại</button></div>
  if (!data) return <p className="loading-state" role="status">Đang tải tình huống…</p>
  if (!data.content.length) return <div className="empty-state scenario-empty"><Icon name="conversation" size={32} /><h2>Chưa có tình huống phù hợp</h2><p role="status">Thử chọn chủ đề hoặc trình độ khác để tìm tình huống luyện tập.</p>{topicId ? <button type="button" onClick={onClear}>Xem tất cả chủ đề</button> : null}</div>
  return <><p className="result-count" role="status">{data.totalElements} tình huống ở trình độ {level}</p><div className="scenario-grid">{data.content.map(item => <article className="scenario-card" key={item.id}>
    <div className="scenario-card-top"><span className="scenario-topic"><Icon name="conversation" size={20} />{item.topicName}</span><span className="level-badge">{level}</span></div>
    <h2>{item.title}</h2><p className="scenario-description">{item.description}</p>
    <a className="scenario-detail-link" href={`#/scenarios/${item.id}?level=${level}`} aria-label={`Xem vai trò và mục tiêu: ${item.title}`}>Xem vai trò và mục tiêu<Icon name="chevron" size={20} /></a>
  </article>)}</div><nav className="pagination" aria-label="Phân trang tình huống"><button disabled={page === 0} onClick={() => onPage(page - 1)}>Trang trước</button><span>Trang {page + 1} / {Math.ceil(data.totalElements / data.size)}</span><button disabled={(page + 1) * data.size >= data.totalElements} onClick={() => onPage(page + 1)}>Trang sau</button></nav></>
}
