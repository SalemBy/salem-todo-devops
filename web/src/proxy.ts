import type { ProxyOptions } from 'vite'

/**
 * Sends `/api/*` to the API with the prefix removed, for both `vite` and `vite preview`.
 * The prefix keeps UI routes such as `/projects/SALEM/board` apart from API routes.
 */
export function apiProxy(target: string): Record<string, ProxyOptions> {
  return {
    '^/api(?:[/?]|$)': {
      target,
      // Keep the browser's host so the API sees the same origin the cookie belongs to.
      changeOrigin: false,
      rewrite: (path) => path.replace(/^\/api(?=\/|\?|$)/, '') || '/',
    },
  }
}
