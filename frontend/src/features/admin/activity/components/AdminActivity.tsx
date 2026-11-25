import { useApiQuery } from '../../../../shared/hooks/useApiQuery'
import type { SessionGuard } from '../../../../shared/types/api'

type Activity = { id: string; activity: string; subject: string; reference: string
  actor: string | null; status: string | null; latency: string | null; occurred: string }

export function AdminActivity({ audit, onSessionExpired }: SessionGuard & { audit: boolean }) {
  const { data, error, reload } = useApiQuery<Activity[]>(audit ? 'admin/audit' : 'admin/ai-attempts', onSessionExpired)
  if (error) return <div><p role="alert">{error}</p><button type="button" onClick={reload}>Tải lại</button></div>
  if (!data) return <p role="status">Đang tải nhật ký…</p>
  if (!data.length) return <p>Chưa có dữ liệu nhật ký.</p>
  return <section className="admin-panel"><div className="admin-panel-heading"><h2>{audit ? 'Thay đổi gần đây' : 'Tác vụ gần đây'}</h2><button onClick={reload}>Tải lại nhật ký</button></div><div className="admin-table-scroll" role="region" aria-label={audit ? 'Bảng nhật ký quản trị' : 'Bảng tác vụ AI'} tabIndex={0}><table className="admin-activity">
    <caption>Tối đa 50 mục gần nhất</caption>
    <thead><tr><th scope="col">Thời điểm UTC</th><th scope="col">Hoạt động</th><th scope="col">{audit ? 'Đối tượng' : 'Nhà cung cấp / model'}</th>
      <th scope="col">{audit ? 'ID người thao tác' : 'Trạng thái / độ trễ'}</th></tr></thead>
    <tbody>{data.map(item => <tr key={item.id}><td>{item.occurred}</td><td>{item.activity}</td>
      <td>{item.subject} · {item.reference}</td><td>{audit ? item.actor ?? '—' :
        [item.status, item.latency == null ? null : item.latency + ' ms'].filter(Boolean).join(' · ')}</td></tr>)}</tbody>
  </table></div></section>
}

