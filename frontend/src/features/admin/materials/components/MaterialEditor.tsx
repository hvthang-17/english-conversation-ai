import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { mutateJson } from '../../../../shared/api/client'
import { reportError } from '../../../../shared/api/errors'
import { useSpeech, type SpeechClip } from '../../../../shared/hooks/useSpeech'
import type { SessionGuard } from '../../../../shared/types/api'
import { Icon } from '../../../../shared/ui/Icon'
import { voices, type Field, type Kind, type Editor } from '../model/material-definitions'

export function MaterialEditor({ fields, kind, editor, base, saved, close, onSessionExpired }: SessionGuard & {
  fields: Field[]; kind: Kind; editor: Editor; base: string; saved: () => void; close: () => void
}) {
  const fieldId = useId()
  const heading = useRef<HTMLHeadingElement>(null)
  useEffect(() => { heading.current?.focus() }, [])
  const [values, setValues] = useState(() => Object.fromEntries(fields.map(field => {
    const value = editor.source?.[field.name]
    return [field.name, value != null ? field.example ? JSON.stringify(value, null, 2) : String(value)
      : field.options?.[0] ?? (field.name === 'sort_order' ? kind === 'TOPICS' ? '0' : '1' : '')]
  })))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const speech = useSpeech()
  const Heading = kind === 'TOPICS' ? 'h2' : 'h3'
  const editorFields = kind === 'TOPICS' ? ['name', 'code', 'sort_order', 'description'].flatMap(name => fields.filter(field => field.name === name)) : fields
  const editingPublishedTopic = kind === 'TOPICS' && editor.source && editor.source.status !== 'DRAFT'

  function readJson(field: Field) {
    try {
      const parsed: unknown = JSON.parse(values[field.name] ?? '')
      if (Array.isArray(parsed)) return parsed
    } catch { /* Report the field label below. */ }
    throw new Error(field.label + ' cần là mảng JSON hợp lệ.')
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) return
    speech.stop()
    setBusy(true)
    setError('')
    try {
      const payload = Object.fromEntries(fields.map(field => {
        const value = values[field.name]?.trim()
        return [field.name, !value ? null : field.example ? readJson(field) : value]
      }))
      const id = editor.copy ? undefined : editor.source?.id
      await mutateJson(id ? base + '/' + id : base, payload, id ? 'PUT' : 'POST')
      saved()
    } catch (failure) { setError(reportError(failure, onSessionExpired)) }
    finally { setBusy(false) }
  }

  function preview() {
    setError('')
    try {
      const segmented = kind === 'DIALOGUES' || kind === 'LISTENING' && values.script_segments_json?.trim()
      if (segmented) {
        const field = fields.find(item => item.name === (kind === 'DIALOGUES' ? 'lines_json' : 'script_segments_json'))
        if (!field) return
        const clips: SpeechClip[] = readJson(field).map((line: unknown, index) => {
          const entry = line as { text_en?: unknown; voice_key?: unknown } | null
          const content = entry?.text_en
          if (typeof content !== 'string' || !content.trim()) throw new Error('Thiếu lời tiếng Anh ở lượt ' + (index + 1) + '.')
          if (entry?.voice_key != null && !voices.includes(String(entry.voice_key))) throw new Error('Mã giọng không hợp lệ.')
          return { id: String(index + 1), content, voiceKey: entry?.voice_key as string | undefined }
        })
        if (!clips.length) throw new Error('Nhập ít nhất một lượt nói để nghe thử.')
        speech.playSequence(clips)
      } else {
        const content = values.expression || values.transcript_en || values.reference_text
        if (!content?.trim()) throw new Error('Nhập văn bản tiếng Anh để nghe thử.')
        speech.playSequence([{ id: 'preview', content, voiceKey: values.tts_voice_key }])
      }
    } catch (failure) { setError(reportError(failure, onSessionExpired)) }
  }

  return <form className={`admin-material-editor${kind === 'TOPICS' ? ' admin-topic-editor' : ''}`} aria-labelledby={fieldId + 'title'} onSubmit={save}>
    {kind === 'TOPICS' && <button type="button" className="button-quiet admin-topic-back" disabled={busy} onClick={close}><Icon name="back" />Về danh sách chủ đề</button>}
    <Heading ref={heading} id={fieldId + 'title'} tabIndex={-1}>{kind === 'TOPICS' ? editor.source ? 'Sửa chủ đề' : 'Thêm chủ đề' : editor.source && !editor.copy ? 'Sửa bản nháp #' + editor.source.id : 'Tạo bản nháp'}</Heading>
    {kind === 'TOPICS' && <p className="hint">{editor.source ? 'Cập nhật thông tin hiển thị. Trạng thái chủ đề được giữ nguyên khi lưu.' : 'Chủ đề mới được lưu thành bản nháp. Bạn có thể rà soát rồi công bố từ danh sách.'}</p>}
    {kind === 'WORDS' && <p>Cần IPA cho từ đơn và cặp ví dụ Anh–Việt trước khi công bố.</p>}
    {kind === 'LISTENING' && <p>Nghe thử bằng giọng cục bộ. Tệp MP3 chính thức được tạo bằng Azure khi công bố.</p>}
    <fieldset disabled={busy}>
      {editorFields.map(field => <div key={field.name} className={'admin-material-field-' + field.name}>
        <div className="form-field"><label htmlFor={fieldId + field.name}>{field.label}</label>
          {field.options ? <select id={fieldId + field.name} value={values[field.name]} onChange={event => setValues({ ...values, [field.name]: event.target.value })}>
            {field.options.map(option => <option key={option}>{option}</option>)}
          </select> : field.multiline || field.example
            ? <textarea id={fieldId + field.name} rows={field.example ? 7 : 3} required={!field.optional} value={values[field.name]}
              onChange={event => setValues({ ...values, [field.name]: event.target.value })} />
            : <input id={fieldId + field.name} required={!field.optional} value={values[field.name]}
              readOnly={Boolean(editingPublishedTopic && field.name === 'code')}
              aria-describedby={kind === 'TOPICS' ? field.name === 'code' ? fieldId + 'code-help' : field.name === 'sort_order' ? fieldId + 'order-help' : undefined : undefined}
              inputMode={field.name.endsWith('_id') || field.name === 'sort_order' ? 'numeric' : undefined}
              onChange={event => setValues({ ...values, [field.name]: event.target.value })} />}
          {kind === 'TOPICS' && field.name === 'code' && <small id={fieldId + 'code-help'}>{editingPublishedTopic ? 'Mã đã cố định sau khi công bố.' : 'Dùng chữ hoa, chữ số và dấu gạch dưới. Ví dụ: DAILY_LIFE.'}</small>}
          {kind === 'TOPICS' && field.name === 'sort_order' && <small id={fieldId + 'order-help'}>Số nhỏ hơn được hiển thị trước (0–9999).</small>}
          {kind === 'TOPICS' && field.name === 'description' && <small>Không bắt buộc. Mô tả ngắn bối cảnh giao tiếp của chủ đề.</small>}
        </div>
        {field.example && !values[field.name] && <button type="button"
          onClick={() => setValues({ ...values, [field.name]: JSON.stringify(field.example, null, 2) })}>Chèn mẫu {field.label}</button>}
      </div>)}
    </fieldset>
    {error && <p role="alert">{error}</p>}
    {speech.speechError && <p role="status">{speech.speechError}</p>}
    <div className="actions">
      <button className="primary" disabled={busy}>{busy ? 'Đang lưu…' : editingPublishedTopic ? 'Lưu chủ đề' : 'Lưu bản nháp'}</button>
      <button type="button" disabled={busy} onClick={close}>{kind === 'TOPICS' ? 'Hủy' : 'Đóng'}</button>
      {kind !== 'TOPICS' && kind !== 'LESSONS' && <button type="button" disabled={busy}
        onClick={speech.playing ? speech.stop : preview}>{speech.playing ? 'Dừng nghe thử' : 'Nghe thử'}</button>}
    </div>
  </form>
}
