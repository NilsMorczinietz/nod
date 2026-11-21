<!-- Section label: a 24×1 rule, then Geist Mono 11/500 uppercase in primary. -->
<script lang="ts">
  import type { Snippet } from 'svelte'

  let { children }: { children: Snippet } = $props()
</script>

<p class="eyebrow">{@render children()}</p>

<style>
  .eyebrow {
    display: flex;
    align-items: center;
    gap: 10px;
    margin: 0;
    font-family: var(--font-mono);
    font-size: var(--type-eyebrow-size);
    font-weight: var(--type-eyebrow-weight);
    letter-spacing: var(--type-eyebrow-tracking);
    text-transform: uppercase;
    color: var(--c-primary-text);
  }
  .eyebrow::before {
    content: '';
    width: 24px;
    height: 1px;
    background: color-mix(in srgb, var(--c-primary-text) 30%, transparent);
  }
</style>
