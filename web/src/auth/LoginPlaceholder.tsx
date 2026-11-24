import { Heading } from '@astryxdesign/core/Heading'
import { Stack } from '@astryxdesign/core/Stack'
import { Text } from '@astryxdesign/core/Text'
import { usePageTitle } from '../shell/usePageTitle'

/** Stands in for the login page until the sign-in pages are rebuilt on Astryx (#76). */
export function LoginPlaceholder() {
  usePageTitle('Log in')
  return (
    <Stack as="main" padding={10} gap={2} maxWidth={480}>
      <Heading level={1}>Log in</Heading>
      <Text color="secondary">You are not logged in, or your session has ended.</Text>
    </Stack>
  )
}
