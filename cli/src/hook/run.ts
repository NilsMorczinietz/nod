/**
 * Entry point for `nod hook`. Claude Code pipes the event JSON to stdin.
 *
 * Golden rule: a hook must never break Claude Code. Every failure path ends
 * with exit code 0 and no output, which makes Claude Code fall back to its
 * normal terminal prompt.
 */
import { mkdir, readdir, readFile, rm, stat, writeFile } from 'node:fs/promises'
import { hostname } from 'node:os'
import { join } from 'node:path'
import { loadConfig, stateDir, type Config } from '../config.ts'
import { logToFile } from '../log.ts'
import { RelayClient } from '../relay-client.ts'
import { waitForDecision } from '../wait.ts'
import { toHookOutput, toNewItem } from './mapping.ts'
import type { HookInput } from './types.ts'

const SESSION_STATE_MAX_AGE_MS = 2 * 24 * 60 * 60 * 1000

const readStdin = async (): Promise<string> => {
  const chunks: Buffer[] = []
  for await (const chunk of process.stdin) chunks.push(chunk as Buffer)
  return Buffer.concat(chunks).toString('utf8')
}

// ---------------------------------------------------------------------------
// Turn timing: UserPromptSubmit stores a timestamp, Stop reads it, so short
// back-and-forth turns don't buzz the phone.
// ---------------------------------------------------------------------------

const sessionFile = (sessionId: string) =>
  join(stateDir(), 'sessions', sessionId.replace(/[^\w-]/g, '_'))

const recordTurnStart = async (sessionId: string): Promise<void> => {
  const dir = join(stateDir(), 'sessions')
  await mkdir(dir, { recursive: true })
  await writeFile(sessionFile(sessionId), String(Date.now()))
  // Opportunistic cleanup of stale sessions.
  const now = Date.now()
  for (const name of await readdir(dir)) {
    const file = join(dir, name)
    const info = await stat(file).catch(() => null)
    if (info && now - info.mtimeMs > SESSION_STATE_MAX_AGE_MS) await rm(file, { force: true })
  }
}

const turnSeconds = async (sessionId: string): Promise<number | null> => {
  const raw = await readFile(sessionFile(sessionId), 'utf8').catch(() => null)
  const started = Number(raw)
  return raw && Number.isFinite(started) ? (Date.now() - started) / 1000 : null
}

// ---------------------------------------------------------------------------

const handle = async (input: HookInput, config: Config): Promise<void> => {
  if (input.hook_event_name === 'UserPromptSubmit') {
    await recordTurnStart(input.session_id)
    return
  }
  if (input.hook_event_name === 'Stop') {
    const seconds = await turnSeconds(input.session_id)
    if (seconds !== null && seconds < config.minTaskSeconds) return
  }

  const newItem = toNewItem(input, {
    host: hostname(),
    projectDir: process.env.CLAUDE_PROJECT_DIR,
    config,
  })
  if (!newItem) return

  const client = new RelayClient(config.relayUrl, config.adminToken)
  const { item, delivered } = await client.createItem(newItem)
  if (item.status === 'info' || !delivered) return

  // If Claude Code kills us (timeout, Ctrl+C), withdraw the request so the
  // phone notification disappears instead of lingering.
  let cancelled = false
  const cancel = async () => {
    if (cancelled) return
    cancelled = true
    await client.cancelItem(item.id).catch(() => undefined)
    process.exit(0)
  }
  for (const signal of ['SIGINT', 'SIGTERM', 'SIGHUP'] as const)
    process.once(signal, () => void cancel())

  const deadline = item.expiresAt + 5_000
  const finished = await waitForDecision(client, item, deadline)
  const output = finished ? toHookOutput(input, finished) : null
  if (output) process.stdout.write(`${JSON.stringify(output)}\n`)
}

export const runHook = async (): Promise<void> => {
  if (process.env.NOD_DISABLED === '1') return
  try {
    const config = await loadConfig()
    if (!config) return
    const input = JSON.parse(await readStdin()) as HookInput
    await handle(input, config)
  } catch (error) {
    await logToFile('hook failed', error)
  }
}
