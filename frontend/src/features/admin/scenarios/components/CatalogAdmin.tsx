import { useRef, useState } from 'react'
import { mutateJson } from '../../../../shared/api/client'
import { reportError } from '../../../../shared/api/errors'
import { Pagination } from '../../../../shared/ui/Pagination'
import { useApiQuery } from '../../../../shared/hooks/useApiQuery'
import type { Page, SessionGuard } from '../../../../shared/types/api'
import { ScenarioEditorLoader } from './ScenarioEditor'
import { type Topic, type Scenario, statuses } from '../model/scenario-fields'
import { Icon } from '../../../../shared/ui/Icon'
import '../styles/scenarios.css'

export function CatalogAdmin({ onSessionExpired }: SessionGuard) {
  const [page, setPage] = useState(0)
  const [editing, setEditing] = useState(false)
  const [selected, setSelected] = useState<Topic | null>(null)
  const topics = useApiQuery<Page<Topic>>(`admin/topics?page=${page}`, onSessionExpired)

  return <div className="admin-scenario-browser"><section className="admin-panel" aria-labelledby="catalog-title">
    <div className="admin-panel-heading"><h2 id="catalog-title">Chọn chủ đề</h2><p>Quản lý tình huống theo bối cảnh giao tiếp</p></div>
    {topics.error ? <div><p role="alert">{topics.error}</p><button onClick={topics.reload}>Tải lại chủ đề</button></div> :
      !topics.data ? <p role="status">Đang tải chủ đề…</p> : <>
        {!topics.data.items.length ? <p>Chưa có chủ đề trong trang này.</p> : null}
        <div className="admin-scenario-topic-picker"><div className="form-field"><label htmlFor="scenario-topic">Chủ đề đang quản lý</label>
          <select id="scenario-topic" disabled={editing} value={selected?.id ?? ''} onChange={event => setSelected(topics.data?.items.find(topic => topic.id === event.target.value) ?? null)}>
            <option value="">Chọn chủ đề</option>
            {topics.data.items.map(topic => <option key={topic.id} value={topic.id}>{topic.name}{topic.active ? '' : ' (Đã ẩn)'}</option>)}
          </select></div><p>{selected ? selected.description || 'Quản lý các tình huống thuộc chủ đề đã chọn.' : 'Chọn một chủ đề để xem, thêm hoặc sửa tình huống.'}</p></div>
        <Pagination page={page} size={topics.data.size} total={topics.data.total} onPage={next => { setPage(next); setSelected(null) }} disabled={editing} />
      </>}
    </section>
    {selected ? <Scenarios key={`scenarios-${selected.id}`} topic={selected} onEditingChange={setEditing} onSessionExpired={onSessionExpired} /> : null}
  </div>
}

