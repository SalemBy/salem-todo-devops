import { usePageTitle } from '../shell/usePageTitle'
import styles from './LoginPlaceholder.module.css'

/** Stands in for the login page until the sign-in pages are built (#76). */
export function LoginPlaceholder() {
  usePageTitle('Log in')
  return (
    <main className={styles.page}>
      <h1 className={styles.title}>Log in</h1>
      <p className={styles.body}>You are not logged in, or your session has ended.</p>
    </main>
  )
}
