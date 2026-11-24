# Design System: Salem Todo DevOps

## Overview

The web UI is built on **Astryx** (`@astryxdesign/core`) with its **Neutral** theme: restrained warm greys, minimal and quiet, so the work stays the focus. Light and dark follow the system setting.

- **Astryx's own conventions apply.** They live in `web/AGENTS.md`, written and kept current by `npx astryx init`. In short:
  - start pages from Astryx templates;
  - read a component's props with `npx astryx component <Name>`;
  - lay out with Astryx components (no raw `<div>`);
  - style with props, then Astryx tokens (`--color-*`, `--spacing-*`, `--radius-*`), never raw hex or px;
  - run `npx astryx doctor` before delivering.
- **The theme is wired once** at the app root (`web/src/main.tsx`) from the generated `web/src/astryx-themes.ts`. To customise it, extend it with `defineTheme({ extends: neutralTheme, … })`. Forking it with `theme eject` happens only when the owner asks for an independent copy.

The rules below are this product's own, on top of Astryx.

## Our rules

- **Links route inside the app.** Every Astryx link goes through React Router (`LinkProvider` with `web/src/shell/RouterLink.tsx`). A full page load would drop the in-memory CSRF token and the cached data.
- **Errors sit next to where they happen.** `ApiErrorBanner` shows an API error in an Astryx `Banner` with `role="alert"`, with its field errors in the description so they are announced too.
- **Label colours.** Labels have no colour in the API. `labelVariant(name)` gives each label a stable Astryx Badge colour variant from a hash of its trimmed lower-case name. Red is never used, so a label never reads as an error.
- **Dates** read "20 Nov 2026", or "20 Nov" in the current year (`web/src/format/date.ts`). Due dates are calendar days.
- **Pages name themselves:** routes declare `handle: { title, crumb? }`, and the shell shows the title and sets the window title.
- **Accessibility:**
  - axe (WCAG 2.1 A/AA) runs in Playwright on every page, in both themes;
  - the shell reflows at 320 CSS px;
  - keyboard and screen-reader behaviour is unit-tested.
- **Page colour:** outside the app shell, the page takes the theme's body colour (`web/src/styles/tokens.css`).

## Salem

**The Margins Rule.** Sober while working, personality in the margins.
- **Quiet mark only:** the board, the task page, filters, forms, confirmations, and any error that blocks the person's work. When something goes wrong, the cat stays out of the way.
- **The drawn cat, with a light touch of humour in the words:** sign-in pages, empty states, page not found, the expired-session page, small rewards (such as clearing a column) and loading moments that take a while.

**Character.** Salem is a happy, playful black cat that plays whenever it can and sleeps a lot. The words next to it stay short and warm, with at most one light joke ("Salem is napping until there is work to do.").

**The mark** (`CatMark`, `web/public/favicon.svg`):
- **Look:** a black cat's head with amber eyes, on a 24×24 grid.
- **Where:** it is the tab icon and heads the side nav next to the product name.
- **Accessibility:** standing alone it is an image named "Salem Todo DevOps"; next to the written name it is hidden from screen readers.

**Poses** (`SalemCat`):
- **Only poses the owner has approved ship.** Today that is **asleep**, for empty and quiet places. Astryx `EmptyState` takes it in its `icon` slot.
- **Decorative only:** poses are always hidden from screen readers and never carry information the words do not.
- **New poses** use the same head (`CatHead`), are shown to the owner in both themes, and ship only once approved.

**Colour** (`web/src/styles/tokens.css`, declared on the element that carries `data-astryx-theme`):
- `--cat-body` (black) and `--cat-eye` (amber) are brand colours, as `light-dark()` pairs.
- `--cat-line` is the theme's `--color-text-primary`, so the cat is solid ink by day and keeps a light outline at night.
- A test checks the outline against the theme's backgrounds and keeps the favicon equal to these values.

**Never:**
- anything that reads as a known cat character (the black cat from *Sabrina the Teenage Witch*, the cat from *Kiki's Delivery Service*);
- cartoon gloss, gradients, emoji or clip-art;
- the cat next to a blocking error or inside a confirmation;
- animation loops.
