<script lang="ts">
  import type { ConnectionStatus } from '../lib/types'
  import Badge from './Badge.svelte'

  let { status, paused = false }: { status: ConnectionStatus; paused?: boolean } = $props()
</script>

{#if status === 'online' && !paused}
  <Badge label="Live" variant="live" />
{:else if status === 'online'}
  <Badge label="Paused" variant="waiting" />
{:else if status === 'unpaired'}
  <Badge label="Not paired" variant="neutral" />
{:else}
  <Badge label="Reconnecting" variant="waiting" />
{/if}
