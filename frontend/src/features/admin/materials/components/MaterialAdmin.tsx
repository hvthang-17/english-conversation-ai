import { useRef, useState } from 'react'
import { mutateJson } from '../../../../shared/api/client'
import { reportError } from '../../../../shared/api/errors'
import { useApiQuery } from '../../../../shared/hooks/useApiQuery'
import type { Page, SessionGuard } from '../../../../shared/types/api'
import { Pagination } from '../../../../shared/ui/Pagination'

import { definitions, statuses, type Material, type Kind, type Editor } from '../model/material-definitions'
import { MaterialEditor } from './MaterialEditor'
import { TopicList } from '../../topics/components/TopicList'
import '../styles/materials.css'

export function MaterialAdmin({ onSessionExpired, topicsOnly = false }: SessionGuard & { topicsOnly?: boolean }) {
  const [kind, setKind] = useState<Kind>('LESSONS')
  if (topicsOnly) return <section className="admin-panel" aria-label="Danh sách chủ đề">
    <MaterialList kind="TOPICS" onSessionExpired={onSessionExpired} />
  </section>
  return <section className="admin-panel" aria-label="Danh sách học liệu">
    <div className="admin-material-toolbar">
    <div className="form-field admin-material-kind"><label htmlFor="admin-material-kind">Loại học liệu</label>
      <select id="admin-material-kind" value={kind} onChange={event => setKind(event.target.value as Kind)}>
        {(Object.keys(definitions) as Kind[]).filter(key => key !== 'TOPICS').map(key => <option key={key} value={key}>{definitions[key].label}</option>)}
      </select>
    </div><p className="hint">Lưu bản nháp, rà nội dung rồi công bố cho người học.</p></div>
    <MaterialList key={kind} kind={kind} onSessionExpired={onSessionExpired} />
  </section>
}

