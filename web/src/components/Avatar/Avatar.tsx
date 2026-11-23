import { initials } from '../initials'
import styles from './Avatar.module.css'

export function Avatar({ name, size = 'md' }: { name: string; size?: 'sm' | 'md' }) {
  return (
    // oxlint-disable-next-line jsx-a11y/prefer-tag-over-role -- initials drawn as a picture; <img> needs a src
    <span role="img" aria-label={name} title={name} className={`${styles.avatar} ${styles[size]}`}>
      <span aria-hidden="true">{initials(name)}</span>
    </span>
  )
}
