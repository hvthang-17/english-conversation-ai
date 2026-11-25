import { useRef, useState } from 'react'
import type { SessionGuard } from '../../../shared/types/api'
import { Brand } from '../../../shared/ui/Brand'
import { Icon } from '../../../shared/ui/Icon'
import { PageHeading } from '../../../shared/ui/PageHeading'
import { AdminAccounts } from '../accounts/components/AdminAccounts'
import { AdminActivity } from '../activity/components/AdminActivity'
import { MaterialAdmin } from '../materials/components/MaterialAdmin'
import { AdminOverview } from '../overview/components/AdminOverview'
import { CatalogAdmin } from '../scenarios/components/CatalogAdmin'
import { AdminNavigation } from './AdminNavigation'
import { sections, type AdminSection } from '../model/navigation'
import '../styles/admin.css'

export function AdminWorkspace({ accountId, email, pending, actionError, onLogout, onSessionExpired }: SessionGuard & {
  accountId: string; email: string; pending: boolean; actionError: string; onLogout: () => void
}) {
  const [section, setSection] = useState<AdminSection>('overview')
  const [menuOpen, setMenuOpen] = useState(false)
  const [collapsed, setCollapsed] = useState(false)
  const menuButton = useRef<HTMLButtonElement>(null)
  const main = useRef<HTMLElement>(null)

  function navigate(next: AdminSection) {
    setSection(next)
    setMenuOpen(false)
    main.current?.focus()
    window.scrollTo({ top: 0 })
  }

  return <div className={`admin-shell${collapsed ? ' admin-shell-collapsed' : ''}`}
    onKeyDown={event => {
      if (event.key === 'Escape' && menuOpen) { setMenuOpen(false); menuButton.current?.focus() }
    }}>
    <a className="skip-link" href="#admin-main" onClick={event => { event.preventDefault(); main.current?.focus() }}>Đến nội dung quản trị</a>
    <header className="admin-header">
      <Brand href="#/admin" />
      <div className="admin-header-actions">
        <span className="admin-identity"><strong>Quản trị viên</strong><span>{email}</span></span>
        <button type="button" className="button-quiet" disabled={pending} onClick={onLogout}>
          <Icon name="logout" /><span>{pending ? 'Đang đăng xuất…' : 'Đăng xuất'}</span>
        </button>
        <button ref={menuButton} type="button" className="admin-menu-toggle" aria-expanded={menuOpen}
          aria-controls="admin-navigation" onClick={() => setMenuOpen(open => !open)}>
          <Icon name={menuOpen ? 'close' : 'menu'} /> Menu
        </button>
      </div>
    </header>
    <aside className={`admin-sidebar${menuOpen ? ' admin-sidebar-open' : ''}`} id="admin-navigation">
      <AdminNavigation section={section} collapsed={collapsed} onNavigate={navigate} />
      <button type="button" className="button-quiet admin-collapse" aria-expanded={!collapsed}
        aria-label={collapsed ? 'Mở rộng điều hướng' : 'Thu gọn điều hướng'} onClick={() => setCollapsed(value => !value)}>
        <Icon name={collapsed ? 'panelOpen' : 'panelClose'} /><span>Thu gọn điều hướng</span>
      </button>
    </aside>
    <main ref={main} id="admin-main" tabIndex={-1} aria-labelledby="admin-title" className="admin-workspace">
      <PageHeading id="admin-title" title={sections[section].title} description={sections[section].description} />
      {actionError ? <p role="alert">{actionError}</p> : null}
      {section === 'overview' ? <AdminOverview onSessionExpired={onSessionExpired} onNavigate={navigate} /> : null}
      {section === 'users' ? <AdminAccounts accountId={accountId} onSessionExpired={onSessionExpired} /> : null}
      {section === 'topics' ? <MaterialAdmin topicsOnly onSessionExpired={onSessionExpired} /> : null}
      {section === 'materials' ? <MaterialAdmin onSessionExpired={onSessionExpired} /> : null}
      {section === 'scenarios' ? <CatalogAdmin onSessionExpired={onSessionExpired} /> : null}
      {section === 'audit' || section === 'ai'
        ? <AdminActivity key={section} audit={section === 'audit'} onSessionExpired={onSessionExpired} /> : null}
      {section === 'tickets' || section === 'announcements' || section === 'notifications'
        ? <div className="admin-empty"><Icon name="settings" size={32} /><h2>Chức năng đang phát triển</h2>
          <p>{sections[section].title} chưa được triển khai.</p>
          <button type="button" onClick={() => navigate('overview')}>Về tổng quan</button></div> : null}
    </main>
  </div>
}
