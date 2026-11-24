import { QueryClientProvider, type QueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { LinkProvider } from '@astryxdesign/core/Link'
import { RouterProvider } from 'react-router'
import { RouterLink } from './shell/RouterLink'
import { installUnauthorizedRedirect, type AppRouter } from './auth/unauthorizedRedirect'

export function App({ router, queryClient }: { router: AppRouter; queryClient: QueryClient }) {
  useEffect(() => installUnauthorizedRedirect(router, queryClient), [router, queryClient])
  return (
    <QueryClientProvider client={queryClient}>
      <LinkProvider component={RouterLink}>
        <RouterProvider router={router} />
      </LinkProvider>
    </QueryClientProvider>
  )
}
