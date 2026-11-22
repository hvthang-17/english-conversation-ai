import { useState, type FormEvent, type ReactNode } from 'react'
import { ApiError, mutateJson } from '../../shared/api/client'
import { Pagination } from '../learning/Pagination'
import { reportError, useLearningData, type Page, type SessionGuard } from '../learning/learningApi'

type Topic = { id: string; code: string; name: string; description: string | null; active: boolean; sortOrder: number }
type Scenario = { id: string; status: 'DRAFT' | 'ACTIVE' | 'ARCHIVED'; levels: string[] }
  & Record<typeof scenarioFields[number][0], string>
const scenarioFields = [
  ['code', 'Mã tình huống', 80],
  ['title', 'Tên tình huống', 200],
  ['description', 'Mô tả', 2000],
  ['learnerRole', 'Vai người học', 200],
  ['aiRole', 'Vai AI', 200],
  ['communicationGoal', 'Mục tiêu giao tiếp', 1000],
  ['contextDescription', 'Bối cảnh', 2000],
  ['aiInstruction', 'Hướng dẫn nội bộ cho AI', 4000],
] as const
const statuses = { DRAFT: 'Bản nháp', ACTIVE: 'Công khai', ARCHIVED: 'Đã ẩn' } as const

export function CatalogAdmin({ onSessionExpired }: SessionGuard) {
  const [page, setPage] = useState(0)
  const [editor, setEditor] = useState<Topic | 'new' | null>(null)
  const [selected, setSelected] = useState<Topic | null>(null)
  const [notice, setNotice] = useState('')
  const topics = useLearningData<Page<Topic>>(`admin/topics?page=${page}`, onSessionExpired)

  return <section aria-labelledby="catalog-title">
    <h3 id="catalog-title">Chủ đề</h3>
    {!editor ? <button onClick={() => { setEditor('new'); setNotice('') }}>Thêm chủ đề</button> : null}
    {notice ? <p role="status">{notice}</p> : null}
    {editor ? <TopicEditor key={editor === 'new' ? 'topic-editor-new' : `topic-editor-${editor.id}`}
      topic={editor === 'new' ? undefined : editor} onSessionExpired={onSessionExpired}
      close={() => setEditor(null)} saved={next => {
        setEditor(null); setNotice('Đã lưu chủ đề.'); topics.reload()
        if (selected?.id === next.id) setSelected(next)
      }} /> : null}
    {topics.error ? <div><p role="alert">{topics.error}</p><button onClick={topics.reload}>Tải lại chủ đề</button></div> :
      !topics.data ? <p role="status">Đang tải chủ đề…</p> : <>
        {!topics.data.items.length ? <p>Chưa có chủ đề trong trang này.</p> : null}
        <ul className="learning-list">{topics.data.items.map(topic => <li key={topic.id}>
          <strong>{topic.name}</strong>
          <p>{topic.code} · {topic.active ? 'Đang mở' : 'Đã ẩn'}</p>
          <div className="actions">
            <button disabled={editor !== null} onClick={() => { setEditor(topic); setNotice('') }}>
              Sửa chủ đề<span className="sr-only"> {topic.name}</span>
            </button>
            <button aria-pressed={selected?.id === topic.id} onClick={() => setSelected(topic)}>
              Quản lý tình huống<span className="sr-only"> của {topic.name}</span>
            </button>
          </div>
        </li>)}</ul>
        <Pagination page={page} size={topics.data.size} total={topics.data.total} onPage={setPage} />
      </>}
    {selected ? <Scenarios key={`scenarios-${selected.id}`} topic={selected} onSessionExpired={onSessionExpired} /> : null}
  </section>
}

function TopicEditor({ topic, close, saved, onSessionExpired }: SessionGuard & {
  topic?: Topic; close: () => void; saved: (topic: Topic) => void
}) {
  return <CatalogForm<Topic> title={topic ? 'Sửa chủ đề' : 'Thêm chủ đề'}
    path={topic ? `admin/topics/${topic.id}` : 'admin/topics'} editing={Boolean(topic)}
    close={close} saved={saved} onSessionExpired={onSessionExpired}
    payload={form => ({ code: form.get('code'), name: form.get('name'), description: form.get('description'),
      sortOrder: Number(form.get('sortOrder')), active: form.has('active') })}>
    <CatalogField name="code" label="Mã chủ đề" max={50} value={topic?.code} />
    <CatalogField name="name" label="Tên chủ đề" max={150} value={topic?.name} />
    <CatalogField name="description" label="Mô tả chủ đề" max={2000} value={topic?.description ?? ''} optional />
    <label className="form-field">Thứ tự hiển thị
      <input name="sortOrder" type="number" min={0} max={9999} defaultValue={topic?.sortOrder ?? 0} required />
    </label>
    <label className="admin-choice"><input name="active" type="checkbox" defaultChecked={topic?.active ?? true} /> Công khai chủ đề</label>
    <p>Ẩn chủ đề sẽ ẩn các tình huống của chủ đề khỏi danh mục người học.</p>
  </CatalogForm>
}

