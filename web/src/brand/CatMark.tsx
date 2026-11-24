import { CatEyesShape, HEAD_PATH } from './CatHead'

/**
 * Salem's quiet mark: a black cat's head with amber eyes. Colours come from the cat tokens
 * (`--cat-body`, `--cat-line`, `--cat-eye`); the outline is the Astryx theme's text colour, so the mark
 * follows light and dark. No ids inside, so any number can share a page.
 */
export function CatMark({ size = 20, label }: { size?: number; label?: string }) {
  const a11y = label ? { role: 'img', 'aria-label': label } : { 'aria-hidden': true as const }
  return (
    <svg {...a11y} data-mark="" width={size} height={size} viewBox="0 0 24 24" focusable="false">
      <path
        d={HEAD_PATH}
        fill="var(--cat-body)"
        stroke="var(--cat-line)"
        strokeWidth="1.4"
        strokeLinejoin="round"
      />
      <CatEyesShape eyes="open" />
    </svg>
  )
}
