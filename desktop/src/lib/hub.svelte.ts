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

  async start(): Promise<void> {
    this.#unlisten = await Promise.all([
      events.snapshot((s) => this.#applySnapshot(s)),
      events.status((s) => (this.status = s)),
      events.message((m) => this.#applyMessage(m)),
    ])
    this.#applySnapshot(await api.snapshot())
    this.ready = true
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
