import type { ReactNode } from 'react'
import styles from './Badge.module.css'

export interface BadgeProps {
  /** Colour supports the words; the text always carries the meaning. */
  tone?: 'neutral' | 'accent' | 'danger' | 'success'
  children: ReactNode
}

export function Badge({ tone = 'neutral', children }: BadgeProps) {
  return <span className={`${styles.badge} ${styles[tone]}`}>{children}</span>
}
