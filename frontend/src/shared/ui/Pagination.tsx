export function Pagination({ page, size, total, onPage, disabled = false }: {
  page: number; size: number; total: number; onPage: (page: number) => void; disabled?: boolean
}) {
  if (total <= size && page === 0) return null
  return <nav aria-label="Phân trang" className="pagination">
    <button disabled={disabled || page === 0} onClick={() => onPage(page - 1)}>Trang trước</button>
    <span>Trang {page + 1}/{Math.max(1, Math.ceil(total / size))}</span>
    <button disabled={disabled || (page + 1) * size >= total} onClick={() => onPage(page + 1)}>Trang tiếp</button>
  </nav>
}
