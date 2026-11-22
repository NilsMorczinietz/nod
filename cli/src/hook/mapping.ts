/**
 * Pure mapping between Claude Code hook payloads and relay items.
 * Kept side-effect free so it can be unit-tested without a relay.
 */
import { basename } from 'node:path'
import type { Item, NewItem, Question, SessionRef } from '@nod/protocol'
import type { Config } from '../config.ts'
import { plainText, summarizeTool } from './summarize.ts'
import type { HookInput, HookOutput, PermissionRequestInput, PreToolUseInput } from './types.ts'

/** Tools that ask the user something themselves; handled via PreToolUse, not PermissionRequest. */
export const INTERACTIVE_TOOLS = ['AskUserQuestion', 'ExitPlanMode'] as const

export interface MappingContext {
  host: string
  projectDir?: string
  config: Pick<Config, 'waitSeconds' | 'notify'>
}

export const sessionRef = (input: HookInput, ctx: MappingContext): SessionRef => {
  const root = ctx.projectDir || input.cwd
  return {
    id: input.session_id,
    agent: 'Claude Code',
    project: basename(root) || root,
    cwd: input.cwd,
    host: ctx.host,
  }
}

const parseQuestions = (toolInput: Record<string, unknown>): Question[] => {
  const raw = Array.isArray(toolInput.questions) ? toolInput.questions : []
  return raw.map((q: Record<string, unknown>) => ({
    question: String(q.question ?? ''),
    header: String(q.header ?? ''),
    multiSelect: q.multiSelect === true,
    options: (Array.isArray(q.options) ? q.options : []).map((o: Record<string, unknown>) => ({
      label: String(o.label ?? ''),
      ...(typeof o.description === 'string' ? { description: o.description } : {}),
    })),
  }))
}

/** Returns the item to send, or null when this event should not reach the phone. */
export const toNewItem = (input: HookInput, ctx: MappingContext): NewItem | null => {
  const session = sessionRef(input, ctx)
  const ttlSeconds = ctx.config.waitSeconds

  switch (input.hook_event_name) {
    case 'PermissionRequest': {
      if ((INTERACTIVE_TOOLS as readonly string[]).includes(input.tool_name)) return null
      const s = summarizeTool(input.tool_name, input.tool_input, input.cwd)
      const canAlwaysAllow = (input.permission_suggestions ?? []).some(
        (p) => p.behavior === 'allow',
      )
      return {
        kind: 'permission',
        session,
        title: s.title,
        body: s.summary,
        ttlSeconds,
        permission: { tool: input.tool_name, summary: s.summary, detail: s.detail, canAlwaysAllow },
      }
    }
    case 'PreToolUse': {
      if (input.tool_name === 'AskUserQuestion') {
        const questions = parseQuestions(input.tool_input)
        if (questions.length === 0) return null
        const first = questions[0]!
        return {
          kind: 'question',
          session,
          title: first.header ? `${first.header}?` : 'Claude has a question',
          body:
            questions.length > 1
              ? `${first.question} (+${questions.length - 1} more)`
              : first.question,
          ttlSeconds,
          questions,
        }
      }
      if (input.tool_name === 'ExitPlanMode') {
        const markdown = typeof input.tool_input.plan === 'string' ? input.tool_input.plan : ''
        return {
          kind: 'plan',
          session,
          title: 'Plan ready for review',
          body: plainText(markdown, 300),
          ttlSeconds,
          plan: { markdown },
        }
      }
      return null
    }
    case 'Stop':
      if (!ctx.config.notify.completed || input.stop_hook_active) return null
      return {
        kind: 'completed',
        session,
        title: 'Task complete',
        body: plainText(input.last_assistant_message ?? '', 600),
      }
    case 'StopFailure': {
      if (!ctx.config.notify.failed) return null
      const error =
        input.message ??
        (typeof input.error === 'string' ? input.error : JSON.stringify(input.error ?? ''))
      return { kind: 'failed', session, title: 'Task failed', body: plainText(error, 600) }
    }
    case 'Notification': {
      const type = input.notification_type
      const wanted =
        type === 'elicitation_dialog' ||
        type === 'agent_needs_input' ||
        (type === 'idle_prompt' && ctx.config.notify.idle)
      if (!wanted) return null
      return {
        kind: 'attention',
        session,
        title: input.title || 'Claude needs you',
        body: input.message,
      }
    }
    default:
      return null
  }
}

const permissionOutput = (input: PermissionRequestInput, item: Item): HookOutput => {
  const resolution = item.resolution!
  if (resolution.decision === 'allow') {
    const rules = resolution.alwaysAllow
      ? (input.permission_suggestions ?? []).filter((p) => p.behavior === 'allow')
      : []
    return {
      hookSpecificOutput: {
        hookEventName: 'PermissionRequest',
        decision: { behavior: 'allow', ...(rules.length > 0 ? { updatedPermissions: rules } : {}) },
      },
    }
  }
  return {
    hookSpecificOutput: {
      hookEventName: 'PermissionRequest',
      decision: {
        behavior: 'deny',
        message: resolution.message || `Denied by the user via Nod (${resolution.deviceName}).`,
      },
    },
  }
}

const denyTool = (reason: string): HookOutput => ({
  hookSpecificOutput: {
    hookEventName: 'PreToolUse',
    permissionDecision: 'deny',
    permissionDecisionReason: reason,
  },
})

const preToolUseOutput = (input: PreToolUseInput, item: Item): HookOutput => {
  const resolution = item.resolution!
  const allow = (updatedInput?: Record<string, unknown>) => ({
    hookSpecificOutput: {
      hookEventName: 'PreToolUse',
      permissionDecision: 'allow',
      permissionDecisionReason: `Answered via Nod (${resolution.deviceName}).`,
      ...(updatedInput ? { updatedInput } : {}),
    },
  })
  if (input.tool_name === 'AskUserQuestion') {
    if (resolution.decision === 'deny') {
      return denyTool(
        resolution.message ||
          'The user dismissed the question. Ask again in the terminal if needed.',
      )
    }
    // Claude Code expects one string per question; multi-select labels are comma-joined.
    const answers = Object.fromEntries(
      Object.entries(resolution.answers ?? {}).map(([question, labels]) => [
        question,
        labels.join(', '),
      ]),
    )
    return allow({ ...input.tool_input, answers })
  }
  if (input.tool_name === 'ExitPlanMode') {
    if (resolution.decision === 'allow') return allow()
    return denyTool(
      resolution.message
        ? `The user rejected the plan with this feedback: ${resolution.message}`
        : 'The user rejected the plan. Ask what should change.',
    )
  }
  return null
}

/** Converts a finished item into the JSON Claude Code expects. null = let the terminal ask. */
export const toHookOutput = (input: HookInput, item: Item): HookOutput => {
  if (item.status !== 'resolved' || !item.resolution) return null
  if (input.hook_event_name === 'PermissionRequest') return permissionOutput(input, item)
  if (input.hook_event_name === 'PreToolUse') return preToolUseOutput(input, item)
  return null
}
