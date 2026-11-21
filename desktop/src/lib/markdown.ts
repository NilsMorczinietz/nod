/**
 * Deliberately tiny Markdown renderer for plan previews. Input is HTML-escaped
 * first, so the output can only contain the tags produced here. Links render
 * as text: the overlay never navigates anywhere.
 */

const escapeHtml = (text: string): string =>
  text.replace(
    /[&<>"']/g,
    (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!,
  )

const inline = (text: string): string =>
  escapeHtml(text)
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/(^|[^*])\*([^*]+)\*/g, '$1<em>$2</em>')
    .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')

export const renderMarkdown = (markdown: string): string => {
  const out: string[] = []
  let list: 'ul' | 'ol' | null = null
  let code: string[] | null = null
  const closeList = () => {
    if (list) out.push(`</${list}>`)
    list = null
  }

  for (const line of markdown.replace(/\r\n/g, '\n').split('\n')) {
    if (code) {
      if (line.startsWith('```')) {
        out.push(`<pre><code>${escapeHtml(code.join('\n'))}</code></pre>`)
        code = null
      } else code.push(line)
      continue
    }
    if (line.startsWith('```')) {
      closeList()
      code = []
      continue
    }
    const heading = /^(#{1,4})\s+(.*)$/.exec(line)
    const bullet = /^\s*[-*+]\s+(.*)$/.exec(line)
    const numbered = /^\s*\d+[.)]\s+(.*)$/.exec(line)
    if (heading) {
      closeList()
      const level = Math.min(4, heading[1]!.length + 1)
      out.push(`<h${level}>${inline(heading[2]!)}</h${level}>`)
    } else if (bullet || numbered) {
      const kind = bullet ? 'ul' : 'ol'
      if (list !== kind) {
        closeList()
        out.push(`<${kind}>`)
        list = kind
      }
      out.push(`<li>${inline((bullet ?? numbered)![1]!)}</li>`)
    } else if (line.trim() === '') {
      closeList()
    } else {
      closeList()
      out.push(`<p>${inline(line)}</p>`)
    }
  }
  if (code) out.push(`<pre><code>${escapeHtml(code.join('\n'))}</code></pre>`)
  closeList()
  return out.join('')
}
