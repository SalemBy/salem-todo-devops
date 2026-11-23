import { CatHead, Limb } from '../CatHead'

/** Sitting up and batting at a ball of string, for welcome screens. viewBox 0 0 160 130. */
export function Playing() {
  return (
    <>
      <path d="M30 124H112" fill="none" stroke="var(--cat-line)" strokeWidth="1.5" />
      <path d="M118 0V30" fill="none" stroke="var(--cat-line)" strokeWidth="1.4" />
      <circle cx="118" cy="40" r="10" fill="none" stroke="var(--cat-line)" strokeWidth="2" />
      <path
        d="M110 34Q118 40 126 34M109 43Q118 49 127 43M114 31Q111 40 116 50"
        fill="none"
        stroke="var(--cat-line)"
        strokeWidth="1.2"
        strokeLinecap="round"
      />
      <Limb d="M50 118Q22 114 26 92Q30 78 40 82" width={8} />
      <path
        d="M48 122Q38 92 56 74Q72 64 88 74Q106 92 96 122Z"
        fill="var(--cat-body)"
        stroke="var(--cat-line)"
        strokeWidth="2"
        strokeLinejoin="round"
      />
      <Limb d="M88 84Q102 70 106 55" width={9} />
      <CatHead x={46} y={24} scale={2.2} eyes="up" tilt={-6} />
    </>
  )
}
