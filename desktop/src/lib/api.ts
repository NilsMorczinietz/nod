/** Typed bridge to the Rust side (commands + events). */
import { invoke } from '@tauri-apps/api/core'
import { listen, type UnlistenFn } from '@tauri-apps/api/event'
import type {
  ConnectionStatus,
  HubSettings,
  Item,
  Prefs,
  ResolveInput,
  RoutingMode,
  ResolveOutcome,
  Snapshot,
  StreamMessage,
  UpdateInfo,
} from './types'

export const api = {
  snapshot: () => invoke<Snapshot>('snapshot'),
  pair: (link: string, code?: string) => invoke<Snapshot>('pair', { link, code: code || null }),
  unpair: () => invoke<Snapshot>('unpair'),
  resolve: (id: string, input: ResolveInput) => invoke<ResolveOutcome>('resolve', { id, input }),
  listItems: (limit = 50) => invoke<Item[]>('list_items', { limit }),
  setPaused: (paused: boolean) => invoke<HubSettings>('set_paused', { paused }),
  setMode: (mode: RoutingMode) => invoke<HubSettings>('set_mode', { mode }),
  setShortcuts: (active: boolean) => invoke<void>('set_shortcuts', { active }),
  setPrefs: (prefs: Prefs) => invoke<Prefs>('set_prefs', { prefs }),
  overlayLayout: (width: number, height: number) =>
    invoke<void>('overlay_layout', { width, height }),
  openMain: (itemId?: string) => invoke<void>('open_main', { itemId: itemId ?? null }),
  checkUpdate: () => invoke<UpdateInfo | null>('check_update'),
  installUpdate: () => invoke<void>('install_update'),
  openReleaseNotes: () => invoke<void>('open_release_notes'),
}

export const events = {
  snapshot: (cb: (s: Snapshot) => void) => listen<Snapshot>('nod://snapshot', (e) => cb(e.payload)),
  status: (cb: (s: ConnectionStatus) => void) =>
    listen<ConnectionStatus>('nod://status', (e) => cb(e.payload)),
  message: (cb: (m: StreamMessage) => void) =>
    listen<StreamMessage>('nod://message', (e) => cb(e.payload)),
  focusItem: (cb: (id: string) => void) => listen<string>('nod://focus-item', (e) => cb(e.payload)),
  shortcut: (cb: (action: 'approve' | 'deny') => void) =>
    listen<'approve' | 'deny'>('nod://shortcut', (e) => cb(e.payload)),
  overview: (cb: () => void) => listen('nod://overview', () => cb()),
}

export type { UnlistenFn }
