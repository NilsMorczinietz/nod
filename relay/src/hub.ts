/**
 * The Hub is a single SQLite-backed Durable Object that owns all state of one
 * Nod installation: paired devices, pairing codes and items (requests and
 * notifications). Being single-threaded, it gives us "first answer wins"
 * semantics for free.
 *
 * Connections:
 *  - CLI (hook)   → HTTPS: create items, long-poll for the decision.
 *  - Desktop      → hibernatable WebSocket (free while idle) + HTTPS to resolve.
 *  - Android      → FCM data messages + HTTPS.
 */
import { DurableObject } from 'cloudflare:workers'
import {
  PROTOCOL_VERSION,
  ROUTING_MODES,
  isInteractive,
  type CreateItemResponse,
  type CreatePairingResponse,
  type Device,
  type HubSettings,
  type Item,
  type PushPayload,
  type RegisterDeviceResponse,
  type RoutingMode,
  type StreamMessage,
} from '@nod/protocol'
import { normalizePairingCode, pairingCode, randomToken, secretsEqual, sha256Hex } from './crypto'
import { FcmClient, firebaseClientConfig, parseServiceAccount, type CachedAccessToken } from './fcm'
import { HttpError, bearerToken, errorResponse, json, noContent, readJson } from './http'
import {
  clipMiddle,
  DEFAULT_TTL_SECONDS,
  parseNewItem,
  parsePresence,
  parsePushToken,
  parseRegisterDevice,
  parseResolveInput,
  parseSettingsPatch,
} from './validate'

interface DeviceRow {
  id: string
  name: string
  kind: Device['kind']
  push_token: string | null
  created_at: number
  last_seen_at: number
  [key: string]: SqlStorageValue
}

/** Per-WebSocket state, survives hibernation via serializeAttachment. */
interface SocketState {
  deviceId: string
  /** Desktop reported recent user input. */
  active: boolean
}

type Principal = { type: 'admin' } | { type: 'device'; device: DeviceRow }

const PAIRING_TTL_MS = 10 * 60 * 1000
const MAX_PAIRING_FAILURES = 10
/** A pending item whose hook stopped polling for this long is considered abandoned. */
const ORPHAN_AFTER_MS = 75 * 1000
const MAX_WAIT_SECONDS = 50
const HISTORY_RETENTION_MS = 7 * 24 * 60 * 60 * 1000
const HISTORY_MAX_ROWS = 300
const LAST_SEEN_THROTTLE_MS = 60 * 1000

const SCHEMA = `
  CREATE TABLE IF NOT EXISTS devices (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    kind TEXT NOT NULL,
    token_hash TEXT NOT NULL UNIQUE,
    push_token TEXT,
    created_at INTEGER NOT NULL,
    last_seen_at INTEGER NOT NULL
  );
  CREATE TABLE IF NOT EXISTS pairings (
    code_hash TEXT PRIMARY KEY,
    expires_at INTEGER NOT NULL
  );
  CREATE TABLE IF NOT EXISTS items (
    id TEXT PRIMARY KEY,
    status TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    expires_at INTEGER NOT NULL,
    last_polled_at INTEGER NOT NULL,
    data TEXT NOT NULL
  );
  CREATE INDEX IF NOT EXISTS items_by_status ON items (status, expires_at);
  CREATE INDEX IF NOT EXISTS items_by_created ON items (created_at);
  CREATE TABLE IF NOT EXISTS kv (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
  );
`

const toDevice = (row: DeviceRow): Device => ({
  id: row.id,
  name: row.name,
  kind: row.kind,
  createdAt: row.created_at,
  lastSeenAt: row.last_seen_at,
  hasPushToken: row.push_token !== null,
})

export class Hub extends DurableObject<Env> {
  private readonly sql: SqlStorage
  /** Long-poll waiters keyed by item id. In-memory only; clients simply re-poll. */
  private readonly waiters = new Map<string, Set<(item: Item) => void>>()
  private fcm: FcmClient | null | undefined

  constructor(ctx: DurableObjectState, env: Env) {
    super(ctx, env)
    this.sql = ctx.storage.sql
    this.sql.exec(SCHEMA)
    // Answer keep-alive pings without waking the object from hibernation.
    ctx.setWebSocketAutoResponse(new WebSocketRequestResponsePair('ping', 'pong'))
  }

