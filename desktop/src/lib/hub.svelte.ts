/**
 * Reactive mirror of the Rust-side state for one window. Both windows create
 * their own instance; Rust is the source of truth and pushes every change.
 */
import { api, events, type UnlistenFn } from './api'
import type {
  ConnectionStatus,
  HubSettings,
  Item,
  Prefs,
  Snapshot,
  StreamMessage,
  UpdateInfo,
} from './types'

type MessageHandler = (message: StreamMessage) => void

export class HubStore {
  status = $state<ConnectionStatus>('connecting')
  pending = $state<Item[]>([])
  settings = $state<HubSettings>({ mode: 'away', paused: false })
  paired = $state(false)
  relayUrl = $state<string | null>(null)
  deviceName = $state<string | null>(null)
  prefs = $state<Prefs>({
    sound: true,
    autostart: true,
    position: 'island',
    islandSize: 'default',
    appTheme: 'light',
    islandTheme: 'dark',
  })
  textScale = $state(1)
  version = $state('')
  update = $state<UpdateInfo | null>(null)
  ready = $state(false)

  #handlers = new Set<MessageHandler>()
  #unlisten: UnlistenFn[] = []

  /**
   * Subscribes and loads the first snapshot. The webview can start before the
   * backend is ready, so failures are retried instead of leaving a blank window.
   */
  async start(): Promise<void> {
    for (let attempt = 0; ; attempt++) {
      try {
        if (this.#unlisten.length === 0) {
          this.#unlisten = await Promise.all([
            events.snapshot((s) => this.#applySnapshot(s)),
            events.status((s) => (this.status = s)),
            events.message((m) => this.#applyMessage(m)),
          ])
        }
        this.#applySnapshot(await api.snapshot())
        this.ready = true
        return
      } catch (error) {
        if (attempt === 0 || attempt % 10 === 9) {
          void api
            .logError(`startup attempt ${attempt + 1} failed: ${String(error)}`)
            .catch(() => {})
        }
        await new Promise((resolve) => setTimeout(resolve, Math.min(2000, 100 * 2 ** attempt)))
      }
    }
  }

  stop(): void {
    for (const unlisten of this.#unlisten) unlisten()
  }

  /** Subscribe to raw stream messages (e.g. to show info cards). */
  onMessage(handler: MessageHandler): () => void {
    this.#handlers.add(handler)
    return () => this.#handlers.delete(handler)
  }

  #applySnapshot(s: Snapshot): void {
    this.status = s.hub.status
    this.pending = s.hub.pending
    this.settings = s.hub.settings
    this.paired = s.paired
    this.relayUrl = s.relayUrl
    this.deviceName = s.deviceName
    this.prefs = s.prefs
    this.textScale = s.textScale
    this.version = s.version
    this.update = s.update
  }

  #applyMessage(message: StreamMessage): void {
    switch (message.type) {
      case 'hello':
        this.pending = message.pending
        this.settings = message.settings
        this.status = 'online'
        break
      case 'item':
        if (message.item.status === 'pending') this.pending = [...this.pending, message.item]
        break
      case 'update':
        this.pending = this.pending.filter((i) => i.id !== message.item.id)
        if (message.item.status === 'pending') this.pending = [...this.pending, message.item]
        break
      case 'settings':
        this.settings = message.settings
        break
    }
    for (const handler of this.#handlers) handler(message)
  }
}
