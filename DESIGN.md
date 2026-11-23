---
name: Salem Todo DevOps
description: A calm engineering-notebook work surface for projects, tasks and labels.
colors:
  paper: "#fbfcfb"
  grid-line: "#e9eef0"
  surface: "#ffffff"
  surface-sunken: "#f2f4f3"
  ink: "#22262b"
  ink-muted: "#586470"
  rule: "#d5dce0"
  rule-strong: "#7b8894"
  rail: "#21324a"
  rail-active: "#2e4467"
  rail-ink: "#d3dcea"
  rail-ink-muted: "#9fb1cb"
  rail-ink-strong: "#ffffff"
  rail-rule: "#33465f"
  rail-focus: "#8fb8f2"
  accent: "#1f5fbf"
  accent-hover: "#194f9f"
  accent-soft: "#e4edf9"
  accent-ink: "#ffffff"
  danger: "#b3261e"
  danger-hover: "#93201a"
  danger-soft: "#fbeae8"
  danger-ink: "#ffffff"
  success: "#2a6630"
  success-soft: "#e7f1e7"
  label-1-bg: "#e4edf9"
  label-1-fg: "#1d4f8f"
  label-2-bg: "#e6f0e3"
  label-2-fg: "#2b5c22"
  label-3-bg: "#f6ecd9"
  label-3-fg: "#77470a"
  label-4-bg: "#ece8f6"
  label-4-fg: "#4f3d8a"
  label-5-bg: "#ddefed"
  label-5-fg: "#1b5955"
  label-6-bg: "#f6e5ea"
  label-6-fg: "#882b46"
  label-7-bg: "#e8ebee"
  label-7-fg: "#3a4550"
  label-8-bg: "#eeefdb"
  label-8-fg: "#55571a"
typography:
  headline:
    fontFamily: "Public Sans Variable, Public Sans, system-ui, sans-serif"
    fontSize: "1.375rem"
    fontWeight: 650
    lineHeight: "2rem"
  title:
    fontFamily: "Public Sans Variable, Public Sans, system-ui, sans-serif"
    fontSize: "1rem"
    fontWeight: 650
    lineHeight: "1.5rem"
  body:
    fontFamily: "Public Sans Variable, Public Sans, system-ui, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 400
    lineHeight: "1.25rem"
  label:
    fontFamily: "Public Sans Variable, Public Sans, system-ui, sans-serif"
    fontSize: "0.8125rem"
    fontWeight: 500
    lineHeight: "1.25rem"
  badge:
    fontFamily: "Public Sans Variable, Public Sans, system-ui, sans-serif"
    fontSize: "0.6875rem"
    fontWeight: 650
    letterSpacing: "0.08em"
  mono-meta:
    fontFamily: "Martian Mono, ui-monospace, Cascadia Mono, monospace"
    fontSize: "0.6875rem"
    fontWeight: 400
rounded:
  sm: "2px"
  round: "999px"
spacing:
  space-1: "4px"
  space-2: "8px"
  space-3: "12px"
  space-4: "16px"
  space-5: "20px"
  space-6: "24px"
  space-8: "32px"
  space-10: "40px"
  grid: "20px"
  control-height: "32px"
  header-height: "60px"
  rail-width: "240px"
components:
  button-primary:
    backgroundColor: "{colors.accent}"
    textColor: "{colors.accent-ink}"
    typography: "{typography.label}"
    rounded: "{rounded.sm}"
    padding: "0 12px"
    height: "32px"
  button-primary-hover:
    backgroundColor: "{colors.accent-hover}"
  button-secondary:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    typography: "{typography.label}"
    rounded: "{rounded.sm}"
    padding: "0 12px"
    height: "32px"
  button-secondary-hover:
    backgroundColor: "{colors.surface-sunken}"
  button-danger:
    backgroundColor: "{colors.danger}"
    textColor: "{colors.danger-ink}"
    typography: "{typography.label}"
    rounded: "{rounded.sm}"
    padding: "0 12px"
    height: "32px"
  button-danger-hover:
    backgroundColor: "{colors.danger-hover}"
  button-ghost:
    textColor: "{colors.ink}"
    typography: "{typography.label}"
    rounded: "{rounded.sm}"
    padding: "0 12px"
    height: "32px"
  button-ghost-hover:
    backgroundColor: "{colors.surface-sunken}"
  text-field:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.sm}"
    padding: "0 12px"
    height: "32px"
  badge-neutral:
    backgroundColor: "{colors.surface-sunken}"
    textColor: "{colors.ink-muted}"
    typography: "{typography.badge}"
    rounded: "{rounded.sm}"
    padding: "0 8px"
    height: "20px"
  badge-danger:
    backgroundColor: "{colors.danger-soft}"
    textColor: "{colors.danger}"
    typography: "{typography.badge}"
    rounded: "{rounded.sm}"
    padding: "0 8px"
    height: "20px"
  badge-success:
    backgroundColor: "{colors.success-soft}"
    textColor: "{colors.success}"
    typography: "{typography.badge}"
    rounded: "{rounded.sm}"
    padding: "0 8px"
    height: "20px"
  label-chip:
    backgroundColor: "{colors.label-1-bg}"
    textColor: "{colors.label-1-fg}"
    typography: "{typography.label}"
    rounded: "{rounded.sm}"
    padding: "0 8px"
    height: "20px"
  menu-item-highlighted:
    backgroundColor: "{colors.accent-soft}"
    textColor: "{colors.ink}"
    rounded: "{rounded.sm}"
    padding: "0 8px"
    height: "32px"
  rail:
    backgroundColor: "{colors.rail}"
    textColor: "{colors.rail-ink}"
    width: "240px"
  rail-link-active:
    backgroundColor: "{colors.rail-active}"
    textColor: "{colors.rail-ink-strong}"
    rounded: "{rounded.sm}"
    height: "32px"
  avatar:
    backgroundColor: "{colors.rail}"
    textColor: "{colors.rail-ink-strong}"
    rounded: "{rounded.round}"
    size: "32px"