  // -------------------------------------------------------------------------
  // Routing
  // -------------------------------------------------------------------------

  override async fetch(request: Request): Promise<Response> {
    try {
      return await this.route(request)
    } catch (error) {
      return errorResponse(error)
    }
  }

  private async route(request: Request): Promise<Response> {
    const url = new URL(request.url)
    const parts = url.pathname.split('/').filter(Boolean) // ["v1", ...]
    const method = request.method
    const [, resource, id, action] = parts

    // Unauthenticated: pairing a new device with a one-time code.
    if (resource === 'devices' && !id && method === 'POST') {
      return json(await this.registerDevice(await readJson(request), url.origin), 201)
    }

    const principal = await this.authenticate(request)

    switch (resource) {
      case 'stream':
        return this.openStream(request, this.requireDevice(principal))
      case 'pairings':
        this.requireAdmin(principal)
        if (method === 'POST') return json(await this.createPairing(url.origin), 201)
        break
      case 'devices':
        this.requireAdmin(principal)
        if (!id && method === 'GET') return json({ devices: this.listDevices() })
        if (id && method === 'DELETE') return this.revokeDevice(id)
        break
      case 'me': {
        const device = this.requireDevice(principal)
        if (method === 'GET') return json({ device: toDevice(device), settings: this.settings() })
        if (method === 'DELETE') return this.revokeDevice(device.id)
        if (id === 'push-token' && method === 'PUT') {
          const token = parsePushToken(await readJson(request))
          this.sql.exec('UPDATE devices SET push_token = ? WHERE id = ?', token, device.id)
          return noContent()
        }
        break
      }
      case 'settings':
        if (method === 'GET') return json(this.settings())
        if (method === 'PATCH')
          return json(this.updateSettings(parseSettingsPatch(await readJson(request))))
        break
      case 'items':
        if (!id && method === 'POST') {
          this.requireAdmin(principal)
          return json(await this.createItem(await readJson(request)), 201)
        }
        if (!id && method === 'GET') return json({ items: this.listItems(url.searchParams) })
        if (id && !action && method === 'GET') return json(this.getItem(id))
        if (id && action === 'wait' && method === 'GET') {
          this.requireAdmin(principal)
          return json(await this.waitForItem(id, Number(url.searchParams.get('timeout') ?? 25)))
        }
        if (id && action === 'resolve' && method === 'POST') {
          const device = this.requireDevice(principal)
          return this.resolveItem(id, device, await readJson(request))
        }
        if (id && action === 'cancel' && method === 'POST') {
          this.requireAdmin(principal)
          return json(this.finishItem(id, 'cancelled'))
        }
        break
    }
    throw new HttpError(404, 'not_found')
  }

  // -------------------------------------------------------------------------
  // Authentication
  // -------------------------------------------------------------------------

  private async authenticate(request: Request): Promise<Principal> {
    const token = bearerToken(request)
    if (!token) throw new HttpError(401, 'unauthorized')

    if (this.env.ADMIN_TOKEN && (await secretsEqual(token, this.env.ADMIN_TOKEN))) {
      return { type: 'admin' }
    }
    const hash = await sha256Hex(token)
    const device = this.sql
      .exec<DeviceRow>(
        'SELECT id, name, kind, push_token, created_at, last_seen_at FROM devices WHERE token_hash = ?',
        hash,
      )
      .toArray()[0]
    if (!device) throw new HttpError(401, 'unauthorized')

    const now = Date.now()
    if (now - device.last_seen_at > LAST_SEEN_THROTTLE_MS) {
      this.sql.exec('UPDATE devices SET last_seen_at = ? WHERE id = ?', now, device.id)
      device.last_seen_at = now
    }
    return { type: 'device', device }
  }

  private requireAdmin(principal: Principal): void {
    if (principal.type !== 'admin') throw new HttpError(403, 'forbidden')
  }

  private requireDevice(principal: Principal): DeviceRow {
    if (principal.type !== 'device') throw new HttpError(403, 'forbidden', 'Device token required')
    return principal.device
  }

