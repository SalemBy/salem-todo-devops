/** First letters of the first and last words of a display name, upper-cased; '?' when there are none. */
export function initials(displayName: string): string {
  const words = displayName.trim().split(/\s+/).filter(Boolean)
  const first = words[0]
  const last = words.length > 1 ? words[words.length - 1] : undefined
  const letter = (word?: string) => (word ? (Array.from(word)[0]?.toUpperCase() ?? '') : '')
  return letter(first) + letter(last) || '?'
}
