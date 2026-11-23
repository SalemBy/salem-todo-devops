import type { QueryClient } from '@tanstack/react-query'
import type { createBrowserRouter } from 'react-router'
import { setUnauthorizedHandler } from '../api/client'

export type AppRouter = ReturnType<typeof createBrowserRouter>

/**
 * On an expired session: forget every cached response, then go to the login page, remembering where the
 * person was. Once on the login page, later 401s from requests still in flight are ignored.
 * Returns a function that removes the handler.
 */
export function installUnauthorizedRedirect(
  router: AppRouter,
  queryClient: QueryClient,
): () => void {
  setUnauthorizedHandler(() => {
    const { pathname, search } = router.state.location
    if (pathname === '/login') return
    queryClient.clear()
    void router.navigate(`/login?next=${encodeURIComponent(pathname + search)}`, { replace: true })
  })
  return () => setUnauthorizedHandler(() => {})
}
