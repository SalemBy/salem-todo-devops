import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
import type { AccountResponse } from '../api/types'

/** The signed-in account. A 401 here is what sends an anonymous visitor to the login page. */
export function useCurrentAccount() {
  return useQuery({ queryKey: ['me'], queryFn: () => api.get<AccountResponse>('/auth/me') })
}
