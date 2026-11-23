import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Avatar } from '../Avatar/Avatar'
import { LabelChip } from '../LabelChip/LabelChip'
import { labelColor } from '../labelColor'
import { Badge } from './Badge'

describe('Badge', () => {
  it('says its meaning in words, not only colour', () => {
    render(<Badge tone="danger">Overdue</Badge>)
    expect(screen.getByText('Overdue')).toBeVisible()
  })
})

describe('LabelChip', () => {
  it('shows the label name in its hashed colour', () => {
    render(<LabelChip name="frontend" />)

    const chip = screen.getByText('frontend')
    expect(chip.style.getPropertyValue('--chip-bg')).toBe(`var(${labelColor('frontend')}-bg)`)
  })
})

describe('Avatar', () => {
  it('is an image named after the person, showing initials', () => {
    render(<Avatar name="Ada Lovelace" />)

    const avatar = screen.getByRole('img', { name: 'Ada Lovelace' })
    expect(avatar).toHaveTextContent('AL')
  })
})
