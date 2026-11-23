import { useEffect } from 'react'

/** What a route shows in the shell header: an optional mono crumb (such as a project key) and the title. */
export interface PageHandle {
  title: string
  crumb?: string
}

/** Names the page in the window title, so tabs, history and screen readers can tell pages apart. */
export function usePageTitle(title: string | undefined): void {
  useEffect(() => {
    document.title = title ? `${title} · Salem Todo DevOps` : 'Salem Todo DevOps'
  }, [title])
}
