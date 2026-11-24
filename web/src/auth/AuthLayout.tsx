import { Card } from '@astryxdesign/core/Card'
import { Center } from '@astryxdesign/core/Center'
import { Grid } from '@astryxdesign/core/Grid'
import { Heading } from '@astryxdesign/core/Heading'
import { HStack, VStack } from '@astryxdesign/core/Layout'
import { Text } from '@astryxdesign/core/Text'
import { useCallback, type ReactNode } from 'react'
import { CatMark } from '../brand/CatMark'
import { SalemCat } from '../brand/SalemCat'
import { usePageTitle } from '../shell/usePageTitle'

// From the Astryx login-split template. A column never gets narrower than this, so the two halves
// collapse to one below about 512px of card width: 320px − page and card padding = 240px.
const COLUMN_MIN_WIDTH = 240

// The template's container query, kept in a plain style tag as the template ships it. Unlike the
// template, the cat stays after the form when the halves stack, so the form is first on a phone.
const SPLIT_CSS = `
.auth-split { container-type: inline-size; container-name: auth-split; padding: var(--spacing-8); }
@container auth-split (max-width: 511px) { .auth-split { padding: var(--spacing-4); } }
`

/**
 * The sign-in surface, from Astryx's login-split template on the Neutral theme: one card, with Salem's
 * mark, the product name and the page on one half, and Salem asleep on the other. `footer` (such as
 * "No account? Register") ends the form half.
 */
export function AuthLayout({
  title,
  screen = title,
  children,
  footer,
}: {
  title: string
  /** Names what the form half shows, when it changes under the same title (a refused link). */
  screen?: string
  children: ReactNode
  footer?: ReactNode
}) {
  usePageTitle(title)
  // Keyed by its screen (the title unless named): when the page changes in place (registered, link
  // refused), the form half remounts and takes focus, so keyboard and screen-reader users start from
  // the new content. It is not a control, so it draws no focus ring.
  const focusOnMount = useCallback(
    (half: HTMLElement | null) => half?.focus({ preventScroll: true }),
    [],
  )
  return (
    <VStack as="main" hAlign="center" vAlign="center" padding={6} minHeight="100dvh">
      <style>{SPLIT_CSS}</style>
      <Card padding={0} width="100%" maxWidth={960}>
        <Grid
          columns={{ minWidth: COLUMN_MIN_WIDTH, repeat: 'fit' }}
          gap={8}
          align="stretch"
          className="auth-split"
        >
          <VStack key={screen} ref={focusOnMount} tabIndex={-1} gap={6} style={{ outline: 'none' }}>
            <HStack gap={2} vAlign="center">
              <CatMark size={24} />
              <Text weight="bold">Salem Todo DevOps</Text>
            </HStack>
            <VStack gap={4}>
              <Heading level={1}>{title}</Heading>
              {children}
            </VStack>
            {footer}
          </VStack>
          <Card variant="muted" padding={8}>
            <Center axis="both" height="100%">
              <SalemCat pose="asleep" size={200} />
            </Center>
          </Card>
        </Grid>
      </Card>
    </VStack>
  )
}