  // -------------------------------------------------------------------------
  // Pairing & devices
  // -------------------------------------------------------------------------

  private async createPairing(origin: string): Promise<CreatePairingResponse> {
    const code = pairingCode()
    const expiresAt = Date.now() + PAIRING_TTL_MS
    this.sql.exec('DELETE FROM pairings WHERE expires_at < ?', Date.now())
    this.sql.exec(
      'INSERT INTO pairings (code_hash, expires_at) VALUES (?, ?)',
      await sha256Hex(code),
      expiresAt,
    )
    const link = `nod://pair?${new URLSearchParams({ relay: origin, code })}`
    return { code, expiresAt, link }
  }

  private async registerDevice(input: unknown, origin: string): Promise<RegisterDeviceResponse> {
    const { code, name, kind } = parseRegisterDevice(input)
    const codeHash = await sha256Hex(normalizePairingCode(code))
    const deleted = this.sql
      .exec(
        'DELETE FROM pairings WHERE code_hash = ? AND expires_at > ? RETURNING code_hash',
        codeHash,
        Date.now(),
      )
      .toArray()

    if (deleted.length === 0) {
      // Brute-force protection: too many wrong codes invalidate all open pairings.
      const failures = Number(this.kvGet('pairing_failures') ?? 0) + 1
      if (failures >= MAX_PAIRING_FAILURES) {
        this.sql.exec('DELETE FROM pairings')
        this.kvSet('pairing_failures', '0')
      } else {
        this.kvSet('pairing_failures', String(failures))
      }
      throw new HttpError(403, 'invalid_pairing_code', 'Pairing code is invalid or expired')
    }
    this.kvSet('pairing_failures', '0')

    const token = randomToken('nod_dev_')
    const now = Date.now()
    const id = crypto.randomUUID()
    this.sql.exec(
      'INSERT INTO devices (id, name, kind, token_hash, push_token, created_at, last_seen_at) VALUES (?, ?, ?, ?, NULL, ?, ?)',
      id,
      name,
      kind,
      await sha256Hex(token),
      now,
      now,
    )
    console.log(`paired ${kind} device "${name}" via ${origin}`)
    return {
      device: { id, name, kind, createdAt: now, lastSeenAt: now, hasPushToken: false },
      token,
      firebase: firebaseClientConfig(this.env),
    }
  }

  private listDevices(): Device[] {
    return this.sql
      .exec<DeviceRow>(
        'SELECT id, name, kind, push_token, created_at, last_seen_at FROM devices ORDER BY created_at',
      )
      .toArray()
      .map(toDevice)
  }

  private revokeDevice(id: string): Response {
    const removed = this.sql.exec('DELETE FROM devices WHERE id = ? RETURNING id', id).toArray()
    if (removed.length === 0) throw new HttpError(404, 'not_found')
    for (const socket of this.ctx.getWebSockets(id)) socket.close(4001, 'device revoked')
    return noContent()
  }

  // -------------------------------------------------------------------------
  // Settings
  // -------------------------------------------------------------------------

  private mode(): RoutingMode {
    const stored = this.kvGet('mode')
    return (ROUTING_MODES as readonly string[]).includes(stored ?? '')
      ? (stored as RoutingMode)
      : 'away'
  }

  private settings(): HubSettings {
    const mode = this.mode()
    return { mode, paused: mode === 'terminal' }
  }

  private updateSettings(patch: { mode?: RoutingMode; paused?: boolean }): HubSettings {
    let mode = patch.mode
    if (mode === undefined && patch.paused !== undefined) {
      // pause = terminal; resume = whatever was active before pausing.
      mode = patch.paused
        ? 'terminal'
        : ((this.kvGet('resume_mode') as RoutingMode | null) ?? 'away')
    }
    if (mode !== undefined) {
      if (mode !== 'terminal') this.kvSet('resume_mode', mode)
      this.kvSet('mode', mode)
    }
    const settings = this.settings()
    this.broadcast({ type: 'settings', settings })
    return settings
  }

  // -------------------------------------------------------------------------
  // Items
  // -------------------------------------------------------------------------

