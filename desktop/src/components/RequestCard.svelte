<!--
  A pending request from an agent (Claude Code by default): permission,
  question or plan.

  island  Compact card for the island (references 08/11): 20 px app icon,
          "Agent - project" over a muted summary, a "Needs you" badge,
          the question, one muted detail line, two equal buttons with their
          shortcuts, and quiet text buttons underneath.
  full    The same content as a card in the main window, with the countdown
          and the command always visible.

  The island forwards the global shortcuts through the exported `approve()`
  and `deny()`. With the card focused, Enter approves and Esc denies.
-->
<script lang="ts">
  import type { Item, ResolveInput } from '@nod/protocol'
  import { api } from '../lib/api'
  import { clock, formatRemaining } from '../lib/clock.svelte'
  import { agentName } from '../lib/status'
  import Badge from './Badge.svelte'
  import CodeBlock from './CodeBlock.svelte'
  import Mark from './Mark.svelte'
  import Markdown from './Markdown.svelte'

  let {
    item,
    variant = 'full',
    onfinished,
  }: {
    item: Item
    variant?: 'island' | 'full'
    /** Called with the finished item after a local answer (or a 409). */
    onfinished?: (item: Item) => void
  } = $props()

  let busy = $state(false)
  let error = $state<string | null>(null)
  let reasoning = $state(false)
  let reason = $state('')
  let showDetails = $state(false)
  let selections = $state<Record<string, string[]>>({})

  $effect(() => clock.use())

  const island = $derived(variant === 'island')
  const remaining = $derived(item.expiresAt - clock.now)
  const questions = $derived(item.questions ?? [])
  const canSend = $derived(
    questions.length > 0 && questions.every((q) => (selections[q.question]?.length ?? 0) > 0),
  )
  const permission = $derived(item.permission)
  /** A question Claude asked in prose: Yes / No, or a free-text reply. */
  const replyQuestion = $derived(
    questions.length === 1 && questions[0]!.allowText ? questions[0]! : null,
  )
  const summary = $derived(replyQuestion ? '' : (permission?.summary ?? item.body ?? ''))
  /** The command (or diff) as code; in the island only behind "Show details". */
  const code = $derived.by(() => {
    if (!permission) return null
    const text = permission.detail ?? permission.summary
    if (island && text === permission.summary) return null
    return permission.tool === 'Bash' ? `$ ${text}` : text
  })
  /** One muted line under the question. */
  const detail = $derived.by(() => {
    if (item.kind === 'question') {
      const first = questions[0]?.question
      return first && first !== item.title ? first : null
    }
    if (item.body && item.body !== summary) return item.body
    return item.kind === 'permission' ? `In ${item.session.cwd}` : null
  })
  const labels = $derived(
    item.kind === 'plan'
      ? { approve: 'Approve', deny: 'Reject', reason: 'Reject with feedback' }
      : item.kind === 'question'
        ? { approve: 'Send', deny: 'Skip', reason: null }
        : { approve: 'Approve', deny: 'Deny', reason: 'Deny with a reason' },
  )

  const toggle = (question: string, label: string, multi: boolean) => {
    const current = selections[question] ?? []
    selections[question] = multi
      ? current.includes(label)
        ? current.filter((l) => l !== label)
        : [...current, label]
      : [label]
  }

  const send = async (input: ResolveInput) => {
    if (busy || item.status !== 'pending') return
    busy = true
    error = null
    try {
      const result = await api.resolve(item.id, input)
      onfinished?.(result.item)
    } catch (e) {
      error = String(e)
    } finally {
      busy = false
    }
  }

  let reply = $state('')
  let expanded = $state(false)
  let replyInput: HTMLInputElement | undefined = $state()
  /** Long questions are clamped to four lines until "Show more". */
  const longQuestion = $derived((replyQuestion?.question.length ?? 0) > 200)

  const choose = (label: string | undefined) => {
    if (replyQuestion && label) {
      void send({ decision: 'answer', answers: { [replyQuestion.question]: [label] } })
    }
  }

  const sendReply = async () => {
    const message = reply.trim()
    if (!message) return
    await send({ decision: 'answer', answers: {}, message })
    if (island) void api.releaseFocus()
  }

  /** The island never takes focus on its own; only a click into the field does. */
  const focusReply = async () => {
    if (!island) return
    await api.focusIsland().catch(() => undefined)
    replyInput?.focus()
  }

  const onReplyKey = (event: KeyboardEvent) => {
    event.stopPropagation()
    if (event.key === 'Enter') {
      event.preventDefault()
      void sendReply()
    } else if (event.key === 'Escape') {
      event.preventDefault()
      replyInput?.blur()
      if (island) void api.releaseFocus()
    }
  }

  export const approve = () => {
    if (replyQuestion) choose(replyQuestion.options[0]?.label)
    else if (item.kind !== 'question') void send({ decision: 'allow' })
    else if (canSend) void send({ decision: 'answer', answers: $state.snapshot(selections) })
  }

  export const deny = () => {
    if (replyQuestion) choose(replyQuestion.options[1]?.label)
    else void send({ decision: 'deny', ...(reason.trim() ? { message: reason.trim() } : {}) })
  }

  const onkeydown = (event: KeyboardEvent) => {
    if (event.target instanceof HTMLTextAreaElement) {
      if (event.key === 'Escape') reasoning = false
      return
    }
    if (event.key === 'Enter') {
      event.preventDefault()
      approve()
    } else if (event.key === 'Escape') {
      event.preventDefault()
      deny()
    }
  }
