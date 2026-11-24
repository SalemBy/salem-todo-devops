import { Button } from '@astryxdesign/core/Button'
import { VStack } from '@astryxdesign/core/Layout'
import { TextInput } from '@astryxdesign/core/TextInput'
import { useMutation } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { ApiError, api } from '../api/client'
import { retryText } from '../api/fieldErrors'
import { ApiErrorBanner } from '../components/ApiErrorBanner'
import { fieldStatus } from './fieldStatus'
import { Notice } from './formParts'

function resendError(error: unknown): string {
  if (error instanceof ApiError && error.status === 429)
    return `Too many emails requested. ${retryText(error)}`
  return error instanceof ApiError ? error.detail : 'Something went wrong. Try again in a moment.'
}

/**
 * Asks for another verification email. The API always answers 202, whether or not the address has a
 * pending account, so the confirmation never reveals one. With `email` known, only the button shows.
 */
export function ResendVerification({ email, label }: { email?: string; label: string }) {
  const [typed, setTyped] = useState('')
  const [missing, setMissing] = useState<string>()
  const resend = useMutation({
    mutationFn: (address: string) => api.post('/auth/verification-email', { email: address }),
  })

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (resend.isPending) return
    const address = email ?? typed.trim()
    setMissing(address ? undefined : 'Enter your email.')
    if (address) resend.mutate(address)
  }

  return (
    <form onSubmit={submit} noValidate>
      <VStack gap={4}>
        {email === undefined && (
          <TextInput
            label="Email"
            htmlName="email"
            type="email"
            autoComplete="email"
            value={typed}
            onChange={setTyped}
            status={fieldStatus(missing)}
          />
        )}
        {resend.isSuccess && <Notice text="Sent. Check your inbox and spam folder." />}
        {resend.isError && <ApiErrorBanner error={resendError(resend.error)} />}
        <Button type="submit" label={label} isLoading={resend.isPending} />
      </VStack>
    </form>
  )
}
