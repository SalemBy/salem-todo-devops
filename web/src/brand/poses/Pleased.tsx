import { CatHead, Limb } from '../CatHead'

/** Sitting tall with a content squint, tail curled up, for small rewards. viewBox -20 0 160 140 (drawn on a 120-wide area, centred). */
export function Pleased() {
  return (
    <>
      <path d="M18 136H102" fill="none" stroke="var(--cat-line)" strokeWidth="1.5" />
      <Limb d="M84 132Q112 130 104 108Q100 98 92 102" width={8} />
      <path
        d="M34 134Q24 100 42 80Q60 70 78 80Q96 100 86 134Z"
        fill="var(--cat-body)"
        stroke="var(--cat-line)"
        strokeWidth="2"
        strokeLinejoin="round"
      />
      <ellipse
        cx="50"
        cy="133"
        rx="7"
        ry="4"
        fill="var(--cat-body)"
        stroke="var(--cat-line)"
        strokeWidth="2"
      />
      <ellipse
        cx="70"
        cy="133"
        rx="7"
        ry="4"
        fill="var(--cat-body)"
        stroke="var(--cat-line)"
        strokeWidth="2"
      />
      <CatHead x={33} y={36} scale={2.2} eyes="squint" tilt={-8} />
    </>
  )
}
