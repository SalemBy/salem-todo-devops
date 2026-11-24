import { describe, expect, it } from 'vitest'
import { ApiError } from './client'
import { fieldErrors, retryText } from './fieldErrors'

const FIELDS = ['email', 'displayName', 'password'] as const
const ALIASES = { passwordLengthValid: 'password' }

describe('fieldErrors', () => {
  it('maps field errors to the form fields', () => {
    const error = new ApiError(400, 'Bad Request', 'Request validation failed', [
      { field: 'email', message: 'must be a well-formed email address' },
      { field: 'displayName', message: 'must not be blank' },
    ])

    expect(fieldErrors(error, FIELDS, ALIASES)).toEqual({
      byField: { email: 'must be a well-formed email address', displayName: 'must not be blank' },
      other: [],
    })
  })

  it('maps an API field name the form does not use through its alias', () => {
    const error = new ApiError(400, 'Bad Request', 'Request validation failed', [
      {
        field: 'passwordLengthValid',
        message: 'Password must contain 15 to 128 Unicode code points',
      },
    ])

    expect(fieldErrors(error, FIELDS, ALIASES).byField).toEqual({
      password: 'Password must contain 15 to 128 Unicode code points',
    })
  })

  it('keeps messages for fields the form does not have', () => {
    const error = new ApiError(400, 'Bad Request', 'Request validation failed', [
      { field: 'other', message: 'x' },
    ])

    expect(fieldErrors(error, FIELDS, ALIASES)).toEqual({ byField: {}, other: ['x'] })
  })

  it('joins several messages for one field', () => {
    const error = new ApiError(400, 'Bad Request', 'Request validation failed', [
      { field: 'email', message: 'a' },
      { field: 'email', message: 'b' },
    ])

    expect(fieldErrors(error, FIELDS).byField.email).toBe('a b')
  })

  it('gives nothing for errors that are not from the API', () => {
    expect(fieldErrors(new Error('boom'), FIELDS)).toEqual({ byField: {}, other: [] })
  })
})

describe('retryText', () => {
  it.each([
    [120, 'Try again in 2 minutes.'],
    [61, 'Try again in 2 minutes.'],
    [60, 'Try again in 1 minute.'],
    [45, 'Try again in 45 seconds.'],
    [1, 'Try again in 1 second.'],
    [undefined, 'Try again in a few minutes.'],
  ])('%s seconds reads "%s"', (seconds, text) => {
    expect(retryText(new ApiError(429, 'Too Many Requests', 'Too many', [], seconds))).toBe(text)
  })
})
