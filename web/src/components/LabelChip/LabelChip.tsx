import type { CSSProperties } from 'react'
import { labelColor } from '../labelColor'
import styles from './LabelChip.module.css'

export function LabelChip({ name }: { name: string }) {
  const color = labelColor(name)
  const style = {
    '--chip-bg': `var(${color}-bg)`,
    '--chip-fg': `var(${color}-fg)`,
  } as CSSProperties
  return (
    <span className={styles.chip} style={style}>
      {name}
    </span>
  )
}
