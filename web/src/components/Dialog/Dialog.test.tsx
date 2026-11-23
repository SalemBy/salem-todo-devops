import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { Button } from '../Button/Button'
import { Dialog } from './Dialog'

function setup(onOpenChange = vi.fn<(open: boolean) => void>()) {
  render(
    <Dialog
      open
      onOpenChange={onOpenChange}
      title="Archive project?"
      description="Archived projects stay readable but take no changes."
      footer={
        <>
          <Button>Cancel</Button>
          <Button variant="danger">Archive</Button>
        </>
      }
    />,
  )
  return onOpenChange
}

describe('Dialog', () => {
  it('is named by its title and described by its description', () => {
    setup()

    const dialog = screen.getByRole('dialog', { name: 'Archive project?' })
    expect(dialog).toHaveAccessibleDescription(
      'Archived projects stay readable but take no changes.',
    )
  })

  it('takes focus and keeps Tab inside', async () => {
    setup()
    const dialog = screen.getByRole('dialog')

    expect(dialog).toContainElement(document.activeElement as HTMLElement)
    for (let i = 0; i < 5; i++) {
      await userEvent.tab()
      expect(dialog).toContainElement(document.activeElement as HTMLElement)
    }
  })

  it('asks to close on Escape', async () => {
    const onOpenChange = setup()

    await userEvent.keyboard('{Escape}')

    expect(onOpenChange).toHaveBeenCalledWith(false)
  })

  it('has a close button with a name', async () => {
    const onOpenChange = setup()

    await userEvent.click(screen.getByRole('button', { name: 'Close' }))

    expect(onOpenChange).toHaveBeenCalledWith(false)
  })
})