  private async createItem(input: unknown): Promise<CreateItemResponse> {
    const newItem = parseNewItem(input)
    const now = Date.now()
    const interactive = isInteractive(newItem.kind)
    const ttlSeconds = newItem.ttlSeconds ?? DEFAULT_TTL_SECONDS
    const deviceCount = this.sql.exec<{ n: number }>('SELECT COUNT(*) AS n FROM devices').one().n
    const mode = this.mode()
    const routed = interactive ? mode === 'away' || mode === 'always' : mode !== 'terminal'
    const delivered = routed && deviceCount > 0

    const item: Item = {
      ...newItem,
      id: crypto.randomUUID(),
      // Undeliverable questions are cancelled right away so the hook falls back to the terminal.
      status: interactive ? (delivered ? 'pending' : 'cancelled') : 'info',
      createdAt: now,
      expiresAt: interactive ? now + ttlSeconds * 1000 : now,
    }
    this.saveItem(item, now)

    if (delivered) {
      this.broadcast({ type: 'item', item })
      this.ctx.waitUntil(this.pushItem(item))
      if (interactive) await this.scheduleAlarm()
    }
    return { item, delivered }
  }

  private listItems(params: URLSearchParams): Item[] {
    const limit = Math.min(100, Math.max(1, Number(params.get('limit') ?? 50)))
    const status = params.get('status')
    const rows = status
      ? this.sql.exec<{ data: string }>(
          'SELECT data FROM items WHERE status = ? ORDER BY created_at DESC LIMIT ?',
          status,
          limit,
        )
      : this.sql.exec<{ data: string }>(
          'SELECT data FROM items ORDER BY created_at DESC LIMIT ?',
          limit,
        )
    return rows.toArray().map((row) => JSON.parse(row.data) as Item)
  }

  private findItem(id: string): Item | null {
    const row = this.sql
      .exec<{ data: string }>('SELECT data FROM items WHERE id = ?', id)
      .toArray()[0]
    return row ? (JSON.parse(row.data) as Item) : null
  }

  private getItem(id: string): Item {
    const item = this.findItem(id)
    if (!item) throw new HttpError(404, 'not_found')
    return item
  }

  private saveItem(item: Item, lastPolledAt?: number): void {
    this.sql.exec(
      `INSERT INTO items (id, status, created_at, expires_at, last_polled_at, data)
       VALUES (?, ?, ?, ?, ?, ?)
       ON CONFLICT (id) DO UPDATE SET status = excluded.status, data = excluded.data`,
      item.id,
      item.status,
      item.createdAt,
      item.expiresAt,
      lastPolledAt ?? Date.now(),
      JSON.stringify(item),
    )
  }

  /** Long-poll: resolves as soon as the item leaves "pending", or after `timeout` seconds. */
  private async waitForItem(id: string, timeoutSeconds: number): Promise<Item> {
    const item = this.getItem(id)
    if (item.status !== 'pending') return item
    this.sql.exec('UPDATE items SET last_polled_at = ? WHERE id = ?', Date.now(), id)

    const timeoutMs = Math.min(MAX_WAIT_SECONDS, Math.max(1, timeoutSeconds || 25)) * 1000
    return new Promise<Item>((resolve) => {
      const waiters = this.waiters.get(id) ?? new Set()
      this.waiters.set(id, waiters)
      const done = (result: Item) => {
        clearTimeout(timer)
        waiters.delete(done)
        if (waiters.size === 0) this.waiters.delete(id)
        resolve(result)
      }
      const timer = setTimeout(() => done(this.findItem(id) ?? item), timeoutMs)
      waiters.add(done)
    })
  }

  private resolveItem(id: string, device: DeviceRow, input: unknown): Response {
    const resolveInput = parseResolveInput(input)
    const item = this.getItem(id)
    if (item.status !== 'pending') {
      // Someone else was faster (or it expired) – tell the client what happened.
      return json({ error: 'already_finished', item }, 409)
    }
    item.status = 'resolved'
    item.resolution = {
      ...resolveInput,
      deviceId: device.id,
      deviceName: device.name,
      at: Date.now(),
    }
    this.settle(item)
    return json(item)
  }

