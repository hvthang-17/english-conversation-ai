import { useEffect, useRef, useState } from 'react'

export type SpeechClip = { id: string; content: string; voiceKey?: string | null }
type Playback = { clips: readonly SpeechClip[]; index: number; rate: number; loop: boolean }

export function useSpeech() {
  const synthesis = 'speechSynthesis' in window && typeof SpeechSynthesisUtterance !== 'undefined'
    ? window.speechSynthesis : null
  const current = useRef<SpeechSynthesisUtterance | null>(null)
  const playback = useRef<Playback | null>(null)
  const [playing, setPlaying] = useState<string | null>(null)
  const [speechError, setSpeechError] = useState('')
  useEffect(() => () => {
    playback.current = null
    if (current.current) {
      current.current = null
      synthesis?.cancel()
    }
  }, [synthesis])

  function stop() {
    playback.current = null
    cancelUtterance()
    setPlaying(null)
  }

  function cancelUtterance() {
    if (current.current) {
      current.current = null
      synthesis?.cancel()
    }
  }

  function speakNext(run: Playback): boolean {
    if (!synthesis || playback.current !== run) return false
    const clip = run.clips[run.index]
    if (!clip) return false
    const voices = synthesis.getVoices().filter(item => item.localService && /^en(?:[-_]|$)/i.test(item.lang))
    const preferred = voices.filter(item => /^en[-_]us$/i.test(item.lang))
    const alternatives = preferred.length >= 2 ? preferred : [...preferred, ...voices.filter(item => !preferred.includes(item))]
    const voice = clip.voiceKey === 'en-us-voice-b-v1' ? alternatives[1] ?? alternatives[0] : alternatives[0]
    if (!voice) {
      stop()
      setSpeechError('Giọng tiếng Anh cục bộ chưa sẵn sàng. Bạn có thể đọc văn bản hoặc thử phát lại.')
      return false
    }
    try {
      const line = new SpeechSynthesisUtterance(clip.content)
      line.voice = voice
      line.lang = voice.lang
      line.rate = run.rate
      line.onend = () => {
        if (current.current !== line) return
        current.current = null
        run.index++
        if (run.index === run.clips.length && run.loop) run.index = 0
        if (run.index < run.clips.length) speakNext(run)
        else stop()
      }
      line.onerror = () => {
        if (current.current !== line) return
        stop()
        setSpeechError('Không phát được câu này. Bạn có thể đọc văn bản hoặc thử lại.')
      }
      current.current = line
      setPlaying(clip.id)
      synthesis.speak(line)
      return true
    } catch {
      stop()
      setSpeechError('Không phát được câu này. Bạn có thể đọc văn bản hoặc thử lại.')
      return false
    }
  }
  function playSequence(clips: readonly SpeechClip[], rate = 1, loop = false) {
    stop()
    setSpeechError('')
    if (!synthesis) {
      setSpeechError('Trình duyệt chưa hỗ trợ phát giọng nói. Bạn có thể đọc văn bản.')
      return false
    }
    if (clips.length === 0) return false
    const run = { clips, index: 0, rate, loop }
    playback.current = run
    return speakNext(run)
  }

  function play(id: string, content: string, rate = 1) {
    return playSequence([{ id, content }], rate)
  }

  function updateRate(rate: number) {
    const run = playback.current
    if (!run || run.rate === rate) return
    run.rate = rate
    cancelUtterance()
    speakNext(run)
  }

  function updateLoop(loop: boolean) {
    if (playback.current) playback.current.loop = loop
  }

  return { supported: synthesis !== null, playing, speechError, play, playSequence, updateRate, updateLoop, stop }
}
