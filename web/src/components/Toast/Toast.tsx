import { X } from 'lucide-react'
import { Toast as Radix } from 'radix-ui'
import { useCallback, useMemo, useState, type ReactNode } from 'react'
import styles from './Toast.module.css'
import { ToastContext, type ToastMessage } from './useToast'

/** Short confirmations of something that happened. Errors a person must act on belong next to the form. */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Array<ToastMessage & { id: number }>>([])
  const show = useCallback(
    (message: ToastMessage) =>
      setToasts((all) => [...all, { ...message, id: Date.now() + Math.random() }]),
    [],
  )
  const api = useMemo(() => ({ show }), [show])

  return (
    <ToastContext.Provider value={api}>
      <Radix.Provider swipeDirection="right" duration={5000}>
        {children}
        {toasts.map((toast) => (
          <Radix.Root
            key={toast.id}
            className={`${styles.toast} ${styles[toast.tone ?? 'neutral']}`}
            onOpenChange={(open) => {
              if (!open) setToasts((all) => all.filter((t) => t.id !== toast.id))
            }}
          >
            <Radix.Title className={styles.title}>{toast.title}</Radix.Title>
            {toast.description && (
              <Radix.Description className={styles.description}>
                {toast.description}
              </Radix.Description>
            )}
            <Radix.Close className={styles.close} aria-label="Dismiss notification">
              <X size={14} aria-hidden="true" />
            </Radix.Close>
          </Radix.Root>
        ))}
        <Radix.Viewport className={styles.viewport} label="Notifications ({hotkey})" />
      </Radix.Provider>
    </ToastContext.Provider>
  )
}
