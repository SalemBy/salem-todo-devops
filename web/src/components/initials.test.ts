import { describe, expect, it } from 'vitest'
import { initials } from './initials'

describe('initials', () => {
  it.each([
    ['Ada Lovelace', 'AL'],
    ['ada', 'A'],
    ['Mary Ann Evans', 'ME'],
    ['  ', '?'],
    ['', '?'],
    ['élodie durand', 'ÉD'],
    ['  Grace   Hopper  ', 'GH'],
  ])('%j gives %j', (name, expected) => {
    expect(initials(name)).toBe(expected)
  })
})
