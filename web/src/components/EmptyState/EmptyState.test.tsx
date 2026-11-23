import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { ApiError } from '../../api/client'
import { Button } from '../Button/Button'
import { ErrorMessage } from '../ErrorMessage/ErrorMessage'
import { EmptyState } from './EmptyState'

describe('EmptyState', () => {
  it('has a heading and a reachable action', async () => {
    render(
      <EmptyState
        title="No projects yet"
        body="Create one to start planning."
        action={<Button>New project</Button>}
      />,
    )

    expect(screen.getByRole('heading', { name: 'No projects yet' })).toBeVisible()
    await userEvent.tab()
    expect(screen.getByRole('button', { name: 'New project' })).toHaveFocus()
  })
})

describe('EmptyState illustration', () => {
  it('shows the illustration and hides it from screen readers', () => {
    const { container } = render(
      <EmptyState
        title="No projects yet"
        illustration={<span data-testid="art">Sleeping cat</span>}
      />,
    )

    const art = screen.getByTestId('art')
    expect(art.parentElement).toHaveAttribute('aria-hidden', 'true')
    expect(screen.queryByText('Sleeping cat', { ignore: '[aria-hidden="true"] *' })).toBeNull()
    expect(screen.getByRole('heading', { name: 'No projects yet' })).toBeVisible()
    expect(container.querySelector('h2')?.previousElementSibling).toBe(art.parentElement)
  })

  it('renders no wrapper without an illustration', () => {
    const { container } = render(<EmptyState title="No projects yet" />)

    expect(container.querySelector('[aria-hidden]')).toBeNull()
  })
})

describe('ErrorMessage', () => {
  it('announces a plain message', () => {
    render(<ErrorMessage error="Could not load projects." />)
    expect(screen.getByRole('alert')).toHaveTextContent('Could not load projects.')
  })

  it('shows an API error and its field errors', () => {
    const error = new ApiError(400, 'Bad Request', 'Request validation failed', [
      { field: 'key', message: 'Key is already taken' },
      { field: 'name', message: 'Name is required' },
    ])
    render(<ErrorMessage error={error} />)

    const alert = screen.getByRole('alert')
    expect(alert).toHaveTextContent('Request validation failed')
    expect(alert).toHaveTextContent('Key is already taken')
    expect(alert).toHaveTextContent('Name is required')
  })
})
