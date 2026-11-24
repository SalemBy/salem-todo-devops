/** A TextInput status for a field's error message, or none. */
export const fieldStatus = (message?: string) =>
  message ? { type: 'error' as const, message } : undefined
