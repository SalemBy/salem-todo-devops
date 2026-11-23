import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { CatMark } from './CatMark'
import { CAT_POSES } from './catPoses'
import { SalemCat } from './SalemCat'

describe('SalemCat', () => {
  it.each(CAT_POSES)('the %s pose renders a hidden drawing', (pose) => {
    const { container } = render(<SalemCat pose={pose} />)

    const svg = container.querySelector('svg')
    expect(svg).toHaveAttribute('aria-hidden', 'true')
    expect(svg).toHaveAttribute('data-pose', pose)
  })

  it('ships only the poses the owner approved', () => {
    expect(CAT_POSES).toEqual(['asleep'])
  })

  it('several cats on one page share no ids', () => {
    const { container } = render(
      <>
        {CAT_POSES.map((pose) => (
          <SalemCat key={pose} pose={pose} />
        ))}
        <CatMark label="Salem Todo DevOps" />
      </>,
    )

    expect(container.querySelectorAll('[id]')).toHaveLength(0)
  })

  it('draws only with the cat tokens', () => {
    const { container } = render(
      <>
        {CAT_POSES.map((pose) => (
          <SalemCat key={pose} pose={pose} />
        ))}
      </>,
    )

    const colours = [...container.querySelectorAll('[fill], [stroke]')].flatMap((element) =>
      ['fill', 'stroke'].map((name) => element.getAttribute(name)).filter((v) => v !== null),
    )
    expect(colours.length).toBeGreaterThan(0)
    for (const colour of colours) expect(colour).toMatch(/^(var\(--cat-[a-z-]+\)|none)$/)
  })

  it('scales the head outline with the drawing, like the rest of it', () => {
    const { container } = render(<SalemCat pose="asleep" size={120} />)

    expect(container.querySelector('[vector-effect]')).toBeNull()
  })

  it('scales to the size asked for', () => {
    const { container } = render(<SalemCat pose="asleep" size={96} />)

    expect(container.querySelector('svg')).toHaveAttribute('width', '96')
  })
})
