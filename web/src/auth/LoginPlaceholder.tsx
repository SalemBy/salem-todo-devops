import styles from './LoginPlaceholder.module.css'

/** Stands in for the login page until the sign-in pages are built (#76). */
export function LoginPlaceholder() {
  return (
    <main className={styles.page}>
      <h1 className={styles.title}>Log in</h1>
      <p className={styles.body}>Your session has ended or you are not signed in.</p>
    </main>
  )
}
