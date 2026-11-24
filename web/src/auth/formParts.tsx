import { Banner } from '@astryxdesign/core/Banner'

/** A notice for the person, announced politely. */
export function Notice({ text }: { text: string }) {
  // oxlint-disable-next-line jsx-a11y/prefer-tag-over-role -- Banner's own role prop: Astryx puts it on the banner, there is no output element to use
  return <Banner role="status" status="success" title={text} collapsible={false} />
}
