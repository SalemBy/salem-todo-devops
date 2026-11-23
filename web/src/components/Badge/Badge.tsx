import type { ReactNode } from 'react'
import styles from './Badge.module.css'

export interface BadgeProps {
  /**
   * Colour supports the words; the text always carries the meaning. Neutral for roles and calm states
   * such as archived; danger only for critical and overdue; success for completed. Blue stays on actions.
   */
  tone?: 'neutral' | 'danger' | 'success'
  children: ReactNode
}

export function Badge({ tone = 'neutral', children }: BadgeProps) {
  return <span className={`${styles.badge} ${styles[tone]}`}>{children}</span>
}
