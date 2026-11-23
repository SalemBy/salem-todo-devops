import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { Menu } from './Menu'

function setup() {
  const edit = vi.fn<() => void>()
  const archive = vi.fn<() => void>()
  render(
    <Menu
      label="Project actions"
      items={[
        { label: 'Edit', onSelect: edit },
        { label: 'Archive', onSelect: archive, tone: 'danger' },
      ]}
    />,
  )
  return { edit, archive, trigger: screen.getByRole('button', { name: 'Project actions' }) }
}

describe('Menu', () => {
  it('runs an item with the keyboard only', async () => {
    const { archive, edit } = setup()

    await userEvent.tab()
    await userEvent.keyboard('{Enter}')
    expect(await screen.findByRole('menu')).toBeInTheDocument()
    await userEvent.keyboard('{ArrowDown}')
    await userEvent.keyboard('{Enter}')

    expect(archive).toHaveBeenCalledOnce()
    expect(edit).not.toHaveBeenCalled()
  })

  it('closes on Escape and gives focus back to its button', async () => {
    const { trigger } = setup()

    await userEvent.tab()
    await userEvent.keyboard('{Enter}')
    expect(await screen.findByRole('menu')).toBeInTheDocument()
    await userEvent.keyboard('{Escape}')

    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
    expect(trigger).toHaveFocus()
  })
})
