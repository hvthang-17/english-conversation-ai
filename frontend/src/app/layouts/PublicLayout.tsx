import '../styles/public-layout.css'
import type { ReactNode } from 'react'
import { Brand } from '../../shared/ui/Brand'

export function PublicLayout({ children, compact = false }: { children: ReactNode; compact?: boolean }) {
  return <div className={`public-shell${compact ? ' public-shell-compact' : ''}`}>
    <a className="skip-link" href="#auth-title" onClick={event => { event.preventDefault(); document.getElementById('auth-title')?.focus() }}>Đến biểu mẫu</a>
    <header className="public-header"><Brand /><span className="level-badge">Dành cho trình độ A2–B1</span></header>
    <main className="public-main" id="public-content">
      <div className="public-form">{children}</div>
      {!compact ? <section className="public-intro" aria-labelledby="intro-title">
        <h2 id="intro-title">Bắt đầu từ một câu.<br />Tự tin thêm mỗi ngày.</h2>
        <p>Luyện tiếng Anh trong những tình huống quen thuộc. Có chỗ để thử, để sửa và để nói theo cách của bạn.</p>
        <figure className="auth-conversation" aria-labelledby="conversation-caption">
          <figcaption id="conversation-caption"><span>Ví dụ hội thoại</span><strong>Gọi món tại quán</strong></figcaption>
          <div className="auth-example-turn"><span>Nhân viên</span><p lang="en">What would you like?</p></div>
          <div className="auth-example-turn auth-example-reply"><span>Bạn</span><p lang="en">I'd like a sandwich, please.</p></div>
          <p className="auth-example-context">Một câu đơn giản. Một tình huống bạn có thể dùng ngay.</p>
        </figure>
        <p className="auth-learning-summary">Chọn tình huống. Thử trò chuyện. Nhận góp ý và lưu để ôn.</p>
      </section> : null}
    </main>
    <footer className="public-footer">English Conversation AI<span>Không gian thực hành tiếng Anh của bạn</span></footer>
  </div>
}
