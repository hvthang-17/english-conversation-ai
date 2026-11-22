import { useId, useState } from 'react'
import type { useSpeech } from '../voice/useSpeech'

export function AssistantMessage({ id, content, role, index, listenFirst, capturing, speech }: {
  id: string; content: string; role: string; index: number; listenFirst: boolean; capturing: boolean
  speech: ReturnType<typeof useSpeech>
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
    <strong>{role}{listenFirst ? ` · Câu ${index}` : ''}</strong>
    {!listenFirst ? <p lang="en">{content}</p> : null}
    {speech.supported || listenFirst ? <div className="actions voice-playback">
      {speech.supported ? <button type="button" disabled={capturing}
        onClick={() => playing ? speech.stop() : play()}>{playLabel}</button> : null}
      {speech.supported && listenFirst ? <button type="button" disabled={capturing}
        onClick={() => play(0.75)}>Nghe chậm</button> : null}
      {listenFirst ? <button type="button" aria-expanded={showCaption} aria-controls={captionId}
        onClick={() => setShowCaption(open => !open)}>{showCaption ? 'Ẩn phụ đề' : 'Xem phụ đề'}</button> : null}
    </div> : null}
    {listenFirst ? <div id={captionId} hidden={!showCaption}>
      {showCaption ? <p lang="en">{content}</p> : null}
    </div> : null}
  </li>
}
