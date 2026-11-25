import { useEffect, useRef, useState } from 'react'
import { maxRecordingSeconds, recordingType, toWave } from '../../../shared/lib/audio/recording'

type Phase = 'idle' | 'permission' | 'recording' | 'converting'
export type Recording = { key: string; wav: Blob; url: string; seconds: number }

export function usePronunciationRecorder() {
  const [phase, setPhase] = useState<Phase>('idle')
  const [seconds, setSeconds] = useState(0)
  const [recording, setRecording] = useState<Recording | null>(null)
  const [error, setError] = useState('')
  const lifecycle = useRef({
    generation: 0, recorder: null as MediaRecorder | null, stream: null as MediaStream | null,
    clock: 0, timeout: 0, url: '',
  })
  const state = lifecycle.current
  useEffect(() => () => reset(state), [state])

  function discard() {
    reset(state)
    setRecording(null)
    setSeconds(0)
    setError('')
    setPhase('idle')
  }

  async function start() {
    discard()
    const type = recordingType()
    if (!type) {
      setError('Trình duyệt này chưa hỗ trợ thu và chuyển đổi bản ghi.')
      return
    }
    const generation = state.generation
    setPhase('permission')
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      if (generation !== state.generation) {
        stream.getTracks().forEach(track => track.stop())
        return
      }
      state.stream = stream
      const recorder = new MediaRecorder(stream, { mimeType: type })
      state.recorder = recorder
      const chunks: BlobPart[] = []
      recorder.ondataavailable = event => { if (event.data.size) chunks.push(event.data) }
      recorder.onerror = () => {
        if (generation !== state.generation) return
        discard()
        setError('Micro ngừng ghi. Hãy kiểm tra micro rồi thử lại.')
      }
      recorder.onstop = async () => {
        releaseMicrophone(state)
        if (generation !== state.generation) return
        state.recorder = null
        setPhase('converting')
        try {
          const wav = await toWave(new Blob(chunks, { type: recorder.mimeType || type }))
          if (generation !== state.generation) return
          const key = crypto.randomUUID()
          const url = URL.createObjectURL(wav)
          state.url = url
          setRecording({ key, wav, url, seconds: (wav.size - 44) / 32_000 })
        } catch {
          if (generation === state.generation)
            setError('Không đọc được bản ghi hoặc bản ghi trống. Hãy thử lại.')
        } finally {
          if (generation === state.generation) setPhase('idle')
        }
      }
      recorder.start()
      setPhase('recording')
      const started = performance.now()
      state.clock = window.setInterval(() =>
        setSeconds(Math.min(maxRecordingSeconds, Math.floor((performance.now() - started) / 1000))), 250)
      state.timeout = window.setTimeout(stop, maxRecordingSeconds * 1000)
    } catch (failure) {
      if (generation !== state.generation) return
      discard()
      setError(failure instanceof DOMException && failure.name === 'NotAllowedError'
        ? 'Quyền truy cập micro đã bị từ chối. Hãy bật quyền rồi thử lại.'
        : 'Không mở được micro. Hãy kiểm tra kết nối và quyền truy cập.')
    }
  }

  function stop() {
    if (state.recorder?.state === 'recording') state.recorder.stop()
  }

  return { phase, seconds, recording, error, start, stop, discard, supported: Boolean(recordingType()) }
}

type RecorderState = {
  generation: number; recorder: MediaRecorder | null; stream: MediaStream | null;
  clock: number; timeout: number; url: string;
}

function releaseMicrophone(state: RecorderState) {
  window.clearInterval(state.clock)
  window.clearTimeout(state.timeout)
  state.stream?.getTracks().forEach(track => track.stop())
  state.stream = null
}

function reset(state: RecorderState) {
  state.generation++
  if (state.recorder) {
    state.recorder.onstop = null
    state.recorder.ondataavailable = null
    state.recorder.onerror = null
    if (state.recorder.state !== 'inactive') state.recorder.stop()
    state.recorder = null
  }
  releaseMicrophone(state)
  if (state.url) {
    URL.revokeObjectURL(state.url)
    state.url = ''
  }
}
