import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { Button } from '../Button/Button'
import { ToastProvider } from './Toast'
import { useToast } from './useToast'

function Trigger() {
  const toast = useToast()
  return (
    <Button onClick={() => toast.show({ title: 'Task archived', tone: 'success' })}>Archive</Button>
  )
}

describe('Toast', () => {
  it('shows a message in a live region and closes from the keyboard', async () => {
    render(
      <ToastProvider>
        <Trigger />
      </ToastProvider>,
    )

    await userEvent.click(screen.getByRole('button', { name: 'Archive' }))
    expect(await screen.findByText('Task archived')).toBeVisible()
    expect(screen.getByRole('status')).toHaveTextContent('Task archived')

    screen.getByRole('button', { name: 'Dismiss' }).focus()
    await userEvent.keyboard('{Enter}')

    expect(screen.queryByText('Task archived')).not.toBeInTheDocument()
  })

  it('needs its provider', () => {
    expect(() => render(<Trigger />)).toThrow('useToast must be used inside ToastProvider')
  })
})
