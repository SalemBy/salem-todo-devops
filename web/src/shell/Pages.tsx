import { Link } from 'react-router'
import styles from './Pages.module.css'

/** The project list arrives with #77; until then the page under the "All projects" header is empty. */
export function AllProjectsPage() {
  return null
}

export function NotFoundPage() {
  return (
    <p className={styles.body}>
      This address does not match any page. <Link to="/">Go to all projects</Link>
    </p>
  )
}
