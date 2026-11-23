import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { CatMark } from './CatMark'

describe('CatMark', () => {
  it('a standalone mark is an image named by its label', () => {
    render(<CatMark label="Salem Todo DevOps" />)

    expect(screen.getByRole('img', { name: 'Salem Todo DevOps' })).toBeInTheDocument()
  })

  it('a mark without a label is hidden from screen readers', () => {
    const { container } = render(<CatMark />)

    expect(container.querySelector('svg')).toHaveAttribute('aria-hidden', 'true')
    expect(screen.queryByRole('img')).toBeNull()
  })

  it('takes its size in pixels', () => {
    const { container } = render(<CatMark size={32} />)

    expect(container.querySelector('svg')).toHaveAttribute('width', '32')
    expect(container.querySelector('svg')).toHaveAttribute('height', '32')
  })
})
