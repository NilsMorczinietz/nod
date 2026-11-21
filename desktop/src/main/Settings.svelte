<!-- Grouped settings rows: label + hint on the left, control on the right. -->
<script lang="ts">
  import ConnectionBadge from '../components/ConnectionBadge.svelte'
  import Eyebrow from '../components/Eyebrow.svelte'
  import Segmented from '../components/Segmented.svelte'
  import Toggle from '../components/Toggle.svelte'
  import { api } from '../lib/api'
  import type { HubStore } from '../lib/hub.svelte'
  import { MODES } from '../lib/modes'
  import type {
    AppTheme,
    IslandSize,
    IslandTheme,
    OverlayPosition,
    Prefs,
    RoutingMode,
  } from '../lib/types'

  let { hub }: { hub: HubStore } = $props()
  let error = $state<string | null>(null)
  let confirmUnpair = $state(false)
  let checking = $state(false)
  let checked = $state(false)
  let installing = $state(false)

  const checkNow = async () => {
    checking = true
    await run(api.checkUpdate)
    checking = false
    checked = true
  }
  const install = async () => {
    installing = true
    await run(api.installUpdate)
    installing = false
  }

  const run = async (action: () => Promise<unknown>) => {
    error = null
    try {
      await action()
    } catch (e) {
      error = String(e)
    }
  }
  const setPref = (patch: Partial<Prefs>) => run(() => api.setPrefs({ ...hub.prefs, ...patch }))
  const explanation = $derived(MODES.find((m) => m.value === hub.settings.mode)?.explanation)
</script>

