import { CatHead } from '../CatHead'

/** Chin on an edge, paws over it, eyes wide: curious, for page not found. viewBox 0 0 160 100. */
export function Peeking() {
  return (
    <>
      <CatHead x={49} y={24} scale={2.3} eyes="wide" />
      <path d="M8 72H152" fill="none" stroke="var(--cat-line)" strokeWidth="2" />
      <ellipse
        cx="64"
        cy="73"
        rx="9"
        ry="6"
        fill="var(--cat-body)"
        stroke="var(--cat-line)"
        strokeWidth="2"
      />
      <ellipse
        cx="90"
        cy="73"
        rx="9"
        ry="6"
        fill="var(--cat-body)"
        stroke="var(--cat-line)"
        strokeWidth="2"
      />
    </>
  )
}