  /** Moves a pending item to a terminal state (expired / cancelled). */
  private finishItem(id: string, status: 'expired' | 'cancelled'): Item {
    const item = this.getItem(id)
    if (item.status !== 'pending') return item
    item.status = status
    this.settle(item)
    return item
  }

  private settle(item: Item): void {
    this.saveItem(item)
    // Each waiter removes itself from the set, so iterate over a snapshot.
    for (const notify of Array.from(this.waiters.get(item.id) ?? [])) notify(item)
    this.broadcast({ type: 'update', item })
    this.ctx.waitUntil(this.pushDismiss(item))
  }

  // -------------------------------------------------------------------------
  // Alarm: expiry, orphan detection, history cleanup
  // -------------------------------------------------------------------------

  private async scheduleAlarm(): Promise<void> {
    const next = this.sql
      .exec<{ t: number | null }>("SELECT MIN(expires_at) AS t FROM items WHERE status = 'pending'")
      .one().t
    const now = Date.now()
    // While something is pending, check every 30 s for abandoned hooks.
    const at = next === null ? now + 6 * 60 * 60 * 1000 : Math.min(next, now + 30_000)
    const current = await this.ctx.storage.getAlarm()
    if (current === null || current > at || next === null) await this.ctx.storage.setAlarm(at)
  }

  override async alarm(): Promise<void> {
    const now = Date.now()
    const due = this.sql
      .exec<{ id: string; expires_at: number; last_polled_at: number }>(
        "SELECT id, expires_at, last_polled_at FROM items WHERE status = 'pending'",
      )
      .toArray()
    for (const row of due) {
      if (row.expires_at <= now) this.finishItem(row.id, 'expired')
      else if (now - row.last_polled_at > ORPHAN_AFTER_MS && !this.waiters.has(row.id)) {
        this.finishItem(row.id, 'cancelled')
      }
    }

    this.sql.exec(
      "DELETE FROM items WHERE status != 'pending' AND created_at < ?",
      now - HISTORY_RETENTION_MS,
    )
    this.sql.exec(
      `DELETE FROM items WHERE status != 'pending' AND id NOT IN
         (SELECT id FROM items ORDER BY created_at DESC LIMIT ?)`,
      HISTORY_MAX_ROWS,
    )
    this.sql.exec('DELETE FROM pairings WHERE expires_at < ?', now)
    await this.scheduleAlarm()
  }

  // -------------------------------------------------------------------------
  // WebSocket stream (hibernation API)
  // -------------------------------------------------------------------------

  private openStream(request: Request, device: DeviceRow): Response {
    if (request.headers.get('upgrade')?.toLowerCase() !== 'websocket') {
      throw new HttpError(426, 'upgrade_required')
    }
    const { 0: client, 1: server } = new WebSocketPair()
    this.ctx.acceptWebSocket(server, [device.id])
    server.serializeAttachment({ deviceId: device.id, active: false } satisfies SocketState)
    const hello: StreamMessage = {
      type: 'hello',
      protocol: PROTOCOL_VERSION,
      pending: this.listItems(new URLSearchParams({ status: 'pending' })),
      settings: this.settings(),
    }
    server.send(JSON.stringify(hello))
    return new Response(null, { status: 101, webSocket: client })
  }

  /** Only presence reports arrive here; "ping" is answered by the auto-response. */
  override webSocketMessage(ws: WebSocket, message: string | ArrayBuffer): void {
    if (typeof message !== 'string') return
    const active = parsePresence(message)
    if (active === null) return
    const state = ws.deserializeAttachment() as SocketState
    if (state.active === active) return
    const wasPresent = this.userAtDesktop()
    ws.serializeAttachment({ ...state, active } satisfies SocketState)
    // The user just left the desk: hand anything still waiting to the phone.
    if (wasPresent && !this.userAtDesktop() && this.mode() === 'away') {
      const pending = this.listItems(new URLSearchParams({ status: 'pending' }))
      this.ctx.waitUntil(
        Promise.all(pending.map((item) => this.pushItem(item))).then(() => undefined),
      )
    }
  }

  /** True while any connected desktop reports recent user input. */
  private userAtDesktop(): boolean {
    return this.ctx
      .getWebSockets()
      .some((socket) => (socket.deserializeAttachment() as SocketState | null)?.active === true)
  }

