import type { ComponentType } from 'react'
import type { CatPose } from './catPoses'
import { Asleep } from './poses/Asleep'

const DRAWINGS: Record<CatPose, { viewBox: string; Drawing: ComponentType }> = {
  asleep: { viewBox: '0 0 160 100', Drawing: Asleep },
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
