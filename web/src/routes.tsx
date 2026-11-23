import type { RouteObject } from 'react-router'
import { LoginPlaceholder } from './auth/LoginPlaceholder'
import { AppShell } from './shell/AppShell'
import { AllProjectsPage, NotFoundPage } from './shell/Pages'

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
  { path: '/login', element: <LoginPlaceholder /> },
  ...devRoutes,
  {
    // Unknown paths sit inside the signed-in shell, so an anonymous visitor is sent to login first.
    element: <AppShell />,
    children: [
      { index: true, element: <AllProjectsPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]
