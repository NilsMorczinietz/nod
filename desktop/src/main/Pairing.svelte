<!-- First run: pair this PC with the relay (v4 style, numbered steps). -->
<script lang="ts">
  import Eyebrow from '../components/Eyebrow.svelte'
  import Mark from '../components/Mark.svelte'
  import { api } from '../lib/api'

  let link = $state('')
  let code = $state('')
  let busy = $state(false)
  let error = $state<string | null>(null)

  const isLink = $derived(link.trim().startsWith('nod://'))
  const canPair = $derived(link.trim() !== '' && (isLink || code.trim() !== ''))

  const pair = async (event: SubmitEvent) => {
    event.preventDefault()
    busy = true
    error = null
    try {
      await api.pair(link.trim(), isLink ? undefined : code.trim())
    } catch (e) {
      error = String(e)
    } finally {
      busy = false
    }
  }
</script>

<div class="page">
  <form class="panel" onsubmit={pair}>
    <Mark size={40} />
    <div class="intro">
      <Eyebrow>Get started</Eyebrow>
      <h1 class="h2">Pair this PC</h1>
    </div>

    <ol class="steps">
      <li>
        <span class="n">01</span>
        <span>Run <code>nod pair</code> on the machine with Claude Code.</span>
      </li>
      <li>
        <span class="n">02</span>
        <span>Paste the <code>nod://pair?…</code> link it prints here.</span>
      </li>
      <li>
        <span class="n">03</span>
        <span>Keep Nod running in the tray. It appears when Claude Code needs you.</span>
      </li>
    </ol>

    <div class="form">
      <!-- svelte-ignore a11y_autofocus -->
      <input
        bind:value={link}
        aria-label="Pairing link or relay URL"
        placeholder="nod://pair?relay=…&code=…"
        autofocus
        spellcheck="false"
      />
      {#if link.trim() && !isLink}
        <input
          bind:value={code}
          aria-label="Pairing code"
          placeholder="Code, e.g. K7QM-4XPA"
          class="code"
          spellcheck="false"
        />
      {/if}
      <button class="btn primary" disabled={!canPair || busy}>
        {busy ? 'Pairing…' : 'Pair'}
      </button>
    </div>
    <p class="hint">You can also paste the relay URL and enter the code separately.</p>
    {#if error}<p class="error">{error}</p>{/if}
  </form>
</div>

<style>
  .page {
    display: grid;
    place-items: center;
    min-height: 100vh;
    padding: 32px;
  }
  .panel {
    --mark-color: var(--c-primary);
    display: grid;
    gap: 28px;
    width: min(480px, 100%);
  }
  .intro {
    display: grid;
    gap: 14px;
  }
  .steps {
    display: grid;
    gap: 14px;
    margin: 0;
    padding: 0;
    list-style: none;
    color: var(--c-muted-foreground);
    font-size: var(--type-body-size);
    line-height: 1.5;
  }
  .steps li {
    display: flex;
    gap: 14px;
  }
  .n {
    flex: none;
    width: 22px;
    font-family: var(--font-mono);
    font-size: 13px;
    line-height: 1.75;
    color: var(--c-primary-text);
  }
  code {
    padding: 1px 5px;
    border-radius: var(--radius-sm);
    background: var(--c-muted);
    color: var(--c-foreground);
    font-family: var(--font-mono);
    font-size: 12.5px;
  }
  .form {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }
  input {
    flex: 1 1 220px;
    height: 40px;
    padding: 0 12px;
    border-radius: var(--radius-md);
    border: 1px solid var(--c-rule);
    background: var(--c-input);
    color: var(--c-foreground);
    outline: none;
    font-family: var(--font-mono);
    font-size: 12.5px;
  }
  input:focus {
    border-color: var(--c-ring);
  }
  .code {
    flex-basis: 140px;
    letter-spacing: 0.1em;
  }
  .form .btn {
    height: 40px;
    padding: 0 22px;
  }
  .hint {
    margin: -18px 0 0;
    font-size: var(--type-xs-size);
    color: var(--c-muted-foreground);
  }
  .error {
    margin: 0;
    color: var(--c-danger);
  }
</style>
