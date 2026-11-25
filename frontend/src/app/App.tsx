import { AuthPanel } from '../features/auth/components/AuthPanel'
import { accountPages, catalogPages, laterPages, listeningPages, protectedPages, route, startPage } from './routing/routes'
import { userResponse, type User } from '../features/auth/model/user'
import { LearningWorkspace } from './workspace/LearningWorkspace'
import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { ApiError, getJson, mutateJson } from '../shared/api/client'
import { AppFrame } from './layouts/AppFrame'
import { PublicLayout } from './layouts/PublicLayout'
import { RecoveryPanel } from '../features/auth/components/RecoveryPanel'
import { LearningDataPanel } from '../features/learning/components/LearningDataPanel'
import { Icon } from '../shared/ui/Icon'
import { AdminWorkspace } from '../features/admin/components/AdminWorkspace'
export function App() {
  const [page, setPage] = useState(() => route())
  const [location, setLocation] = useState(window.location.hash)
  const [recoveryRevision, setRecoveryRevision] = useState(0)
  const [learningAvailable, setLearningAvailable] = useState(false)
  const [lessonCatalogAvailable, setLessonCatalogAvailable] = useState(false)
  const [lessonMaterialsAvailable, setLessonMaterialsAvailable] = useState(false)
  const [listeningAvailable, setListeningAvailable] = useState(false)
  const [pronunciationAvailable, setPronunciationAvailable] = useState(false)
  const returnTo = useRef('')
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)
  const [pending, setPending] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [fields, setFields] = useState<Record<string, string>>({})
  useEffect(() => {
    const onHash = () => {
      const next = route()
      if (next === 'reset-password' && window.location.hash.includes('?')) {
        setRecoveryRevision(revision => revision + 1)
      }
      setLocation(window.location.hash)
      setPage(next)
      setError('')
      setFields({})
    }
    window.addEventListener('hashchange', onHash)
    const controller = new AbortController()
    const features = getJson('system/features', controller.signal).then(value => {
      const features = value as { learningWorkspaceAvailable?: boolean; lessonCatalogAvailable?: boolean; lessonMaterialsAvailable?: boolean; listeningAvailable?: boolean; pronunciationAvailable?: boolean }
      const learning = features.learningWorkspaceAvailable === true
      const catalog = features.lessonCatalogAvailable === true
      if (!controller.signal.aborted) {
        setLearningAvailable(learning)
        setLessonCatalogAvailable(catalog)
        setLessonMaterialsAvailable(features.lessonMaterialsAvailable === true)
        setListeningAvailable(features.listeningAvailable === true)
        setPronunciationAvailable(features.pronunciationAvailable === true)
      }
      return { learning, catalog }
    })
    Promise.allSettled([getJson('auth/me', controller.signal), features]).then(([identity, capabilities]) => {
      if (controller.signal.aborted) return
      if (identity.status === 'rejected') throw identity.reason
      const { learning, catalog } = capabilities.status === 'fulfilled' ? capabilities.value : { learning: false, catalog: false }
      const value = identity.value
      const account = userResponse(value)
      setUser(account)
      if (account.role === 'ADMIN') window.location.hash = '/admin'
      else if (route() === 'login' || route() === 'register')
        window.location.hash = startPage(catalog, learning)
    }).catch((e: unknown) => {
      if (controller.signal.aborted) return
      if (!(e instanceof ApiError && e.status === 401)) setError('Không thể kết nối máy chủ. Hãy tải lại trang để thử lại.')
      if (protectedPages.includes(route())) { returnTo.current = window.location.hash; window.location.hash = '/login'; setMessage('Vui lòng đăng nhập để tiếp tục.') }
    }).finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => { controller.abort(); window.removeEventListener('hashchange', onHash) }
  }, [])
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (pending) return
    const form = event.currentTarget
    const data = new FormData(form)
    const mode = user ? 'password' : page === 'register' ? 'register' : 'login'
    setError(''); setMessage(''); setFields({})
    if (mode !== 'login' && data.get('newPassword') !== data.get('confirmation')) { setFields({ confirmation: 'Mật khẩu xác nhận chưa khớp.' }); return }
    setPending(true)
    try {
      if (mode === 'password') {
        await mutateJson('auth/password', { currentPassword: data.get('currentPassword'), newPassword: data.get('newPassword') })
        setUser(null); window.location.hash = '/login'; setMessage('Đã đổi mật khẩu và kết thúc mọi phiên. Vui lòng đăng nhập lại.')
      } else if (mode === 'register') {
        await mutateJson('auth/register', { email: data.get('email'), password: data.get('newPassword') })
        window.location.hash = '/login'; setMessage('Tạo tài khoản thành công. Bạn có thể đăng nhập ngay.')
      } else {
        const account = userResponse(await mutateJson('auth/login', { email: data.get('email'), password: data.get('password') }))
        setUser(account)
        const requested = returnTo.current
        returnTo.current = ''
        const requestedPage = route(requested)
        const allowed = accountPages.includes(requestedPage)
          || (lessonCatalogAvailable && catalogPages.includes(requestedPage))
          || (lessonMaterialsAvailable && requestedPage === 'notebook')
          || (listeningAvailable && listeningPages.includes(requestedPage))
          || (pronunciationAvailable && requestedPage === 'pronunciation')
          || (learningAvailable && laterPages.includes(requestedPage))
        window.location.hash = account.role === 'ADMIN' ? '/admin'
          : allowed ? requested : startPage(lessonCatalogAvailable, learningAvailable)
      }
      form.reset()
    } catch (e) {
      if (e instanceof ApiError) {
        setFields(e.fieldErrors)
        if (e.status === 401 && mode === 'password') { setUser(null); window.location.hash = '/login'; setMessage('Phiên đã hết hạn hoặc bị thu hồi. Vui lòng đăng nhập lại.') }
        else setError(e.message)
      } else setError('Không thể hoàn tất yêu cầu. Kiểm tra kết nối rồi thử lại; nếu đã gửi đăng ký, hãy thử đăng nhập.')
    } finally { setPending(false) }
  }
  async function logout() {
    setPending(true); setError(''); setMessage('')
    try { await mutateJson('auth/logout'); setUser(null); window.location.hash = '/login'; setMessage('Đã đăng xuất khỏi thiết bị này.') }
    catch (e) { if (e instanceof ApiError && e.status === 401) { setUser(null); window.location.hash = '/login'; setMessage('Phiên đã kết thúc. Vui lòng đăng nhập lại.') } else setError(e instanceof Error ? e.message : 'Không thể đăng xuất. Vui lòng thử lại.') }
    finally { setPending(false) }
  }
  async function refresh() {
    setPending(true); setError(''); setMessage('')
    try {
      const account = userResponse(await getJson('auth/me'))
      setUser(account)
    }
    catch (e) { if (e instanceof ApiError && e.status === 401) { setUser(null); window.location.hash = '/login'; setMessage('Phiên đã hết hạn hoặc bị thu hồi. Vui lòng đăng nhập lại.') } else setError('Không thể tải tài khoản. Vui lòng thử lại.') }
    finally { setPending(false) }
  }
  const onSessionExpired = useCallback(() => { if (protectedPages.includes(route())) returnTo.current = window.location.hash; setUser(null); window.location.hash = '/login'; setMessage('Phiên đã hết hạn hoặc bị thu hồi. Vui lòng đăng nhập lại.') }, [])
  if (loading) return <div className="session-loading" role="status"><Icon name="conversation" /><p>Đang mở không gian của bạn…</p></div>
  if (user?.role === 'ADMIN') return <AdminWorkspace key={user.id} accountId={user.id} email={user.email}
    pending={pending} actionError={error} onLogout={() => void logout()} onSessionExpired={onSessionExpired} />
  if (page === 'forgot-password' || page === 'reset-password') return <PublicLayout compact><RecoveryPanel key={`${page}:${recoveryRevision}`} mode={page} onReset={() => setUser(null)} /></PublicLayout>
  if (user && page === 'data') return <AppFrame page="data" email={user.email} isAdmin={false}
    learningAvailable={learningAvailable} lessonCatalogAvailable={lessonCatalogAvailable} lessonMaterialsAvailable={lessonMaterialsAvailable} listeningAvailable={listeningAvailable} pronunciationAvailable={pronunciationAvailable} pending={pending} onLogout={() => void logout()}>
    <LearningDataPanel learningAvailable={learningAvailable} lessonCatalogAvailable={lessonCatalogAvailable} lessonMaterialsAvailable={lessonMaterialsAvailable} listeningAvailable={listeningAvailable} pronunciationAvailable={pronunciationAvailable} onSessionExpired={onSessionExpired} />
  </AppFrame>
  if (user && page !== 'account') return <LearningWorkspace key={user.id}
    page={(learningAvailable && laterPages.includes(page)) || (lessonCatalogAvailable && catalogPages.includes(page))
      || (lessonMaterialsAvailable && page === 'notebook') || (listeningAvailable && listeningPages.includes(page)) || (pronunciationAvailable && page === 'pronunciation') ? page : 'profile'} location={location} email={user.email}
    accountId={user.id} isAdmin={false} onSessionExpired={onSessionExpired} onLogout={() => void logout()}
    pending={pending} learningAvailable={learningAvailable} lessonCatalogAvailable={lessonCatalogAvailable}
    lessonMaterialsAvailable={lessonMaterialsAvailable} listeningAvailable={listeningAvailable} pronunciationAvailable={pronunciationAvailable} actionError={error} />
  const panel = <AuthPanel page={page} user={user} pending={pending} message={message} error={error} fields={fields} submit={submit} refresh={refresh} onSessionExpired={onSessionExpired} onDeleted={() => { setUser(null); window.location.hash='/login'; setMessage('Đã nhận yêu cầu xóa. Tài khoản đã bị chặn, mọi phiên đã kết thúc và hệ thống đang dọn dữ liệu.'); }} />
  return user ? <AppFrame page="account" email={user.email} isAdmin={false} learningAvailable={learningAvailable} lessonCatalogAvailable={lessonCatalogAvailable} lessonMaterialsAvailable={lessonMaterialsAvailable} listeningAvailable={listeningAvailable} pronunciationAvailable={pronunciationAvailable} pending={pending} onLogout={() => void logout()}>{panel}</AppFrame> : <PublicLayout>{panel}</PublicLayout>
}