function Scenarios({ topic, onEditingChange, onSessionExpired }: SessionGuard & { topic: Topic; onEditingChange: (editing: boolean) => void }) {
  const [page, setPage] = useState(0)
  const [editor, setEditor] = useState<Scenario | 'new' | null>(null)
  const [notice, setNotice] = useState('')
  const [selectedAction, setSelectedAction] = useState<Scenario>()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const panel = useRef<HTMLDivElement>(null)
  const trigger = useRef<HTMLButtonElement>(null)
  const heading = useRef<HTMLHeadingElement>(null)
  const path = `admin/topics/${topic.id}/scenarios`
  const scenarios = useApiQuery<Page<Scenario>>(`${path}?page=${page}`, onSessionExpired)

  function openEditor(value: Scenario | 'new', button: HTMLButtonElement) {
    trigger.current = button
    setEditor(value); setNotice(''); onEditingChange(true)
  }
  function closeEditor() {
    setEditor(null); onEditingChange(false)
    requestAnimationFrame(() => trigger.current?.focus())
  }

  async function confirm() {
    if (!selectedAction || busy) return
    const deleting = selectedAction.status === 'DRAFT'
    const restoring = selectedAction.status === 'ARCHIVED'
    const target = path + '/' + selectedAction.id
    setBusy(true)
    setError('')
    try {
      await mutateJson(deleting ? target : target + (restoring ? '/restore' : '/archive'), undefined, deleting ? 'DELETE' : 'POST')
      if (deleting && page > 0 && scenarios.data?.items.length === 1) setPage(page - 1)
      setSelectedAction(undefined)
      setNotice(deleting ? 'Đã xóa tình huống nháp.' : restoring ? 'Đã bỏ ẩn tình huống.' : 'Đã ẩn tình huống.')
      scenarios.reload()
      requestAnimationFrame(() => heading.current?.focus())
    } catch (failure) {
      setError(reportError(failure, onSessionExpired))
    } finally {
      setBusy(false)
    }
  }

  return <section className="admin-panel" aria-labelledby="scenario-admin-title">
    <div className="admin-panel-heading"><div><h2 id="scenario-admin-title" tabIndex={-1} ref={heading}>{editor ? 'Biên tập tình huống' : 'Tình huống trong chủ đề'}</h2><p>{topic.name}{scenarios.data ? ' / ' + scenarios.data.total + ' tình huống' : ''}</p></div>
      {!editor ? <button className="primary" disabled={!!selectedAction} onClick={event => openEditor('new', event.currentTarget)}><Icon name="plus" />Thêm tình huống</button> : null}</div>
    {!topic.active ? <p role="status">Chủ đề đang ẩn. Hãy mở chủ đề trước khi công khai tình huống.</p> : null}
    {notice ? <p role="status">{notice}</p> : null}
    {selectedAction && <div ref={panel} tabIndex={-1} className="admin-confirmation" aria-label="Xác nhận thay đổi tình huống">
      <p><strong>{selectedAction.title}</strong></p>
      <p>{selectedAction.status === 'DRAFT' ? 'Xóa vĩnh viễn tình huống nháp này?'
        : selectedAction.status === 'ARCHIVED' ? 'Công khai lại tình huống cho người học?'
        : 'Ẩn tình huống khỏi danh mục. Các buổi đã mở vẫn giữ nội dung và lịch sử.'}</p>
      {error && <p role="alert">{error}</p>}
      <div className="actions">
        <button className={selectedAction.status === 'ARCHIVED' ? 'primary' : 'button-danger'} disabled={busy} onClick={() => void confirm()}>
          {busy ? 'Đang xử lý…' : selectedAction.status === 'DRAFT' ? 'Xác nhận xóa' : selectedAction.status === 'ARCHIVED' ? 'Xác nhận bỏ ẩn' : 'Xác nhận ẩn'}
        </button>
        <button disabled={busy} onClick={() => {
          setSelectedAction(undefined)
          requestAnimationFrame(() => trigger.current?.focus())
        }}>Hủy</button>
      </div>
    </div>}
    {editor ? <ScenarioEditorLoader key={editor === 'new' ? 'new' : editor.id}
      scenario={editor === 'new' ? undefined : editor} path={path} topicActive={topic.active}
      close={closeEditor} saved={() => {
        setEditor(null); onEditingChange(false); requestAnimationFrame(() => heading.current?.focus()); setNotice('Đã lưu tình huống.'); setPage(0); scenarios.reload()
      }} onSessionExpired={onSessionExpired} /> : null}
    {!editor && (scenarios.error ? <div><p role="alert">{scenarios.error}</p><button onClick={scenarios.reload}>Tải lại tình huống</button></div> :
      !scenarios.data ? <p role="status">Đang tải tình huống…</p> : <>
        {!scenarios.data.items.length ? <p>Chưa có tình huống trong chủ đề.</p> : null}
        <table className="admin-scenarios-table"><caption className="sr-only">Danh sách tình huống trong chủ đề</caption><thead><tr><th scope="col">Tình huống</th><th scope="col">Vai giao tiếp</th><th scope="col">Trình độ</th><th scope="col">Trạng thái</th><th scope="col">Thao tác</th></tr></thead><tbody>{scenarios.data.items.map(scenario => <tr key={scenario.id}>
          <th scope="row"><strong>{scenario.title}</strong><p>{scenario.description}</p></th>
          <td data-label="Vai giao tiếp"><span>Người học: {scenario.learnerRole}</span><span>Đối phương: {scenario.aiRole}</span></td>
          <td data-label="Trình độ">{scenario.levels.join(', ') || 'Chưa chọn'}</td>
          <td data-label="Trạng thái"><span className={`status-badge ${scenario.status === 'ACTIVE' && topic.active ? 'status-success' : scenario.status === 'DRAFT' ? 'status-warning' : ''}`}>{!topic.active && scenario.status === 'ACTIVE' ? 'Ẩn theo chủ đề' : statuses[scenario.status]}</span></td>
          <td><div className="admin-row-actions"><button className="admin-scenario-icon-action" title="Sửa tình huống" disabled={!!editor || !!selectedAction} onClick={event => openEditor(scenario, event.currentTarget)}>
            <Icon name="edit" /><span className="sr-only">Sửa tình huống {scenario.title}</span>
          </button>
          <button disabled={!!editor || !!selectedAction}
            className={`admin-scenario-icon-action${scenario.status === 'DRAFT' ? ' button-danger' : ''}`}
            title={scenario.status === 'DRAFT' ? 'Xóa bản nháp' : scenario.status === 'ARCHIVED' ? 'Bỏ ẩn tình huống' : 'Ẩn tình huống'}
            onClick={event => {
              trigger.current = event.currentTarget
              setSelectedAction(scenario)
              setNotice('')
              setError('')
              requestAnimationFrame(() => panel.current?.focus())
            }}>
            <Icon name={scenario.status === 'DRAFT' ? 'trash' : scenario.status === 'ARCHIVED' ? 'eye' : 'eyeOff'} />
            <span className="sr-only">{scenario.status === 'DRAFT' ? 'Xóa bản nháp' : scenario.status === 'ARCHIVED' ? 'Bỏ ẩn' : 'Ẩn tình huống'} {scenario.title}</span>
          </button></div></td>
        </tr>)}</tbody></table>
        <Pagination page={page} size={scenarios.data.size} total={scenarios.data.total} onPage={setPage} disabled={!!editor || !!selectedAction} />
      </>)}
  </section>
}

