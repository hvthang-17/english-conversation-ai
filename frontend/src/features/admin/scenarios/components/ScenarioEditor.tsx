import { useEffect, useRef, useId, useState, type FormEvent, type ReactNode } from 'react'
import { ApiError, mutateJson } from '../../../../shared/api/client'
import { reportError } from '../../../../shared/api/errors'
import { useApiQuery } from '../../../../shared/hooks/useApiQuery'
import { type SessionGuard } from '../../../../shared/types/api'

import { scenarioFields, variantFields, statuses, type Scenario, type ScenarioVariant } from '../model/scenario-fields'

type ScenarioEditorProps = SessionGuard & {
  scenario?: Scenario; path: string; topicActive: boolean; close: () => void; saved: (scenario: Scenario) => void
}
export function ScenarioEditorLoader(props: ScenarioEditorProps) {
  if (!props.scenario) return <ScenarioEditor {...props} variants={[]} />
  return <ExistingScenarioEditor {...props} id={props.scenario.id} />
}

function ExistingScenarioEditor(props: ScenarioEditorProps & { id: string }) {
  const roles = useApiQuery<ScenarioVariant[]>(props.path + '/' + props.id + '/roles', props.onSessionExpired)
  if (roles.data) return <ScenarioEditor {...props} variants={roles.data} />
  return <div>
    {roles.error ? <p role="alert">{roles.error} <button onClick={roles.reload}>Thử lại</button></p>
      : <p role="status">Đang tải các biến thể vai…</p>}
    <button onClick={props.close}>Đóng</button>
  </div>
}

