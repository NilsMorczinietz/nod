/** Desktop-specific types; the wire protocol comes from the shared package. */
import type { HubSettings, Item, ResolveInput, RoutingMode, StreamMessage } from '@nod/protocol'

export type { HubSettings, Item, ResolveInput, RoutingMode, StreamMessage }

export type ConnectionStatus = 'unpaired' | 'connecting' | 'online' | 'offline'

export type OverlayPosition = 'island' | 'corner'

export type IslandSize = 'small' | 'default' | 'large'
export type AppTheme = 'system' | 'light' | 'dark'
export type IslandTheme = 'dark' | 'light' | 'app'

export interface Prefs {
  sound: boolean
  autostart: boolean
  position: OverlayPosition
  islandSize: IslandSize
  appTheme: AppTheme
  islandTheme: IslandTheme
}

export interface Hub {
  status: ConnectionStatus
  pending: Item[]
  settings: HubSettings
}

export interface Snapshot {
  hub: Hub
  paired: boolean
  relayUrl: string | null
  deviceName: string | null
  prefs: Prefs
  /** Windows text size setting (1.0–2.25). */
  textScale: number
  version: string
  /** A newer release, if one was found. */
  update: UpdateInfo | null
}

export interface UpdateInfo {
  version: string
  notes: string | null
}

export interface ResolveOutcome {
  item: Item
  alreadyFinished: boolean
}