  override webSocketClose(ws: WebSocket, code: number): void {
    try {
      ws.close(code === 1005 ? 1000 : code, 'closing')
    } catch {
      // already closed
    }
  }

  private broadcast(message: StreamMessage): void {
    const data = JSON.stringify(message)
    for (const socket of this.ctx.getWebSockets()) {
      try {
        socket.send(data)
      } catch {
        // Dead socket; the runtime cleans it up.
      }
    }
  }

  // -------------------------------------------------------------------------
  // Push (FCM)
  // -------------------------------------------------------------------------

  private getFcm(): FcmClient | null {
    if (this.fcm !== undefined) return this.fcm
    const account = parseServiceAccount(this.env.FIREBASE_SERVICE_ACCOUNT)
    const cached = this.kvGet('fcm_access_token')
    this.fcm = account
      ? new FcmClient(account, cached ? (JSON.parse(cached) as CachedAccessToken) : null, (token) =>
          this.kvSet('fcm_access_token', JSON.stringify(token)),
        )
      : null
    return this.fcm
  }

  private pushTargets(): { id: string; push_token: string }[] {
    return this.sql
      .exec<{ id: string; push_token: string }>(
        "SELECT id, push_token FROM devices WHERE kind = 'android' AND push_token IS NOT NULL",
      )
      .toArray()
  }

  private async fanOut(
    payload: PushPayload,
    highPriority: boolean,
    ttlSeconds: number,
  ): Promise<void> {
    const fcm = this.getFcm()
    if (!fcm) return
    await Promise.all(
      this.pushTargets().map(async (target) => {
        try {
          const result = await fcm.send(target.push_token, payload, { highPriority, ttlSeconds })
          if (result === 'unregistered') {
            this.sql.exec('UPDATE devices SET push_token = NULL WHERE id = ?', target.id)
          }
        } catch (error) {
          console.error('push failed', error)
        }
      }),
    )
  }

  private pushItem(item: Item): Promise<void> {
    // "When I'm away": the phone stays quiet while the user works at a desktop.
    if (this.mode() === 'away' && this.userAtDesktop()) return Promise.resolve()
    const question = item.questions?.length === 1 ? item.questions[0] : undefined
    const quickOptions =
      question && !question.multiSelect && question.options.length <= 3
        ? JSON.stringify(question.options.map((o) => o.label))
        : undefined
    const payload: PushPayload = {
      type: 'item',
      id: item.id,
      kind: item.kind,
      title: clipMiddle(item.title, 120),
      // A single question leads with the question itself; its body may be the whole message.
      // 700 characters keep the payload under FCM's 4 KB even in 4-byte UTF-8.
      body: clipMiddle(question?.question ?? item.body ?? item.permission?.summary ?? '', 700),
      project: item.session.project,
      ...(item.session.agent ? { agent: item.session.agent } : {}),
      expiresAt: String(item.expiresAt),
      ...(quickOptions ? { options: quickOptions } : {}),
      ...(item.permission?.canAlwaysAllow ? { canAlwaysAllow: '1' } : {}),
      ...(question?.allowText ? { allowText: '1' } : {}),
    }
    const interactive = isInteractive(item.kind)
    const ttl = interactive ? (item.expiresAt - Date.now()) / 1000 : 60 * 60
    return this.fanOut(payload, interactive, ttl)
  }

  private pushDismiss(item: Item): Promise<void> {
    return this.fanOut({ type: 'dismiss', id: item.id }, false, 10 * 60)
  }

  // -------------------------------------------------------------------------
  // Key/value helpers
  // -------------------------------------------------------------------------

  private kvGet(key: string): string | null {
    const row = this.sql
      .exec<{ value: string }>('SELECT value FROM kv WHERE key = ?', key)
      .toArray()[0]
    return row?.value ?? null
  }

  private kvSet(key: string, value: string): void {
    this.sql.exec(
      'INSERT INTO kv (key, value) VALUES (?, ?) ON CONFLICT (key) DO UPDATE SET value = excluded.value',
      key,
      value,
    )
  }
}
