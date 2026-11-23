/** Labels have no colour in the API, so each gets a stable one from the token palette (`--label-N-bg/-fg`). */
export const LABEL_COLORS = Array.from({ length: 8 }, (_, i) => `--label-${i + 1}`)

/** Label names are unique regardless of case in a project, so the colour ignores case and outer spaces. */
export function labelColor(name: string): string {
  let hash = 0x811c9dc5 // FNV-1a
  for (const char of name.trim().toLowerCase()) {
    hash ^= char.codePointAt(0) ?? 0
    hash = Math.imul(hash, 0x01000193) >>> 0
  }
  return LABEL_COLORS[hash % LABEL_COLORS.length] ?? '--label-1'
}
