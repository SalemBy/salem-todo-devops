import { LayoutGrid } from 'lucide-react'
import { NavLink, Outlet } from 'react-router'
import { ApiError } from '../api/client'
import { useCurrentAccount } from '../auth/useCurrentAccount'
import { Avatar } from '../components/Avatar/Avatar'
import { Button } from '../components/Button/Button'
import { ErrorMessage } from '../components/ErrorMessage/ErrorMessage'
import styles from './AppShell.module.css'

/** The signed-in frame: project rail, account header and the page. Later issues fill the rail. */
export function AppShell() {
  const me = useCurrentAccount()

  if (me.isPending) return <div className={styles.loading} aria-busy="true" />
  if (me.isError) {
    // A 401 is already on its way to the login page.
    if (me.error instanceof ApiError && me.error.status === 401) return null
    return (
      <main className={styles.failed}>
        <ErrorMessage
          error={me.error instanceof ApiError ? me.error : 'Your account could not be loaded.'}
        />
        <Button onClick={() => void me.refetch()} loading={me.isFetching}>
          Try again
        </Button>
      </main>
    )
  }

  return (
    <div className={styles.shell}>
      <a className={styles.skip} href="#content">
        Skip to content
      </a>
      <nav className={styles.rail} aria-label="Projects">
        <span className={styles.brand}>Salem Todo DevOps</span>
        <NavLink to="/" end className={styles.link}>
          <LayoutGrid size={16} aria-hidden="true" />
          All projects
        </NavLink>
      </nav>
      <header className={styles.header}>
        <span className={styles.account}>
          <span className={styles.name}>{me.data.displayName}</span>
          <Avatar name={me.data.displayName} />
        </span>
      </header>
      <main id="content" tabIndex={-1} className={styles.main}>
        <Outlet />
      </main>
    </div>
  )
}
