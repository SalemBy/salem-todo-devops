// A zone west of Greenwich, so a date-only value parsed as UTC midnight would show the day before.
// CI runs in UTC, where that bug would otherwise go unnoticed.
process.env.TZ = 'America/Sao_Paulo'

import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

afterEach(() => cleanup())

// jsdom lacks the pointer-capture, scrolling and resize APIs Radix primitives call.
Element.prototype.hasPointerCapture ??= () => false
Element.prototype.setPointerCapture ??= () => {}
Element.prototype.releasePointerCapture ??= () => {}
Element.prototype.scrollIntoView ??= () => {}
globalThis.ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
}
