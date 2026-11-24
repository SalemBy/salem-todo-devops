import '@astryxdesign/core/reset.css'
import '@astryxdesign/core/astryx.css'
import { Theme } from '@astryxdesign/core/theme'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter } from 'react-router'
import { App } from './App'
import { defaultThemeSlug, themes } from './astryx-themes'
import { createQueryClient } from './queryClient'
import { routes } from './routes'
import './styles/global.css'

const root = document.getElementById('root')
if (!root) throw new Error('Missing #root element')

createRoot(root).render(
  <StrictMode>
    <Theme theme={themes[defaultThemeSlug]}>
      <App router={createBrowserRouter(routes)} queryClient={createQueryClient()} />
    </Theme>
  </StrictMode>,
)
