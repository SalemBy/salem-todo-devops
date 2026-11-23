import { describe, expect, it } from 'vitest'
import { formatDate } from './date'

const now = new Date(2026, 10, 23, 12, 0) // 23 Nov 2026, local time

describe('formatDate', () => {
  it('shows the year for dates in another year', () => {
    expect(formatDate('2025-11-20', now)).toBe('20 Nov 2025')
    expect(formatDate('2027-01-05', now)).toBe('5 Jan 2027')
  })

  it('leaves the year out in the current year', () => {
    expect(formatDate('2026-11-20', now)).toBe('20 Nov')
  })

  it('reads a due date as a calendar day, whatever the time zone', () => {
    // A date-only value parsed as UTC midnight would show the day before west of Greenwich.
    expect(formatDate('2026-01-01', now)).toBe('1 Jan')
    expect(formatDate('2026-12-31', now)).toBe('31 Dec')
  })

  it('formats an instant in local time', () => {
    const instant = new Date(2026, 4, 9, 23, 30).toISOString()
    expect(formatDate(instant, now)).toBe('9 May')
  })
})
