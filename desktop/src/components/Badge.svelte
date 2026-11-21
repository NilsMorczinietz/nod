<!--
  Status badge (status only): radius 8, 6×2 padding, 12/500.
  approved = primary on primary/10, denied = danger on destructive/10,
  waiting = amber dot + amber text, live = pulsing primary dot.
-->
<script lang="ts">
  import type { BadgeVariant } from '../lib/status'

  let { label, variant }: { label: string; variant: BadgeVariant | 'live' } = $props()
</script>

<span class="badge {variant}">
  {#if variant === 'waiting' || variant === 'live'}<span class="dot"></span>{/if}{label}
</span>

<style>
  .badge {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: var(--badge-padding);
    border-radius: var(--badge-radius);
    font-size: var(--type-xs-size);
    font-weight: var(--type-xs-weight);
    line-height: var(--type-xs-line);
    white-space: nowrap;
    flex: none;
    /* Never stretch inside grid or flex layouts. */
    justify-self: start;
    align-self: center;
  }
  .approved,
  .live {
    color: var(--c-primary-text);
    background: color-mix(in srgb, var(--c-primary) 12%, transparent);
  }
  .denied {
    color: var(--c-danger);
    background: color-mix(in srgb, var(--c-destructive) 15%, transparent);
  }
  .waiting {
    color: var(--c-waiting-text);
    background: color-mix(in srgb, var(--c-waiting) 10%, transparent);
  }
  .neutral {
    color: var(--c-muted-foreground);
    background: var(--c-muted);
  }
  .dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--c-waiting);
  }
  .live .dot {
    width: 6px;
    height: 6px;
    background: var(--c-primary);
  }
  @media (prefers-reduced-motion: no-preference) {
    .live .dot {
      animation: pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
    }
  }
  @keyframes pulse {
    50% {
      opacity: 0.5;
    }
  }
</style>