function MaterialList({ kind, onSessionExpired }: SessionGuard & { kind: Kind }) {
  const [page, setPage] = useState(0)
  const [editor, setEditor] = useState<Editor>()
  const [notice, setNotice] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [confirmation, setConfirmation] = useState<{ row: Material; action: 'publish' | 'archive' | 'delete' }>()
  const confirmationPanel = useRef<HTMLDivElement>(null)
  const actionTrigger = useRef<HTMLButtonElement | null>(null)
  const editorTrigger = useRef<HTMLButtonElement | null>(null)
  const listHeading = useRef<HTMLHeadingElement>(null)
  const base = 'admin/materials/' + kind
  const query = useApiQuery<Page<Material>>(base + '?page=' + page, onSessionExpired)
  const { label, labelField, fields } = definitions[kind]
  const isTopic = kind === 'TOPICS'
  const addLabel = isTopic ? 'Thêm chủ đề' : 'Tạo bản nháp'

  function openEditor(value: Editor, button: HTMLButtonElement) {
    editorTrigger.current = button
    setEditor(value); setNotice('')
  }

  function closeEditor() {
    setEditor(undefined)
    requestAnimationFrame(() => {
      const previous = editorTrigger.current
      const target = previous?.isConnected ? previous : document.getElementById(previous?.id ?? '')
      if (target) target.focus()
      else listHeading.current?.focus()
    })
  }

  function requestConfirmation(row: Material, action: 'publish' | 'archive' | 'delete', button: HTMLButtonElement) {
    actionTrigger.current = button
    setError(''); setNotice(''); setConfirmation({ row, action })
    requestAnimationFrame(() => confirmationPanel.current?.focus())
  }

  function cancelConfirmation() {
    setConfirmation(undefined); setError('')
    requestAnimationFrame(() => actionTrigger.current?.focus())
  }

  async function confirm() {
    if (!confirmation || busy) return
    const { row, action } = confirmation
    setBusy(true)
    setError('')
    setNotice('')
    try {
      if (action === 'delete') {
        await mutateJson(base + '/' + row.id, undefined, 'DELETE')
        if (query.data?.items.length === 1 && page > 0) setPage(page - 1)
      } else {
        const path = kind === 'LISTENING' && action === 'publish'
          ? 'admin/listening-exercises/' + row.id + '/publish' : base + '/' + row.id + '/' + action
        await mutateJson(path)
      }
      query.reload()
      setConfirmation(undefined)
      setNotice(isTopic ? { publish: 'Đã công bố chủ đề.', archive: 'Đã ẩn chủ đề.', delete: 'Đã xóa chủ đề nháp.' }[action] : { publish: 'Đã công bố học liệu.', archive: 'Đã ẩn học liệu.', delete: 'Đã xóa bản nháp.' }[action])
      requestAnimationFrame(() => listHeading.current?.focus())
    } catch (failure) { setError(reportError(failure, onSessionExpired)) }
    finally { setBusy(false) }
  }

  const editorView = editor ? <MaterialEditor fields={fields} kind={kind} editor={editor} base={base}
      onSessionExpired={onSessionExpired} close={closeEditor}
      saved={() => { setEditor(undefined); setNotice(editor?.source?.status && editor.source.status !== 'DRAFT' && !editor.copy ? 'Đã lưu chủ đề.' : 'Đã lưu bản nháp.'); setPage(0); query.reload(); requestAnimationFrame(() => listHeading.current?.focus()) }} /> : null

  if (isTopic && editor) return <div className="admin-topic-edit-workspace">{editorView}</div>

  return <>
    <div className="admin-panel-heading"><div><h2 ref={listHeading} tabIndex={-1}>{isTopic ? 'Danh sách chủ đề' : label}</h2>{isTopic && <p>{query.data ? query.data.total.toLocaleString('vi-VN') + ' chủ đề' : 'Chủ đề sắp xếp nội dung học tập theo bối cảnh giao tiếp.'}</p>}</div>
      <button id={isTopic ? 'topic-create' : undefined} className="primary" disabled={!!editor || !!confirmation} onClick={event => openEditor({}, event.currentTarget)}>{addLabel}</button>
    </div>
    {notice && <p className="admin-success" role="status">{notice}</p>}
    {editorView}
    {confirmation && <div ref={confirmationPanel} tabIndex={-1} className="admin-confirmation" aria-label="Xác nhận thay đổi">
      <p><strong>{String(confirmation.row[labelField])}</strong></p>
      <p>{confirmation.action === 'delete' ? (isTopic ? 'Xóa vĩnh viễn chủ đề nháp này?' : 'Xóa vĩnh viễn bản nháp chưa có tham chiếu?')
        : confirmation.action === 'archive' ? (isTopic ? 'Ẩn chủ đề cùng các bài học và tình huống trong danh mục. Lịch sử được giữ lại.' : 'Ẩn học liệu khỏi danh mục và giữ lịch sử?')
          : kind === 'LISTENING' ? 'Công bố sẽ tạo MP3 bằng Azure nếu chưa có audio và ẩn đề đang công khai của cùng bài.'
            : isTopic ? 'Công bố chủ đề trong danh mục người học?' : 'Công bố học liệu cho người học?'}</p>
      {error && <p role="alert">{error}</p>}
      <div className="actions">
        <button className={confirmation.action === 'publish' ? 'primary' : 'button-danger'} disabled={busy} onClick={() => void confirm()}>{busy ? 'Đang xử lý…' : (isTopic ? { publish: 'Công bố chủ đề', archive: 'Ẩn chủ đề', delete: 'Xóa chủ đề nháp' } : { publish: 'Công bố học liệu', archive: 'Ẩn học liệu', delete: 'Xóa bản nháp' })[confirmation.action]}</button>
        <button disabled={busy} onClick={cancelConfirmation}>Hủy</button>
      </div>
    </div>}
    {query.error ? <p role="alert">{query.error} <button onClick={query.reload}>Tải lại</button></p>
      : !query.data ? <p className="loading-state" role="status">{isTopic ? 'Đang tải chủ đề…' : 'Đang tải học liệu…'}</p>
      : query.data.items.length === 0 ? <p>{isTopic ? 'Chưa có chủ đề trong trang này.' : 'Chưa có học liệu trong trang này.'} Chọn “{addLabel}” để thêm.</p>
      : isTopic ? <TopicList rows={query.data.items} disabled={!!editor || !!confirmation} onEdit={(row, button) => openEditor({ source: row }, button)} onAction={requestConfirmation} />
      : <ul className="admin-list">{query.data.items.map(row => <li key={row.id}>
        <div><strong>{String(row[labelField])}</strong>
        <div className="admin-material-meta"><span className={`status-badge ${row.status === 'ACTIVE' ? 'status-success' : row.status === 'DRAFT' ? 'status-warning' : ''}`}>{statuses[row.status]}</span><span>ID {row.id}</span>{isTopic && <span>{String(row.code)} · Thứ tự {String(row.sort_order)}</span>}{row.lesson_id ? <span>Bài #{String(row.lesson_id)}</span> : null}</div></div>
        <div className="actions">
          <button disabled={!!editor || !!confirmation} onClick={event => openEditor({ source: row, copy: row.status !== 'DRAFT' }, event.currentTarget)}>
            {isTopic ? 'Sửa chủ đề' : row.status === 'DRAFT' ? 'Sửa' : 'Sao chép thành bản nháp'}
            <span className="sr-only"> {String(row[labelField])}</span>
          </button>
          {(row.status === 'DRAFT' || row.status === 'ACTIVE' || kind !== 'LISTENING') && <button
            disabled={!!editor || !!confirmation} onClick={event => requestConfirmation(row, row.status === 'ACTIVE' ? 'archive' : 'publish', event.currentTarget)}>
            {row.status === 'ACTIVE' ? 'Ẩn' : 'Công bố'}
            <span className="sr-only"> {String(row[labelField])}</span>
          </button>}
          {row.status === 'DRAFT' && <button className="button-danger" disabled={!!editor || !!confirmation}
            onClick={event => requestConfirmation(row, 'delete', event.currentTarget)}>Xóa bản nháp<span className="sr-only"> {String(row[labelField])}</span></button>}
        </div>
      </li>)}</ul>}
    {query.data && <Pagination page={page} size={query.data.size} total={query.data.total}
      onPage={setPage} disabled={!!editor || !!confirmation} />}
  </>
}

