import type { ReactNode } from 'react'
import styles from './EmptyState.module.css'

export interface EmptyStateProps {
  title: string
  body?: string
  action?: ReactNode
  /** A decorative drawing above the title, such as a Salem pose. Hidden from screen readers. */
  illustration?: ReactNode
}

export function EmptyState({ title, body, action, illustration }: EmptyStateProps) {
  return (
    <section className={styles.empty}>
      {illustration && (
        <div className={styles.illustration} aria-hidden="true">
          {illustration}
        </div>
      )}
      <h2 className={styles.title}>{title}</h2>
      {body && <p className={styles.body}>{body}</p>}
      {action && <div className={styles.action}>{action}</div>}
    </section>
  )
}
