<script lang="ts">
  import { renderMarkdown } from '../lib/markdown'

  let { source }: { source: string } = $props()
  const html = $derived(renderMarkdown(source))
</script>

<!-- Safe: renderMarkdown escapes all input before adding its own tags. -->
<div class="markdown">{@html html}</div>

<style>
  .markdown {
    font-size: 13.5px;
    line-height: 1.6;
    color: var(--c-foreground);
    user-select: text;
  }
  .markdown :global(h2),
  .markdown :global(h3),
  .markdown :global(h4) {
    margin: 12px 0 4px;
    font-size: 14px;
    font-weight: 550;
  }
  .markdown :global(h2:first-child),
  .markdown :global(h3:first-child) {
    margin-top: 0;
  }
  .markdown :global(p) {
    margin: 4px 0;
  }
  .markdown :global(ul),
  .markdown :global(ol) {
    margin: 4px 0;
    padding-left: 20px;
  }
  .markdown :global(code) {
    font-family: var(--font-mono);
    font-size: 12.5px;
    padding: 1px 5px;
    border-radius: 6px;
    background: var(--c-accent);
  }
  .markdown :global(pre) {
    margin: 8px 0;
    padding: 10px 12px;
    border-radius: var(--radius-md);
    background: var(--c-accent);
    overflow-x: auto;
  }
  .markdown :global(pre code) {
    padding: 0;
    background: none;
  }
</style>