</script>

<!-- The card is focusable so Enter/Esc answer it without a mouse. -->
<!-- svelte-ignore a11y_no_noninteractive_tabindex, a11y_no_noninteractive_element_interactions -->
<article class="card {variant}" tabindex="0" aria-label={item.title} {onkeydown}>
  <header>
    <Mark size={island ? 20 : 32} />
    <span class="who">
      <span class="name">{agentName(item)} - {item.session.project}</span>
      {#if summary}<span class="summary" class:mono={!!permission}>{summary}</span>{/if}
    </span>
    {#if !island}
      <span class="time" class:urgent={remaining <= 30_000}>{formatRemaining(remaining)}</span>
    {/if}
    <Badge label="Needs you" variant={island ? 'neutral' : 'waiting'} />
  </header>

  {#if replyQuestion}
    <div class="ask">
      <p class="question prose" class:clamped={longQuestion && !expanded}>
        {replyQuestion.question}
      </p>
      {#if longQuestion}
        <button class="btn text more" onclick={() => (expanded = !expanded)}>
          {expanded ? 'Show less' : 'Show more'}
        </button>
      {/if}
    </div>
  {:else}
    <div class="ask">
      <h3 class="question">{item.title}</h3>
      {#if detail}<p class="detail">{detail}</p>{/if}
    </div>
  {/if}

  {#if code}
    {#if island}
      <button
        class="btn text disclosure"
        aria-expanded={showDetails}
        onclick={() => (showDetails = !showDetails)}
      >
        <span class="chevron" class:open={showDetails}>›</span>
        {showDetails ? 'Hide details' : 'Show details'}
      </button>
    {/if}
    {#if showDetails || !island}
      <CodeBlock text={code} maxHeight={island ? '160px' : '360px'} />
    {/if}
  {/if}

  {#if item.kind === 'question' && !replyQuestion}
    {#each questions as q (q.question)}
      <div class="options" role="group" aria-label={q.question}>
        {#each q.options as option (option.label)}
          {@const selected = selections[q.question]?.includes(option.label) ?? false}
          <button
            type="button"
            class="option"
            class:selected
            aria-pressed={selected}
            onclick={() => toggle(q.question, option.label, q.multiSelect)}
          >
            <span class="marker" class:multi={q.multiSelect}></span>
            <span class="option-text">
              <span class="option-label">{option.label}</span>
              {#if option.description}<span class="option-hint">{option.description}</span>{/if}
            </span>
          </button>
        {/each}
      </div>
    {/each}
  {:else if item.kind === 'plan' && item.plan}
    <div class="plan" class:clamped={island}>
      <Markdown source={item.plan.markdown} />
    </div>
  {/if}

  {#if reasoning}
    <!-- svelte-ignore a11y_autofocus -->
    <textarea bind:value={reason} rows="2" placeholder="Tell Claude why" autofocus></textarea>
  {/if}

  {#if error}<p class="error xs">{error}</p>{/if}

  {#if replyQuestion}
    <div class="actions">
      {#each replyQuestion.options.slice(0, 2) as option, i (option.label)}
        <button class="btn" disabled={busy} onclick={() => choose(option.label)}>
          {option.label}
          {#if island}<kbd>{i === 0 ? 'Ctrl+Shift+Y' : 'Ctrl+Shift+N'}</kbd>{/if}
        </button>
      {/each}
    </div>
    <form
      class="reply"
      onsubmit={(event) => {
        event.preventDefault()
        void sendReply()
      }}
    >
      <input
        bind:this={replyInput}
        bind:value={reply}
        placeholder="Reply…"
        aria-label="Reply to {agentName(item)}"
        onpointerdown={focusReply}
        onkeydown={onReplyKey}
      />
      <button class="btn primary" disabled={busy || !reply.trim()}>Send</button>
    </form>
  {:else}
    <div class="actions">
      <button class="btn" disabled={busy} onclick={deny}>
        {reasoning ? 'Send reason' : labels.deny}
        {#if island}<kbd>Ctrl+Shift+N</kbd>{/if}
      </button>
      <button
        class="btn primary"
        disabled={busy || (item.kind === 'question' && !canSend)}
        onclick={approve}
      >
        {labels.approve}
        {#if island}<kbd>Ctrl+Shift+Y</kbd>{/if}
      </button>
    </div>
  {/if}

  <div class="links">
    {#if labels.reason && !reasoning}
      <button class="btn text" onclick={() => (reasoning = true)}>{labels.reason}</button>
    {/if}
    {#if permission?.canAlwaysAllow}
      <button
        class="btn text"
        disabled={busy}
        onclick={() => send({ decision: 'allow', alwaysAllow: true })}
      >
        Approve and remember
      </button>
    {/if}
    {#if item.kind === 'plan' && island}
      <button class="btn text" onclick={() => api.openMain(item.id)}>Open the full plan</button>
    {/if}
  </div>
</article>

<style>
  .card {
    display: grid;
    outline: none;
  }

  /* ---------------------------------------------------------- island */
  .island {
    gap: 10px;
    padding: 14px 16px 12px;
    font-size: 13px;
  }
  .island header {
    gap: 10px;
  }
  .island .name {
    font-size: 13px;
  }
  .island .summary {
    font-size: 12px;
  }
  .island .question {
    font-size: 14px;
  }
  .island .detail {
    font-size: 12px;
  }
  .island .actions .btn {
    height: 30px;
    font-size: 13px;
  }
  .island .links .btn {
    font-size: 12px;
  }
  .island .option {
    padding: 5px 8px;
  }
  .island .option-label {
    font-size: 13px;
  }
  .island .option-hint {
    font-size: 12px;
  }
  .island :global(.badge.neutral) {
    color: var(--c-foreground);
  }

  /* ---------------------------------------------------- main window */
  .full {
    --code-surface: var(--c-muted);
    gap: 14px;
    padding: 20px 22px 18px;
    border-radius: var(--radius-xl);
    background: var(--c-card);
    box-shadow: var(--shadow-surface-sm);
    /* A waiting card gets the amber/50 outline. */
    outline: var(--outline-waiting);
    outline-offset: -1px;
  }
  .full:focus-visible {
    outline: 2px solid var(--c-ring);
  }
  .full header {
    gap: 12px;
  }
  .full .name {
    font-size: var(--type-small-size);
  }
  .full .summary {
    font-size: 13px;
  }
  .full .question {
    font-size: var(--type-heading-size);
    font-weight: var(--type-heading-weight);
    letter-spacing: var(--type-heading-tracking);
  }
  .full .detail {
    font-size: var(--type-small-size);
    font-weight: 400;
  }
  .full .actions .btn {
    height: 40px;
  }
  .full .links .btn {
    font-size: var(--type-small-size);
  }

  /* ---------------------------------------------------------- shared */
  header {
    display: flex;
    align-items: center;
    min-width: 0;
  }
  .who {
    display: grid;
    flex: 1;
    min-width: 0;
    line-height: 1.35;
  }
  .name,
  .summary {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .name {
    font-weight: 500;
  }
  .summary {
    color: var(--c-muted-foreground);
  }
  .summary.mono {
    font-size: 11.5px;
  }
  .time {
    color: var(--c-muted-foreground);
    margin-right: 10px;
  }
  .time.urgent {
    color: var(--c-waiting-text);
  }
  .ask {
    display: grid;
    gap: 2px;
  }
  .question {
    margin: 0;
    font-weight: 400;
    line-height: 1.35;
  }
  .detail {
    margin: 0;
    color: var(--c-muted-foreground);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .disclosure {
    justify-self: start;
    gap: 6px;
    margin-top: -4px;
    font-size: 12px;
  }
  .chevron {
    display: inline-block;
    transition: transform var(--motion-button-duration) var(--motion-button-easing);
  }
  .chevron.open {
    transform: rotate(90deg);
  }

  .options {
    display: grid;
    gap: 2px;
  }
  .option {
    display: flex;
    align-items: flex-start;
    gap: 10px;
    padding: 7px 10px;
    border: 1px solid transparent;
    border-radius: var(--radius-md);
    background: transparent;
    text-align: left;
    transition: background-color var(--motion-button-duration) var(--motion-button-easing);
  }
  .option:hover {
    background: var(--c-accent);
  }
  .option.selected {
    border-color: color-mix(in srgb, var(--c-primary) 50%, transparent);
    background: var(--c-accent);
  }
  .marker {
    flex: none;
    width: 13px;
    height: 13px;
    margin-top: 3px;
    border-radius: 50%;
    border: 1.5px solid var(--c-muted-foreground);
  }
  .marker.multi {
    border-radius: 4px;
  }
  .selected .marker {
    border: 4px solid var(--c-primary);
  }
  .option-text {
    display: grid;
    min-width: 0;
  }
  .option-label {
    font-weight: 500;
  }
  .option-hint {
    font-size: 0.92em;
    color: var(--c-muted-foreground);
  }

  .plan {
    padding: 10px 12px;
    border-radius: var(--radius-md);
    background: var(--code-surface, var(--c-muted));
  }
  .plan.clamped {
    max-height: 120px;
    overflow: hidden;
  }

  textarea {
    width: 100%;
    resize: none;
    padding: 8px 10px;
    border-radius: var(--radius-md);
    border: 1px solid var(--c-rule);
    background: var(--c-input);
    outline: none;
  }
  textarea:focus {
    border-color: var(--c-ring);
  }

  .actions {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 8px;
    margin-top: 2px;
  }
  .actions kbd {
    color: inherit;
    opacity: 0.55;
    font-size: 10.5px;
  }
  .links {
    display: flex;
    justify-content: center;
    gap: 18px;
    margin-top: -2px;
  }
  .links:empty {
    display: none;
  }
  .error {
    margin: 0;
    color: var(--c-danger);
  }

  /* ------------------------------------------------- free-text reply */
  .prose {
    white-space: pre-wrap;
    overflow-wrap: anywhere;
  }
  .prose.clamped {
    display: -webkit-box;
    -webkit-line-clamp: 4;
    line-clamp: 4;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
  .more {
    justify-self: start;
    margin-top: 2px;
    font-size: 12px;
  }
  .reply {
    display: flex;
    gap: 8px;
  }
  .reply input {
    flex: 1;
    min-width: 0;
    padding: 0 10px;
    border-radius: var(--radius-md);
    border: 1px solid var(--c-rule);
    background: var(--c-input);
    color: var(--c-foreground);
    outline: none;
  }
  .reply input:focus {
    border-color: var(--c-ring);
  }
  .island .reply input,
  .island .reply .btn {
    height: 30px;
    font-size: 13px;
  }
  .full .reply input,
  .full .reply .btn {
    height: 40px;
  }
</style>
