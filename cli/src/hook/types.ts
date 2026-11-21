/**
 * The subset of Claude Code hook payloads Nod uses.
 * Reference: https://code.claude.com/docs/en/hooks
 */

export interface PermissionSuggestion {
  type: string
  behavior?: string
  [key: string]: unknown
}

interface BaseInput {
  session_id: string
  transcript_path?: string
  cwd: string
  permission_mode?: string
  hook_event_name: string
}

export interface PermissionRequestInput extends BaseInput {
  hook_event_name: 'PermissionRequest'
  tool_name: string
  tool_input: Record<string, unknown>
  permission_suggestions?: PermissionSuggestion[]
}

export interface PreToolUseInput extends BaseInput {
  hook_event_name: 'PreToolUse'
  tool_name: string
  tool_input: Record<string, unknown>
  tool_use_id?: string
}

export interface StopInput extends BaseInput {
  hook_event_name: 'Stop'
  stop_hook_active?: boolean
  last_assistant_message?: string
}

export interface StopFailureInput extends BaseInput {
  hook_event_name: 'StopFailure'
  error?: unknown
  message?: string
}

export interface NotificationInput extends BaseInput {
  hook_event_name: 'Notification'
  message: string
  title?: string
  notification_type?: string
}

export interface UserPromptSubmitInput extends BaseInput {
  hook_event_name: 'UserPromptSubmit'
  prompt?: string
}

export type HookInput =
  | PermissionRequestInput
  | PreToolUseInput
  | StopInput
  | StopFailureInput
  | NotificationInput
  | UserPromptSubmitInput

/** What the hook prints on stdout. `null` means "no decision": Claude Code asks in the terminal. */
export type HookOutput = Record<string, unknown> | null
