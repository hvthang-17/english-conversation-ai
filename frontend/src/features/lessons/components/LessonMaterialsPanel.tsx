import '../styles/lesson-materials.css'
import { Icon } from '../../../shared/ui/Icon'
import '../styles/lesson-catalog.css'
import { useState } from 'react'
import { useSpeech } from '../../../shared/hooks/useSpeech'
import { reportError } from '../../../shared/api/errors'
import { useApiQuery } from '../../../shared/hooks/useApiQuery'
import { type SessionGuard } from '../../../shared/types/api'
import { saveLanguageItem } from '../../learning/api/notebook-api'

type LanguageItem = {
  id: string; kind: 'WORD' | 'PHRASE'; expression: string; ipa: string | null
  meaningVi: string; exampleEn: string | null; exampleVi: string | null; savedCardId: string | null
}
type Dialogue = {
  id: string; title: string
  lines: { line_no: number; speaker_label: string; text_en: string; text_vi: string; voice_key: string | null }[]
}
type Materials = { items: LanguageItem[]; dialogues: Dialogue[] }
type Speech = ReturnType<typeof useSpeech>

export function LessonMaterialsPanel({ id, onSessionExpired }: SessionGuard & { id: string }) {
  const { data, error, reload } = useApiQuery<Materials>(`lessons/${id}/materials`, onSessionExpired)
  const speech = useSpeech()
  const [rate, setRate] = useState(1)
  const [loop, setLoop] = useState(false)
  const [showEnglish, setShowEnglish] = useState(true)
  const [showVietnamese, setShowVietnamese] = useState(false)

  return <section className="lesson-materials" aria-labelledby="lesson-materials-title">
    <header className="lesson-materials-heading"><h2 id="lesson-materials-title">Chuẩn bị cho cuộc trò chuyện</h2><p>Từ vựng, mẫu câu và cách dùng trong hội thoại. Lưu điều bạn muốn luyện lại vào sổ tay.</p></header>
    {error ? <div role="alert"><p>{error}</p><button type="button" onClick={reload}>Thử tải lại học liệu</button></div> : null}
    {!data && !error ? <p role="status">Đang tải học liệu…</p> : null}
    {data && !data.items.length && !data.dialogues.length ? <p>Chưa có học liệu cho bài này.</p> : null}
    {data && (data.items.length > 0 || data.dialogues.length > 0) ? <>
      <div className="material-controls">
        <label>Tốc độ <select value={rate} onChange={event => {
          const next = Number(event.target.value)
          setRate(next)
          speech.updateRate(next)
        }}><option value={1}>1×</option><option value={0.75}>0,75×</option></select></label>
        <button type="button" disabled={!speech.playing} onClick={speech.stop}>Dừng phát</button>
      </div>
      {speech.speechError ? <p role="status">{speech.speechError}</p> : null}
      {data.items.length > 0 ? <div className="material-groups">
        {(['WORD', 'PHRASE'] as const).map(kind => {
          const items = data.items.filter(item => item.kind === kind)
          return items.length ? <section key={kind}><h3>{kind === 'WORD' ? 'Từ vựng' : 'Mẫu câu'}</h3>
            <ul className="material-list">{items.map(item => <MaterialCard key={item.id} item={item}
              play={() => speech.play(`item-${item.id}`, item.expression, rate)} playing={speech.playing === `item-${item.id}`}
              onSessionExpired={onSessionExpired} />)}</ul>
          </section> : null
        })}
      </div> : null}
      {data.dialogues.length ? <section className="sample-dialogues">
        <h3>Hội thoại mẫu</h3>
        <div className="material-controls">
          <label><input type="checkbox" checked={showEnglish} onChange={event => setShowEnglish(event.target.checked)} /> Lời tiếng Anh</label>
          <label><input type="checkbox" checked={showVietnamese} onChange={event => setShowVietnamese(event.target.checked)} /> Bản dịch tiếng Việt</label>
          <label><input type="checkbox" checked={loop} onChange={event => {
            setLoop(event.target.checked)
            speech.updateLoop(event.target.checked)
          }} /> Nghe lặp</label>
        </div>
        {data.dialogues.map(dialogue => <DialoguePlayer key={dialogue.id} dialogue={dialogue} speech={speech}
          rate={rate} loop={loop} showEnglish={showEnglish} showVietnamese={showVietnamese} />)}
      </section> : null}
    </> : null}
  </section>
}

function MaterialCard({ item, playing, play, onSessionExpired }: SessionGuard & {
  item: LanguageItem; playing: boolean; play: () => void
}) {
  const [saved, setSaved] = useState(Boolean(item.savedCardId))
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')

  async function save() {
    setPending(true)
    setError('')
    try {
      await saveLanguageItem(item.id)
      setSaved(true)
    } catch (failure) {
      setError(reportError(failure, onSessionExpired))
    } finally {
      setPending(false)
    }
  }

  return <li className="lesson-material-card" aria-current={playing || undefined}>
    <h4 lang="en">{item.expression}</h4>
    {item.ipa ? <p className="hint">{item.ipa}</p> : null}
    <p>{item.meaningVi}</p>
    {item.exampleEn ? <p className="material-example" lang="en">{item.exampleEn}</p> : null}{item.exampleVi ? <p className="hint">{item.exampleVi}</p> : null}
    <div className="material-controls">
      <button type="button" onClick={play} aria-label={`Nghe ${item.expression}`}><Icon name="volume" size={20} />{playing ? 'Nghe lại' : 'Nghe'}</button>
      <span role="status">{saved ? <a href="#/notebook">Đã lưu vào sổ tay</a> : null}</span>
      {!saved ? <button type="button" disabled={pending} onClick={() => void save()}
        aria-label={`Lưu vào sổ tay: ${item.expression}`}><Icon name="plus" size={20} />{pending ? 'Đang lưu…' : 'Lưu vào sổ tay'}</button> : null}
    </div>
    {error ? <p role="alert">{error}</p> : null}
  </li>
}

function DialoguePlayer({ dialogue, speech, rate, loop, showEnglish, showVietnamese }: {
  dialogue: Dialogue; speech: Speech; rate: number; loop: boolean; showEnglish: boolean; showVietnamese: boolean
}) {
  const clips = dialogue.lines.map(line => ({ id: `line-${dialogue.id}-${line.line_no}`,
    content: line.text_en, voiceKey: line.voice_key }))
  const current = clips.findIndex(clip => clip.id === speech.playing)

  return <article className="lesson-dialogue" aria-labelledby={`dialogue-${dialogue.id}`}>
    <h4 id={`dialogue-${dialogue.id}`}>{dialogue.title}</h4>
    <div className="material-controls"><button type="button" onClick={() => speech.playSequence(clips, rate, loop)}>
      <Icon name="volume" size={20} />Phát cả đoạn</button><span role="status">{current >= 0 ? `Đang phát câu ${current + 1}` : ''}</span></div>
    <ol className="dialogue-lines">
      {dialogue.lines.map((line, index) => <li key={line.line_no} aria-current={current === index || undefined}>
        <div><strong>{line.speaker_label}</strong>
          {showEnglish ? <p lang="en">{line.text_en}</p> : null}
          {showVietnamese ? <p className="hint">{line.text_vi}</p> : null}
        </div>
        <button type="button" onClick={() => {
          const clip = clips[index]
          if (clip) speech.playSequence([clip], rate, loop)
        }}
          aria-label={`Nghe lại câu ${line.line_no}`}><Icon name="volume" size={20} />Nghe lại</button>
      </li>)}
    </ol>
  </article>
}
