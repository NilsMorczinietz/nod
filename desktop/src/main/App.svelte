<script lang="ts">
  import { onMount } from 'svelte'
  import ConnectionBadge from '../components/ConnectionBadge.svelte'
  import Mark from '../components/Mark.svelte'
  import Segmented from '../components/Segmented.svelte'
  import { events } from '../lib/api'
  import { HubStore } from '../lib/hub.svelte'
  import { applyThemeClass } from '../lib/theme.svelte'
  import Inbox from './Inbox.svelte'
  import Pairing from './Pairing.svelte'
  import Settings from './Settings.svelte'

  type Tab = 'inbox' | 'settings'
  const hub = new HubStore()
  let tab = $state<Tab>('inbox')
  let focusId = $state<string | null>(null)

  $effect(() => applyThemeClass(hub.prefs.appTheme === 'system' ? null : hub.prefs.appTheme))

  onMount(() => {
    void hub.start()
    const focus = events.focusItem((id) => {
      tab = 'inbox'
      focusId = id
    })
    return () => {
      hub.stop()
      void focus.then((unlisten) => unlisten())
    }
  })
</script>

<div class="canvas">
  {#if hub.ready}
    {#if !hub.paired}
      <Pairing />
    {:else}
      <header>
        <div class="brand"><Mark size={24} /><span>Nod</span></div>
        <Segmented
          label="Section"
          value={tab}
          options={[
            {
              value: 'inbox',
              label: hub.pending.length ? `Inbox · ${hub.pending.length}` : 'Inbox',
            },
            { value: 'settings', label: 'Settings' },
          ]}
          onchange={(value: Tab) => (tab = value)}
        />
        <div class="status">
          <ConnectionBadge status={hub.status} paused={hub.settings.paused} />
        </div>
      </header>
      <main>
        {#if tab === 'inbox'}
          <Inbox {hub} bind:focusId />
        {:else}
          <Settings {hub} />
        {/if}
      </main>
    {/if}
  {/if}
</div>

<style>
  .canvas {
    position: relative;
    display: grid;
    grid-template-rows: auto 1fr;
    height: 100vh;
  }

  header {
    display: grid;
    grid-template-columns: 1fr auto 1fr;
    align-items: center;
    padding: 14px 28px;
    border-bottom: 1px solid var(--c-rule);
    font-size: var(--type-small-size);
  }
  .brand {
    --mark-color: var(--c-primary);
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 15px;
    font-weight: 500;
    letter-spacing: -0.01em;
  }
  .status {
    justify-self: end;
  }
  main {
    overflow-y: auto;
    scrollbar-gutter: stable both-edges;
    scrollbar-width: thin;
    scrollbar-color: var(--c-rule) transparent;
  }
</style>
