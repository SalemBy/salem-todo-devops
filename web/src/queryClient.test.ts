import { describe, expect, it } from 'vitest'
import { ApiError } from './api/client'
import { createQueryClient } from './queryClient'

const retry = createQueryClient().getDefaultOptions().queries?.retry as (
  failures: number,
  error: unknown,
) => boolean

describe('query retries', () => {
  it.each([400, 401, 403, 404, 409, 422])(
    'never retries a %i: the answer will not change',
    (status) => {
      expect(retry(0, new ApiError(status, 'Error', 'Refused'))).toBe(false)
    },
  )

  it.each([0, 500, 503])('retries a %i twice', (status) => {
    const error = new ApiError(status, 'Error', 'Failed')
    expect(retry(0, error)).toBe(true)
    expect(retry(1, error)).toBe(true)
    expect(retry(2, error)).toBe(false)
  })
})
