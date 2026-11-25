import { useId, useState, type ReactNode } from 'react'
import { Icon } from '../../../shared/ui/Icon'
import type { useSpeech } from '../../../shared/hooks/useSpeech'

export function AssistantMessage({ id, content, role, index, listenFirst, capturing, speech, children }: {
  id: string; content: string; role: string; index: number; listenFirst: boolean; capturing: boolean
  speech: ReturnType<typeof useSpeech>
  children?: ReactNode
}) {
  const captionId = useId()
  const [showCaption, setShowCaption] = useState(false)
  const [heard, setHeard] = useState(false)
  const playing = speech.playing === id
  const playLabel = playing ? 'Dừng phát' : heard && listenFirst ? 'Nghe lại' : 'Nghe câu AI'

  function play(rate?: number) {
    if (speech.play(id, content, rate)) setHeard(true)
  }

  return <li className={`practice-assistant${listenFirst ? ' practice-listen-card' : ''}`}>
    <div className="practice-message-heading"><strong className="practice-speaker">{role}</strong>{listenFirst ? <span>Câu {index}</span> : null}</div>{listenFirst ? <p className="practice-listen-instruction">Nghe câu AI, rồi thử trả lời theo vai của bạn.</p> : null}
    {!listenFirst ? <p lang="en">{content}</p> : null}
    {speech.supported || listenFirst ? <div className="actions voice-playback">
      {speech.supported ? <button className={listenFirst ? 'button-primary' : undefined} type="button" disabled={capturing}
        onClick={() => playing ? speech.stop() : play()}><Icon name={playing ? 'close' : 'volume'} size={20} />{playLabel}</button> : null}
      {speech.supported && listenFirst ? <button type="button" disabled={capturing}
        onClick={() => play(0.75)}>Nghe chậm</button> : null}
      {listenFirst ? <button type="button" aria-expanded={showCaption} aria-controls={captionId}
        onClick={() => setShowCaption(open => !open)}>{showCaption ? 'Ẩn phụ đề' : 'Xem phụ đề'}</button> : null}
    </div> : null}
    {listenFirst ? <div className="practice-caption" id={captionId} hidden={!showCaption}>
      {showCaption ? <p lang="en">{content}</p> : null}
    </div> : null}
    {children}
  </li>
}
