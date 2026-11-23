import { QueryClient } from '@tanstack/react-query'
import { ApiError } from './api/client'

/** A refusal (4xx) will not change on retry; a network failure (0) or a server error (5xx) may. */
function shouldRetry(failures: number, error: unknown): boolean {
  if (error instanceof ApiError && error.status >= 400 && error.status < 500) return false
  return failures < 2
}

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: shouldRetry, staleTime: 30_000, refetchOnWindowFocus: true },
      mutations: { retry: false },
    },
  })
}
