import { LayoutGrid } from 'lucide-react'
import { NavLink, Outlet, useMatches } from 'react-router'
import { ApiError } from '../api/client'
import { useCurrentAccount } from '../auth/useCurrentAccount'
import { Avatar } from '../components/Avatar/Avatar'
import { Button } from '../components/Button/Button'
import { ErrorMessage } from '../components/ErrorMessage/ErrorMessage'
import styles from './AppShell.module.css'
import { usePageTitle, type PageHandle } from './usePageTitle'

/** The signed-in frame: project rail, a header with the page title and account, and the page. */
export function AppShell() {
  const me = useCurrentAccount()
  // The deepest route that names itself titles the page; routes declare it in their handle.
  const page = useMatches()
    .map((match) => match.handle as PageHandle | undefined)
    .findLast((handle) => handle?.title)
  usePageTitle(page?.title)

  if (me.isPending) return <div className={styles.loading} aria-busy="true" />
  if (me.isError) {
    // A 401 is already on its way to the login page.
    if (me.error instanceof ApiError && me.error.status === 401) return null
    return (
      <main className={styles.failed}>
        <ErrorMessage
          error={
            me.error instanceof ApiError
              ? me.error
              : 'Your account could not be loaded. Try again in a moment.'
          }
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
        <div className={styles.heading}>
          {page?.crumb && <span className={styles.crumb}>{page.crumb} /</span>}
          {page && <h1 className={styles.title}>{page.title}</h1>}
        </div>
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