function Scenarios({ topic, onSessionExpired }: SessionGuard & { topic: Topic }) {
  const [page, setPage] = useState(0)
  const [editor, setEditor] = useState<Scenario | 'new' | null>(null)
  const [notice, setNotice] = useState('')
  const path = `admin/topics/${topic.id}/scenarios`
  const scenarios = useLearningData<Page<Scenario>>(`${path}?page=${page}`, onSessionExpired)

  return <section aria-labelledby="scenario-admin-title">
    <h3 id="scenario-admin-title">Tình huống · {topic.name}</h3>
    {!topic.active ? <p role="status">Chủ đề đang ẩn. Hãy mở chủ đề trước khi công khai tình huống.</p> : null}
    {!editor ? <button onClick={() => { setEditor('new'); setNotice('') }}>Thêm tình huống</button> : null}
    {notice ? <p role="status">{notice}</p> : null}
    {editor ? <ScenarioEditor key={editor === 'new' ? 'new' : editor.id}
      scenario={editor === 'new' ? undefined : editor} path={path} topicActive={topic.active}
      close={() => setEditor(null)} saved={() => {
        setEditor(null); setNotice('Đã lưu tình huống.'); setPage(0); scenarios.reload()
      }} onSessionExpired={onSessionExpired} /> : null}
    {scenarios.error ? <div><p role="alert">{scenarios.error}</p><button onClick={scenarios.reload}>Tải lại tình huống</button></div> :
      !scenarios.data ? <p role="status">Đang tải tình huống…</p> : <>
        {!scenarios.data.items.length ? <p>Chưa có tình huống trong chủ đề.</p> : null}
        <ul className="learning-list">{scenarios.data.items.map(scenario => <li key={scenario.id}>
          <strong>{scenario.title}</strong>
          <p>{!topic.active && scenario.status === 'ACTIVE' ? 'Ẩn theo chủ đề' : statuses[scenario.status]} · {scenario.levels.join(', ') || 'Chưa chọn trình độ'}</p>
          <button disabled={editor !== null} onClick={() => { setEditor(scenario); setNotice('') }}>
            Sửa tình huống<span className="sr-only"> {scenario.title}</span>
          </button>
        </li>)}</ul>
        <Pagination page={page} size={scenarios.data.size} total={scenarios.data.total} onPage={setPage} />
      </>}
  </section>
}

function ScenarioEditor({ scenario, path, topicActive, close, saved, onSessionExpired }: SessionGuard & {
  scenario?: Scenario; path: string; topicActive: boolean; close: () => void; saved: (scenario: Scenario) => void
}) {
  return <CatalogForm<Scenario> title={scenario ? 'Sửa tình huống' : 'Thêm tình huống'}
    path={scenario ? `${path}/${scenario.id}` : path} editing={Boolean(scenario)}
    close={close} saved={saved} onSessionExpired={onSessionExpired}
    payload={form => {
      const status = form.get('status')
      const levels = form.getAll('levels')
      if (status === 'ACTIVE' && (!topicActive || !levels.length))
        throw new Error('Để công khai, hãy mở chủ đề và chọn ít nhất một trình độ.')
      return { ...Object.fromEntries(scenarioFields.map(([name]) => [name, form.get(name)])), status, levels }
    }}>
    {scenarioFields.map(([name, label, max]) =>
      <CatalogField key={name} name={name} label={label} max={max} value={scenario?.[name]} />)}
    <label className="form-field">Trạng thái tình huống
      <select name="status" defaultValue={scenario?.status ?? 'DRAFT'}>
        {Object.entries(statuses).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      </select>
    </label>
    <fieldset><legend>Trình độ</legend>
      {['A2', 'B1'].map(level => <label className="admin-choice" key={level}>
        <input type="checkbox" name="levels" value={level} defaultChecked={scenario?.levels.includes(level)} /> {level}
      </label>)}
    </fieldset>
  </CatalogForm>
}

function CatalogField({ name, label, max, value, optional = false }: {
  name: string; label: string; max: number; value?: string; optional?: boolean
}) {
  return <label className="form-field">{label}
    {max > 200 ? <textarea name={name} maxLength={max} rows={3} required={!optional} defaultValue={value} /> :
      <input name={name} maxLength={max} required={!optional} defaultValue={value}
        pattern={name === 'code' ? `[A-Z][A-Z0-9_]{1,${max - 1}}` : undefined}
        title={name === 'code' ? 'Dùng chữ hoa ASCII, chữ số hoặc dấu gạch dưới, bắt đầu bằng chữ.' : undefined} />}
  </label>
}

function CatalogForm<T>({ title, path, editing, payload, children, close, saved, onSessionExpired }:
  SessionGuard & { title: string; path: string; editing: boolean; payload: (form: FormData) => unknown
    children: ReactNode; close: () => void; saved: (data: T) => void }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
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
  return <form className="admin-form" onSubmit={event => void submit(event)}>
    <fieldset disabled={busy}>
      <legend>{title}</legend>
      {children}
      {error ? <p role="alert">{error}</p> : null}
      <div className="actions">
        <button>{busy ? 'Đang lưu…' : 'Lưu'}</button>
        <button type="button" onClick={close}>Hủy</button>
      </div>
    </fieldset>
  </form>
}
