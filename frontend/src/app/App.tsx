import { AuthApp } from '../features/auth/AuthApp'
export function App() {
  return <main className="shell">
    <header className="brand"><span className="brand-mark" aria-hidden="true">E</span>English Conversation AI</header>
    <section className="intro" aria-labelledby="page-title"><h1 id="page-title">Mỗi cuộc trò chuyện,<br />một bước tự tin hơn.</h1><p>Không gian luyện giao tiếp tiếng Anh theo tình huống, dành cho người học A2–B1.</p></section>
    <AuthApp />
    <footer>English Conversation AI · Học qua từng cuộc trò chuyện</footer>
  </main>
}
