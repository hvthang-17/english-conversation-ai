import type { ReactNode } from 'react'
import './shared-ui.css'

export function PageHeading({ id, title, description, action }: { id: string; title: string; description?: string; action?: ReactNode }) {
  return <header className="page-heading"><div className="page-heading-copy"><h1 id={id}>{title}</h1>{description ? <p>{description}</p> : null}</div>{action ? <div className="page-heading-action">{action}</div> : null}</header>
}
