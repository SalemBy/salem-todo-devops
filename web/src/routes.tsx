import type { RouteObject } from 'react-router'
import { LoginPage } from './auth/LoginPage'
import { AppShell } from './shell/AppShell'
import { AllProjectsPage, NotFoundPage } from './shell/Pages'
import type { PageHandle } from './shell/usePageTitle'

// Development builds only; production builds drop the gallery and its code.
const devRoutes: RouteObject[] = import.meta.env.DEV
  ? [
      {
        path: '/dev/components',
        lazy: async () => ({
          Component: (await import('./dev/ComponentGallery')).ComponentGallery,
        }),
      },
    ]
  : []

export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  ...devRoutes,
  {
    // Unknown paths sit inside the signed-in shell, so an anonymous visitor is sent to login first.
    element: <AppShell />,
    children: [
      {
        index: true,
        element: <AllProjectsPage />,
        handle: { title: 'All projects' } satisfies PageHandle,
      },
      {
        path: '*',
        element: <NotFoundPage />,
        handle: { title: 'Page not found' } satisfies PageHandle,
      },
    ],
  },
]
