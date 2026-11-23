import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { TextField } from './TextField'

describe('TextField', () => {
  it('is named by its label and takes typing', async () => {
    render(<TextField label="Project name" />)

    const input = screen.getByRole('textbox', { name: 'Project name' })
    await userEvent.type(input, 'Salem')

    expect(input).toHaveValue('Salem')
    expect(input).not.toHaveAttribute('aria-invalid')
  })

  it('links its hint', () => {
    render(<TextField label="Key" hint="2 to 10 letters or digits" />)

    expect(screen.getByRole('textbox', { name: 'Key' })).toHaveAccessibleDescription(
      '2 to 10 letters or digits',
    )
  })

  it('marks an error and is described by it', () => {
    render(<TextField label="Key" hint="2 to 10 letters" error="Key is already taken" />)

    const input = screen.getByRole('textbox', { name: 'Key' })
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input).toHaveAccessibleDescription('2 to 10 letters Key is already taken')
  })
})
