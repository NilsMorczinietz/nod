/** Terminal styling and a tiny append-only log for hook runs (hooks must stay silent). */
import { appendFile, mkdir, stat, rename } from 'node:fs/promises'
import { join } from 'node:path'
import { stateDir } from './config.ts'

const useColor = process.stdout.isTTY && !process.env.NO_COLOR
const paint = (code: number) => (text: string) => (useColor ? `\x1b[${code}m${text}\x1b[0m` : text)

export const style = {
  bold: paint(1),
  dim: paint(2),
  green: paint(32),
  red: paint(31),
  yellow: paint(33),
  cyan: paint(36),
}

export const ok = (text: string) => console.log(`${style.green('✓')} ${text}`)
export const warn = (text: string) => console.log(`${style.yellow('!')} ${text}`)
export const fail = (text: string) => console.error(`${style.red('✗')} ${text}`)

const MAX_LOG_BYTES = 512 * 1024

/** Best-effort file log; never throws. Rotates once it grows beyond 512 KB. */
export const logToFile = async (message: string, error?: unknown): Promise<void> => {
  try {
    const dir = stateDir()
    const file = join(dir, 'hook.log')
    await mkdir(dir, { recursive: true })
    const size = await stat(file).then(
      (s) => s.size,
      () => 0,
    )
    if (size > MAX_LOG_BYTES) await rename(file, `${file}.1`)
    const detail =
      error instanceof Error ? ` ${error.name}: ${error.message}` : error ? ` ${String(error)}` : ''
    await appendFile(file, `${new Date().toISOString()} ${message}${detail}\n`)
  } catch {
    // logging must never break a hook
  }
}
