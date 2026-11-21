# Design anti-patterns: how Nod avoids looking AI-generated

> **Precedence:** the style reference (Pushary, captured in `design/tokens.json` v4 and
> `docs/design.md`) wins over this checklist. Mono uppercase eyebrows with a leading rule, light
> display headlines, the Geist Pixel stat numbers, layered `surface` shadows, the primary-button
> inner glow and the pulsing "Live" dot are deliberate parts of that reference and are allowed.
> Everything else here still applies.

Language models and their starter templates converge on the same defaults. Users have learned
to read those defaults as "made by AI". This checklist names the tells and what Nod does
instead. Review every UI change against it.

Sources: Anthropic, _Improving frontend design through Skills_; pixelslop _AI slop pattern
catalog_; ux-skill _Anti AI slop ban list_; 925studios _AI slop design tells_;
capitalandcompute _How to fix AI slop, tell by tell_. Links are at the end.

## The Claude/LLM defaults to avoid

| Default                                                                    | Why it reads as AI                                                                   |
| -------------------------------------------------------------------------- | ------------------------------------------------------------------------------------ |
| Inter / Roboto / system font as the only face; Space Grotesk as "edgy"     | Every template uses them; Anthropic names Inter and Space Grotesk as Claude defaults |
| Purple→blue gradients (`from-violet-500 to-indigo-600`, `#667eea→#764ba2`) | Tailwind's palette plus "safe" choices                                               |
| Cream background + terracotta accent (`#F4F1EA` / `#D97757`)               | Anthropic's own palette; Claude reaches for it once purple is banned                 |
| Neon/acid accent (cyan, lime, pink) on near-black                          | "Dev tool" cliché. **Nod v2 did exactly this** and was dropped                       |
| Glassmorphism, glow halos, gradient text                                   | Effects used instead of hierarchy                                                    |
| Centred hero, pill badge, three identical icon cards                       | The stock landing-page skeleton                                                      |
| Fade-up on every element, bouncy/overshoot easing, pulsing dots            | Motion without meaning                                                               |
| Mono uppercase eyebrow above every heading, `01/02/03` on everything       | Decoration posing as structure                                                       |
| Emoji as icons, mixed icon styles, an icon in a tinted circle per card     | No icon system                                                                       |
| "Unlock / elevate / seamless / effortless", Title Case buttons             | Filler copy                                                                          |

## Rules for Nod

### Typography

- Geist for UI, Geist Mono **only** for commands, paths, diffs and times. Both are bundled, never
  loaded from a CDN.
- Clear weight ladder: display 400 (large, tight tracking), headline 500, title 600, body 400.
  No light 100–300 weights for UI text.
- Sentence case everywhere: "Deny with a reason", not "Deny With A Reason".
- Left-aligned text. Centre only single-line status ("Approved").
- No all-caps labels except very short status abbreviations. No eyebrow labels above headings.

### Colour

- One brand colour: **teal** (`primary`). Other colours appear only as status: green = approved
  or done, amber = waiting for you, red = denied or failed.
- Surfaces are tinted (teal-black `#010707`, mint-white `#F1F6F6`), never `#000`/`#FFF`. The one
  exception is the desktop island, which is pure black like a hardware notch.
- Secondary text uses the tinted `textMuted`, not neutral grey. Body text meets WCAG AA (4.5:1).
- No gradients on text, buttons or backgrounds. No glow.

### Layout & components

- One card per request. No cards inside cards, and no grid of identical feature cards.
- Radii by role: pill 999 (status only), island 22, card 14, button 10, input/code 6.
- Exactly one primary button per surface (Approve). Deny is secondary, and it must look different
  in weight, not just in colour. "Deny with a reason" is a text button.
- Pills only for status (Waiting, Approved, Denied, Expired), never as decoration.
- Every interactive element has hover (desktop), pressed, focus-visible and disabled states.
- Loading uses skeletons shaped like the content, not spinners.
- No hover-only affordances; everything also works with keyboard and touch.

### Motion

- One orchestrated moment per event: the island morphing from pill to card, or the completion
  check. Everything else is static or a plain crossfade.
- No overshoot or bounce. Springs are critically damped (damping ratio 1.0).
- Durations: press 120 ms, small transitions 160–240 ms, island 320 ms.
- Animate `transform` and `opacity` only, never width or height (use scale or clip for the island).
- No continuous animation without a state change (no pulsing "pending" dots).
- Respect "reduce motion": then crossfade only.

### Icons

- One icon set (Lucide on desktop, Material Symbols Rounded on Android), one stroke weight,
  icons only where they help recognition (approve ✓, deny ✕, note ✎). No emoji in the UI.

### Copy

- Concrete verbs: Approve, Deny, Deny with a reason, Show details, Open in terminal.
- Say what happened: "Approved on Pixel 7a", "Expired, asked in terminal".
- Never invent numbers or show stats we don't compute.
- No welcome text, no slogans, no subheadings that repeat the heading.
- No placeholder data (Acme, John Doe, lorem ipsum) in shipped UI or screenshots.

### Effects

- Elevation by tone (surface → raised) on dark, and by one subtle shadow on light.
- No glass and no blur behind text that must be read to approve a command.

## Review checklist (PRs touching UI)

- [ ] Only teal plus the status colours are used; no new hex literals outside the theme files
- [ ] Mono font only on code, paths and times
- [ ] One primary action per surface; secondary visibly different
- [ ] No glow, gradient text, glass, bounce, or pulsing idle animation
- [ ] Sentence case, concrete verbs, no filler words
- [ ] Focus, pressed and disabled states exist
- [ ] Checked in both light and dark, and with reduce motion

## Sources

- <https://claude.com/blog/improving-frontend-design-through-skills>
- <https://cdn.jsdelivr.net/npm/pixelslop@0.3.8/dist/skill/resources/ai-slop-patterns.md>
- <https://github.com/Laith0003/ux-skill/wiki/Anti-AI-slop-ban-list>
- <https://www.925studios.co/blog/ai-slop-design-tells>
- <https://capitalandcompute.net/blog/fix-ai-slop-design/>
