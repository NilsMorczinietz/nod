/**
 * Wire protocol shared by the relay, the CLI and (mirrored by hand) the
 * Android and desktop clients. Bump PROTOCOL_VERSION on breaking changes.
 * See docs/protocol.md for the human-readable version.
 */
export const PROTOCOL_VERSION = 1

/** Interactive kinds block Claude Code until someone answers (or the item expires). */
export const INTERACTIVE_KINDS = ['permission', 'question', 'plan'] as const
/** Informational kinds are fire-and-forget. */
export const INFO_KINDS = ['completed', 'failed', 'attention'] as const

export type InteractiveKind = (typeof INTERACTIVE_KINDS)[number]
export type InfoKind = (typeof INFO_KINDS)[number]
export type ItemKind = InteractiveKind | InfoKind

export type ItemStatus =
  /** Waiting for an answer. */
  | 'pending'
  /** Answered by a device. */
  | 'resolved'
  /** Nobody answered in time; Claude Code falls back to the terminal. */
  | 'expired'
  /** The waiting hook went away (e.g. answered in the terminal). */
  | 'cancelled'
  /** Informational item, nothing to answer. */
  | 'info'

export interface SessionRef {
  /** Session id of the agent (Claude Code session id, MCP connection id, …). */
  id: string
  /**
   * Display name of the agent that asks, e.g. "Claude Code", "Cursor", "Script".
   * Optional for backwards compatibility; clients show "Claude Code" when missing.
   */
  agent?: string
  /** Short project name (basename of the working directory). */
  project: string
  cwd: string
  /** Hostname of the machine running Claude Code. */
  host: string
}

export interface PermissionDetails {
  /** Tool name, e.g. "Bash", "Edit", "mcp__github__create_issue". */
  tool: string
  /** One-line, human-readable summary ("npm test", "Edit src/app.ts"). */
  summary: string
  /** Longer preview (command, diff excerpt, JSON input), already truncated. */
  detail?: string
  /** True when Claude Code offered an "always allow" rule for this call. */
  canAlwaysAllow: boolean
}

export interface QuestionOption {
  label: string
  description?: string
}

export interface Question {
  question: string
  header: string
  multiSelect: boolean
  options: QuestionOption[]
  /**
   * Also accept a free-text reply (sent as ResolveInput.message with decision "answer").
   * Used when Claude ends a turn with a question in prose.
   */
  allowText?: boolean
}

export interface PlanDetails {
  markdown: string
}

/** Body of POST /v1/items. */
export interface NewItem {
  kind: ItemKind
  session: SessionRef
  title: string
  body?: string
  permission?: PermissionDetails
  questions?: Question[]
  plan?: PlanDetails
  /** How long interactive items stay answerable. Clamped by the relay. */
  ttlSeconds?: number
}

export type Decision = 'allow' | 'deny' | 'answer'

/** Body of POST /v1/items/:id/resolve. */
export interface ResolveInput {
  decision: Decision
  /** Persist an "always allow" rule (permission items only). */
  alwaysAllow?: boolean
  /** question text -> chosen option labels (question items only). */
  answers?: Record<string, string[]>
  /** Optional free text: deny reason, plan feedback, or a free-text reply to a question. */
  message?: string
}

export interface Resolution extends ResolveInput {
  deviceId: string
  deviceName: string
  /** Epoch milliseconds. */
  at: number
}

export interface Item extends NewItem {
  id: string
  status: ItemStatus
  /** Epoch milliseconds. */
  createdAt: number
  /** Epoch milliseconds; equals createdAt for info items. */
  expiresAt: number
  resolution?: Resolution
}

/** Response of POST /v1/items. */
export interface CreateItemResponse {
  item: Item
  /**
   * False when nobody will be asked (mode terminal/updates, or no device paired). The hook
   * should then return immediately and let Claude Code ask in the terminal.
   */
  delivered: boolean
}

export type DeviceKind = 'android' | 'desktop'

export interface Device {
  id: string
  name: string
  kind: DeviceKind
  createdAt: number
  lastSeenAt: number
  hasPushToken: boolean
}

/** Public (non-secret) Firebase config so a generic APK can register for FCM. */
export interface FirebaseClientConfig {
  projectId: string
  applicationId: string
  apiKey: string
  senderId: string
}

export interface CreatePairingResponse {
  code: string
  expiresAt: number
  /** Deep link encoded in the QR code: nod://pair?relay=…&code=… */
  link: string
}

export interface RegisterDeviceInput {
  code: string
  name: string
  kind: DeviceKind
}

export interface RegisterDeviceResponse {
  device: Device
  /** Device bearer token. Shown exactly once; the relay only keeps a hash. */
  token: string
  firebase: FirebaseClientConfig | null
}

/**
 * Where requests go:
 * - away:     desktop always; the phone only while no desktop reports the user as active
 * - always:   desktop and phone
 * - updates:  only informational items (done/failed); requests stay in the terminal
 * - terminal: nothing is delivered; Claude Code asks in the terminal (a.k.a. paused)
 */
export const ROUTING_MODES = ['away', 'always', 'updates', 'terminal'] as const
export type RoutingMode = (typeof ROUTING_MODES)[number]

export interface HubSettings {
  mode: RoutingMode
  /** Shorthand for mode === "terminal". PATCH { paused: false } restores the previous mode. */
  paused: boolean
}

// ---------------------------------------------------------------------------
// WebSocket stream (GET /v1/stream). Clients send the text "ping" every ~30 s
// and, desktops only, a PresenceMessage whenever the user becomes active/idle.
// ---------------------------------------------------------------------------

/** Desktop clients report whether the user is at the computer (input within ~3 min). */
export interface PresenceMessage {
  type: 'presence'
  active: boolean
}

export type StreamMessage =
  | { type: 'hello'; protocol: number; pending: Item[]; settings: HubSettings }
  | { type: 'item'; item: Item }
  | { type: 'update'; item: Item }
  | { type: 'settings'; settings: HubSettings }

// ---------------------------------------------------------------------------
// FCM data payload (all values are strings, max ~4 KB).
// ---------------------------------------------------------------------------

export type PushPayload =
  | {
      type: 'item'
      id: string
      kind: ItemKind
      title: string
      body: string
      project: string
      /** Display name of the asking agent (missing = "Claude Code"). */
      agent?: string
      /** JSON-encoded string[] of option labels for single-choice questions (≤ 3). */
      options?: string
      /** "1" when a free-text reply is accepted (question items). */
      allowText?: string
      /** "1" when the permission can be persisted as an "always allow" rule. */
      canAlwaysAllow?: string
      expiresAt: string
    }
  | { type: 'dismiss'; id: string }

export const isInteractive = (kind: ItemKind): kind is InteractiveKind =>
  (INTERACTIVE_KINDS as readonly string[]).includes(kind)
