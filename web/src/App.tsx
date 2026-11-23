import { QueryClientProvider, type QueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { RouterProvider } from 'react-router'
import { installUnauthorizedRedirect, type AppRouter } from './auth/unauthorizedRedirect'
import { ToastProvider } from './components/Toast/Toast'

export function App({ router, queryClient }: { router: AppRouter; queryClient: QueryClient }) {
  useEffect(() => installUnauthorizedRedirect(router, queryClient), [router, queryClient])
  return (
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <RouterProvider router={router} />
      </ToastProvider>
    </QueryClientProvider>
  )
}
