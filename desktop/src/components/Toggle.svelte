<!-- A switch; place it inside a labelled settings row. -->
<script lang="ts">
  let {
    checked,
    label,
    disabled = false,
    onchange,
  }: {
    checked: boolean
    label: string
    disabled?: boolean
    onchange: (value: boolean) => void
  } = $props()
</script>

<input
  type="checkbox"
  role="switch"
  aria-label={label}
  {checked}
  {disabled}
  onchange={(e) => onchange(e.currentTarget.checked)}
/>

<style>
  input {
    appearance: none;
    position: relative;
    flex: none;
    width: 36px;
    height: 20px;
    margin: 0;
    border-radius: 10px;
    background: var(--c-accent);
    border: 1px solid var(--c-rule);
    cursor: pointer;
    transition: background var(--motion-button-duration) var(--motion-button-easing);
  }
  input::after {
    content: '';
    position: absolute;
    top: 2px;
    left: 2px;
    width: 14px;
    height: 14px;
    border-radius: 50%;
    background: var(--c-muted-foreground);
    transition:
      transform var(--motion-button-duration) var(--motion-button-easing),
      background var(--motion-button-duration) var(--motion-button-easing);
  }
  input:checked {
    background: var(--c-button-primary);
    border-color: color-mix(in srgb, var(--c-button-primary) 90%, transparent);
  }
  input:checked::after {
    transform: translateX(16px);
    background: var(--c-button-primary-foreground);
  }
  input:disabled {
    opacity: 0.5;
    cursor: default;
  }
</style>
