<!-- Mono block for commands and diffs; "+ " / "- " lines are tinted. -->
<script lang="ts">
  let { text, maxHeight = 'none' }: { text: string; maxHeight?: string } = $props()

  const lines = $derived(
    text.split('\n').map((line) => ({
      line,
      kind: line.startsWith('+ ') ? 'add' : line.startsWith('- ') ? 'del' : 'ctx',
    })),
  )
</script>

<pre class="code" style:max-height={maxHeight}>{#each lines as { line, kind }, i (i)}<span
      class={kind}>{line || ' '}</span
    >{/each}</pre>

<style>
  .code {
    margin: 0;
    padding: 10px 0;
    border-radius: var(--radius-md);
    background: var(--code-surface, var(--c-muted));
    overflow: auto;
    font-family: var(--font-mono);
    font-size: var(--type-code-size);
    line-height: var(--type-code-line);
    user-select: text;
    scrollbar-width: thin;
  }
  span {
    display: block;
    padding: 0 12px;
    white-space: pre-wrap;
    word-break: break-word;
  }
  .add {
    background: color-mix(in srgb, var(--c-primary) 10%, transparent);
  }
  .del {
    background: color-mix(in srgb, var(--c-danger) 10%, transparent);
  }
</style>
