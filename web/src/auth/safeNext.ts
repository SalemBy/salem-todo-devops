/**
 * Where to go after logging in. Only a path inside this app is followed, so a crafted link cannot send
 * someone to another site. The value is resolved the way the browser will resolve it (which drops
 * tabs and newlines and reads `\` as `/`), so `/\t/host` cannot pass as a path. Any spelling of the
 * login page, or anything unsafe, falls back to all projects.
 */
export function safeNext(next: string | null): string {
  if (!next?.startsWith('/')) return '/'
  const origin = window.location.origin
  let url: URL
  try {
    url = new URL(next, origin)
  } catch {
    return '/'
  }
  if (url.origin !== origin) return '/'
  if (url.pathname.toLowerCase().replace(/\/+$/, '') === '/login') return '/'
  return url.pathname + url.search + url.hash
}
