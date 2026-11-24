/** Astryx Badge colour variants a label can take; red is left out so a label never reads as an error. */
export const LABEL_VARIANTS = [
  'blue',
  'cyan',
  'green',
  'orange',
  'pink',
  'purple',
  'teal',
  'yellow',
] as const
export type LabelVariant = (typeof LABEL_VARIANTS)[number]

/** Labels have no colour in the API: each gets a stable one, from a hash of its trimmed lower-case name. */
export function labelVariant(name: string): LabelVariant {
  let hash = 0x811c9dc5 // FNV-1a
  for (const char of name.trim().toLowerCase()) {
    hash ^= char.codePointAt(0) ?? 0
    hash = Math.imul(hash, 0x01000193) >>> 0
  }
  return LABEL_VARIANTS[hash % LABEL_VARIANTS.length] ?? 'blue'
}