<div class="settings">
  <section class="hero">
    <Eyebrow>Settings</Eyebrow>
    <h1 class="h2">Where requests go</h1>
    <div class="group surface">
      <div class="row stacked">
        <Segmented
          label="Where requests go"
          stretch
          value={hub.settings.mode}
          options={MODES.map(({ value, label }) => ({ value, label }))}
          onchange={(mode: RoutingMode) => run(() => api.setMode(mode))}
        />
        <p class="hint">{explanation}</p>
      </div>
    </div>
  </section>

  <section>
    <Eyebrow>Appearance</Eyebrow>
    <div class="group surface">
      <div class="row">
        <div class="text">
          <span class="name">App theme</span>
          <span class="hint">Colours of this window.</span>
        </div>
        <Segmented
          label="App theme"
          value={hub.prefs.appTheme}
          options={[
            { value: 'system', label: 'System' },
            { value: 'light', label: 'Light' },
            { value: 'dark', label: 'Dark' },
          ]}
          onchange={(appTheme: AppTheme) => setPref({ appTheme })}
        />
      </div>
      <div class="row">
        <div class="text">
          <span class="name">Notification theme</span>
          <span class="hint">The island at the top of the screen.</span>
        </div>
        <Segmented
          label="Notification theme"
          value={hub.prefs.islandTheme}
          options={[
            { value: 'dark', label: 'Dark' },
            { value: 'light', label: 'Light' },
            { value: 'app', label: 'Match app' },
          ]}
          onchange={(islandTheme: IslandTheme) => setPref({ islandTheme })}
        />
      </div>
      <div class="row">
        <div class="text">
          <span class="name">Notification size</span>
          <span class="hint">On top of your Windows display and text size.</span>
        </div>
        <Segmented
          label="Notification size"
          value={hub.prefs.islandSize}
          options={[
            { value: 'small', label: 'Small' },
            { value: 'default', label: 'Default' },
            { value: 'large', label: 'Large' },
          ]}
          onchange={(islandSize: IslandSize) => setPref({ islandSize })}
        />
      </div>
      <div class="row">
        <div class="text">
          <span class="name">Position</span>
          <span class="hint">Where requests appear on your screen.</span>
        </div>
        <Segmented
          label="Position"
          value={hub.prefs.position}
          options={[
            { value: 'island', label: 'Top centre' },
            { value: 'corner', label: 'Bottom right' },
          ]}
          onchange={(position: OverlayPosition) => setPref({ position })}
        />
      </div>
    </div>
  </section>

  <section>
    <Eyebrow>This PC</Eyebrow>
    <div class="group surface">
      <div class="row">
        <div class="text">
          <span class="name">Sound</span>
          <span class="hint">A short tone when a request arrives.</span>
        </div>
        <Toggle label="Sound" checked={hub.prefs.sound} onchange={(sound) => setPref({ sound })} />
      </div>
      <div class="row">
        <div class="text">
          <span class="name">Start with Windows</span>
          <span class="hint">Nod waits in the tray and appears when Claude Code needs you.</span>
        </div>
        <Toggle
          label="Start with Windows"
          checked={hub.prefs.autostart}
          onchange={(autostart) => setPref({ autostart })}
        />
      </div>
    </div>
  </section>

  <section>
    <Eyebrow>Connection</Eyebrow>
    <div class="group surface">
      <div class="row">
        <span class="name">Status</span>
        <ConnectionBadge status={hub.status} paused={hub.settings.paused} />
      </div>
      <div class="row">
        <span class="name">Relay</span>
        <span class="value mono">{hub.relayUrl}</span>
      </div>
      <div class="row">
        <span class="name">Device name</span>
        <span class="value">{hub.deviceName}</span>
      </div>
      <div class="row">
        {#if confirmUnpair}
          <span class="hint">Remove this PC from Nod? You can pair it again at any time.</span>
          <div class="actions">
            <button class="btn" onclick={() => (confirmUnpair = false)}>Cancel</button>
            <button class="btn danger" onclick={() => run(api.unpair)}>Unpair</button>
          </div>
        {:else}
          <div class="text">
            <span class="name">Unpair</span>
            <span class="hint">Revokes this PC's access on the relay.</span>
          </div>
          <button class="btn danger" onclick={() => (confirmUnpair = true)}>Unpair this PC</button>
        {/if}
      </div>
    </div>
  </section>

  <section>
    <Eyebrow>About</Eyebrow>
    <div class="group surface">
      <div class="row">
        <div class="text">
          <span class="name">Nod {hub.version}</span>
          <span class="hint">
            {#if hub.update}
              Update {hub.update.version} available
            {:else if checked}
              You're on the latest version.
            {:else}
              Checks for updates automatically.
            {/if}
          </span>
        </div>
        {#if hub.update}
          <button class="btn primary" disabled={installing} onclick={install}>
            {installing ? 'Installing…' : 'Install and restart'}
          </button>
        {:else}
          <button class="btn" disabled={checking} onclick={checkNow}>
            {checking ? 'Checking…' : 'Check for updates'}
          </button>
        {/if}
      </div>
      <div class="row">
        <span class="name">Release notes</span>
        <button class="btn text" onclick={() => run(api.openReleaseNotes)}>
          Open on GitHub ↗
        </button>
      </div>
    </div>
  </section>

  {#if error}<p class="error">{error}</p>{/if}
</div>

<style>
  .settings {
    display: grid;
    gap: 40px;
    max-width: 720px;
    margin: 0 auto;
    padding: 48px 32px 72px;
  }
  section {
    display: grid;
    gap: 14px;
  }
  .hero {
    gap: 14px;
  }
  .hero .group {
    margin-top: 10px;
  }
  .row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 24px;
    min-height: 64px;
    padding: 12px 20px;
  }
  .row.stacked {
    display: grid;
    gap: 12px;
    padding: 18px 20px;
  }
  .row + .row {
    border-top: 1px solid var(--c-rule);
  }
  .text {
    display: grid;
    gap: 2px;
  }
  .name {
    font-size: var(--type-small-size);
    font-weight: 500;
  }
  .hint {
    margin: 0;
    font-size: var(--type-small-size);
    color: var(--c-muted-foreground);
  }
  .value {
    color: var(--c-muted-foreground);
    overflow-wrap: anywhere;
    text-align: right;
    user-select: text;
  }
  .actions {
    display: flex;
    gap: 8px;
  }
  .error {
    margin: 0;
    color: var(--c-danger);
  }
</style>
