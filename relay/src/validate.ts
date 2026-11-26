/**
 * Hand-written input validation. Keeps the worker dependency-free and small;
 * every string is length-limited so a single item can never blow up storage
 * or the 4 KB FCM payload.
 */
import {
  INFO_KINDS,
  INTERACTIVE_KINDS,
  ROUTING_MODES,
  type DeviceKind,
  type ItemKind,
  type NewItem,
  type Question,
  type RegisterDeviceInput,
  type ResolveInput,
  type RoutingMode,
} from '@nod/protocol'
import { HttpError } from './http'

type Json = Record<string, unknown>

const fail = (message: string): never => {
  throw new HttpError(400, 'invalid_request', message)
}

const isObject = (value: unknown): value is Json =>
  typeof value === 'object' && value !== null && !Array.isArray(value)

const object = (value: unknown, field: string): Json =>
  isObject(value) ? value : fail(`${field} must be an object`)

/**
 * Over-long text loses its middle, not its end: questions and messages tend
 * to put the decision last.
 */
export const clipMiddle = (value: string, max: number): string => {
  if (value.length <= max) return value
  const head = Math.floor((max - 1) / 3)
  return `${value.slice(0, head)}…${value.slice(value.length - (max - 1 - head))}`
}

const string = (value: unknown, field: string, max: number): string => {
  if (typeof value !== 'string') return fail(`${field} must be a string`)
  return clipMiddle(value, max)
}

const optionalString = (value: unknown, field: string, max: number): string | undefined =>
  value === undefined || value === null ? undefined : string(value, field, max)

const ITEM_KINDS: readonly string[] = [...INTERACTIVE_KINDS, ...INFO_KINDS]

export const MIN_TTL_SECONDS = 30
export const MAX_TTL_SECONDS = 60 * 60
export const DEFAULT_TTL_SECONDS = 10 * 60

const parseQuestion = (value: unknown, index: number): Question => {
  const q = object(value, `questions[${index}]`)
  const options = Array.isArray(q.options)
    ? q.options.slice(0, 8)
    : fail('options must be an array')
  return {
    question: string(q.question, 'question', 1000),
    header: string(q.header ?? '', 'header', 40),
    multiSelect: q.multiSelect === true,
    ...(q.allowText === true ? { allowText: true } : {}),
    options: options.map((raw, i) => {
      const option = object(raw, `options[${i}]`)
      return {
        label: string(option.label, 'label', 120),
        description: optionalString(option.description, 'description', 300),
      }
    }),
  }
}

export const parseNewItem = (input: unknown): NewItem => {
  const body = object(input, 'body')
  const kind = string(body.kind, 'kind', 20) as ItemKind
  if (!ITEM_KINDS.includes(kind)) fail(`unknown kind "${kind}"`)

  const session = object(body.session, 'session')
  const item: NewItem = {
    kind,
    session: {
      id: string(session.id, 'session.id', 100),
      agent: optionalString(session.agent, 'session.agent', 40),
      project: string(session.project, 'session.project', 100),
      cwd: string(session.cwd, 'session.cwd', 500),
      host: string(session.host, 'session.host', 100),
    },
    title: string(body.title, 'title', 200),
    body: optionalString(body.body, 'body', 4000),
  }

  if (typeof body.ttlSeconds === 'number' && Number.isFinite(body.ttlSeconds)) {
    item.ttlSeconds = Math.round(
      Math.min(MAX_TTL_SECONDS, Math.max(MIN_TTL_SECONDS, body.ttlSeconds)),
    )
  }

  if (kind === 'permission') {
    const p = object(body.permission, 'permission')
    item.permission = {
      tool: string(p.tool, 'permission.tool', 200),
      summary: string(p.summary, 'permission.summary', 500),
      detail: optionalString(p.detail, 'permission.detail', 8000),
      canAlwaysAllow: p.canAlwaysAllow === true,
    }
  }
  if (kind === 'question') {
    const questions = Array.isArray(body.questions)
      ? body.questions
      : fail('questions must be an array')
    if (questions.length === 0 || questions.length > 4) fail('questions must contain 1–4 entries')
    item.questions = questions.map(parseQuestion)
  }
  if (kind === 'plan') {
    const plan = object(body.plan, 'plan')
    item.plan = { markdown: string(plan.markdown, 'plan.markdown', 60_000) }
  }
  return item
}

export const parseResolveInput = (input: unknown): ResolveInput => {
  const body = object(input, 'body')
  const decision = string(body.decision, 'decision', 10)
  if (decision !== 'allow' && decision !== 'deny' && decision !== 'answer') {
    return fail('decision must be allow, deny or answer')
  }
  const result: ResolveInput = { decision }
  if (body.alwaysAllow === true) result.alwaysAllow = true
  const message = optionalString(body.message, 'message', 2000)
  if (message) result.message = message
  if (body.answers !== undefined) {
    const answers = object(body.answers, 'answers')
    result.answers = Object.fromEntries(
      Object.entries(answers)
        .slice(0, 4)
        .map(([question, labels]) => {
          const list = Array.isArray(labels) ? labels : fail('answers values must be arrays')
          return [question, list.slice(0, 8).map((l, i) => string(l, `answers[${i}]`, 500))]
        }),
    )
  }
  return result
}

const DEVICE_KINDS: readonly DeviceKind[] = ['android', 'desktop']

export const parseRegisterDevice = (input: unknown): RegisterDeviceInput => {
  const body = object(input, 'body')
  const kind = string(body.kind, 'kind', 20) as DeviceKind
  if (!DEVICE_KINDS.includes(kind)) fail('kind must be android or desktop')
  return {
    code: string(body.code, 'code', 20),
    name: string(body.name, 'name', 60).trim() || kind,
    kind,
  }
}

export const parsePushToken = (input: unknown): string => {
  const body = object(input, 'body')
  return string(body.pushToken, 'pushToken', 4096)
}

export const parseSettingsPatch = (input: unknown): { mode?: RoutingMode; paused?: boolean } => {
  const body = object(input, 'body')
  const patch: { mode?: RoutingMode; paused?: boolean } = {}
  if (body.mode !== undefined) {
    if (!(ROUTING_MODES as readonly unknown[]).includes(body.mode)) {
      fail(`mode must be one of ${ROUTING_MODES.join(', ')}`)
    }
    patch.mode = body.mode as RoutingMode
  }
  if (typeof body.paused === 'boolean') patch.paused = body.paused
  return patch
}

/** Parses a client → relay WebSocket message; returns null for anything unknown. */
export const parsePresence = (raw: string): boolean | null => {
  try {
    const message = JSON.parse(raw) as unknown
    return isObject(message) && message.type === 'presence' && typeof message.active === 'boolean'
      ? message.active
      : null
  } catch {
    return null
  }
}
