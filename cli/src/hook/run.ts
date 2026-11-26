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
import { sessionRef, toHookOutput, toNewItem } from './mapping.ts'
import { pendingQuestion, replyItem, replyText } from './reply.ts'
import type { HookInput, StopInput } from './types.ts'

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

/** The current turn's marker (its start time); changes whenever the user sends a prompt. */
const turnMarker = (sessionId: string): Promise<string | null> =>
  readFile(sessionFile(sessionId), 'utf8').catch(() => null)

// Pending reply request of a session: { itemId, turn }.
const replyFile = (sessionId: string) => `${sessionFile(sessionId)}.reply`

interface PendingReply {
  itemId: string
  turn: string | null
}

const readPendingReply = async (sessionId: string): Promise<PendingReply | null> => {
  const raw = await readFile(replyFile(sessionId), 'utf8').catch(() => null)
  try {
    return raw ? (JSON.parse(raw) as PendingReply) : null
  } catch {
    return null
  }
}

/** The user answered in the terminal: withdraw the question from the devices. */
const cancelPendingReply = async (sessionId: string, config: Config): Promise<void> => {
  const pending = await readPendingReply(sessionId)
  if (!pending) return
  await rm(replyFile(sessionId), { force: true })
  await new RelayClient(config.relayUrl, config.adminToken)
    .cancelItem(pending.itemId)
    .catch(() => undefined)
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
    await cancelPendingReply(input.session_id, config)
    return
  }
  if (input.hook_event_name === 'Stop') {
    // A turn that ends with a question is handled by the reply hook instead.
    if (config.notify.replies && pendingQuestion(input.last_assistant_message)) return
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

/**
 * `nod hook --reply`: registered on Stop with `asyncRewake`, so it runs in the
 * background and never blocks the terminal. If the turn ended with a question,
 * it asks on the devices and, when the user answers, exits with code 2: Claude
 * Code then wakes the session with the reply (stderr) as its next input.
 * Any other outcome exits 0 silently.
 */
export const runReplyHook = async (): Promise<void> => {
  if (process.env.NOD_DISABLED === '1') return
  try {
    const config = await loadConfig()
    if (!config?.notify.replies) return
    const input = JSON.parse(await readStdin()) as StopInput
    if (input.hook_event_name !== 'Stop' || input.stop_hook_active) return
    const question = pendingQuestion(input.last_assistant_message)
    if (!question) return

    const turn = await turnMarker(input.session_id)
    const session = sessionRef(input, {
      host: hostname(),
      projectDir: process.env.CLAUDE_PROJECT_DIR,
      config,
    })
    const client = new RelayClient(config.relayUrl, config.adminToken)
    const { item, delivered } = await client.createItem(
      replyItem(session, question, config.waitSeconds, input.last_assistant_message),
    )
    if (!delivered) return

    await mkdir(join(stateDir(), 'sessions'), { recursive: true })
    await writeFile(
      replyFile(input.session_id),
      JSON.stringify({ itemId: item.id, turn } satisfies PendingReply),
    )
    for (const signal of ['SIGINT', 'SIGTERM', 'SIGHUP'] as const) {
      process.once(signal, () => void client.cancelItem(item.id).finally(() => process.exit(0)))
    }

    const finished = await waitForDecision(client, item, item.expiresAt + 5_000)

    // Deliver only if nothing happened in the terminal meanwhile: same turn,
    // and the pending request is still ours (UserPromptSubmit removes it).
    const pending = await readPendingReply(input.session_id)
    const current = pending?.itemId === item.id && (await turnMarker(input.session_id)) === turn
    if (pending?.itemId === item.id) await rm(replyFile(input.session_id), { force: true })
    const text = finished && current ? replyText(finished) : null
    if (!finished || !current) await client.cancelItem(item.id).catch(() => undefined)
    if (text) {
      process.stderr.write(`${text}\n`)
      process.exitCode = 2
    }
  } catch (error) {
    await logToFile('reply hook failed', error)
  }
}
