<!--
  Status glyph for the island: a check or cross that draws itself once
  (stroke-dashoffset, no overshoot), or a plain dot.
-->
<script lang="ts">
  let {
    kind,
    tone,
    duration = 240,
    size = 14,
  }: { kind: 'check' | 'cross' | 'dot'; tone: string; duration?: number; size?: number } = $props()
</script>

{#if kind === 'dot'}
  <span class="dot" style:background={tone}></span>
{:else}
  <svg
    viewBox="0 0 16 16"
    width={size}
    height={size}
    style:color={tone}
    style:--draw="{duration}ms"
    aria-hidden="true"
  >
    {#if kind === 'check'}
      <path d="M3.2 8.4l3 3 6.6-7" pathLength="1" />
    {:else}
      <path d="M4 4l8 8M12 4l-8 8" pathLength="1" />
    {/if}
  </svg>
{/if}

<style>
  .dot {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    flex: none;
  }
  svg {
    flex: none;
    display: block;
  }
  path {
    fill: none;
    stroke: currentColor;
    stroke-width: 2;
    stroke-linecap: round;
    stroke-linejoin: round;
    stroke-dasharray: 1;
    animation: draw var(--draw) var(--motion-button-easing) both;
  }
  @keyframes draw {
    from {
      stroke-dashoffset: 1;
    }
    to {
      stroke-dashoffset: 0;
    }
  }
</style>
