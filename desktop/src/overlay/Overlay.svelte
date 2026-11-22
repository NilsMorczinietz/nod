<!--
  The island: a notch-like surface hanging from the top centre of the screen
  (or floating bottom-right, per settings). Proportions follow the style
  references (design/style_references/pushary/user 01–04, 08–11).

  States
  - bar       ~340 × 30. Mark + "Claude Code · project" left; dot + "Needs you" right.
  - request   ~400 wide card with the decision (see RequestCard, variant island).
  - outcome   After an answer: icon + project, check circle + "Approved" (or
              "Denied"); hides after 2 s.
  - finished  Completed tasks: icon + project, check circle + "Finished"; ~4 s.
  - overview  Clicking the bar (or "Show recent activity" in the tray):
              recent sessions and the routing mode.

  Size: CSS px at 100 % are multiplied by the Windows text size and the
  "Notification size" setting (html zoom); Windows display scaling is applied
  by Tauri's logical pixels. Theme: dark (black notch) by default, or light.

  Motion: width and height are revealed with a clip-path tween (320 ms,
  cubic-bezier from tokens.json); content crossfades. The transparent window
  grows before and shrinks after the motion and never takes focus.
-->
<script lang="ts">
  import { onMount } from 'svelte'
  import type { Item } from '@nod/protocol'
  import Glyph from '../components/Glyph.svelte'
  import Mark from '../components/Mark.svelte'
  import RequestCard from '../components/RequestCard.svelte'
  import Segmented from '../components/Segmented.svelte'
  import { api, events } from '../lib/api'
  import { clock } from '../lib/clock.svelte'
  import { markReady } from '../lib/diagnostics'
  import { HubStore } from '../lib/hub.svelte'
  import { MODES } from '../lib/modes'
  import { ISLAND_MOTION, fade } from '../lib/motion'
  import { chime } from '../lib/sound'
  import { agentName, isOpenQuestion, relativeTime, statusOf } from '../lib/status'
  import { applyThemeClass, resolveIsland } from '../lib/theme.svelte'
  import { Tween } from '../lib/tween.svelte'

  // Geometry in CSS px at 100 % (the zoom scales all of it).
  const CARD_WIDTH = 400
  const SIDE_PAD = 24
  const SHADOW_PAD = 32
  const CORNER_GAP = 12
  const RADIUS = 16
  const WINDOW_STEP = 48
  const SHRINK_AFTER_MS = 2000
  const SIZE_FACTOR = { small: 0.85, default: 1, large: 1.15 } as const

  // Timings (ms).
  const OUTCOME_MS = 2000
  const FINISHED_MS = 4000
  const OVERVIEW_LINGER_MS = 1500
  /** An untouched request folds into the "Needs you" bar after this long. */
  const COLLAPSE_AFTER_MS = 8000

  const hub = new HubStore()

  let requests = $state<Item[]>([])
  let collapsed = $state(false)
  let outcome = $state<Item | null>(null)
  let info = $state<Item | null>(null)
  let infoQueue: Item[] = []
  let overview = $state(false)
  let recent = $state<Item[] | null>(null)
  let card: RequestCard | undefined = $state()

  const corner = $derived(hub.prefs.position === 'corner')
  const zoom = $derived(hub.textScale * SIZE_FACTOR[hub.prefs.islandSize])
  const theme = $derived(resolveIsland(hub.prefs.islandTheme, hub.prefs.appTheme))

  $effect(() => {
    document.documentElement.style.zoom = String(zoom)
  })
  $effect(() => applyThemeClass(theme))

  type View =
    | { kind: 'card'; item: Item }
    | { kind: 'bar'; item: Item; glyphMs: number }
    | { kind: 'overview' }
  const view = $derived.by((): View | null => {
    if (overview) return { kind: 'overview' }
    const front = requests[0]
    if (front)
      return collapsed ? { kind: 'bar', item: front, glyphMs: 0 } : { kind: 'card', item: front }
    if (outcome) return { kind: 'bar', item: outcome, glyphMs: 240 }
    if (info) return { kind: 'bar', item: info, glyphMs: 520 }
    return null
  })
  const viewKey = $derived(
    view
      ? view.kind === 'overview'
        ? 'overview'
        : `${view.kind}:${view.item.id}:${view.item.status}`
      : '',
  )

  // ------------------------------------------------------------------ flow

  const timers = new Set<ReturnType<typeof setTimeout>>()
  const later = (ms: number, fn: () => void) => {
    const id = setTimeout(() => {
      timers.delete(id)
      fn()
    }, ms)
    timers.add(id)
    return id
  }

  const upsertRequest = (item: Item) => {
    const index = requests.findIndex((r) => r.id === item.id)
    if (item.status === 'pending') {
      if (index === -1) {
        requests.push(item)
        // Only a new front request opens the island; queued ones just count.
        if (requests.length === 1) expand()
        overview = false
      } else requests[index] = item
      return
    }
    if (index === -1) return
    requests = requests.filter((r) => r.id !== item.id)
    if (requests.length === 0) {
      outcome = item
      later(OUTCOME_MS, () => {
        if (outcome?.id === item.id) outcome = null
        playNextInfo()
      })
    }
  }

  let infoTimer: ReturnType<typeof setTimeout> | undefined
  const playNextInfo = () => {
    if (info || outcome || requests.length > 0) return
    const next = infoQueue.shift()
    if (!next) return
    info = next
    infoTimer = later(FINISHED_MS, endInfo)
  }
  const endInfo = () => {
    info = null
    playNextInfo()
  }

  let collapseTimer: ReturnType<typeof setTimeout> | undefined
  /** Opens the front request; it folds back into the bar if left alone. */
  const expand = (after = COLLAPSE_AFTER_MS) => {
    collapsed = false
    clearTimeout(collapseTimer)
    collapseTimer = later(after, () => {
      if (requests.length > 0) collapsed = true
    })
  }

  // Hovering opens a waiting request and keeps cards, bars and the overview open.
  let overviewTimer: ReturnType<typeof setTimeout> | undefined
  const hover = (inside: boolean) => {
    clearTimeout(infoTimer)
    clearTimeout(overviewTimer)
    clearTimeout(collapseTimer)
    if (inside) {
      if (requests.length > 0) collapsed = false
      return
    }
    if (requests.length > 0) expand(4000)
    if (info) infoTimer = later(1500, endInfo)
    if (overview) overviewTimer = later(OVERVIEW_LINGER_MS, () => (overview = false))
  }

  const onBarClick = () => {
    if (requests.length > 0) expand()
    else void openOverview()
  }

  const closeOverview = () => {
    overview = false
  }

  const onkeydown = (event: KeyboardEvent) => {
    if (event.key === 'Escape' && overview) closeOverview()
  }

  /** `fromTray`: opened without the pointer, so close it if it never gets visited. */
  let overviewLoad = 0
  const openOverview = async (fromTray = false) => {
    overview = true
    clearTimeout(overviewTimer)
    if (fromTray) overviewTimer = later(4000, closeOverview)
    // Only the newest request may fill the list (the tray and the bar can race).
    const load = ++overviewLoad
    recent = null
    let items: Item[] = []
    try {
      items = await api.listItems(40)
    } catch {
      // An empty list says "Nothing yet"; the next open tries again.
    }
    if (load === overviewLoad) recent = items
  }

  /** Latest item per session, newest first. */
  const sessions = $derived.by(() => {
    if (!recent) return null
    const latest = new Map<string, Item>()
    for (const item of recent) if (!latest.has(item.session.id)) latest.set(item.session.id, item)
    return [...latest.values()].slice(0, 5)
  })

  // Global shortcuts exist only while a request is on screen.
  let shortcutsOn = false
  $effect(() => {
    const active = requests.length > 0
    if (active === shortcutsOn) return
    shortcutsOn = active
    void api.setShortcuts(active).catch(() => undefined)
  })

  const onShortcut = (action: 'approve' | 'deny') => {
    const front = requests[0]
    if (!front) return
    if (card && !collapsed) {
      if (action === 'approve') card.approve()
      else card.deny()
    } else if (front.kind === 'question') {
      expand()
    } else {
      void api
        .resolve(front.id, { decision: action === 'approve' ? 'allow' : 'deny' })
        .then((r) => upsertRequest(r.item))
    }
  }

  // ------------------------------------------------------- clip morph

  /** Visible size (tweened) and the element's box (jumps). */
  const visibleW = new Tween(0, ISLAND_MOTION)
  const visibleH = new Tween(0, ISLAND_MOTION)
  let boxW = $state(0)
  let boxH = $state(0)
  let reported = 0
  let showing = false
  /** The last view stays rendered while the island retracts. */
  let leaving = $state<{ view: View; key: string } | null>(null)
  const shown = $derived(view ?? leaving?.view ?? null)
  const shownKey = $derived(view ? viewKey : (leaving?.key ?? ''))

  const report = (h: number) => {
    if (Math.abs(h - reported) < 0.5) return
    reported = h
    const width = Math.ceil((CARD_WIDTH + SIDE_PAD * 2) * zoom)
    void api.overlayLayout(h > 0 ? width : 0, h).catch(() => {
      // The window kept its old frame: forget it so the next report retries.
      if (reported === h) reported = -1
    })
  }
  /** Window height in logical px for an island of `h` CSS px. */
  const windowHeight = (h: number) => Math.ceil(((corner ? CORNER_GAP : 0) + h + SHADOW_PAD) * zoom)

  /**
   * Every window resize makes WebView2 repaint (a visible flash), so the
   * window grows in steps right away and shrinks only once the island has
   * stayed smaller for a while. The extra area is transparent.
   */
  let shrinkTimer: ReturnType<typeof setTimeout> | undefined
  const fit = (h: number) => {
    const needed = Math.ceil(windowHeight(h) / WINDOW_STEP) * WINDOW_STEP
    clearTimeout(shrinkTimer)
    if (needed > reported) report(needed)
    else if (needed < reported) shrinkTimer = setTimeout(() => report(needed), SHRINK_AFTER_MS)
  }

  const target = (w: number, h: number) => {
    // The content of a view on its way out still reports sizes (e.g. the list
    // finishing to load). That must never reopen the island: it would come back
    // with nothing left to close it.
    if (!view) return
    if (!showing) {
      showing = true
      // Appear out of the screen edge: full width, no height yet. A retract
      // still in progress simply turns around instead of jumping to zero.
      if (visibleH.current === 0) visibleW.set(w, { instant: true })
    }
    boxW = Math.max(boxW, w)
    if (h >= boxH) {
      boxH = h
      fit(h)
    }
    visibleW.set(w, { onRest: () => (boxW = w) })
    visibleH.set(h, {
      onRest: () => {
        boxH = h
        fit(h)
      },
    })
  }

  /** Attachment: measures the island content and steers the morph. */
  const measure = (element: HTMLElement) => {
    const observer = new ResizeObserver(() => target(element.offsetWidth, element.offsetHeight))
    observer.observe(element)
    return () => observer.disconnect()
  }

  let last: { view: View; key: string } | null = null
  $effect(() => {
    if (view) {
      last = { view, key: viewKey }
      leaving = null
      return
    }
    if (!showing) return
    showing = false
    leaving = last
    visibleH.set(0, {
      onRest: () => {
        if (view) return
        leaving = null
        boxW = 0
        boxH = 0
        clearTimeout(shrinkTimer)
        report(0)
      },
    })
  })

  // Re-anchor when the position, size or text scale changes.
  let lastGeometry = ''
  $effect(() => {
    const geometry = `${corner}:${zoom}`
    if (lastGeometry && geometry !== lastGeometry && reported > 0) {
      reported = 0
      fit(boxH)
    }
    lastGeometry = geometry
  })

  const radius = $derived(Math.min(RADIUS, visibleH.current / 2))
  const clip = $derived.by(() => {
    const dx = Math.max(0, boxW - visibleW.current)
    const dy = Math.max(0, boxH - visibleH.current)
    return corner
      ? `inset(${dy}px 0 0 ${dx}px round ${radius}px)`
      : `inset(0 ${dx / 2}px ${dy}px ${dx / 2}px round 0 0 ${radius}px ${radius}px)`
  })

  $effect(() => clock.use())

  onMount(() => {
    const stop = hub.onMessage((message) => {
      switch (message.type) {
        case 'hello': {
          const live = new Set(message.pending.map((i) => i.id))
          requests = requests.filter((r) => live.has(r.id))
          message.pending.forEach(upsertRequest)
          break
        }
        case 'item':
          if (hub.prefs.sound) chime(message.item.kind)
          if (message.item.status === 'info') {
            infoQueue.push(message.item)
            playNextInfo()
          } else upsertRequest(message.item)
          break
        case 'update':
          upsertRequest(message.item)
          break
        case 'settings':
          if (message.settings.paused) requests = []
          break
      }
    })
    const unlisten = [events.shortcut(onShortcut), events.overview(() => void openOverview(true))]
    void hub.start().then(() => {
      markReady()
      hub.pending.forEach(upsertRequest)
    })
    return () => {
      stop()
      hub.stop()
      timers.forEach(clearTimeout)
      void Promise.all(unlisten).then((fns) => fns.forEach((fn) => fn()))
      void api.setShortcuts(false).catch(() => undefined)
    }
  })

  const modeExplanation = $derived(MODES.find((m) => m.value === hub.settings.mode)?.explanation)

  /** Bar label on the right for a non-pending item. */
  const barLabel = (item: Item): string => {
    if (item.status === 'info' && item.kind === 'completed') return 'Finished'
    return statusOf(item).label
  }
