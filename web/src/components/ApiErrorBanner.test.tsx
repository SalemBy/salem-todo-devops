import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ApiError } from '../api/client'
import { ApiErrorBanner } from './ApiErrorBanner'

describe('ApiErrorBanner', () => {
  it('announces a plain message', () => {
    render(<ApiErrorBanner error="Could not load projects." />)
    expect(screen.getByRole('alert')).toHaveTextContent('Could not load projects.')
  })

  it('shows an API error and its field errors', () => {
    const error = new ApiError(400, 'Bad Request', 'Request validation failed', [
      { field: 'key', message: 'Key is already in use' },
      { field: 'name', message: 'Name is required' },
    ])
    render(<ApiErrorBanner error={error} />)

    const alert = screen.getByRole('alert')
    expect(alert).toHaveTextContent('Request validation failed')
    expect(alert).toHaveTextContent('Key is already in use')
    expect(alert).toHaveTextContent('Name is required')
  })
})
