import type { Material } from '../../materials/model/material-definitions'
import { statuses } from '../../materials/model/material-definitions'
import { Icon } from '../../../../shared/ui/Icon'
import '../styles/topics.css'

export function TopicList({ rows, disabled, onEdit, onAction }: {
  rows: Material[]; disabled: boolean
  onEdit: (row: Material, button: HTMLButtonElement) => void
  onAction: (row: Material, action: 'publish' | 'archive' | 'delete', button: HTMLButtonElement) => void
}) {
  return <table className="admin-topics-table">
    <caption className="sr-only">Danh sách chủ đề và trạng thái hiển thị</caption>
    <colgroup><col className="topic-col-name" /><col className="topic-col-description" /><col className="topic-col-order" /><col className="topic-col-status" /><col className="topic-col-actions" /></colgroup>
    <thead><tr><th scope="col">Chủ đề</th><th scope="col">Mô tả</th><th scope="col">Thứ tự</th><th scope="col">Trạng thái</th><th scope="col">Thao tác</th></tr></thead>
    <tbody>{rows.map(row => <tr key={row.id}>
      <th scope="row"><strong>{String(row.name)}</strong></th>
      <td data-label="Mô tả" className="admin-topic-description">{row.description ? String(row.description) : 'Chưa có mô tả.'}</td>
      <td data-label="Thứ tự"><span className="admin-topic-order">{String(row.sort_order)}</span></td>
      <td data-label="Trạng thái"><span className={`status-badge ${row.status === 'ACTIVE' ? 'status-success' : row.status === 'DRAFT' ? 'status-warning' : ''}`}>{statuses[row.status]}</span></td>
      <td><div className="admin-row-actions">
        <button id={'topic-edit-' + row.id} className="admin-topic-icon-action" title="Sửa chủ đề" disabled={disabled} onClick={event => onEdit(row, event.currentTarget)}><Icon name="edit" /><span className="sr-only">Sửa chủ đề {String(row.name)}</span></button>
        <button className="admin-topic-icon-action" title={row.status === 'ACTIVE' ? 'Ẩn chủ đề' : 'Công bố chủ đề'} disabled={disabled} onClick={event => onAction(row, row.status === 'ACTIVE' ? 'archive' : 'publish', event.currentTarget)}>
          <Icon name={row.status === 'ACTIVE' ? 'eyeOff' : 'eye'} /><span className="sr-only">{row.status === 'ACTIVE' ? 'Ẩn' : 'Công bố'} {String(row.name)}</span>
        </button>
        {row.status === 'DRAFT' && <button className="admin-topic-icon-action button-danger" title="Xóa chủ đề nháp" disabled={disabled} onClick={event => onAction(row, 'delete', event.currentTarget)}>
          <Icon name="trash" /><span className="sr-only">Xóa nháp {String(row.name)}</span>
        </button>}
      </div></td>
    </tr>)}</tbody>
  </table>
}
