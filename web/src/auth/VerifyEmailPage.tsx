import { Button } from '@astryxdesign/core/Button'
import { VStack } from '@astryxdesign/core/Layout'
import { Link } from '@astryxdesign/core/Link'
import { Text } from '@astryxdesign/core/Text'
import { TextInput } from '@astryxdesign/core/TextInput'
import { useMutation } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { ApiError, api } from '../api/client'
import { retryText } from '../api/fieldErrors'
import type { VerifyEmailRequest } from '../api/types'
import { ApiErrorBanner } from '../components/ApiErrorBanner'
import { AuthLayout } from './AuthLayout'
import { fieldStatus } from './fieldStatus'
import { ResendVerification } from './ResendVerification'

/** The API answers the same 400 for an unknown, used or expired link and for a wrong password. */
const INVALID =
  'This link is invalid or has expired, or the password is not the one you registered with.'

const loginLink = (
  <Text color="secondary">
    Already verified? <Link href="/login">Log in</Link>
  </Text>
)

export function VerifyEmailPage() {
  const [params] = useSearchParams()
  const token = params.get('token')?.trim()
  const navigate = useNavigate()
  const [password, setPassword] = useState('')
  const [missing, setMissing] = useState<string>()
  const verify = useMutation({
    mutationFn: (body: VerifyEmailRequest) => api.post('/auth/verify', body),
    onSuccess: () => void navigate('/login', { replace: true, state: { notice: 'verified' } }),
  })

  if (!token || (verify.error instanceof ApiError && verify.error.status === 400))
    return (
      <AuthLayout title="Verify your email" screen="refused" footer={loginLink}>
        <ApiErrorBanner error={INVALID} />
        <Text>Enter your email to get a new link.</Text>
        <ResendVerification label="Send a new link" />
      </AuthLayout>
    )

  const error = verify.error
  const formError =
    error instanceof ApiError && error.status === 429
      ? `Too many attempts. ${retryText(error)}`
      : error instanceof ApiError
        ? error.detail
        : error
          ? 'Something went wrong. Try again in a moment.'
          : undefined

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (verify.isPending || !token) return
    setMissing(password ? undefined : 'Enter the password you chose when you registered.')
    if (password) verify.mutate({ token, password })
  }

  return (
    <AuthLayout title="Verify your email" footer={loginLink}>
      <Text>Enter the password you chose when you registered.</Text>
      <form onSubmit={submit} noValidate>
        <VStack gap={4}>
          <TextInput
            label="Password"
            htmlName="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={setPassword}
            status={fieldStatus(missing)}
          />
          {formError && <ApiErrorBanner error={formError} />}
          <Button
            type="submit"
            variant="primary"
            label="Verify email"
            isLoading={verify.isPending}
          />
        </VStack>
      </form>
    </AuthLayout>
  )
}
