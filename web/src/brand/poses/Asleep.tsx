import { CatHead, Limb } from '../CatHead'

/** Curled up asleep, tail around the front, for empty and quiet places. viewBox 0 0 160 100. */
export function Asleep() {
  return (
    <>
      <path d="M14 86H146" fill="none" stroke="var(--cat-line)" strokeWidth="1.5" />
      <path
        d="M34 84Q22 58 54 44Q92 30 124 46Q148 60 138 84Z"
        fill="var(--cat-body)"
        stroke="var(--cat-line)"
        strokeWidth="2"
        strokeLinejoin="round"
      />
      <Limb d="M136 78Q150 92 116 90Q86 88 62 86" width={9} />
      <CatHead x={18} y={38} scale={1.9} eyes="closed" tilt={-12} />
      <path
        d="M70 22h7l-7 8h7M84 9h5l-5 6h5"
        fill="none"
        stroke="var(--cat-line)"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </>
  )
}
