<!--
  Inbox: eyebrow + light headline with the pending count, a stat row (pixel
  numbers, real data only), one card per request, then the history ledger.
-->
<script lang="ts">
  import { onMount } from 'svelte'
  import type { Item } from '@nod/protocol'
  import Badge from '../components/Badge.svelte'
  import Eyebrow from '../components/Eyebrow.svelte'
  import Halftone from '../components/Halftone.svelte'
  import RequestCard from '../components/RequestCard.svelte'
  import { api } from '../lib/api'
  import { clock } from '../lib/clock.svelte'
  import type { HubStore } from '../lib/hub.svelte'
  import { rise } from '../lib/motion'
  import { agentName, answerOf, isOpenQuestion, relativeTime, statusOf } from '../lib/status'
  import ItemDetail from './ItemDetail.svelte'

  let { hub, focusId = $bindable() }: { hub: HubStore; focusId: string | null } = $props()

  let history = $state<Item[] | null>(null)
  let loadError = $state<string | null>(null)

  $effect(() => clock.use())

  const load = async () => {
    try {
      history = await api.listItems(80)
      loadError = null
    } catch (e) {
      loadError = String(e)
    }
  }

  const ledger = $derived((history ?? []).filter((i) => i.status !== 'pending').slice(0, 30))
  const today = $derived.by(() => {
    const start = new Date(clock.now).setHours(0, 0, 0, 0)
    const items = (history ?? []).filter((i) => i.createdAt >= start)
    return {
      approved: items.filter((i) => i.status === 'resolved' && i.resolution?.decision !== 'deny')
        .length,
      projects: new Set(items.map((i) => i.session.project)).size,
    }
  })
  const selected = $derived(
    focusId
      ? (hub.pending.find((i) => i.id === focusId) ??
          history?.find((i) => i.id === focusId) ??
          null)
      : null,
  )

  /** Where it was answered: this PC, another device, or the terminal. */
  const channel = (item: Item): string => {
    if (item.resolution) {
      return item.resolution.deviceName === hub.deviceName ? 'Desktop' : item.resolution.deviceName
    }
    return item.status === 'info' ? '' : 'Terminal'
  }

  onMount(() => {
    void load()
    let debounce: ReturnType<typeof setTimeout> | undefined
    const stop = hub.onMessage(() => {
      clearTimeout(debounce)
      debounce = setTimeout(load, 300)
    })
    return () => {
      clearTimeout(debounce)
      stop()
    }
  })
</script>

<div class="inbox">
  <section class="hero">
    <Eyebrow>Inbox</Eyebrow>
    {#if hub.pending.length > 0}
      <h1 class="h2">{hub.pending.length} waiting</h1>
    {:else}
      <h1 class="pixel-headline">Nothing waiting.</h1>
    {/if}
    <p class="body lead">
      {#if hub.settings.mode === 'terminal'}
        Nod is paused. Claude Code asks in the terminal until you resume.
      {:else if hub.pending.length > 0}
        {agentName(hub.pending[0]!)} is stopped until you answer, here or in the island.
      {:else}
        When an agent needs a decision, it shows up here and in the island at the top of your
        screen.
      {/if}
    </p>
  </section>

  <dl class="stats surface">
    <div>
      <dt class="xs">waiting</dt>
      <dd class="stat">{hub.pending.length}</dd>
    </div>
    <div>
      <dt class="xs">approved today</dt>
      <dd class="stat">{history ? today.approved : '–'}</dd>
    </div>
    <div>
      <dt class="xs">projects today</dt>
      <dd class="stat">{history ? today.projects : '–'}</dd>
    </div>
  </dl>

  {#if hub.pending.length > 0}
    <section class="cards">
      {#each hub.pending as item (item.id)}
        <div in:rise>
          <RequestCard {item} />
        </div>
      {/each}
    </section>
  {:else}
    <div class="empty surface">
      <Halftone />
      <p class="body">Nothing needs you right now. Your agents keep working.</p>
    </div>
  {/if}

  <section class="history">
    <Eyebrow>History</Eyebrow>
    {#if loadError}
      <p class="body">Could not load the history: {loadError}</p>
    {:else if history === null}
      <ul class="ledger surface" aria-hidden="true">
        {#each [0, 1, 2] as i (i)}<li class="skeleton"></li>{/each}
      </ul>
    {:else if ledger.length === 0}
      <p class="body">Answered requests and finished tasks appear here.</p>
    {:else}
      <ul class="ledger surface">
        {#each ledger as item (item.id)}
          {@const status = statusOf(item)}
          <li>
            <button class="row" onclick={() => (focusId = item.id)}>
              {#if item.permission}
                <span class="what mono">{item.permission.summary}</span>
              {:else if isOpenQuestion(item) && answerOf(item)}
                <span class="what" title={item.questions?.[0]?.question}>“{answerOf(item)}”</span>
              {:else}
                <span class="what">{item.title}</span>
              {/if}
              <span class="project xs" title={agentName(item)}>{item.session.project}</span>
              <Badge label={status.label} variant={status.variant} />
              <span class="channel xs">{channel(item)}</span>
              <span class="when time">{relativeTime(item.createdAt, clock.now)}</span>
            </button>
          </li>
        {/each}
      </ul>
    {/if}
  </section>
</div>

{#if selected}
  <ItemDetail item={selected} onclose={() => (focusId = null)} />
{/if}

<style>
  .inbox {
    display: grid;
    gap: 40px;
    max-width: 800px;
    margin: 0 auto;
    padding: 48px 32px 72px;
  }
  .hero {
    display: grid;
    gap: 14px;
  }
  .lead {
    margin: 0;
    max-width: 56ch;
  }
  /* Big statements use the pixel face, like the reference. */
  .pixel-headline {
    margin: 0;
    font-family: var(--font-pixel);
    font-size: var(--type-h2-size-large);
    font-weight: 500;
    letter-spacing: -0.02em;
    line-height: 1.1;
  }

  .stats {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    margin: 0;
  }
  .stats div {
    display: flex;
    flex-direction: column-reverse;
    gap: 8px;
    padding: 18px 20px;
  }
  .stats div + div {
    border-left: 1px solid var(--c-rule);
  }
  .stats dt,
  .stats dd {
    margin: 0;
  }

  .cards {
    display: grid;
    gap: 14px;
  }
  .empty {
    overflow: hidden;
  }
  .empty p {
    margin: 0;
    padding: 4px 22px 20px;
  }

  .history {
    display: grid;
    gap: 14px;
  }
  .ledger {
    margin: 0;
    padding: 0 4px;
    list-style: none;
    overflow: hidden;
  }
  .ledger li + li {
    border-top: 1px solid var(--c-rule);
  }
  .skeleton {
    height: 48px;
  }
  .row {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 96px 84px 96px 60px;
    align-items: center;
    gap: 14px;
    width: 100%;
    min-height: 48px;
    padding: 10px 14px;
    border: 0;
    background: transparent;
    text-align: left;
    transition: background-color var(--motion-button-duration) var(--motion-button-easing);
  }
  .row:hover {
    background: var(--c-accent);
  }
  .what {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: var(--type-small-size);
  }
  .what.mono {
    font-size: var(--type-mono-size);
  }
  .project {
    max-width: 120px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .channel {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .when {
    text-align: right;
    color: var(--c-muted-foreground);
  }
</style>
