/** Config & state locations (XDG on Linux/macOS, %APPDATA% on Windows). */
import { mkdir, readFile, rename, writeFile } from 'node:fs/promises'
import { homedir } from 'node:os'
import { dirname, join } from 'node:path'

export interface NotifyOptions {
  /** Push when Claude finishes a turn. */
  completed: boolean
  /** Push when a turn fails (API error, etc.). */
  failed: boolean
  /** Push when Claude has been idle and waits for input (often redundant with "completed"). */
  idle: boolean
  /** When a turn ends with a question, ask it on the devices and wake Claude with the reply. */
  replies: boolean
}

export interface Config {
  relayUrl: string
  adminToken: string
  /** How long a permission/question waits for an answer before falling back to the terminal. */
  waitSeconds: number
  /** Only push "completed" for turns that took at least this long. */
  minTaskSeconds: number
  notify: NotifyOptions
}

export const DEFAULT_CONFIG: Omit<Config, 'relayUrl' | 'adminToken'> = {
  waitSeconds: 600,
  minTaskSeconds: 20,
  notify: { completed: true, failed: true, idle: false, replies: true },
}

const isWindows = process.platform === 'win32'

export const configDir = (): string =>
  isWindows
    ? join(process.env.APPDATA ?? join(homedir(), 'AppData', 'Roaming'), 'nod')
    : join(process.env.XDG_CONFIG_HOME ?? join(homedir(), '.config'), 'nod')

export const dataDir = (): string =>
  isWindows
    ? join(process.env.LOCALAPPDATA ?? join(homedir(), 'AppData', 'Local'), 'nod')
    : join(process.env.XDG_DATA_HOME ?? join(homedir(), '.local', 'share'), 'nod')

export const stateDir = (): string =>
  isWindows
    ? join(dataDir(), 'state')
    : join(process.env.XDG_STATE_HOME ?? join(homedir(), '.local', 'state'), 'nod')

export const configPath = (): string => join(configDir(), 'config.json')

export const claudeSettingsPath = (): string =>
  join(process.env.CLAUDE_CONFIG_DIR ?? join(homedir(), '.claude'), 'settings.json')

export const loadConfig = async (): Promise<Config | null> => {
  let stored: Partial<Config> = {}
  try {
    stored = JSON.parse(await readFile(configPath(), 'utf8')) as Partial<Config>
  } catch {
    // missing or unreadable; env vars may still provide everything
  }
  const relayUrl = process.env.NOD_RELAY_URL ?? stored.relayUrl
  const adminToken = process.env.NOD_ADMIN_TOKEN ?? stored.adminToken
  if (!relayUrl || !adminToken) return null
  return {
    ...DEFAULT_CONFIG,
    ...stored,
    notify: { ...DEFAULT_CONFIG.notify, ...stored.notify },
    relayUrl: relayUrl.replace(/\/+$/, ''),
    adminToken,
  }
}

/** Writes JSON atomically (temp file + rename) with owner-only permissions. */
export const writeJsonFile = async (path: string, data: unknown, mode = 0o600): Promise<void> => {
  await mkdir(dirname(path), { recursive: true })
  const temp = `${path}.${process.pid}.tmp`
  await writeFile(temp, `${JSON.stringify(data, null, 2)}\n`, { mode })
  await rename(temp, path)
}

export const saveConfig = (config: Config): Promise<void> => writeJsonFile(configPath(), config)
