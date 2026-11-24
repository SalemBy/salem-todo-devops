import { ApiError } from './client'

/**
 * Splits an API error's field errors into the form's fields and the rest. Each form field gets its
 * messages joined; an alias maps an API field name the form does not use (such as the password
 * length check, `passwordLengthValid`) to a form field. Nothing is dropped: unknown fields go to
 * `other`, for the form-level error.
 */
export function fieldErrors(
  error: unknown,
  fields: readonly string[],
  aliases: Record<string, string> = {},
): { byField: Record<string, string>; other: string[] } {
  const byField: Record<string, string> = {}
  const other: string[] = []
  if (!(error instanceof ApiError)) return { byField, other }
  for (const { field, message } of error.errors) {
    const name = aliases[field] ?? field
    if (fields.includes(name))
      byField[name] = byField[name] ? `${byField[name]} ${message}` : message
    else other.push(message)
  }
  return { byField, other }
}

/** When to try again after a 429, from its Retry-After seconds. */
export function retryText(error: ApiError): string {
  const seconds = error.retryAfter
  if (seconds === undefined) return 'Try again in a few minutes.'
  if (seconds < 60) return `Try again in ${seconds} second${seconds === 1 ? '' : 's'}.`
  const minutes = Math.ceil(seconds / 60)
  return `Try again in ${minutes} minute${minutes === 1 ? '' : 's'}.`
}
