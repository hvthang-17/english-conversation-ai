import { Icon } from '../../../shared/ui/Icon'
import { navigation, sections, type AdminSection } from '../model/navigation'

export function AdminNavigation({ section, collapsed, onNavigate }: {
  section: AdminSection; collapsed: boolean; onNavigate: (section: AdminSection) => void
}) {
  return <nav aria-label="Mục quản trị">
    {navigation.map(group => <div className="admin-nav-group" key={group.label}>
      <p>{group.label}</p>
      {group.keys.map(key => <button key={key} type="button" aria-current={section === key ? 'page' : undefined}
        title={collapsed ? sections[key].label : undefined} onClick={() => onNavigate(key)}>
        <Icon name={sections[key].icon} /><span>{sections[key].label}</span>
      </button>)}
    </div>)}
  </nav>
}
