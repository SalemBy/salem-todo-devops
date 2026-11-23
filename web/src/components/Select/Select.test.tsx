import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { Select } from './Select'

const options = [
  { value: 'LOW', label: 'Low' },
  { value: 'MEDIUM', label: 'Medium' },
  { value: 'HIGH', label: 'High' },
]

describe('Select', () => {
  it('is named by its label and shows the value', () => {
    render(<Select label="Priority" options={options} value="MEDIUM" onValueChange={() => {}} />)

    expect(screen.getByRole('combobox', { name: 'Priority' })).toHaveTextContent('Medium')
  })

  it('picks an option with the keyboard only', async () => {
    const onValueChange = vi.fn<(value: string) => void>()
    render(<Select label="Priority" options={options} value="LOW" onValueChange={onValueChange} />)

    await userEvent.tab()
    expect(screen.getByRole('combobox', { name: 'Priority' })).toHaveFocus()
    await userEvent.keyboard('{Enter}')
    await userEvent.keyboard('{ArrowDown}')
    await userEvent.keyboard('{Enter}')

    expect(onValueChange).toHaveBeenCalledWith('MEDIUM')
  })
})
