import { MoreHorizontal } from 'lucide-react'
import { DropdownMenu } from 'radix-ui'
import type { ReactNode } from 'react'
import styles from './Menu.module.css'

export interface MenuItem {
  label: string
  onSelect: () => void
  tone?: 'default' | 'danger'
  disabled?: boolean
}

export interface MenuProps {
  /** Accessible name of the trigger button, such as "Project actions". */
  label: string
  items: MenuItem[]
  /** The trigger's visible content; a "more" icon by default. */
  icon?: ReactNode
}

export function Menu({ label, items, icon }: MenuProps) {
  return (
    <DropdownMenu.Root>
      <DropdownMenu.Trigger className={styles.trigger} aria-label={label}>
        {icon ?? <MoreHorizontal size={16} aria-hidden="true" />}
      </DropdownMenu.Trigger>
      <DropdownMenu.Portal>
        <DropdownMenu.Content className={styles.content} align="start" sideOffset={4}>
          {items.map((item) => (
            <DropdownMenu.Item
              key={item.label}
              className={[styles.item, item.tone === 'danger' ? styles.danger : '']
                .filter(Boolean)
                .join(' ')}
              disabled={item.disabled}
              onSelect={item.onSelect}
            >
              {item.label}
            </DropdownMenu.Item>
          ))}
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  )
}
