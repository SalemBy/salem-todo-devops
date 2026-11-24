import { Button } from '@astryxdesign/core/Button'
import { VStack } from '@astryxdesign/core/Layout'
import { Link } from '@astryxdesign/core/Link'
import { Text } from '@astryxdesign/core/Text'
import { TextInput } from '@astryxdesign/core/TextInput'
import { useMutation } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { ApiError, api } from '../api/client'
import { fieldErrors, retryText } from '../api/fieldErrors'
import type { AccountRegistration, AccountResponse } from '../api/types'
import { ApiErrorBanner } from '../components/ApiErrorBanner'
import { AuthLayout } from './AuthLayout'
import { fieldStatus } from './fieldStatus'
import { ResendVerification } from './ResendVerification'

const FIELDS = ['email', 'displayName', 'password'] as const
type Field = (typeof FIELDS)[number]
const REQUIRED: Record<Field, string> = {
  email: 'Enter your email.',
  displayName: 'Enter the name others will see.',
  password: 'Enter a password.',
}

/** The page's own words for a field the API refused; the API's wording is technical. */
const INVALID: Record<Field, string> = {
  email: 'Enter an email address like name@example.com.',
  displayName: 'Use 1 to 100 characters.',
  password: 'Use 15 to 128 characters.',
}

const loginLink = (
  <Text color="secondary">
    Already have an account? <Link href="/login">Log in</Link>
  </Text>
)

/** What the form says above its button; field errors (400, 409) show on their fields instead. */
function formLevelError(error: unknown, unplaced: string[]): string | undefined {
  if (!error) return undefined
  if (!(error instanceof ApiError)) return 'Something went wrong. Try again in a moment.'
  if (error.status === 429) return `Too many registrations from this network. ${retryText(error)}`
  if (error.status === 400 || error.status === 409) return unplaced.join(' ') || undefined
  return error.detail
}

export function RegisterPage() {
  const [values, setValues] = useState<Record<Field, string>>({
    email: '',
    displayName: '',
    password: '',
  })
  const [missing, setMissing] = useState<Partial<Record<Field, string>>>({})
  const register = useMutation({
    mutationFn: (body: AccountRegistration) => api.post<AccountResponse>('/auth/register', body),
  })

  if (register.isSuccess) return <CheckYourEmail email={register.data.email} />

  const error = register.error
  if (error instanceof ApiError && error.status === 403)
    return (
      <AuthLayout title="Registration is closed" footer={loginLink}>
        <Text>Ask the person who runs this workspace for access.</Text>
      </AuthLayout>
    )

  const server = fieldErrors(error, FIELDS, { passwordLengthValid: 'password' })
  const taken = error instanceof ApiError && error.status === 409
  const fieldError = (field: Field) =>
    missing[field] ??
    (field === 'email' && taken
      ? 'This email is already registered.'
      : server.byField[field] && INVALID[field])
  const passwordError = fieldError('password')
  const formError = formLevelError(error, server.other)
  const set = (field: Field) => (value: string) => setValues({ ...values, [field]: value })

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (register.isPending) return
    const body = {
      email: values.email.trim(),
      displayName: values.displayName.trim(),
      password: values.password,
    }
    const blank = Object.fromEntries(
      FIELDS.filter((field) => !body[field]).map((field) => [field, REQUIRED[field]]),
    )
    setMissing(blank)
    if (Object.keys(blank).length === 0) register.mutate(body)
  }

  return (
    <AuthLayout title="Create an account" footer={loginLink}>
      <form onSubmit={submit} noValidate>
        <VStack gap={4}>
          <TextInput
            label="Email"
            htmlName="email"
            type="email"
            autoComplete="email"
            value={values.email}
            onChange={set('email')}
            status={fieldStatus(fieldError('email'))}
          />
          <TextInput
            label="Display name"
            htmlName="displayName"
            autoComplete="name"
            description="Shown to the people in your projects."
            value={values.displayName}
            onChange={set('displayName')}
            status={fieldStatus(fieldError('displayName'))}
          />
          <TextInput
            label="Password"
            htmlName="password"
            type="password"
            autoComplete="new-password"
            description={passwordError ? undefined : '15 to 128 characters.'}
            value={values.password}
            onChange={set('password')}
            status={fieldStatus(passwordError)}
          />
          {formError && <ApiErrorBanner error={formError} />}
          <Button
            type="submit"
            variant="primary"
            label="Create account"
            isLoading={register.isPending}
          />
        </VStack>
      </form>
    </AuthLayout>
  )
}

/** After registering. Salem already naps on the layout's other half, so only the words mention it. */
function CheckYourEmail({ email }: { email: string }) {
  return (
    <AuthLayout title="Check your email" footer={loginLink}>
      <Text>
        We sent a link to <strong>{email}</strong>. Open it to finish creating your account. Salem
        will nap until then.
      </Text>
      <ResendVerification email={email} label="Send it again" />
    </AuthLayout>
  )
}