---

# Design System: Salem Todo DevOps

## Overview

**Creative North Star: "The Engineering Notebook"**

A calm engineering-notebook page. Everything snaps to one quiet grid, is written in graphite, and carries one blue pen for what you can act on. The page is the working surface of a developer who manages projects and tasks every day next to GitHub and an editor: dense enough to scan many tasks fast, quiet enough to live in all day. It is Operate mode: standard controls, plain words, nothing playful.

The world refuses the category default of grey trays, pill chips and soft-shadow cards. Resting surfaces are flat and separated by hairline rules; corners are nearly square; shadows exist only for things that float. Colour has four jobs and no others: the blue pen for actions, selection and focus; red for critical, overdue and destructive; muted label chips from a fixed palette; and a mono face for data such as #ids, keys and dates.

Light is the notebook by day: near-white paper, graphite ink, an ink-navy rail. Dark, chosen by the system setting, is the same notebook at night: a deep ink ground, a slate grid and chalk-grey text. Every token has both values in `web/src/styles/tokens.css`, which is the single source of every colour, size and duration; components never use literals.

**Key Characteristics:**
- Ink-navy project rail (240px) beside a paper work area.
- A barely visible 20px paper grid, on big work surfaces only.
- Graphite text, hairline rules, 2px corners, no shadows at rest.
- One blue pen; red only for what is critical or destructive.
- Martian Mono for data, Public Sans for everything people read as words.
- Light and dark follow the system setting; every text pair is contrast-tested.

## Colors

A cool graphite-and-paper neutral set, one ink-navy rail, one blue pen, one red, one green, and eight muted label inks.

### Primary
- **Blue Pen** (`accent`): primary buttons, links, the caret, selected and highlighted items, and the checkmark in a select. `accent-hover` darkens it for hover; `accent-soft` is the tint behind selection and highlighted menu items; `accent-ink` is text on it.
- **Focus Blue** (`--focus`, same value as the accent in both themes): the 2px focus outline, offset 2px (offset 0 on text inputs, which also take the focus colour on their border).

### Secondary
- **Ink Navy** (`rail`): the project rail, and the account avatar disc by day. `rail-active` marks the current and hovered rail link; `rail-ink`, `rail-ink-muted` and `rail-ink-strong` are its three text weights; `rail-rule` its edge; `rail-focus` a lighter blue focus ring that stays visible on navy.

### Tertiary
- **Signal Red** (`danger`): critical and overdue badges, destructive buttons and menu items, field errors and error panels. `danger-soft` is its quiet background, `danger-hover` its pressed state, `danger-ink` text on solid red.
- **Ledger Green** (`success`): completed badges and success toasts, with `success-soft` behind it.
- **Label Inks** (`label-1` to `label-8`, each a `-bg` and `-fg` pair): blue, green, amber, violet, teal, rose, slate and olive, all muted. Used only for label chips.

### Neutral
- **Notebook Paper** (`paper`): the page ground and the header.
- **Grid Line** (`grid-line`): the 20px paper grid, drawn on the work area only.
- **Sheet White** (`surface`): controls, menus, dialogs and toasts.
- **Sunken Sheet** (`surface-sunken`): hover for secondary and ghost controls, neutral badge fill.
- **Graphite** (`ink`): all body text and titles.
- **Soft Graphite** (`ink-muted`): secondary text, placeholders, hints, the crumb, icon buttons at rest.
- **Hairline** (`rule`): dividers, overlay borders, the header rule.
- **Strong Rule** (`rule-strong`): control borders (inputs, selects, secondary buttons, toasts); meets 3:1 against paper and surface.

