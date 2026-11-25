import { useEffect, useState } from 'react'
import { ApiError } from '../../shared/api/client'
import { getProfile, type Profile } from '../../features/profile/api/profile-api'
import { ProfileForm } from '../../features/profile/components/ProfileForm'
import { getTopics, type Topic } from '../../features/scenarios/api/catalog-api'
import { ScenarioList } from '../../features/scenarios/components/ScenarioList'
import { ScenarioDetail } from '../../features/scenarios/components/ScenarioDetail'
import { PracticeChat } from '../../features/conversation/components/PracticeChat'
import { ReviewCards } from '../../features/review/components/ReviewCards'
import { NotebookPanel } from '../../features/learning/components/NotebookPanel'
import { PracticeHistory } from '../../features/history/components/PracticeHistory'
import { LearningStats } from '../../features/statistics/components/LearningStats'
import { LessonCatalog, LessonDetail } from '../../features/lessons/components/LessonCatalog'
import { ListeningAttemptScreen, ListeningHistory } from '../../features/listening/components/ListeningScreens'
import { AppFrame } from '../layouts/AppFrame'
import { PronunciationScreen } from '../../features/pronunciation/components/PronunciationScreen'

type Props = { page: string; location: string; email: string; accountId: string; isAdmin: boolean; onSessionExpired: () => void; onLogout: () => void; pending: boolean; actionError: string; learningAvailable: boolean; lessonCatalogAvailable: boolean; lessonMaterialsAvailable: boolean; listeningAvailable: boolean; pronunciationAvailable: boolean }
export function LearningWorkspace(props: Props) {
  const [retry, setRetry] = useState(0)
  return <AppFrame page={props.page} email={props.email} isAdmin={props.isAdmin} pending={props.pending} learningAvailable={props.learningAvailable} lessonCatalogAvailable={props.lessonCatalogAvailable} lessonMaterialsAvailable={props.lessonMaterialsAvailable} listeningAvailable={props.listeningAvailable} pronunciationAvailable={props.pronunciationAvailable} onLogout={props.onLogout}><div className="learning-workspace">{props.actionError ? <p role="alert">{props.actionError}</p> : null}{
    props.page === 'admin' ? <p role="alert">Trang này yêu cầu quyền quản trị. <a href="#/scenarios">Về tình huống</a></p> :
    props.page === 'review' ? <ReviewCards onSessionExpired={props.onSessionExpired} /> :
    props.page === 'notebook' ? <NotebookPanel onSessionExpired={props.onSessionExpired} /> :
    props.page === 'history' ? <PracticeHistory onSessionExpired={props.onSessionExpired} /> :
    props.page === 'stats' ? <LearningStats onSessionExpired={props.onSessionExpired} /> :
    props.page === 'pronunciation' ? <PronunciationScreen key={props.location} lessonId={new URLSearchParams(props.location.split('?')[1]).get('lesson') ?? undefined} onSessionExpired={props.onSessionExpired} /> :
    props.page === 'listening' ? <ListeningHistory onSessionExpired={props.onSessionExpired} isAdmin={props.isAdmin} /> :
    props.page === 'listening-attempt' ? <ListeningAttemptScreen key={props.location} id={props.location.split(/[/?]/)[3] ?? ''} onSessionExpired={props.onSessionExpired} /> :
    props.page === 'lesson' ? <LessonDetail key={props.location} id={props.location.split(/[/?]/)[2] ?? ''} onSessionExpired={props.onSessionExpired} materialsAvailable={props.lessonMaterialsAvailable} listeningAvailable={props.listeningAvailable} pronunciationAvailable={props.pronunciationAvailable} /> :
    <WorkspaceContent key={retry} {...props} onRetry={() => setRetry(r => r + 1)} />
  }</div></AppFrame>
}
function WorkspaceContent({ page, location, learningAvailable, onSessionExpired, onRetry }: Props & { onRetry: () => void }) {
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
  if (page === 'profile') return <ProfileForm profile={data.profile} topics={data.topics} learningAvailable={learningAvailable} onSaved={profile => setData(previous => previous ? { ...previous, profile } : previous)} onSessionExpired={onSessionExpired} />
  if (page === 'scenario') return <ScenarioDetail key={location} location={location} onSessionExpired={onSessionExpired} practiceAvailable={learningAvailable} />
  if (page === 'lessons' || page === 'saved-lessons') return <LessonCatalog key={location} location={location} topics={data.topics} saved={page === 'saved-lessons'} onSessionExpired={onSessionExpired} />
  return <ScenarioList key={location} location={location} defaultLevel={data.profile?.selectedLevel ?? 'A2'} topics={data.topics} onSessionExpired={onSessionExpired} />
}
