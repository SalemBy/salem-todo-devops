import { useToast } from '@astryxdesign/core/Toast'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { api } from '../api/client'

/**
 * Ends the session, forgets every cached response and goes to the login page. If the server cannot
 * end the session, the person stays signed in and is told, rather than seeing a login page while the
 * session is still valid.
 */
export function useLogout() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const showToast = useToast()
  return useMutation({
    mutationFn: () => api.post('/auth/logout'),
    onSuccess: async () => {
      // Leave the signed-in pages first, so none of them refetches what is about to be forgotten.
      await navigate('/login', { replace: true, state: { notice: 'loggedOut' } })
      queryClient.clear()
    },
    onError: () => showToast({ body: 'Could not log out. Try again in a moment.', type: 'error' }),
  })
}
