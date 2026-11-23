/**
 * Salem's quiet mark: a black cat's head in the notebook's ink, with amber eyes. Colours come from the
 * cat tokens (`--cat-body`, `--cat-line`, `--cat-eye`), so it follows light and dark; a parent can set
 * `--cat-line` to `--cat-line-on-rail` on dark grounds. No ids inside, so any number can share a page.
 */
export function CatMark({ size = 20, label }: { size?: number; label?: string }) {
  const a11y = label ? { role: 'img', 'aria-label': label } : { 'aria-hidden': true as const }
  return (
    <svg {...a11y} data-mark="" width={size} height={size} viewBox="0 0 24 24" focusable="false">
      <path
        d="M4.2 3.4 8.7 7.3Q12 6.3 15.3 7.3L19.8 3.4 19.6 10.6Q21 13 20.4 15.7 19 20.6 12 20.8 5 20.6 3.6 15.7 3 13 4.4 10.6Z"
        fill="var(--cat-body)"
        stroke="var(--cat-line)"
        strokeWidth="1.4"
        strokeLinejoin="round"
      />
      <ellipse cx="8.7" cy="13.7" rx="1.75" ry="1.35" fill="var(--cat-eye)" />
      <ellipse cx="15.3" cy="13.7" rx="1.75" ry="1.35" fill="var(--cat-eye)" />
      <ellipse cx="8.7" cy="13.7" rx="0.42" ry="1.05" fill="var(--cat-body)" />
      <ellipse cx="15.3" cy="13.7" rx="0.42" ry="1.05" fill="var(--cat-body)" />
    </svg>
  )
}
