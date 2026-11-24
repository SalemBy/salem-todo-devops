import { AppShell as AstryxAppShell } from '@astryxdesign/core/AppShell'
import { Avatar } from '@astryxdesign/core/Avatar'
import { Button } from '@astryxdesign/core/Button'
import { DropdownMenu } from '@astryxdesign/core/DropdownMenu'
import { Heading } from '@astryxdesign/core/Heading'
import { Layout, LayoutContent, LayoutHeader } from '@astryxdesign/core/Layout'
import { SideNav, SideNavHeading, SideNavItem, SideNavSection } from '@astryxdesign/core/SideNav'
import { Stack } from '@astryxdesign/core/Stack'
import { Text } from '@astryxdesign/core/Text'
import { LayoutGrid } from 'lucide-react'
import { Outlet, useLocation, useMatches } from 'react-router'
import { ApiError } from '../api/client'
import { useCurrentAccount } from '../auth/useCurrentAccount'
import { useLogout } from '../auth/useLogout'
import { CatMark } from '../brand/CatMark'
import { ApiErrorBanner } from '../components/ApiErrorBanner'
import { usePageTitle, type PageHandle } from './usePageTitle'

/**
 * The signed-in frame, from the Astryx shell-side-nav template: the side nav (Salem's mark, the
 * product name, the project links), a header with the page title and the account, and the page.
 * Astryx's AppShell brings the skip link to the content.
 */
export function AppShell() {
  const me = useCurrentAccount()
  const logout = useLogout()
  const { pathname } = useLocation()
  // The deepest route that names itself titles the page; routes declare it in their handle.
  const page = useMatches()
    .map((match) => match.handle as PageHandle | undefined)
    .findLast((handle) => handle?.title)
  usePageTitle(page?.title)

  if (me.isPending) return null
  if (me.isError) {
    // A 401 is already on its way to the login page.
    if (me.error instanceof ApiError && me.error.status === 401) return null
    return (
      <Stack as="main" padding={8} gap={4} maxWidth={520}>
        <ApiErrorBanner
          error={
            me.error instanceof ApiError
              ? me.error
              : 'Your account could not be loaded. Try again in a moment.'
          }
        />
        <Button label="Try again" onClick={() => void me.refetch()} isLoading={me.isFetching} />
      </Stack>
    )
  }

  return (
    <AstryxAppShell
      contentPadding={0}
      sideNav={
        <SideNav
          header={
            <SideNavHeading
              heading="Salem Todo DevOps"
              icon={<CatMark size={24} />}
              headingHref="/"
            />
          }
        >
          <SideNavSection title="Projects" isHeaderHidden>
            <SideNavItem
              label="All projects"
              icon={LayoutGrid}
              href="/"
              isSelected={pathname === '/'}
            />
          </SideNavSection>
        </SideNav>
      }
    >
      <Layout
        height="fill"
        header={
          <LayoutHeader hasDivider padding={4}>
            <Stack direction="horizontal" hAlign="between" vAlign="center">
              <Stack direction="horizontal" gap={2} vAlign="center">
                {page?.crumb && <Text color="secondary">{page.crumb} /</Text>}
                {page && <Heading level={1}>{page.title}</Heading>}
              </Stack>
              <Stack direction="horizontal" gap={2} vAlign="center">
                <DropdownMenu
                  button={{
                    label: me.data.displayName,
                    'aria-label': `Account: ${me.data.displayName}`,
                    variant: 'ghost',
                  }}
                  items={[{ label: 'Log out', onClick: () => logout.mutate() }]}
                />
                <Avatar name={me.data.displayName} size="sm" tooltip={false} />
              </Stack>
            </Stack>
          </LayoutHeader>
        }
        content={
          <LayoutContent padding={6}>
            <Outlet />
          </LayoutContent>
        }
      />
    </AstryxAppShell>
  )
}
