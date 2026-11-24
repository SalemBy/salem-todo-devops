import type { AnchorHTMLAttributes, Ref } from 'react'
import { Link } from 'react-router'

type Props = AnchorHTMLAttributes<HTMLAnchorElement> & { ref?: Ref<HTMLAnchorElement> }

/**
 * The link every Astryx component renders, through LinkProvider: in-app paths go through React
 * Router, so following one never reloads the page (which would drop the CSRF token and the cache).
 */
export function RouterLink({ href = '', ref, children, ...rest }: Props) {
  if (/^(https?:|mailto:|tel:|#)/.test(href))
    return (
      <a ref={ref} href={href} {...rest}>
        {children}
      </a>
    )
  return (
    <Link ref={ref} to={href} {...rest}>
      {children}
    </Link>
  )
}
