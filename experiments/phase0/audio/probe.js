'use strict'

const candidates = ['audio/webm;codecs=opus', 'audio/webm', 'audio/ogg;codecs=opus', 'audio/mp4']
const report = {
  recorded_at: new Date().toISOString(),
  browser: navigator.userAgent,
  secure_context: window.isSecureContext,
  media_recorder: typeof MediaRecorder !== 'undefined',
  get_user_media: Boolean(navigator.mediaDevices?.getUserMedia),
  supported_types: candidates.filter((type) => typeof MediaRecorder !== 'undefined' && MediaRecorder.isTypeSupported(type)),
  synthetic: { status: 'NOT_RUN', source: 'SYNTHETIC_TONE_NOT_MICROPHONE' },
  microphone: { status: 'NOT_RUN', source: 'PHYSICAL_MICROPHONE', user_heard_clear_speech: false },
  uploaded: false,
  tts: { local_english_voice_count: 0, attempts: [] },
}
const ui = Object.fromEntries(['synthetic', 'record', 'stop', 'clear', 'status', 'playback', 'heard', 'report'].map((id) => [id, document.getElementById(id)]))
let active = null
let audioUrl = null
let generation = 0

function render() {
  report.recorded_at = new Date().toISOString()
  ui.report.textContent = JSON.stringify(report, null, 2)
}

async function encodeProviderWave(decoded) {
  const rate = 16000
  const offline = new OfflineAudioContext(1, Math.ceil(decoded.duration * rate), rate)
  const source = offline.createBufferSource()
  source.buffer = decoded
  source.connect(offline.destination)
  source.start()
  const samples = (await offline.startRendering()).getChannelData(0)
  const view = new DataView(new ArrayBuffer(44 + samples.length * 2))
  const tag = (offset, value) => [...value].forEach((character, index) => view.setUint8(offset + index, character.charCodeAt(0)))
  tag(0, 'RIFF'); view.setUint32(4, view.byteLength - 8, true)
  tag(8, 'WAVE'); tag(12, 'fmt '); view.setUint32(16, 16, true)
  view.setUint16(20, 1, true); view.setUint16(22, 1, true)
  view.setUint32(24, rate, true); view.setUint32(28, rate * 2, true)
  view.setUint16(32, 2, true); view.setUint16(34, 16, true)
  tag(36, 'data'); view.setUint32(40, samples.length * 2, true)
  samples.forEach((sample, index) => {
    const bounded = Math.min(1, Math.max(-1, sample))
    view.setInt16(44 + index * 2, Math.round(bounded * (bounded < 0 ? 32768 : 32767)), true)
  })
  return view.buffer
}

async function capture(stream, milliseconds, source, onReady) {
  if (!report.media_recorder || !report.supported_types.length) {
    stream.getTracks().forEach((track) => track.stop())
    throw new Error('MediaRecorder hoặc định dạng thử chưa được hỗ trợ.')
  }
  let recorder
  try { recorder = new MediaRecorder(stream, { mimeType: report.supported_types[0] }) }
  catch (error) { stream.getTracks().forEach((track) => track.stop()); throw error }
  const chunks = []
  const started = performance.now()
  return await new Promise((resolve, reject) => {
    let timer
    const cleanup = () => {
      clearTimeout(timer)
      stream.getTracks().forEach((track) => track.stop())
    }
    recorder.addEventListener('dataavailable', (event) => { if (event.data.size) chunks.push(event.data) })
    recorder.addEventListener('error', () => { cleanup(); reject(new Error('Trình duyệt báo lỗi ghi âm.')) })
    recorder.addEventListener('stop', async () => {
      cleanup()
      const blob = new Blob(chunks, { type: recorder.mimeType })
      let context
      try {
        if (!blob.size) throw new Error('Bản ghi rỗng.')
        context = new AudioContext()
        const decoded = await context.decodeAudioData(await blob.arrayBuffer())
        let energy = 0
        const channel = decoded.getChannelData(0)
        for (const sample of channel) energy += sample * sample
        const wave = await encodeProviderWave(decoded)
        const verifiedWave = await context.decodeAudioData(wave.slice(0))
        resolve({ blob, metadata: { status: 'CAPTURED_AND_DECODED', source, actual_mime: recorder.mimeType,
          bytes: blob.size, elapsed_ms: Math.round(performance.now() - started), duration_seconds: decoded.duration,
          sample_rate: decoded.sampleRate, rms: Math.sqrt(energy / channel.length),
          provider_wav: { bytes: wave.byteLength, format: 'PCM16_MONO_16000', decoded_seconds: verifiedWave.duration } } })
      } catch (error) { reject(error) }
      finally { if (context) await context.close() }
    })
    try {
      recorder.start(100)
      timer = setTimeout(() => { if (recorder.state !== 'inactive') recorder.stop() }, milliseconds)
      onReady?.(recorder)
    } catch (error) { cleanup(); reject(error) }
  })
}

async function runSynthetic() {
  if (active) throw new Error('Dừng micro trước khi chạy kiểm tra tổng hợp.')
  const context = new AudioContext()
  const destination = context.createMediaStreamDestination()
  const oscillator = context.createOscillator()
  const mute = context.createGain()
  mute.gain.value = 0
  oscillator.frequency.value = 440
  oscillator.connect(destination)
  oscillator.connect(mute)
  mute.connect(context.destination)
  try {
    await context.resume()
    oscillator.start()
    const result = await capture(destination.stream, 1800, 'SYNTHETIC_TONE_NOT_MICROPHONE')
    report.synthetic = result.metadata
    render()
    return result.metadata
  } catch (error) {
    report.synthetic = { status: 'FAILED', source: 'SYNTHETIC_TONE_NOT_MICROPHONE', error: error.name }
    render()
    throw error
  } finally { oscillator.stop(); await context.close() }
}

