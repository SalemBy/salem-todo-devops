import { ComponentGallery } from './dev/ComponentGallery'

// Replaced by the router and app shell (#75, Task 4).
export function App() {
  if (import.meta.env.DEV && window.location.pathname === '/dev/components')
    return <ComponentGallery />
  return <main>Salem Todo DevOps</main>
}
