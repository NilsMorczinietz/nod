/**
 * `nod mcp`: a Model Context Protocol server (stdio) so any MCP-capable agent –
 * Cursor, Claude Desktop, Codex, Gemini CLI, Windsurf, … – can ask the user
 * through Nod. Dependency-free: newline-delimited JSON-RPC 2.0 on stdin/stdout.
 *
 * Unlike Claude Code hooks this is cooperative: the agent decides when to call
 * the tools. The server instructions tell it to do so before risky actions.
 */
import { createInterface } from 'node:readline'
import {
  approvalItem,
  ask,
  cancel,
  notifyItem,
  questionItem,
  send,
  sessionFor,
  type Outcome,
} from '../asking.ts'
import { loadConfig, type Config } from '../config.ts'
import { logToFile } from '../log.ts'
import { VERSION } from '../version.ts'

const SUPPORTED_VERSIONS = ['2025-11-25', '2025-06-18', '2025-03-26', '2024-11-05']

const INSTRUCTIONS = `Nod reaches the user on their phone and desktop, even when they are away from the computer.
Call request_approval before actions that are destructive, irreversible, costly or leave the machine
(deleting data, deploying, pushing, sending messages, spending money). Call ask_human when you need
a decision to continue. Call notify when a long task is finished or failed.
Respect the answer: never perform an action that was denied or not answered.`

const TOOLS = [
  {
    name: 'request_approval',
    title: 'Request approval',
    description:
      'Ask the user to approve or deny an action on their phone/desktop. Blocks until they answer or the request expires. Returns whether the action is approved.',
    inputSchema: {
      type: 'object',
      properties: {
        action: {
          type: 'string',
          description: 'Short question, e.g. "Deploy api-gateway to production?"',
        },
        details: {
          type: 'string',
          description: 'Optional details: the command, affected files, amounts.',
        },
      },
      required: ['action'],
    },
    annotations: { readOnlyHint: true, openWorldHint: true },
  },
  {
    name: 'ask_human',
    title: 'Ask the user',
    description:
      'Ask the user a question with predefined answer options on their phone/desktop. Blocks until they answer or the request expires.',
    inputSchema: {
      type: 'object',
      properties: {
        question: {
          type: 'string',
          description: 'The question, e.g. "Which database should the service use?"',
        },
        options: {
          type: 'array',
          items: { type: 'string' },
          minItems: 2,
          maxItems: 8,
          description: 'Answer options.',
        },
        multi_select: { type: 'boolean', description: 'Allow several options. Default false.' },
        details: { type: 'string', description: 'Optional context shown with the question.' },
      },
      required: ['question', 'options'],
    },
    annotations: { readOnlyHint: true, openWorldHint: true },
  },
  {
    name: 'notify',
    title: 'Notify the user',
    description:
      'Send a one-way notification, e.g. when a long task finished or failed. Does not wait.',
    inputSchema: {
      type: 'object',
      properties: {
        title: { type: 'string', description: 'e.g. "Migration finished"' },
        message: { type: 'string', description: 'One or two sentences.' },
        status: {
          type: 'string',
          enum: ['done', 'failed', 'info'],
          description: 'Default "done".',
        },
      },
      required: ['title'],
    },
    annotations: { readOnlyHint: true, openWorldHint: true },
  },
] as const

type Json = Record<string, unknown>
interface Request {
  jsonrpc: '2.0'
  id?: string | number
  method: string
  params?: Json
}

const write = (message: Json) =>
  process.stdout.write(`${JSON.stringify({ jsonrpc: '2.0', ...message })}\n`)
const text = (value: string, isError = false) => ({
  content: [{ type: 'text', text: value }],
  ...(isError ? { isError: true } : {}),
})

/** "cursor-vscode" → "Cursor", "claude-ai" → "Claude"; NOD_AGENT overrides. */
const agentName = (clientName: unknown): string => {
  if (process.env.NOD_AGENT) return process.env.NOD_AGENT
  const raw = typeof clientName === 'string' && clientName ? clientName : 'MCP agent'
  const first = raw.split(/[-_\s]/)[0] ?? raw
  return first.charAt(0).toUpperCase() + first.slice(1)
}

const describe = (
  outcome: Outcome,
  timeoutSeconds: number,
): { message: string; isError: boolean } => {
  switch (outcome.status) {
    case 'approved':
      return {
        message: 'APPROVED: the user approved this action. You may proceed.',
        isError: false,
      }
    case 'denied':
      return {
        message: `DENIED: the user denied this.${outcome.reason ? ` Reason: ${outcome.reason}` : ''} Do not proceed with it.`,
        isError: false,
      }
    case 'answered':
      return { message: `The user answered: ${outcome.answers.join(', ')}`, isError: false }
    case 'no_answer': {
      const why =
        outcome.reason === 'undelivered'
          ? 'Nod is paused or no device is paired.'
          : `Nobody answered within ${Math.round(timeoutSeconds / 60)} minutes.`
      return {
        message: `NO ANSWER: ${why} Do not proceed; ask the user in the conversation instead.`,
        isError: false,
      }
    }
  }
}