function ScenarioEditor({ scenario, variants, path, topicActive, close, saved, onSessionExpired }:
  ScenarioEditorProps & { variants: ScenarioVariant[] }) {
  const [rows, setRows] = useState(() => variants.map(variant => ({ key: crypto.randomUUID(), variant: variant as ScenarioVariant | undefined })))
  const [status, setStatus] = useState(scenario?.status ?? 'DRAFT')
  const [reviewed, setReviewed] = useState(false)
  return <CatalogForm<Scenario> title={scenario ? 'Sửa tình huống' : 'Thêm tình huống'}
    path={scenario ? `${path}/${scenario.id}` : path} editing={Boolean(scenario)}
    close={close} saved={saved} onSessionExpired={onSessionExpired}
    payload={form => {
      const status = form.get('status')
      const levels = form.getAll('levels')
      if (status === 'ACTIVE' && (!topicActive || !levels.length))
        throw new Error('Để công khai, hãy mở chủ đề và chọn ít nhất một trình độ.')
      if (status === 'ACTIVE' && !reviewed)
        throw new Error('Hãy rà soát chỉ dẫn AI cho vai mặc định và các biến thể trước khi lưu công khai.')
      const roleVariants = rows.map(({ key }) => Object.fromEntries(variantFields.map(([name]) =>
        [name, String(form.get(key + '-' + name) ?? '').trim()])))
      const codes = ['DEFAULT', ...roleVariants.map(variant => variant.code)]
      if (new Set(codes).size !== codes.length)
        throw new Error('Mã biến thể vai phải khác nhau và không dùng DEFAULT.')
      return { ...Object.fromEntries(scenarioFields.map(([name]) => [name, form.get(name)])), status, levels, roleVariants }
    }}>
    <section className="admin-scenario-form-section" aria-labelledby="scenario-general"><h3 id="scenario-general">Thông tin chung</h3>
      <p>Tên và mô tả giúp người học nhận biết tình huống trước khi bắt đầu.</p>
      <div className="admin-scenario-fields">{scenarioFields.filter(([name]) => ['code', 'title', 'description'].includes(name)).map(([name, label, max]) => <CatalogField key={name} name={name} label={label} max={max} value={scenario?.[name]} />)}</div>
    </section>
    <section className="admin-scenario-form-section" aria-labelledby="scenario-default"><h3 id="scenario-default">Vai mặc định</h3>
      <p>Xác định người học nói với ai, trong bối cảnh nào và cần đạt mục tiêu gì.</p>
      <div className="admin-scenario-fields">{scenarioFields.filter(([name]) => !['code', 'title', 'description'].includes(name)).map(([name, label, max]) => <CatalogField key={name} name={name} label={label} max={max} value={scenario?.[name]} />)}</div>
      <p className="hint">Chỉ dẫn AI nêu rõ vai, cách mở lời và mục tiêu giao tiếp. Dùng dữ liệu hư cấu.</p>
    </section>
    <section className="admin-scenario-form-section" aria-label="Biến thể vai">
      <h3>Biến thể vai</h3>
      <p>Mỗi biến thể áp dụng cho các trình độ đã chọn và có bối cảnh, mục tiêu, chỉ dẫn riêng. Các buổi đã mở giữ vai và nội dung tại thời điểm bắt đầu.</p>
      {rows.map(({ key, variant }, index) => <fieldset className="admin-role-variant" key={key}>
        <legend>Biến thể vai {index + 1}</legend>
        <div className="admin-scenario-fields">{variantFields.map(([name, label, max]) =>
          <CatalogField key={name} name={key + '-' + name} label={name === 'code' ? 'Mã biến thể' : label}
            max={name === 'code' ? 32 : max} value={variant?.[name as keyof ScenarioVariant]} codeField={name === 'code'} />)}</div>
        <button type="button" onClick={() => { setReviewed(false); setRows(items => items.filter(row => row.key !== key)) }}>
          Xóa biến thể {index + 1}
        </button>
      </fieldset>)}
      <button type="button" disabled={rows.length >= 12} onClick={() => {
        setReviewed(false)
        setRows(items => [...items, { key: crypto.randomUUID(), variant: undefined }])
      }}>Thêm biến thể vai</button>
    </section>
    <section className="admin-scenario-form-section" aria-labelledby="scenario-publish"><h3 id="scenario-publish">Trạng thái và trình độ</h3>
      <p>Để công khai, chủ đề phải đang mở và tình huống có ít nhất một trình độ A2 hoặc B1.</p>
      <div className="form-field"><label htmlFor="scenario-status">Trạng thái tình huống</label><select id="scenario-status" name="status" value={status} onChange={event => setStatus(event.target.value as Scenario['status'])}>
        {Object.entries(statuses).filter(([value]) => value !== 'DRAFT' || !scenario || scenario.status === 'DRAFT').map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      </select></div>
      <fieldset className="admin-scenario-levels"><legend>Trình độ người học</legend>{['A2', 'B1'].map(level => <label className="admin-choice" key={level}><input type="checkbox" name="levels" value={level} defaultChecked={scenario?.levels.includes(level)} />{level}</label>)}</fieldset>
    {status === 'ACTIVE' && <label className="admin-choice">
      <input type="checkbox" required checked={reviewed} onChange={event => setReviewed(event.target.checked)} />
      Đã rà chỉ dẫn AI theo vai, bối cảnh và mục tiêu.
    </label>}
    </section>
  </CatalogForm>
}

function CatalogField({ name, label, max, value, optional = false, codeField = name === 'code' }: {
  name: string; label: string; max: number; value?: string; optional?: boolean; codeField?: boolean
}) {
  return <label className={`form-field${max > 200 ? ' admin-scenario-field-wide' : ''}`}>{label}
    {max > 200 ? <textarea name={name} maxLength={max} rows={3} required={!optional} defaultValue={value} /> :
      <input name={name} maxLength={max} required={!optional} defaultValue={value}
        pattern={codeField ? `[A-Z][A-Z0-9_]{${name === 'code' ? 1 : 0},${max - 1}}` : undefined}
        title={codeField ? 'Dùng chữ hoa ASCII, chữ số hoặc dấu gạch dưới, bắt đầu bằng chữ.' : undefined} />}
  </label>
}

function CatalogForm<T>({ title, path, editing, payload, children, close, saved, onSessionExpired }:
  SessionGuard & { title: string; path: string; editing: boolean; payload: (form: FormData) => unknown
    children: ReactNode; close: () => void; saved: (data: T) => void }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const heading = useRef<HTMLHeadingElement>(null)
  const titleId = useId()
  useEffect(() => { heading.current?.focus() }, [])
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) return
    setError('')
    const form = new FormData(event.currentTarget)
    try {
      const body = payload(form)
      setBusy(true)
      saved(await mutateJson(path, body, editing ? 'PUT' : 'POST') as T)
    } catch (failure) {
      setError(failure instanceof ApiError && failure.code === 'VALIDATION_ERROR'
        ? 'Kiểm tra trường bắt buộc, độ dài và định dạng dữ liệu.'
        : reportError(failure, onSessionExpired))
    } finally { setBusy(false) }
  }
  return <form className="admin-form admin-scenario-editor" aria-labelledby={titleId} onSubmit={event => void submit(event)}>
    <h2 ref={heading} tabIndex={-1} id={titleId}>{title}</h2>
    <fieldset disabled={busy}>
      {children}
      {error ? <p role="alert">{error}</p> : null}
      <div className="actions">
        <button className="primary">{busy ? 'Đang lưu…' : 'Lưu tình huống'}</button>
        <button type="button" onClick={close}>Hủy</button>
      </div>
    </fieldset>
  </form>
}
