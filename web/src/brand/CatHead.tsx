/** Salem's head on a 24×24 grid, shared by the mark and every pose so they are one cat. */
export const HEAD_PATH =
  'M4.2 3.4 8.7 7.3Q12 6.3 15.3 7.3L19.8 3.4 19.6 10.6Q21 13 20.4 15.7 19 20.6 12 20.8 5 20.6 3.6 15.7 3 13 4.4 10.6Z'

export type CatEyes = 'open' | 'wide' | 'up' | 'closed' | 'squint'

const EYES_X = [8.7, 15.3] as const
const EYE_Y = 13.7

/** The eyes carry the mood: slits at rest, round when curious, arcs when asleep or pleased. */
export function CatEyesShape({ eyes }: { eyes: CatEyes }) {
  return (
    <>
      {EYES_X.map((x) => {
        if (eyes === 'closed' || eyes === 'squint') {
          const lid = eyes === 'closed' ? EYE_Y + 1.3 : EYE_Y - 1.3
          return (
            <path
              key={x}
              d={`M${x - 1.8} ${EYE_Y}Q${x} ${lid} ${x + 1.8} ${EYE_Y}`}
              fill="none"
              stroke="var(--cat-eye)"
              strokeWidth="0.8"
              strokeLinecap="round"
            />
          )
        }
        const round = eyes !== 'open'
        const dx = eyes === 'up' ? 0.45 : 0
        const dy = eyes === 'up' ? -0.45 : 0
        return (
          <g key={x}>
            <ellipse cx={x} cy={EYE_Y} rx="1.8" ry={round ? 1.7 : 1.35} fill="var(--cat-eye)" />
            <ellipse
              cx={x + dx}
              cy={EYE_Y + dy}
              rx={round ? 0.75 : 0.42}
              ry={round ? 0.85 : 1.05}
              fill="var(--cat-body)"
            />
          </g>
        )
      })}
    </>
  )
}

/** The head placed in a larger drawing; the outline keeps the same width at any scale. */
export function CatHead({
  x,
  y,
  scale,
  eyes,
  tilt = 0,
}: {
  x: number
  y: number
  scale: number
  eyes: CatEyes
  tilt?: number
}) {
  return (
    <g
      transform={`translate(${x} ${y}) rotate(${tilt} ${12 * scale} ${12 * scale}) scale(${scale})`}
    >
      <path
        d={HEAD_PATH}
        fill="var(--cat-body)"
        stroke="var(--cat-line)"
        strokeWidth="2"
        strokeLinejoin="round"
        vectorEffect="non-scaling-stroke"
      />
      <CatEyesShape eyes={eyes} />
    </g>
  )
}

/** A thick shape such as a tail or a leg, drawn as an outlined stroke. */
export function Limb({ d, width }: { d: string; width: number }) {
  return (
    <>
      <path
        d={d}
        fill="none"
        stroke="var(--cat-line)"
        strokeWidth={width + 4}
        strokeLinecap="round"
      />
      <path d={d} fill="none" stroke="var(--cat-body)" strokeWidth={width} strokeLinecap="round" />
    </>
  )
}
