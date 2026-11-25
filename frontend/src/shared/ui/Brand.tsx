import { Icon } from './Icon'
import './shared-ui.css'

export function Brand({ href = '#/scenarios' }: { href?: string }) {
  return <a className="brand" href={href} aria-label="English Conversation AI — Trang chính">
    <span className="brand-mark"><Icon name="dialogue" size={32} /></span>
    <span className="brand-copy"><span className="brand-name" lang="en">English Conversation</span><span className="brand-subtitle">Luyện tập. Trò chuyện. Tự tin.</span></span>
  </a>
}
