import type { ComponentType } from 'react'
import type { CatPose } from './catPoses'
import { Asleep } from './poses/Asleep'
import { Peeking } from './poses/Peeking'
import { Playing } from './poses/Playing'
import { Pleased } from './poses/Pleased'

const DRAWINGS: Record<CatPose, { viewBox: string; Drawing: ComponentType }> = {
  asleep: { viewBox: '0 0 160 100', Drawing: Asleep },
  playing: { viewBox: '0 0 160 130', Drawing: Playing },
  peeking: { viewBox: '0 0 160 100', Drawing: Peeking },
  pleased: { viewBox: '-20 0 160 140', Drawing: Pleased },
}

/**
 * Salem in a pose, for the moments around the work only: sign-in pages, empty states, page not found,
 * small rewards (see DESIGN.md). Always decorative: the words next to it carry the meaning.
 */
export function SalemCat({ pose, size = 160 }: { pose: CatPose; size?: number }) {
  const { viewBox, Drawing } = DRAWINGS[pose]
  return (
    <svg aria-hidden="true" data-pose={pose} width={size} viewBox={viewBox} focusable="false">
      <Drawing />
    </svg>
  )
}
