import { CircleAlert } from 'lucide-react'
import type { ApiError } from '../../api/client'
import styles from './ErrorMessage.module.css'

/** An error next to where it happened. Announced to screen readers as soon as it appears. */
export function ErrorMessage({ error }: { error: ApiError | string }) {
  const detail = typeof error === 'string' ? error : error.detail
  const fields = typeof error === 'string' ? [] : error.errors
  return (
    <div role="alert" className={styles.error}>
      <CircleAlert className={styles.icon} size={16} aria-hidden="true" />
      <div>
        <p>{detail}</p>
        {fields.length > 0 && (
          <ul className={styles.fields}>
            {fields.map((field) => (
              <li key={`${field.field}:${field.message}`}>{field.message}</li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}
