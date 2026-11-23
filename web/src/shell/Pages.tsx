import { Link } from 'react-router'
import styles from './Pages.module.css'

/** The project list arrives with #77; until then the landing page holds its place. */
export function AllProjectsPage() {
  return (
    <div className={styles.page}>
      <h1 className={styles.title}>All projects</h1>
    </div>
  )
}

export function NotFoundPage() {
  return (
    <div className={styles.page}>
      <h1 className={styles.title}>Page not found</h1>
      <p className={styles.body}>
        This address does not match any page. <Link to="/">Go to all projects</Link>
      </p>
    </div>
  )
}
