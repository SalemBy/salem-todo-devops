import type { ReactNode } from 'react'
import styles from './EmptyState.module.css'

export interface EmptyStateProps {
  title: string
  body?: string
  action?: ReactNode
}

export function EmptyState({ title, body, action }: EmptyStateProps) {
  return (
    <section className={styles.empty}>
      <h2 className={styles.title}>{title}</h2>
      {body && <p className={styles.body}>{body}</p>}
      {action && <div className={styles.action}>{action}</div>}
    </section>
  )
}
