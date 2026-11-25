export const maxRecordingSeconds = 60
const sampleRate = 16_000

export function recordingType() {
  if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia ||
      typeof MediaRecorder === 'undefined' || typeof OfflineAudioContext === 'undefined') return undefined
  return ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4'].find(type => MediaRecorder.isTypeSupported(type))
}

export async function toWave(recording: Blob) {
  const context = new OfflineAudioContext(1, sampleRate, sampleRate)
  // decodeAudioData resamples to the context sample rate.
  const decoded = await context.decodeAudioData(await recording.arrayBuffer())
  const frames = Math.min(decoded.length, sampleRate * maxRecordingSeconds)
  if (!frames) throw new Error('Bản ghi trống. Hãy ghi lại.')
  const channels = Array.from({ length: decoded.numberOfChannels }, (_, i) => decoded.getChannelData(i))
  const dataSize = frames * 2
  const bytes = new ArrayBuffer(44 + dataSize)
  const wav = new DataView(bytes)
  function tag(at: number, value: string) {
    for (let i = 0; i < value.length; i++) wav.setUint8(at + i, value.charCodeAt(i))
  }
  tag(0, 'RIFF'); wav.setUint32(4, 36 + dataSize, true); tag(8, 'WAVE')
  tag(12, 'fmt '); wav.setUint32(16, 16, true); wav.setUint16(20, 1, true)
  wav.setUint16(22, 1, true); wav.setUint32(24, sampleRate, true); wav.setUint32(28, sampleRate * 2, true)
  wav.setUint16(32, 2, true); wav.setUint16(34, 16, true); tag(36, 'data')
  wav.setUint32(40, dataSize, true)
  let hasSound = false
  for (let i = 0; i < frames; i++) {
    let sum = 0
    for (const channel of channels) sum += channel[i] ?? 0
    const sample = Math.max(-1, Math.min(1, sum / channels.length))
    const pcm = Math.round(sample < 0 ? sample * 32768 : sample * 32767)
    if (pcm !== 0) hasSound = true
    wav.setInt16(44 + i * 2, pcm, true)
  }
  if (!hasSound) throw new Error('Không nghe thấy âm thanh. Hãy kiểm tra micro rồi ghi lại.')
  return new Blob([bytes], { type: 'audio/wav' })
}
