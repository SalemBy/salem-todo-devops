import { X } from 'lucide-react'
import { Dialog as Radix } from 'radix-ui'
import type { ReactNode } from 'react'
import styles from './Dialog.module.css'

export interface DialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description?: string
  children?: ReactNode
  /** Actions, right-aligned; the confirming action goes last. */
  footer?: ReactNode
}

export function Dialog({ open, onOpenChange, title, description, children, footer }: DialogProps) {
  return (
    <Radix.Root open={open} onOpenChange={onOpenChange}>
      <Radix.Portal>
        <Radix.Overlay className={styles.overlay} />
        <Radix.Content
          className={styles.content}
          // Without a description Radix warns; undefined tells it there is none on purpose.
          {...(description ? {} : { 'aria-describedby': undefined })}
        >
          <header className={styles.header}>
            <Radix.Title className={styles.title}>{title}</Radix.Title>
            <Radix.Close className={styles.close} aria-label="Close">
              <X size={16} aria-hidden="true" />
            </Radix.Close>
          </header>
          {description && (
            <Radix.Description className={styles.description}>{description}</Radix.Description>
          )}
          {children && <div className={styles.body}>{children}</div>}
          {footer && <footer className={styles.footer}>{footer}</footer>}
        </Radix.Content>
      </Radix.Portal>
    </Radix.Root>
  )
}
