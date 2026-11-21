<!-- Side panel with everything about one item (full plan, full command, outcome). -->
<script lang="ts">
  import type { Item } from '@nod/protocol'
  import CodeBlock from '../components/CodeBlock.svelte'
  import Markdown from '../components/Markdown.svelte'
  import Badge from '../components/Badge.svelte'
  import RequestCard from '../components/RequestCard.svelte'
  import { clockTime, dayLabel } from '../lib/format'
  import { statusOf } from '../lib/status'

  let { item, onclose }: { item: Item; onclose: () => void } = $props()
  const status = $derived(statusOf(item))

  const onkeydown = (event: KeyboardEvent) => {
    if (event.key === 'Escape') onclose()
  }
</script>

<svelte:window {onkeydown} />

<div class="scrim" role="presentation" onclick={onclose}></div>
<aside class="panel" aria-label="Details">
  <header>
    <span class="xs">
      {item.session.project} · {dayLabel(item.createdAt)}
      <span class="time">{clockTime(item.createdAt)}</span>
    </span>
    <button class="btn" onclick={onclose}>Close</button>
  </header>

  {#if item.status === 'pending'}
    <RequestCard {item} />
  {:else}
    <h2 class="h3">{item.title}</h2>
    <div><Badge label={status.label} variant={status.variant} /></div>
    {#if item.body && item.body !== item.permission?.summary}<p class="body">{item.body}</p>{/if}
    {#if item.permission}<CodeBlock
        text={`${item.permission.tool === 'Bash' ? '$ ' : ''}${item.permission.detail ?? item.permission.summary}`}
      />{/if}
    {#if item.plan}<div class="plan"><Markdown source={item.plan.markdown} /></div>{/if}
    {#if item.questions}
      {#each item.questions as q (q.question)}
        <div class="qa">
          <p class="xs">{q.question}</p>
          <p class="answer">{item.resolution?.answers?.[q.question]?.join(', ') ?? 'No answer'}</p>
        </div>
      {/each}
    {/if}
    {#if item.resolution}
      <p class="xs">
        {status.label} on {item.resolution.deviceName} at
        <span class="time">{clockTime(item.resolution.at)}</span>{item.resolution.message
          ? `: “${item.resolution.message}”`
          : ''}
      </p>
    {:else if item.status === 'expired'}
      <p class="xs">Expired, asked in the terminal.</p>
    {/if}
  {/if}
  <p class="cwd mono">{item.session.cwd} · {item.session.host}</p>
</aside>

<style>
  .scrim {
    position: fixed;
    inset: 0;
    background: rgb(0 0 0 / 0.4);
  }
  .panel {
    position: fixed;
    top: 0;
    right: 0;
    bottom: 0;
    width: min(560px, 92vw);
    display: flex;
    flex-direction: column;
    gap: 16px;
    padding: 20px 24px;
    overflow-y: auto;
    background: var(--c-popover);
    box-shadow: var(--shadow-surface);
  }
  header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }
  .body {
    margin: 0;
    color: var(--c-muted-foreground);
    user-select: text;
  }
  .plan {
    padding: 14px 16px;
    border-radius: var(--radius-md);
    background: var(--c-muted);
  }
  .qa p {
    margin: 0;
  }
  .answer {
    margin-top: 4px !important;
    font-weight: 500;
  }
  .cwd {
    margin-top: auto;
    color: var(--c-muted-foreground);
    font-size: 12px;
  }
</style>
