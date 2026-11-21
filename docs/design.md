# Design

Nod is calm, precise and quiet until it needs you. Tokens live in
[`design/tokens.json`](../design/tokens.json) (v3). Android (`ui/theme`) and desktop
(`src/lib/styles`) mirror them, so change all three together. Every UI change must also pass
[design-anti-patterns.md](design-anti-patterns.md).

## Precedence

1. `design/tokens.json` (v4): exact values taken from the style reference (Pushary).
2. This document.
3. [design-anti-patterns.md](design-anti-patterns.md): applies wherever 1 and 2 don't say otherwise.

## Type system

| Role                            | Spec                                                                                                |
| ------------------------------- | --------------------------------------------------------------------------------------------------- |
| H1                              | Geist 40/48, weight 400, line-height 1.1, tracking −0.022em                                         |
| H2 (screen titles, "2 waiting") | Geist 28/42, **weight 300**, line-height 1.1, tracking −0.022em                                     |
| H3 (panel titles)               | Geist 20/24, weight 300, tracking −0.01em                                                           |
| Heading (card titles)           | Geist 16, weight 500                                                                                |
| Body                            | Geist 15, line-height 1.6, `mutedForeground`                                                        |
| Eyebrow                         | Geist Mono 11, weight 500, UPPERCASE, tracking 0.2em, `primary`, after a 24×1 px rule in primary/30 |
| Stat number                     | **Geist Pixel Square** 28, tabular (pending count, approvals today, time saved)                     |
| Code, commands, times           | Geist Mono 12–13                                                                                    |

## Components

- **Card**: `card` background, radius 14, no border, `surfaceSm` shadow (inset 1 px highlight plus
  a hairline shadow). Elevated panels use `surface`. A waiting request gets a 1 px amber/50 outline.
- **Primary button**: `buttonPrimary` `#007273`, white text 500, radius 8, a drop shadow plus an
  inset teal glow (`shadow.buttonPrimary`); on hover the glow brightens; when pressed it gets
  an inset dark shadow. Transition 300 ms `cubic-bezier(0.165,0.84,0.44,1)`. Optional trailing arrow.
- **Secondary button**: `card` background, 1 px `rule` border, foreground text, a tiny shadow, hover `accent`.
- **Badge**: radius 8 (not a pill), 6×2 padding, 12/500. Approved = primary on primary/10,
  Denied = danger on destructive/10, Waiting = amber dot + amber text, Live = pulsing 6 px primary dot.
- **Ledger row**: mono command (12) · badge · channel (Phone / Desktop) · relative time, separated by `rule` hairlines.
- **Stat row**: equal cells separated by hairlines, a pixel-font number above a muted 12 px caption.
- **Segmented control**: h 32, radius 10, 12.5/500; active = card + `surfaceSm`, inactive muted text.
- **Texture**: Nod's own procedural halftone/stipple band (dots), used sparingly to frame one
  panel (e.g. the empty state). Pushary's artwork is never used.

## Surfaces

### Windows: the island

A black, notch-like surface attached to the top centre of the primary monitor
(references: `design/style_references/pushary/user/01–05`).

| State     | Content                                                                                                                                                                                                                                                                                                |
| --------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Collapsed | Agent mark + "Claude Code" (or project) on the left; status dot + "Needs you" / "Approved" / "Done" on the right. Height 36, width ~560.                                                                                                                                                               |
| Request   | Header: mark, "Claude Code · project", summary line in muted text, status pill "Needs you". Question in title size. Detail lines. "Show details" disclosure. Buttons: Deny (secondary) and Approve (primary teal) with shortcut hints (Ctrl+Shift+N / Ctrl+Shift+Y). Text button "Deny with a reason". |
| Question  | Same header; options as a vertical list of rows (label + description), single- or multi-select; Send.                                                                                                                                                                                                  |
| Resolved  | Collapses back to the pill with a check icon + "Approved" for 1.5 s, then hides.                                                                                                                                                                                                                       |
| Overview  | Clicking the collapsed island opens a list of sessions (mark, project, last action, relative time, state dot) plus the routing mode as a segmented control: When I'm away · Every time · Updates only · Terminal. One sentence of explanation underneath.                                              |

### Android

- **Notification**: small icon = monochrome mark, colour = primary, title "Claude Code is
  asking", text = "Allow Bash: `npm test`?", sub-text = project. Actions: Approve (unlock
  required), Deny, and "Deny with note" (RemoteInput). Native countdown chronometer until it
  expires. The lock screen shows the public version only ("Claude Code needs approval · project").
- **App**: an inbox with the pending count as a display headline ("2 waiting" / "Nothing waiting"),
  one card per request, then history grouped by day with mono times and status pills.
  The detail screen shows the full command or diff in a code block, with Approve and Deny pinned
  to the bottom.

## Motion

| Moment          | Animation                                                                                       |
| --------------- | ----------------------------------------------------------------------------------------------- |
| Request arrives | Island scales/clips from pill to card, 320 ms, critically damped; content fades in at 120 ms    |
| Decision        | Buttons disable, the island collapses to "Approved ✓" (icon draws in 240 ms), hides after 1.5 s |
| Task complete   | The collapsed island shows "Done" with a check that draws (520 ms); no confetti, no glow        |
| Countdown       | Mono `m:ss`; turns amber in the last 30 s. No progress-bar animation loop                       |

## Logo

A pixel-grid mark (11×11 rounded squares, like Geist Pixel Square): a round head above an arc,
the moment of a nod. It is generated by [`design/scripts/generate-logo.py`](../design/scripts/generate-logo.py):

- [`design/logo.svg`](../design/logo.svg): app icon. White tile, teal `#007273` mark at about 50 % of the tile.
- [`design/mark.svg`](../design/mark.svg): the teal mark alone, for light surfaces.
- [`design/mark-mono.svg`](../design/mark-mono.svg): `currentColor`, for notification icons, the tray and the island
  (white on dark, teal on light).

## Style references

Private screenshots and notes live in `design/style_references/` (git-ignored, not
redistributed): `pushary/pages`, `pushary/slices`, `pushary/user` and `pushary/NOTES.md`.
