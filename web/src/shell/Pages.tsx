import { Link } from '@astryxdesign/core/Link'
import { Text } from '@astryxdesign/core/Text'

/** The project list arrives with #77; until then the page under the "All projects" header is empty. */
export function AllProjectsPage() {
  return null
}

export function NotFoundPage() {
  return (
    <Text as="p" color="secondary">
      This address does not match any page. <Link href="/">Go to all projects</Link>
    </Text>
  )
}