</script>

<svelte:window {onkeydown} />

<div
  class="stage"
  class:corner
  class:light={theme === 'light'}
  style:--side-pad="{SIDE_PAD}px"
  style:--corner-gap="{CORNER_GAP}px"
>
  {#if shown}
    <!-- svelte-ignore a11y_no_static_element_interactions -->
    <div
      class="island"
      class:is-card={shown.kind !== 'bar'}
      style:width="{boxW}px"
      style:height="{boxH}px"
      style:clip-path={clip}
      onmouseenter={() => hover(true)}
      onmouseleave={() => hover(false)}
    >
      {#key shownKey}
        <div
          class="content"
          class:wide={shown.kind !== 'bar'}
          {@attach measure}
          in:fade={{ delay: 120 }}
          out:fade={{ duration: 100 }}
        >
          {#if shown.kind === 'card'}
            <RequestCard
              bind:this={card}
              item={shown.item}
              variant="island"
              onfinished={upsertRequest}
            />
            {#if requests.length > 1}
              <p class="queue">{requests.length - 1} more waiting</p>
            {/if}
          {:else if shown.kind === 'bar'}
            {@const status = statusOf(shown.item)}
            {@const pending = shown.item.status === 'pending'}
            <button
              class="bar"
              onclick={onBarClick}
              aria-label="{shown.item.title}: {status.label}"
            >
              <Mark size={16} />
              <span class="bar-name">
                {pending
                  ? `${agentName(shown.item)} · ${shown.item.session.project}`
                  : shown.item.session.project}
              </span>
              <span class="bar-status">
                {#if pending}
                  <span class="dot"></span>
                  <span>
                    {isOpenQuestion(shown.item) ? 'Waiting for you' : 'Needs you'}{requests.length >
                    1
                      ? ` · ${requests.length}`
                      : ''}
                  </span>
                {:else}
                  <span class="check" class:bad={status.variant === 'denied'}>
                    <Glyph
                      kind={status.variant === 'denied' ? 'cross' : 'check'}
                      tone="currentColor"
                      size={9}
                      duration={shown.glyphMs}
                    />
                  </span>
                  <span>{barLabel(shown.item)}</span>
                {/if}
              </span>
            </button>
          {:else}
            <div class="overview">
              <div class="overview-head">
                <button class="head-toggle" onclick={closeOverview} aria-label="Collapse">
                  <Mark size={16} />
                  <span class="overview-title">Agents</span>
                </button>
                <span class="live" class:offline={hub.status !== 'online'}>
                  <span class="live-dot"></span>
                  {hub.status === 'online' ? 'Live' : 'Offline'}
                </span>
                <button
                  class="collapse"
                  onclick={closeOverview}
                  title="Collapse"
                  aria-label="Collapse"
                >
                  <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
                    <path d="M4 10l4-4 4 4" />
                  </svg>
                </button>
              </div>
              <ul class="sessions">
                {#if sessions === null}
                  {#each [0, 1, 2] as i (i)}<li class="skeleton"></li>{/each}
                {:else if sessions.length === 0}
                  <li class="empty">Nothing yet. Requests from your agents will show up here.</li>
                {:else}
                  {#each sessions as item (item.id)}
                    <li>
                      <button class="session" onclick={() => api.openMain(item.id)}>
                        <Mark size={24} />
                        <span class="session-text">
                          <span class="session-name">{item.session.project}</span>
                          <span class="session-action">
                            {agentName(item)} · {item.permission?.summary ?? item.title}
                          </span>
                        </span>
                        <span class="time">{relativeTime(item.createdAt, clock.now)}</span>
                        <span
                          class="ring"
                          class:waiting={item.status === 'pending'}
                          class:done={item.status === 'info'}
                        ></span>
                      </button>
                    </li>
                  {/each}
                {/if}
              </ul>
              <span class="approvals">Approvals</span>
              <Segmented
                label="Approvals"
                stretch
                value={hub.settings.mode}
                options={MODES.map(({ value, label }) => ({ value, label }))}
                onchange={(mode) => void api.setMode(mode)}
              />
              <p class="explanation">{modeExplanation}</p>
            </div>
          {/if}
        </div>
      {/key}
    </div>
  {/if}
</div>

<style>
  .stage {
    /* Captured here so the island can derive its quieter Approve colour from it. */
    --approve-base: var(--c-button-primary);
    position: fixed;
    inset: 0;
    display: flex;
    justify-content: center;
    align-items: flex-start;
    padding: 0 var(--side-pad);
    filter: drop-shadow(0 10px 16px rgb(0 0 0 / 0.45));
  }
  .stage.corner {
    justify-content: flex-end;
    align-items: flex-end;
    padding: 0 var(--side-pad) var(--corner-gap);
  }

  .island {
    position: relative;
    flex: none;
    overflow: hidden;
    color: var(--c-foreground);
  }

  /* Dark (default): black like a hardware notch, island tokens. */
  .stage:not(.light) .island {
    --mark-color: var(--island-foreground);
    --c-primary-text: var(--island-foreground);
    --c-foreground: var(--island-foreground);
    --c-muted-foreground: var(--island-muted-foreground);
    --c-card: var(--island-secondary-button);
    --c-rule: var(--island-secondary-button-edge);
    --c-accent: var(--island-pill);
    --c-muted: var(--island-pill);
    --c-input: var(--island-pill);
    --code-surface: var(--island-pill);
    --shadow-button-secondary: none;
    /* Approve is a quieter, desaturated teal on black, with a soft inset glow. */
    --c-button-primary: color-mix(
      in srgb,
      var(--approve-base) 78%,
      var(--island-secondary-button-edge)
    );
    --shadow-button-primary:
      0 1px 1px rgb(0 0 0 / 0.45), inset 0 0 0 1px rgb(114 208 208 / 0.28),
      inset 0 1px 2px rgb(114 208 208 / 0.35);
    --shadow-button-primary-hover:
      0 1px 2px rgb(0 0 0 / 0.5), inset 0 0 0 1px rgb(138 219 219 / 0.4),
      inset 0 1px 3px rgb(138 219 219 / 0.45);
    background: var(--island-background);
  }
  /* The card fades from a lifted slate at the screen edge into black. */
  .stage:not(.light) .island.is-card {
    background: linear-gradient(180deg, #1a2222 0, var(--island-background) 56px);
  }

  /* Light: the light card tokens; secondary buttons are card + rule border. */
  .light .island {
    --mark-color: var(--c-button-primary);
    background: var(--c-card);
  }
  .light {
    filter: drop-shadow(0 8px 14px rgb(20 23 23 / 0.12)) drop-shadow(0 0 1px rgb(0 0 0 / 0.2));
  }

  .content {
    position: absolute;
    top: 0;
    left: 50%;
    width: 340px;
    transform: translateX(-50%);
  }
  .content.wide {
    width: 400px;
  }
  .corner .content {
    top: auto;
    bottom: 0;
    left: auto;
    right: 0;
    transform: none;
  }

  /* -------------------------------------------------------------- bar */
  .bar {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 100%;
    height: 30px;
    padding: 0 14px 0 12px;
    border: 0;
    background: transparent;
    color: var(--c-foreground);
    font-size: 12.5px;
    text-align: left;
    white-space: nowrap;
  }
  .bar-name {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .bar-status {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    color: var(--c-muted-foreground);
  }
  .dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--c-primary);
  }
  .check {
    display: grid;
    place-items: center;
    width: 13px;
    height: 13px;
    border-radius: 50%;
    background: var(--c-primary);
    color: var(--island-background);
  }
  .check.bad {
    background: var(--c-danger);
  }
  .light .check {
    color: var(--c-card);
  }

  .queue {
    margin: -4px 0 0;
    padding: 0 16px 10px;
    font-size: 12px;
    color: var(--c-muted-foreground);
  }

  /* --------------------------------------------------------- overview */
  .overview {
    display: grid;
    gap: 10px;
    padding: 14px 16px 14px;
    font-size: 13px;
  }
  .overview-head {
    display: flex;
    align-items: center;
    gap: 10px;
  }
  .head-toggle {
    display: flex;
    flex: 1;
    align-items: center;
    gap: 8px;
    padding: 0;
    border: 0;
    background: none;
    color: inherit;
    font-size: 13px;
    text-align: left;
  }
  .collapse {
    display: grid;
    place-items: center;
    width: 24px;
    height: 24px;
    margin-right: -6px;
    border: 0;
    border-radius: var(--radius-md);
    background: transparent;
    color: var(--c-muted-foreground);
    transition:
      background-color var(--motion-button-duration) var(--motion-button-easing),
      color var(--motion-button-duration) var(--motion-button-easing);
  }
  .collapse:hover {
    background: var(--c-accent);
    color: var(--c-foreground);
  }
  .collapse path {
    fill: none;
    stroke: currentColor;
    stroke-width: 1.6;
    stroke-linecap: round;
    stroke-linejoin: round;
  }
  .overview-title {
    flex: 1;
    color: var(--c-muted-foreground);
  }
  .live {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    color: var(--c-primary-text);
  }
  .live.offline {
    color: var(--c-muted-foreground);
  }
  .offline .live-dot {
    background: var(--c-muted-foreground);
  }
  .live-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--c-primary);
  }
  @media (prefers-reduced-motion: no-preference) {
    .live:not(.offline) .live-dot {
      animation: pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
    }
  }
  @keyframes pulse {
    50% {
      opacity: 0.5;
    }
  }
  .sessions {
    display: grid;
    gap: 2px;
    margin: 0;
    padding: 0;
    list-style: none;
  }
  .session {
    display: grid;
    grid-template-columns: auto 1fr auto auto;
    align-items: center;
    gap: 10px;
    width: 100%;
    padding: 6px 4px;
    border: 0;
    border-radius: var(--radius-md);
    background: transparent;
    color: var(--c-foreground);
    text-align: left;
  }
  .session:hover {
    background: var(--c-accent);
  }
  .session-text {
    display: grid;
    min-width: 0;
    line-height: 1.3;
  }
  .session-name {
    font-size: 13px;
  }
  .session-action {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 12px;
    color: var(--c-muted-foreground);
  }
  .session .time {
    font-size: 12px;
    color: var(--c-muted-foreground);
  }
  .ring {
    width: 9px;
    height: 9px;
    border-radius: 50%;
    border: 1.5px solid var(--c-muted-foreground);
  }
  .ring.waiting {
    border-color: var(--c-waiting);
  }
  .ring.done {
    border-color: transparent;
    background: var(--c-muted-foreground);
  }
  .skeleton {
    height: 38px;
    border-radius: var(--radius-md);
    background: var(--c-accent);
  }
  .empty {
    padding: 6px 4px;
    color: var(--c-muted-foreground);
  }
  .approvals {
    margin-top: 4px;
    color: var(--c-muted-foreground);
  }
  /* Four equal keys; the selected one is the teal button. */
  .overview :global(.segmented) {
    --seg-track: transparent;
    --seg-padding: 0;
    --seg-bg: var(--c-card);
    --seg-border: 1px solid var(--c-rule);
    --seg-fg: var(--c-foreground);
    --seg-active-bg: var(--c-button-primary);
    --seg-active-fg: var(--c-button-primary-foreground);
    --seg-active-shadow: var(--shadow-button-primary);
    --seg-pad-x: 2px;
    --seg-gap: 5px;
    height: 30px;
  }
  .overview :global(.segmented button) {
    font-size: 11.5px;
    letter-spacing: -0.01em;
  }
  .explanation {
    margin: 0;
    font-size: 12px;
    color: var(--c-muted-foreground);
  }
</style>
