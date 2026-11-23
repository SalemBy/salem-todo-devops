const DATE_ONLY = /^(\d{4})-(\d{2})-(\d{2})$/

/**
 * "20 Nov 2026", or "20 Nov" in the current year. A date-only value (a due date) is a calendar day,
 * read as is; an instant (createdAt) is shown in the browser's time zone.
 */
export function formatDate(value: string, now: Date = new Date()): string {
  const day = DATE_ONLY.exec(value)
  const date = day ? new Date(Number(day[1]), Number(day[2]) - 1, Number(day[3])) : new Date(value)
  const sameYear = date.getFullYear() === now.getFullYear()
  return new Intl.DateTimeFormat('en-GB', {
    day: 'numeric',
    month: 'short',
    year: sameYear ? undefined : 'numeric',
  }).format(date)
}
