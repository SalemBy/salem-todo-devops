/**
 * Where to go after logging in. Only a path inside this app is followed, so a crafted link cannot send
 * someone to another site (`//host` and `/\host` are other hosts to a browser). The login page itself,
 * or anything unsafe, falls back to all projects.
 */
export function safeNext(next: string | null): string {
  if (!next || !next.startsWith('/') || next.startsWith('//') || next.startsWith('/\\')) return '/'
  const path = next.split(/[?#]/)[0]
  return path === '/login' ? '/' : next
}
