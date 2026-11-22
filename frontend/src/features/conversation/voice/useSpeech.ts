import { useEffect, useRef, useState } from 'react'

export function useSpeech() {
  const synthesis = 'speechSynthesis' in window && typeof SpeechSynthesisUtterance !== 'undefined'
    ? window.speechSynthesis : null
  const current = useRef<SpeechSynthesisUtterance | null>(null)
  const [playing, setPlaying] = useState<string | null>(null)
  const [speechError, setSpeechError] = useState('')
  useEffect(() => () => {
    if (current.current) {
      current.current = null
      synthesis?.cancel()
    }
  }, [synthesis])

  function stop() {
    if (!current.current) return
    current.current = null
    synthesis?.cancel()
    setPlaying(null)
  }

  function play(id: string, content: string, rate = 1) {
    if (!synthesis) return false
    stop()
    setSpeechError('')
    const voice = synthesis.getVoices().find(item => item.localService && /^en(?:[-_]|$)/i.test(item.lang))
    if (!voice) {
      setSpeechError('Thiết bị chưa có giọng tiếng Anh cục bộ.')
      return false
    }
    try {
      const line = new SpeechSynthesisUtterance(content)
      line.voice = voice
      line.lang = voice.lang
      line.rate = rate
      line.onend = () => {
        if (current.current !== line) return
        current.current = null
        setPlaying(null)
      }
      line.onerror = () => {
        if (current.current !== line) return
        stop()
        setSpeechError('Không phát được câu AI.')
      }
      current.current = line
      setPlaying(id)
      synthesis.speak(line)
      return true
    } catch {
      stop()
      setSpeechError('Không phát được câu AI.')
      return false
    }
  }
  return { supported: synthesis !== null, playing, speechError, play, stop }
}
