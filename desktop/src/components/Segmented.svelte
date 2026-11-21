<!--
  Segmented control: h 32, radius 10, 12.5/500. Active = card + surfaceSm,
  inactive = muted text. The island restyles it through --seg-* variables.
-->
<script lang="ts" generics="T extends string">
  let {
    value,
    options,
    onchange,
    label,
    stretch = false,
  }: {
    value: T
    options: { value: T; label: string }[]
    onchange: (value: T) => void
    label: string
    stretch?: boolean
  } = $props()
</script>

<div class="segmented" class:stretch role="radiogroup" aria-label={label}>
  {#each options as option (option.value)}
    <button
      type="button"
      role="radio"
      aria-checked={value === option.value}
      class:active={value === option.value}
      onclick={() => onchange(option.value)}
    >
      {option.label}
    </button>
  {/each}
</div>

<style>
  .segmented {
    display: inline-flex;
    gap: var(--seg-gap, 2px);
    padding: var(--seg-padding, 2px);
    height: 32px;
    border-radius: var(--radius-lg);
    background: var(--seg-track, var(--c-muted));
    box-sizing: content-box;
  }
  .stretch {
    display: grid;
    grid-auto-columns: minmax(0, 1fr);
    grid-auto-flow: column;
  }
  button {
    height: 100%;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    padding: 0 var(--seg-pad-x, 12px);
    border: var(--seg-border, 0);
    border-radius: var(--radius-md);
    background: var(--seg-bg, transparent);
    color: var(--seg-fg, var(--c-muted-foreground));
    font-size: 12.5px;
    font-weight: 500;
    white-space: nowrap;
    transition:
      background-color var(--motion-button-duration) var(--motion-button-easing),
      box-shadow var(--motion-button-duration) var(--motion-button-easing),
      color var(--motion-button-duration) var(--motion-button-easing);
  }
  button:hover {
    color: var(--seg-fg-hover, var(--c-foreground));
  }
  .active,
  .active:hover {
    background: var(--seg-active-bg, var(--c-card));
    color: var(--seg-active-fg, var(--c-foreground));
    box-shadow: var(--seg-active-shadow, var(--shadow-surface-sm));
  }
</style>