Dark values for every token live under `@media (prefers-color-scheme: dark)` in `tokens.css` and in the sidecar.

### Named Rules
**The One Blue Pen Rule.** Blue (`accent`) is only for actions, selection and focus. There is no blue badge tone; roles render as neutral badges. If it is blue, you can act on it.

**The Red Means Trouble Rule.** Red (`danger`) is only for critical, overdue and destructive actions, and for errors. Archived is a calm neutral state, never red.

**The Tested Pair Rule.** WCAG 2.1 AA pairs are unit-tested in both themes in `web/src/styles/tokens.test.ts` (4.5:1 for text, 3:1 for UI boundaries and focus). Any new text or background pair must be added there before it ships.

## Typography

**Body Font:** Public Sans Variable (with Public Sans, system-ui, sans-serif), self-hosted via Fontsource.
**Label/Mono Font:** Martian Mono 400 and 500 (with ui-monospace, Cascadia Mono, monospace), self-hosted via Fontsource.

**Character:** A plain, civic grotesque for words, and a wide engineering mono for the data a developer reads by shape: numbers, keys, dates. Two sizes carry lists; rank comes from weight, case and rules rather than from a large type scale.

### Hierarchy
- **Headline** (650, 22px, 32px line): the page title (h1) in the shell header, single line with ellipsis.
- **Title** (650, 16px, 24px line): dialog titles and empty-state titles.
- **Body** (400, 14px, 20px line): body text, list rows, inputs. Prose blocks cap at 65ch.
- **Label** (500, 13px): buttons, field labels, select and menu items, label chips (regular weight), secondary text.
- **Badge** (650, 11px, 0.08em tracking, uppercase): status, type, priority and role badges.
- **Mono meta** (Martian Mono 400, 11px): #ids, project keys, the header crumb, dates.

The 20px line height (`--leading`) is the grid unit.

### Named Rules
**The Mono Is Data Rule.** Martian Mono is only for #ids, project keys, dates and other data. Never for names, titles or avatar initials.

**The Two Sizes Rule.** Lists are set in 14px and 13px. Rank comes from weight (400, 500, 650), uppercase badges and hairline rules, not from new sizes.

## Layout

The signed-in shell is a two-column grid: a 240px ink-navy rail on the left (sticky, full height), and on the right a 60px header over the work area. The header sits on paper with a bottom hairline and holds an optional mono crumb plus the h1 on the left, the account name and avatar on the right.

The work area draws the 20px paper grid (two 1px `grid-line` gradients) and pads 40px on every side, two grid cells, so content starts on a grid line. The header uses the same 40px side padding, so the title lines up with the page.

Spacing runs on a 4px scale (4, 8, 12, 16, 20, 24, 32, 40px). Controls are 32px tall; badges and label chips are 20px, one grid cell.

Each route declares `handle: { title, crumb? }`. The shell renders the crumb and h1 from the deepest titled route and sets the document title to "Title · Salem Todo DevOps".

At 40rem and below the rail stacks above the header as a wrapping bar, header padding drops to 16px and the work area to 20px by 16px, so nothing scrolls sideways at 400% zoom. A full phone layout is a later step.

