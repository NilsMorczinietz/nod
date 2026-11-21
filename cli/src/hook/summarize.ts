/** Turns raw tool inputs into short, phone-friendly titles and previews. */
import { relative, isAbsolute } from 'node:path'

export interface ToolSummary {
  title: string
  summary: string
  detail?: string
}

const clip = (text: string, max: number): string =>
  text.length > max ? `${text.slice(0, max - 1)}…` : text

const firstLine = (text: string): string => text.split('\n', 1)[0] ?? ''

const str = (value: unknown): string => (typeof value === 'string' ? value : '')

const displayPath = (path: string, cwd: string): string => {
  if (!path) return ''
  const rel = isAbsolute(path) ? relative(cwd, path) : path
  return rel && !rel.startsWith('..') ? rel : path
}

const indent = (prefix: string, text: string, maxLines: number): string => {
  const lines = text.split('\n')
  const shown = lines.slice(0, maxLines).map((line) => `${prefix}${line}`)
  if (lines.length > maxLines) shown.push(`${prefix}… ${lines.length - maxLines} more lines`)
  return shown.join('\n')
}

export const summarizeTool = (
  tool: string,
  input: Record<string, unknown>,
  cwd: string,
): ToolSummary => {
  switch (tool) {
    case 'Bash': {
      const command = str(input.command)
      const description = str(input.description)
      return {
        title: description ? clip(description, 80) : 'Run a command',
        summary: clip(firstLine(command), 200),
        detail: clip(command, 6000),
      }
    }
    case 'Edit':
    case 'MultiEdit': {
      const path = displayPath(str(input.file_path), cwd)
      const edits =
        tool === 'MultiEdit' && Array.isArray(input.edits)
          ? (input.edits as Record<string, unknown>[])
          : [input]
      const diff = edits
        .slice(0, 5)
        .map(
          (e) => `${indent('- ', str(e.old_string), 12)}\n${indent('+ ', str(e.new_string), 12)}`,
        )
        .join('\n⋯\n')
      return { title: 'Edit a file', summary: path, detail: clip(diff, 6000) }
    }
    case 'Write':
      return {
        title: 'Write a file',
        summary: displayPath(str(input.file_path), cwd),
        detail: clip(indent('+ ', str(input.content), 40), 6000),
      }
    case 'NotebookEdit':
      return { title: 'Edit a notebook', summary: displayPath(str(input.notebook_path), cwd) }
    case 'Read':
      return { title: 'Read a file', summary: displayPath(str(input.file_path), cwd) }
    case 'Glob':
    case 'Grep':
      return { title: `Search files (${tool})`, summary: clip(str(input.pattern), 200) }
    case 'WebFetch':
      return {
        title: 'Fetch a web page',
        summary: clip(str(input.url), 300),
        detail: str(input.prompt) || undefined,
      }
    case 'WebSearch':
      return { title: 'Search the web', summary: clip(str(input.query), 300) }
    default: {
      const mcp = /^mcp__(.+?)__(.+)$/.exec(tool)
      const json = clip(JSON.stringify(input, null, 2), 6000)
      if (mcp)
        return { title: `${mcp[1]} › ${mcp[2]}`, summary: clip(firstLine(json), 200), detail: json }
      return { title: `Use ${tool}`, summary: clip(JSON.stringify(input), 200), detail: json }
    }
  }
}

/** Light Markdown cleanup for notification bodies. */
export const plainText = (markdown: string, max: number): string =>
  clip(
    markdown
      .replace(/```[\s\S]*?```/g, '[code]')
      .replace(/`([^`]+)`/g, '$1')
      .replace(/\*\*([^*]+)\*\*/g, '$1')
      .replace(/^#{1,6}\s+/gm, '')
      .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
      .replace(/\n{3,}/g, '\n\n')
      .trim(),
    max,
  )
