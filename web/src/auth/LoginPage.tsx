import { Button } from '@astryxdesign/core/Button'
import { VStack } from '@astryxdesign/core/Layout'
import { Link } from '@astryxdesign/core/Link'
import { Text } from '@astryxdesign/core/Text'
import { TextInput } from '@astryxdesign/core/TextInput'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router'
import { ApiError, api } from '../api/client'
import { retryText } from '../api/fieldErrors'
import type { AccountResponse, LoginRequest } from '../api/types'
import { ApiErrorBanner } from '../components/ApiErrorBanner'
import { AuthLayout } from './AuthLayout'
import { fieldStatus } from './fieldStatus'
import { Notice } from './formParts'
import { safeNext } from './safeNext'

export type SignInNotice = 'verified' | 'loggedOut' | 'returning'

const NOTICES: Record<SignInNotice, string> = {
  verified: 'Your email is verified. You can log in now.',
  loggedOut: 'You are logged out.',
  returning: 'Log in to go back to the page you were on.',
}

/** Every refusal reads the same, so the page never reveals whether an account exists or is verified. */
function loginError(error: unknown): string {
  if (!(error instanceof ApiError)) return 'Something went wrong. Try again in a moment.'
  if (error.status === 401)
    return 'Invalid email or password. If you registered recently, open the link in your verification email first.'
  if (error.status === 429) return `Too many sign-in attempts. ${retryText(error)}`
  if (error.status === 503) return 'Signing in is unavailable right now. Try again in a moment.'
  return error.detail
}

export function LoginPage() {
  const [params] = useSearchParams()
  const location = useLocation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [missing, setMissing] = useState<{ email?: string; password?: string }>({})
  const next = safeNext(params.get('next'))
  const notice =
    (location.state as { notice?: SignInNotice } | null)?.notice ??
    (next === '/' ? undefined : 'returning')

  const login = useMutation({
    mutationFn: (body: LoginRequest) => api.post<AccountResponse>('/auth/login', body),
    onSuccess: (account) => {
      queryClient.setQueryData(['me'], account)
      void navigate(next, { replace: true })
    },
  })

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (login.isPending) return
    const address = email.trim()
    const blank = {
      email: address ? undefined : 'Enter your email.',
      password: password ? undefined : 'Enter your password.',
    }
    setMissing(blank)
    if (blank.email || blank.password) return
    login.mutate({ email: address, password })
  }

  return (
    <AuthLayout
      title="Log in"
      footer={
        <Text color="secondary">
          No account? <Link href="/register">Register</Link>
        </Text>
      }
    >
      {notice && NOTICES[notice] && <Notice text={NOTICES[notice]} />}
      <form onSubmit={submit} noValidate>
        <VStack gap={4}>
          <TextInput
            label="Email"
            htmlName="email"
            type="email"
            autoComplete="username"
            value={email}
            onChange={setEmail}
            status={fieldStatus(missing.email)}
          />
          <TextInput
            label="Password"
            htmlName="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={setPassword}
            status={fieldStatus(missing.password)}
          />
          {login.isError && <ApiErrorBanner error={loginError(login.error)} />}
          <Button type="submit" variant="primary" label="Log in" isLoading={login.isPending} />
        </VStack>
      </form>
    </AuthLayout>
  )
}
