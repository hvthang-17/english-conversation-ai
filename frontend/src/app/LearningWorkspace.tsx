import { useEffect, useState } from 'react'
import { ApiError } from '../shared/api/client'
import { getProfile, type Profile } from '../features/profile/api/profile-api'
import { ProfileForm } from '../features/profile/components/ProfileForm'
import { getTopics, type Topic } from '../features/scenarios/api/catalog-api'
import { ScenarioList } from '../features/scenarios/components/ScenarioList'
import { ScenarioDetail } from '../features/scenarios/components/ScenarioDetail'
import { PracticeChat } from '../features/conversation/components/PracticeChat'
import { ReviewCards } from '../features/learning/ReviewCards'
import { PracticeHistory } from '../features/learning/PracticeHistory'
import { LearningStats } from '../features/learning/LearningStats'
import { AdminWorkspace } from '../features/admin/AdminWorkspace'
import '../features/learning/learning.css'
type Props = { page: string; location: string; email: string; accountId: string; isAdmin: boolean; onSessionExpired: () => void; onLogout: () => void; pending: boolean; actionError: string }
export function LearningWorkspace(props: Props) {
  const [retry, setRetry] = useState(0)
  return <section className="learning-workspace"><header className="workspace-header"><p className="signed-in">{props.email}</p><nav aria-label="Không gian học tập"><a href="#/profile" aria-current={props.page === 'profile' ? 'page' : undefined}>Hồ sơ học tập</a><a href="#/scenarios" aria-current={['scenarios', 'scenario', 'practice'].includes(props.page) ? 'page' : undefined}>Tình huống</a><a href="#/review" aria-current={props.page === 'review' ? 'page' : undefined}>Ôn tập</a><a href="#/history" aria-current={props.page === 'history' ? 'page' : undefined}>Lịch sử</a><a href="#/stats" aria-current={props.page === 'stats' ? 'page' : undefined}>Thống kê</a>
    {props.isAdmin ? <a href="#/admin" aria-current={props.page === 'admin' ? 'page' : undefined}>Quản trị</a> : null}
    <a href="#/account">Tài khoản</a><button type="button" disabled={props.pending} onClick={props.onLogout}>Đăng xuất</button></nav></header>{props.actionError ? <p role="alert">{props.actionError}</p> : null}{
    props.page === 'admin' ? props.isAdmin
      ? <AdminWorkspace accountId={props.accountId} onSessionExpired={props.onSessionExpired} />
      : <p role="alert">Trang này yêu cầu quyền quản trị. <a href="#/scenarios">Về tình huống</a></p> :
    props.page === 'review' ? <ReviewCards onSessionExpired={props.onSessionExpired} /> :
    props.page === 'history' ? <PracticeHistory onSessionExpired={props.onSessionExpired} /> :
    props.page === 'stats' ? <LearningStats onSessionExpired={props.onSessionExpired} /> :
    <WorkspaceContent key={retry} {...props} onRetry={() => setRetry(r => r + 1)} />
  }</section>
}
function WorkspaceContent({ page, location, onSessionExpired, onRetry }: Props & { onRetry: () => void }) {
  const [data, setData] = useState<{ profile: Profile | null; topics: Topic[] } | null>(null)
  const [error, setError] = useState('')
  useEffect(() => {
    const controller = new AbortController()
    const profile = getProfile(controller.signal).catch((e: unknown) => { if (e instanceof ApiError && e.status === 404 && e.code === 'PROFILE_NOT_FOUND') return null; throw e })
    Promise.all([profile, getTopics(controller.signal)]).then(([profile, topics]) => setData({ profile, topics })).catch((e: unknown) => { if (controller.signal.aborted) return; if (e instanceof ApiError && e.status === 401) onSessionExpired(); else setError(e instanceof Error ? e.message : 'Không thể tải dữ liệu. Hãy thử lại.') })
    return () => controller.abort()
  }, [onSessionExpired])
  if (page === 'practice') {
    const id = location.split('/')[2] ?? ''
    return <PracticeChat key={id} id={id} onSessionExpired={onSessionExpired} />
  }
  if (error) return <div role="alert"><p>{error}</p><button onClick={onRetry}>Thử tải lại</button></div>
  if (!data) return <p role="status">Đang tải hồ sơ và chủ đề…</p>
  if (page === 'profile') return <ProfileForm profile={data.profile} topics={data.topics} onSaved={profile => setData(previous => previous ? { ...previous, profile } : previous)} onSessionExpired={onSessionExpired} />
  if (page === 'scenario') return <ScenarioDetail key={location} location={location} onSessionExpired={onSessionExpired} />
  return <ScenarioList key={location} location={location} defaultLevel={data.profile?.selectedLevel ?? 'A2'} topics={data.topics} onSessionExpired={onSessionExpired} />
}
