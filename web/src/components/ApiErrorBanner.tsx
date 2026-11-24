import { Banner } from '@astryxdesign/core/Banner'
import { List, ListItem } from '@astryxdesign/core/List'
import type { ApiError } from '../api/client'

/**
 * An error next to where it happened. role="alert" announces it as it appears; Banner applies the
 * role to its title and description, so the field errors go in the description to be read too.
 */
export function ApiErrorBanner({ error }: { error: ApiError | string }) {
  const detail = typeof error === 'string' ? error : error.detail
  const fields = typeof error === 'string' ? [] : error.errors
  return (
    <Banner
      role="alert"
      status="error"
      title={detail}
      collapsible={false}
      description={
        fields.length > 0 ? (
          <List density="compact">
            {fields.map((field) => (
              <ListItem key={`${field.field}:${field.message}`} label={field.message} />
            ))}
          </List>
        ) : undefined
      }
    />
  )
}