const str = (v: unknown) => (typeof v === 'string' && v.trim() ? v.trim() : undefined)

export const runMcpServer = async (): Promise<void> => {
  let agent = agentName(undefined)
  const pending = new Map<string | number, string>() // request id → Nod item id

  const callTool = async (id: string | number, params: Json): Promise<Json> => {
    const config: Config | null = await loadConfig()
    if (!config)
      return text('Nod is not configured on this machine. Ask the user to run `nod setup`.', true)
    const args = (params.arguments ?? {}) as Json
    const session = sessionFor(agent)
    const timeout = config.waitSeconds

    // Keep clients with short tool timeouts informed while we wait.
    const progressToken = (params._meta as Json | undefined)?.progressToken
    const started = Date.now()
    const ticker =
      progressToken === undefined
        ? undefined
        : setInterval(() => {
            write({
              method: 'notifications/progress',
              params: {
                progressToken,
                progress: Math.round((Date.now() - started) / 1000),
                total: timeout,
                message: 'Waiting for the user to answer on their device…',
              },
            })
          }, 15_000)

    try {
      switch (params.name) {
        case 'request_approval': {
          const action = str(args.action)
          if (!action) return text('`action` is required.', true)
          const outcome = await ask(
            config,
            approvalItem(session, action, str(args.details)),
            timeout,
            (itemId) => pending.set(id, itemId),
          )
          const { message, isError } = describe(outcome, timeout)
          return text(message, isError)
        }
        case 'ask_human': {
          const question = str(args.question)
          const options = Array.isArray(args.options)
            ? args.options.map(String).filter(Boolean).slice(0, 8)
            : []
          if (!question || options.length < 2)
            return text('`question` and at least two `options` are required.', true)
          const item = questionItem(
            session,
            question,
            options,
            args.multi_select === true,
            str(args.details),
          )
          const outcome = await ask(config, item, timeout, (itemId) => pending.set(id, itemId))
          const { message, isError } = describe(outcome, timeout)
          return text(message, isError)
        }
        case 'notify': {
          const title = str(args.title)
          if (!title) return text('`title` is required.', true)
          const status = args.status === 'failed' || args.status === 'info' ? args.status : 'done'
          const delivered = await send(
            config,
            notifyItem(session, title, str(args.message), status),
          )
          return text(
            delivered
              ? 'Notification sent.'
              : 'Not delivered (Nod is paused or no device is paired).',
          )
        }
        default:
          return text(`Unknown tool: ${String(params.name)}`, true)
      }
    } finally {
      if (ticker) clearInterval(ticker)
      pending.delete(id)
    }
  }

  const handle = async (request: Request): Promise<void> => {
    const { id, method, params = {} } = request
    try {
      switch (method) {
        case 'initialize': {
          const clientInfo = params.clientInfo as Json | undefined
          agent = agentName(clientInfo?.name)
          const requested = String(params.protocolVersion ?? '')
          write({
            id,
            result: {
              protocolVersion: SUPPORTED_VERSIONS.includes(requested)
                ? requested
                : SUPPORTED_VERSIONS[0],
              capabilities: { tools: {} },
              serverInfo: { name: 'nod', title: 'Nod', version: VERSION },
              instructions: INSTRUCTIONS,
            },
          })
          return
        }
        case 'ping':
          write({ id, result: {} })
          return
        case 'tools/list':
          write({ id, result: { tools: TOOLS } })
          return
        case 'tools/call':
          write({ id, result: await callTool(id ?? 0, params) })
          return
        case 'notifications/cancelled': {
          const itemId = pending.get(params.requestId as string | number)
          const config = await loadConfig()
          if (itemId && config) await cancel(config, itemId)
          return
        }
        default:
          if (id !== undefined)
            write({ id, error: { code: -32601, message: `Method not found: ${method}` } })
      }
    } catch (error) {
      await logToFile(`mcp ${method} failed`, error)
      if (id !== undefined)
        write({ id, error: { code: -32603, message: (error as Error).message } })
    }
  }

  const lines = createInterface({ input: process.stdin })
  lines.on('line', (line) => {
    if (!line.trim()) return
    let request: Request
    try {
      request = JSON.parse(line) as Request
    } catch {
      write({ id: null, error: { code: -32700, message: 'Parse error' } })
      return
    }
    void handle(request)
  })
  await new Promise<void>((resolve) => lines.once('close', resolve))
}