### Named Rules
**The Paper Grid Rule.** The 20px grid shows only on big work surfaces (the shell's work area). Controls, overlays, the rail and the header sit on plain colour.

## Elevation & Depth

Flat by default. Resting surfaces (header, cards, columns, panels) are separated by hairline rules, never by shadow or tonal trays. Depth appears only on things that float above the page.

### Shadow Vocabulary
- **Overlay** (`--shadow-overlay`, light: `0 8px 24px rgb(16 24 40 / 0.14), 0 2px 6px rgb(16 24 40 / 0.08)`; dark: `0 8px 24px rgb(0 0 0 / 0.45), 0 2px 6px rgb(0 0 0 / 0.3)`): menus, select lists, dialogs and toasts, always paired with a 1px border.
- **Scrim** (`--scrim`, `rgb(16 24 40 / 0.4)`, dark `rgb(0 0 0 / 0.55)`): behind a modal dialog.

### Named Rules
**The Floating Only Rule.** Shadows appear only on overlays: menus, selects, dialogs, toasts. Nothing at rest casts a shadow.

## Shapes

Nearly square. Every control, card, badge, chip, menu, dialog and toast uses a 2px radius. The only round shapes are the avatar disc and the button spinner. Borders are 1px: `rule` for dividers and overlays, `rule-strong` for control outlines.

### Named Rules
**The Square Corner Rule.** Corners are 2px. No pills: chips and badges are small square-cornered tags.

## Components

### Buttons
Standard and firm: a clear filled action, a ruled alternative, nothing decorative.
- **Shape:** 2px corners, 32px tall, 12px side padding, 13px medium label, optional 14 to 16px icon with 8px gap.
- **Primary:** blue pen fill, white text; darker blue on hover.
- **Secondary:** sheet white with a strong rule border and graphite text; sunken sheet on hover.
- **Danger:** solid red for destructive confirmation only; darker red on hover.
- **Ghost:** transparent; sunken sheet on hover.
- **States:** 120ms background and border transitions; disabled at 55% opacity; loading shows a small spinning ring and sets `aria-busy`.

### Inputs / Fields
- **Style:** sheet white, 1px strong rule, 2px corners, 32px tall, 14px text; label above in 13px medium, hint below in muted 13px.
- **Focus:** 2px focus-blue outline with 0 offset, border turns focus blue.
- **Error / Disabled:** invalid fields take a red border and a red medium error line; disabled fields go transparent with a hairline border and muted text.
- **Select:** same trigger shape (13px text, chevron in muted ink); the list floats as an overlay matching the trigger width.

### Menus and Select Lists
- **Overlay:** sheet white, hairline border, 4px inner padding, overlay shadow.
- **Items:** 32px tall, 13px text, 2px corners.
- **Highlighted:** accent-soft tint plus a full 1px inset ring in the accent, so the highlight reaches 3:1 against the menu. Destructive items are red, and highlight with danger-soft and a red ring.

### Badges
Small uppercase stamps for status, type, priority and role. 20px tall, 11px bold tracked caps, 2px corners. Three tones only: **neutral** (sunken sheet, hairline border, muted ink) for roles and calm states such as archived; **danger** for critical and overdue; **success** for completed. The text always carries the meaning; colour supports it.

### Label Chips
20px square-cornered tags in 13px regular text, max 12rem with ellipsis. Each label takes one of the eight label-ink pairs, chosen by an FNV-1a hash of its trimmed, lowercased name, so a label keeps its colour everywhere and case never changes it.

### Dialog
Centred sheet, max 32rem wide, 2px corners, hairline border, overlay shadow over a scrim. Header (16px title, ghost close button) is ruled off from the body; actions align right in the footer. Enters with a 200ms fade and 8px rise.

### Toast
Bottom-right stack, max 24rem. Sheet white with a strong rule border and overlay shadow; success and danger toasts colour the border and title. Slides in 8px over 200ms; swipe to dismiss.

### Error Message and Empty State
Errors are written plainly next to where they happen: a danger-soft panel with a red border and red alert icon, graphite text, and per-field problems listed below. Empty states are left-aligned, max 28rem: a 16px title, muted body, and one action.

### Navigation (Project Rail)
Ink-navy, 240px, sticky full height, with the product name in bold strong rail ink at the top. Links are 32px rows with a 16px icon; hover and the current page (`aria-current`) take `rail-active` and strong rail ink, the current page also medium weight. Focus uses the lighter rail-focus blue.

### Avatar
A round disc with initials in bold sans (never mono), 22px or 32px. Rail navy by day; at night it lifts to `rail-active` with a `rule-strong` edge so it does not vanish into the ground.

### Icons
lucide-react only, 14 to 16px, `aria-hidden` next to text.

### Motion
Short and quiet: 120ms for control state changes, 200ms for overlays, all on `cubic-bezier(0.16, 1, 0.3, 1)`. Reduced motion collapses every animation and transition.

## Do's and Don'ts

### Do:
- **Do** take every colour, size and duration from a token in `tokens.css`, and define its dark value too.
- **Do** keep blue for actions, selection and focus only, and red for critical, overdue, errors and destructive actions only.
- **Do** add every new text or background pair to `web/src/styles/tokens.test.ts`.
- **Do** set #ids, project keys and dates in Martian Mono 11px; format dates as "20 Nov 2026", or "20 Nov" in the current year, and read due dates as calendar days.
- **Do** pad work surfaces 40px so content starts on a 20px grid line.
- **Do** give highlighted menu and select items the full 1px inset ring as well as the tint.
- **Do** declare `handle: { title, crumb? }` on every route.

### Don't:
- **Don't** add a blue badge tone or render roles in colour; roles are neutral badges.
- **Don't** show archived in red; archived is neutral.
- **Don't** use mono for names, titles or initials.
- **Don't** put a shadow on a resting surface, or use grey trays to separate content; use hairline rules.
- **Don't** round corners past 2px or make pill chips; only the avatar is round.
- **Don't** draw the paper grid on controls, overlays, the rail or the header.
- **Don't** use icon sets other than lucide-react, or icon glyphs in place of SVG icons.
