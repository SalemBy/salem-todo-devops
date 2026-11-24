import { ApiError } from '../api/client'

/** A TextInput status for a field's error message, or none. */
export const fieldStatus = (message?: string) =>
  message ? { type: 'error' as const, message } : undefined

/** The page's own words for an email the API refused; the API's wording is technical. */
export const INVALID_EMAIL = 'Enter an email address like name@example.com.'

/** Whether the API refused the request because of the email field (a 400 naming it). */
export const refusedEmail = (error: unknown) =>
  error instanceof ApiError &&
  error.status === 400 &&
  error.errors.some((fieldError) => fieldError.field === 'email')
