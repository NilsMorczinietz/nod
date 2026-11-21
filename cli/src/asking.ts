/**
 * Agent-agnostic requests: used by `nod ask`, `nod notify` and the MCP server,
 * so any tool – not only Claude Code hooks – can ask the user through Nod.
 */
import { createHash } from 'node:crypto'
import { hostname } from 'node:os'
import { basename } from 'node:path'
import type { Item, NewItem, SessionRef } from '@nod/protocol'
import type { Config } from './config.ts'
import { RelayClient } from './relay-client.ts'
import { waitForDecision } from './wait.ts'

export interface AskOptions {
  agent: string
  /** Stable id for the conversation/process; defaults to agent + cwd. */
  sessionId?: string
  timeoutSeconds?: number
}

export const sessionFor = (agent: string, sessionId?: string): SessionRef => {
  const cwd = process.cwd()
  const id =
    sessionId ??
    `${agent.toLowerCase().replace(/\W+/g, '-')}-${createHash('sha256').update(cwd).digest('hex').slice(0, 12)}`
  return { id, agent, project: basename(cwd) || cwd, cwd, host: hostname() }
}

export const approvalItem = (session: SessionRef, action: string, details?: string): NewItem => ({
  kind: 'permission',
  session,
  title: action,
  body: details?.split('\n', 1)[0] ?? action,
  permission: {
    tool: session.agent ?? 'Agent',
    summary: details?.split('\n', 1)[0] ?? action,
    detail: details,
    canAlwaysAllow: false,
  },
})

export const questionItem = (
  session: SessionRef,
  question: string,
  options: string[],
  multiSelect: boolean,
  details?: string,
): NewItem => ({
  kind: 'question',
  session,
  title: question.length > 80 ? `${question.slice(0, 79)}…` : question,
  body: details ?? question,
  questions: [{ question, header: '', multiSelect, options: options.map((label) => ({ label })) }],
})

export const notifyItem = (
  session: SessionRef,
  title: string,
  message: string | undefined,
  level: 'done' | 'failed' | 'info',
): NewItem => ({
  kind: level === 'done' ? 'completed' : level === 'failed' ? 'failed' : 'attention',
  session,
  title,
  body: message,
})

export type Outcome =
  | { status: 'approved' }
  | { status: 'denied'; reason?: string }
  | { status: 'answered'; answers: string[] }
  | { status: 'no_answer'; reason: 'timeout' | 'undelivered' | 'expired' | 'cancelled' }

const outcomeOf = (item: Item | null): Outcome => {
  if (!item) return { status: 'no_answer', reason: 'timeout' }
  if (item.status === 'expired' || item.status === 'cancelled')
    return { status: 'no_answer', reason: item.status }
  const r = item.resolution
  if (!r) return { status: 'no_answer', reason: 'timeout' }
  if (r.decision === 'deny')
    return { status: 'denied', ...(r.message ? { reason: r.message } : {}) }
  if (r.decision === 'answer')
    return { status: 'answered', answers: Object.values(r.answers ?? {}).flat() }
  return { status: 'approved' }
}

/**
 * Sends an interactive item and waits for the answer.
 * `onCreated` receives the item id (e.g. to cancel it when the caller aborts).
 */
export const ask = async (
  config: Config,
  newItem: NewItem,
  timeoutSeconds: number,
  onCreated?: (id: string) => void,
): Promise<Outcome> => {
  const client = new RelayClient(config.relayUrl, config.adminToken)
  const { item, delivered } = await client.createItem({ ...newItem, ttlSeconds: timeoutSeconds })
  if (!delivered) return { status: 'no_answer', reason: 'undelivered' }
  onCreated?.(item.id)
  return outcomeOf(await waitForDecision(client, item, item.expiresAt + 5_000))
}

export const send = async (config: Config, newItem: NewItem): Promise<boolean> => {
  const client = new RelayClient(config.relayUrl, config.adminToken)
  return (await client.createItem(newItem)).delivered
}

export const cancel = async (config: Config, id: string): Promise<void> => {
  await new RelayClient(config.relayUrl, config.adminToken).cancelItem(id).catch(() => undefined)
}