ui.synthetic.addEventListener('click', async () => {
  ui.synthetic.disabled = true
  try { await runSynthetic() }
  catch { ui.status.textContent = 'Kiểm tra âm tổng hợp thất bại; xem metadata. Có thể nhập văn bản.' }
  finally { ui.synthetic.disabled = false }
})

ui.record.addEventListener('click', async () => {
  if (active) return
  const current = ++generation
  ui.record.disabled = true
  ui.heard.checked = false
  ui.status.textContent = 'Đang chờ bạn cấp quyền micro…'
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    if (current !== generation) { stream.getTracks().forEach((track) => track.stop()); return }
    ui.status.textContent = 'Đang ghi âm; bấm Dừng ghi khi đọc xong. Không có upload.'
    const result = await capture(stream, 60000, 'PHYSICAL_MICROPHONE', (recorder) => { active = recorder; ui.stop.disabled = false })
    if (current !== generation) return
    if (audioUrl) URL.revokeObjectURL(audioUrl)
    audioUrl = URL.createObjectURL(result.blob)
    ui.playback.src = audioUrl
    report.microphone = { ...result.metadata, user_heard_clear_speech: false }
    ui.status.textContent = 'Đã ghi và giải mã. Bạn hãy nghe lại để xác nhận có lời nói rõ.'
  } catch (error) {
    if (current !== generation) return
    report.microphone = { status: 'FAILED', source: 'PHYSICAL_MICROPHONE', error: error.name, user_heard_clear_speech: false }
    ui.status.textContent = 'Không thể ghi âm. Kiểm tra quyền micro hoặc dùng văn bản.'
  } finally {
    if (current === generation) { active = null; ui.record.disabled = false; ui.stop.disabled = true; render() }
  }
})
ui.stop.addEventListener('click', () => { if (active?.state === 'recording') active.stop() })
ui.heard.addEventListener('change', () => {
  report.microphone.user_heard_clear_speech = report.microphone.status === 'CAPTURED_AND_DECODED' && ui.heard.checked
  render()
})
function clearRecording() {
  generation++
  if (active?.state === 'recording') active.stop()
  active = null
  ui.playback.pause()
  ui.playback.removeAttribute('src')
  ui.playback.load()
  if (audioUrl) URL.revokeObjectURL(audioUrl)
  audioUrl = null
  ui.heard.checked = false
  ui.record.disabled = false
  ui.stop.disabled = true
  report.microphone = { status: 'CLEARED', source: 'PHYSICAL_MICROPHONE', user_heard_clear_speech: false }
  ui.status.textContent = 'Đã bỏ bản ghi khỏi tab; không có upload.'
  render()
}
ui.clear.addEventListener('click', clearRecording)
const speech = window.speechSynthesis
const voiceSelect = document.getElementById('voice')
const rateSelect = document.getElementById('rate')
const speakButton = document.getElementById('speak')
const ttsStatus = document.getElementById('tts-status')
let voices = []
let currentSpeech = null

function refreshVoices() {
  const previous = voiceSelect.value
  voices = speech?.getVoices().filter(voice => voice.localService && /^en(?:-|$)/i.test(voice.lang)) ?? []
  voiceSelect.replaceChildren(...voices.map(voice => new Option(voice.name, voice.voiceURI)))
  if (voices.some(voice => voice.voiceURI === previous)) voiceSelect.value = previous
  speakButton.disabled = voices.length === 0
  report.tts.local_english_voice_count = voices.length
  if (!currentSpeech) ttsStatus.textContent = voices.length ? 'Chọn giọng để thử.' : 'Chưa có giọng tiếng Anh local.'
  render()
}

function finishSpeech(status) {
  if (!currentSpeech) return
  report.tts.attempts = [...report.tts.attempts, { ...currentSpeech.metadata, status }].slice(-10)
  currentSpeech = null
  ttsStatus.textContent = { PLAYED: 'Đã phát hết câu.', FAILED: 'Không phát được câu.', CANCELLED: 'Đã dừng đọc.' }[status]
  render()
}

function cancelSpeech() {
  finishSpeech('CANCELLED')
  speech?.cancel()
}

speakButton.addEventListener('click', () => {
  const voice = voices.find(candidate => candidate.voiceURI === voiceSelect.value)
  if (!voice) return
  cancelSpeech()
  const sample = new SpeechSynthesisUtterance('I would like a glass of water, please.')
  const started = performance.now()
  sample.voice = voice
  sample.lang = voice.lang
  sample.rate = Number(rateSelect.value)
  const metadata = { locale: voice.lang, voice_index: voices.indexOf(voice), rate: sample.rate, start_latency_ms: null }
  currentSpeech = { sample, metadata }
  sample.onstart = () => {
    if (currentSpeech?.sample !== sample) return
    metadata.start_latency_ms = Math.round(performance.now() - started)
    ttsStatus.textContent = 'Đang phát câu mẫu…'
  }
  sample.onend = () => { if (currentSpeech?.sample === sample) finishSpeech('PLAYED') }
  sample.onerror = () => { if (currentSpeech?.sample === sample) finishSpeech('FAILED') }
  try { speech.speak(sample) }
  catch { finishSpeech('FAILED') }
})
document.getElementById('cancel-speech').addEventListener('click', cancelSpeech)
speech?.addEventListener('voiceschanged', refreshVoices)
window.addEventListener('pagehide', () => { clearRecording(); cancelSpeech() })
window.phase0Probe = { runSynthetic, report }
refreshVoices()
