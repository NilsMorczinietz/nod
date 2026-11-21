import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import type { Item } from '@nod/protocol'
import { DEFAULT_CONFIG } from '../src/config.ts'
import { toHookOutput, toNewItem, type MappingContext } from '../src/hook/mapping.ts'
import type { HookInput } from '../src/hook/types.ts'

const ctx: MappingContext = { host: 'devbox', projectDir: '/work/acme', config: DEFAULT_CONFIG }
const base = { session_id: 's1', cwd: '/work/acme/api', permission_mode: 'default' }

const resolved = (
  newItem: NonNullable<ReturnType<typeof toNewItem>>,
  resolution: Partial<Item['resolution']>,
): Item => ({
  ...newItem,
  id: 'i1',
  status: 'resolved',
  createdAt: 0,
  expiresAt: 1,
  resolution: { decision: 'allow', deviceId: 'd1', deviceName: 'Pixel', at: 1, ...resolution },
})

describe('PermissionRequest', () => {
  const input: HookInput = {
    ...base,
    hook_event_name: 'PermissionRequest',
    tool_name: 'Bash',
    tool_input: { command: 'npm test\n--watch=false', description: 'Run tests' },
    permission_suggestions: [
      {
        type: 'addRules',
        behavior: 'allow',
        rules: [{ toolName: 'Bash', ruleContent: 'npm test' }],
      },
    ],
  }

  it('maps to a permission item with project name and summary', () => {
    const item = toNewItem(input, ctx)!
    assert.equal(item.kind, 'permission')
    assert.equal(item.session.project, 'acme')
    assert.equal(item.title, 'Run tests')
    assert.equal(item.body, 'npm test')
    assert.equal(item.permission?.canAlwaysAllow, true)
  })

  it('returns allow with rules when "always" was chosen', () => {
    const out = toHookOutput(
      input,
      resolved(toNewItem(input, ctx)!, { decision: 'allow', alwaysAllow: true }),
    ) as any
    assert.equal(out.hookSpecificOutput.decision.behavior, 'allow')
    assert.equal(out.hookSpecificOutput.decision.updatedPermissions.length, 1)
  })

  it('returns deny with a message', () => {
    const out = toHookOutput(input, resolved(toNewItem(input, ctx)!, { decision: 'deny' })) as any
    assert.equal(out.hookSpecificOutput.decision.behavior, 'deny')
    assert.match(out.hookSpecificOutput.decision.message, /Pixel/)
  })

  it('yields no output for expired items (terminal fallback)', () => {
    const item = { ...resolved(toNewItem(input, ctx)!, {}), status: 'expired' as const }
    assert.equal(toHookOutput(input, item), null)
  })

  it('skips AskUserQuestion (handled via PreToolUse)', () => {
    assert.equal(toNewItem({ ...input, tool_name: 'AskUserQuestion' }, ctx), null)
  })
})

describe('AskUserQuestion', () => {
  const input: HookInput = {
    ...base,
    hook_event_name: 'PreToolUse',
    tool_name: 'AskUserQuestion',
    tool_input: {
      questions: [
        {
          question: 'Which stack?',
          header: 'Stack',
          multiSelect: true,
          options: [{ label: 'Kotlin' }, { label: 'Rust' }],
        },
      ],
    },
  }

  it('echoes the questions and joins multi-select answers', () => {
    const item = toNewItem(input, ctx)!
    assert.equal(item.kind, 'question')
    const out = toHookOutput(
      input,
      resolved(item, { decision: 'answer', answers: { 'Which stack?': ['Kotlin', 'Rust'] } }),
    ) as any
    assert.equal(out.hookSpecificOutput.permissionDecision, 'allow')
    assert.deepEqual(out.hookSpecificOutput.updatedInput.answers, {
      'Which stack?': 'Kotlin, Rust',
    })
    assert.equal(out.hookSpecificOutput.updatedInput.questions.length, 1)
  })
})

describe('ExitPlanMode', () => {
  const input: HookInput = {
    ...base,
    hook_event_name: 'PreToolUse',
    tool_name: 'ExitPlanMode',
    tool_input: { plan: '# Plan\n- do it' },
  }

  it('passes feedback when the plan is rejected', () => {
    const out = toHookOutput(
      input,
      resolved(toNewItem(input, ctx)!, { decision: 'deny', message: 'smaller steps' }),
    ) as any
    assert.equal(out.hookSpecificOutput.permissionDecision, 'deny')
    assert.match(out.hookSpecificOutput.permissionDecisionReason, /smaller steps/)
  })
})

describe('informational events', () => {
  it('skips Stop while a stop hook is already active', () => {
    assert.equal(toNewItem({ ...base, hook_event_name: 'Stop', stop_hook_active: true }, ctx), null)
  })

  it('strips markdown from the completion summary', () => {
    const item = toNewItem(
      { ...base, hook_event_name: 'Stop', last_assistant_message: '## Done\n**All** tests `pass`' },
      ctx,
    )!
    assert.equal(item.body, 'Done\nAll tests pass')
  })

  it('ignores permission_prompt notifications', () => {
    const n: HookInput = {
      ...base,
      hook_event_name: 'Notification',
      message: 'x',
      notification_type: 'permission_prompt',
    }
    assert.equal(toNewItem(n, ctx), null)
  })
})

describe('compareVersions', async () => {
  const { compareVersions } = await import('../src/version.ts')
  it('orders semantic versions numerically', () => {
    assert.ok(compareVersions('0.10.0', '0.9.9') > 0)
    assert.ok(compareVersions('v1.0.0', '1.0.0') === 0)
    assert.ok(compareVersions('1.2.3', '1.3.0') < 0)
  })
})
