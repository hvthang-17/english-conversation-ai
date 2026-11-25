import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Brand } from '../../shared/ui/Brand'
import { Icon, type IconName } from '../../shared/ui/Icon'
import { HeaderAccount } from './HeaderAccount'
import '../styles/app-frame.css'

type Props = { page: string; email: string; isAdmin: boolean; pending: boolean; learningAvailable?: boolean; lessonCatalogAvailable?: boolean; lessonMaterialsAvailable?: boolean; listeningAvailable?: boolean; pronunciationAvailable?: boolean; onLogout: () => void; children: ReactNode }
const preferenceKey = 'english-conversation.sidebar-collapsed'

export function AppFrame({ page, email, isAdmin, pending, learningAvailable = true, lessonCatalogAvailable = false, lessonMaterialsAvailable = false, listeningAvailable = false, pronunciationAvailable = false, onLogout, children }: Props) {
  const [menuOpen, setMenuOpen] = useState(false)
  const [collapsed, setCollapsed] = useState(() => {
    try { return localStorage.getItem(preferenceKey) === 'true' } catch { return false }
  })
  const menuButton = useRef<HTMLButtonElement>(null)
  const mobileClose = useRef<HTMLButtonElement>(null)
  const activePage = page === 'lesson' ? 'lessons' : page === 'listening-attempt' ? 'listening'
    : ['scenario', 'practice'].includes(page) ? 'scenarios' : page
  useEffect(() => {
    const media = window.matchMedia('(min-width: 1024px)')
    const resize = () => { if (media.matches) setMenuOpen(false) }
    media.addEventListener('change', resize)
    return () => media.removeEventListener('change', resize)
  }, [])
  useEffect(() => {
    if (!menuOpen) return
    const previous = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    mobileClose.current?.focus()
    return () => { document.body.style.overflow = previous }
  }, [menuOpen])
  function closeMenu() { setMenuOpen(false); menuButton.current?.focus() }
  function toggleSidebar() {
    const next = !collapsed
    setCollapsed(next)
    try { localStorage.setItem(preferenceKey, String(next)) } catch { /* Tùy chọn vẫn hoạt động khi storage bị chặn. */ }
  }
  function link(target: string, label: string, icon: IconName) {
    return <a href={`#/${target}`} aria-label={label} title={collapsed ? label : undefined} aria-current={activePage === target ? 'page' : undefined}
      onClick={() => { if (menuOpen) closeMenu() }}>
      <Icon name={icon} /><span className="nav-text">{label}</span>
    </a>
  }
  return <div className={`app-frame${collapsed ? ' is-collapsed' : ''}`}>
    <a className="skip-link" href="#workspace-content" onClick={event => { event.preventDefault(); document.getElementById('workspace-content')?.focus() }}>Đến nội dung chính</a>
    <header className="app-topbar"><div className="topbar-leading">
      <button ref={menuButton} className="mobile-menu" type="button" aria-label="Mở menu điều hướng" aria-expanded={menuOpen} aria-controls="app-navigation" onClick={() => setMenuOpen(true)}><Icon name="menu" /></button>
      <Brand href={lessonCatalogAvailable ? '#/lessons' : '#/profile'} />
    </div><HeaderAccount key={email} email={email} isAdmin={isAdmin} /></header>
    {menuOpen ? <button className="sidebar-backdrop" type="button" aria-label="Đóng menu điều hướng" tabIndex={-1} onClick={closeMenu} /> : null}
    <aside className={`app-sidebar${menuOpen ? ' is-open' : ''}`} id="app-navigation" role={menuOpen ? 'dialog' : undefined} aria-modal={menuOpen || undefined} aria-label="Menu điều hướng" onKeyDown={event => {
      if (event.key === 'Escape' && menuOpen) { event.preventDefault(); closeMenu() }
      if (event.key === 'Tab' && menuOpen) {
        const controls = Array.from(event.currentTarget.querySelectorAll<HTMLElement>('a[href], button:not(:disabled)')).filter(element => element.getClientRects().length)
        const first = controls[0], last = controls.at(-1)
        if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
        else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
      }
    }}>
      <div className="sidebar-mobile-heading"><strong>Không gian học tập</strong><button ref={mobileClose} type="button" aria-label="Đóng menu" onClick={closeMenu}><Icon name="close" /></button></div>
      <nav className="sidebar-nav" aria-label="Điều hướng chính">
        <div className="sidebar-nav-heading">
          {learningAvailable || lessonCatalogAvailable || lessonMaterialsAvailable || listeningAvailable || pronunciationAvailable ? <p className="nav-label">Học và thực hành</p> : null}
          <button className="sidebar-toggle" type="button" aria-controls="app-navigation" aria-expanded={!collapsed} aria-label={collapsed ? 'Mở rộng thanh điều hướng' : 'Thu gọn thanh điều hướng'} title={collapsed ? 'Mở rộng thanh điều hướng' : 'Thu gọn thanh điều hướng'} onClick={toggleSidebar}><Icon name={collapsed ? 'panelOpen' : 'panelClose'} /></button>
        </div>
        {learningAvailable || lessonCatalogAvailable || lessonMaterialsAvailable || listeningAvailable || pronunciationAvailable ? <div className="nav-group">
          {lessonCatalogAvailable ? <>{link('lessons', 'Khám phá bài học', 'book')}{link('saved-lessons', 'Bài đã lưu', 'bookmark')}</> : null}
          {lessonMaterialsAvailable ? link('notebook', 'Sổ tay ôn tập', 'notebook') : null}
          {listeningAvailable ? link('listening', 'Luyện nghe', 'headphones') : null}
          {pronunciationAvailable ? link('pronunciation', 'Luyện phát âm', 'microphone') : null}
          {learningAvailable || lessonCatalogAvailable ? link('scenarios', 'Tình huống', 'conversation') : null}
        </div> : null}
        {learningAvailable ? <div className="nav-group"><p className="nav-label">Theo dõi học tập</p>{link('review', 'Ôn tập', 'cards')}{link('history', 'Lịch sử', 'history')}{link('stats', 'Thống kê', 'chart')}</div> : null}
        <div className="nav-group"><p className="nav-label">Cá nhân</p>{link('profile', 'Hồ sơ học tập', 'profile')}{link('data', 'Dữ liệu học tập', 'database')}{link('account', 'Tài khoản', 'settings')}</div>
        {isAdmin && learningAvailable ? <div className="nav-group"><p className="nav-label">Quản lý</p>{link('admin', 'Quản trị hệ thống', 'shield')}</div> : null}
      </nav>
      <div className="sidebar-bottom"><p className="sidebar-email" title={email}>{email}</p><button className="button-quiet" type="button" aria-label={pending ? 'Đang đăng xuất' : 'Đăng xuất'} title={collapsed ? 'Đăng xuất' : undefined} disabled={pending} onClick={onLogout}><Icon name="logout" /><span className="nav-text">{pending ? 'Đang xử lý…' : 'Đăng xuất'}</span></button></div>
    </aside>
    <main id="workspace-content" tabIndex={-1} className={`workspace-content${page === 'practice' ? ' workspace-practice' : ''}`}>
      {children}
      <footer className="workspace-footer">English Conversation AI<span>Học qua từng cuộc trò chuyện</span></footer>
    </main>
  </div>
}
