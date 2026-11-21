/**
 * `nod install` / `nod uninstall`: copies the bundled CLI to a stable location
 * and (un)registers the hooks in the user-level Claude Code settings.
 * Existing settings are preserved; a backup is written before every change.
 */
import { copyFile, mkdir, readFile, writeFile } from 'node:fs/promises'
import { homedir } from 'node:os'
import { delimiter, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { claudeSettingsPath, dataDir, loadConfig, writeJsonFile } from '../config.ts'
import { ok, style, warn } from '../log.ts'

interface HookHandler {
  type: 'command'
  command: string
  timeout?: number
}
interface MatcherGroup {
  matcher?: string
  hooks: HookHandler[]
}
type HooksConfig = Record<string, MatcherGroup[]>

/** Every Nod hook command contains this marker, which is how uninstall finds them. */
const MARKER = 'nod.mjs" hook'

const isNodGroup = (group: MatcherGroup) => group.hooks?.some((h) => h.command?.includes(MARKER))

const readSettings = async (path: string): Promise<Record<string, unknown>> => {
  try {
    return JSON.parse(await readFile(path, 'utf8')) as Record<string, unknown>
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code === 'ENOENT') return {}
    throw new Error(`Cannot parse ${path}; fix it first. (${(error as Error).message})`, {
      cause: error,
    })
  }
}

const withoutNod = (hooks: HooksConfig): HooksConfig =>
  Object.fromEntries(
    Object.entries(hooks)
      .map(([event, groups]) => [event, groups.filter((g) => !isNodGroup(g))] as const)
      .filter(([, groups]) => groups.length > 0),
  )

const nodHooks = (command: string, waitSeconds: number): HooksConfig => {
  const blocking = waitSeconds + 30 // must outlive the relay TTL
  const handler = (timeout: number): HookHandler => ({ type: 'command', command, timeout })
  return {
    PermissionRequest: [{ matcher: '*', hooks: [handler(blocking)] }],
    PreToolUse: [{ matcher: 'AskUserQuestion|ExitPlanMode', hooks: [handler(blocking)] }],
    UserPromptSubmit: [{ hooks: [handler(5)] }],
    Stop: [{ hooks: [handler(15)] }],
    StopFailure: [{ hooks: [handler(15)] }],
    Notification: [{ hooks: [handler(15)] }],
  }
}

const backup = async (path: string, settings: Record<string, unknown>) => {
  if (Object.keys(settings).length > 0) await writeJsonFile(`${path}.nod-backup.json`, settings)
}

export const install = async (): Promise<void> => {
  const config = await loadConfig()
  if (!config) throw new Error('Run `nod setup` first.')

  // Copy ourselves to a stable path so hooks keep working after the repo moves.
  const target = join(dataDir(), 'nod.mjs')
  await mkdir(dataDir(), { recursive: true })
  const self = fileURLToPath(import.meta.url)
  if (self !== target) await copyFile(self, target)

  const path = claudeSettingsPath()
  const settings = await readSettings(path)
  await backup(path, settings)

  const command = `node "${target}" hook`
  const current = withoutNod((settings.hooks as HooksConfig | undefined) ?? {})
  for (const [event, groups] of Object.entries(nodHooks(command, config.waitSeconds))) {
    current[event] = [...(current[event] ?? []), ...groups]
  }
  await writeJsonFile(path, { ...settings, hooks: current }, 0o644)

  ok(`CLI installed to ${style.dim(target)}`)
  ok(`Hooks registered in ${style.dim(path)}`)
  await installShim(target)
  console.log(style.dim('  Restart running Claude Code sessions to pick up the hooks.'))
}

/**
 * Puts a `nod` command on the PATH that runs the installed copy:
 * ~/.local/bin/nod on Linux/macOS, %LOCALAPPDATA%\nod\bin\nod.cmd on Windows.
 */
const installShim = async (target: string): Promise<void> => {
  const windows = process.platform === 'win32'
  const binDir = windows ? join(dataDir(), 'bin') : join(homedir(), '.local', 'bin')
  const shim = join(binDir, windows ? 'nod.cmd' : 'nod')
  await mkdir(binDir, { recursive: true })
  const content = windows
    ? `@echo off\r\nnode "${target}" %*\r\n`
    : `#!/bin/sh\nexec node "${target}" "$@"\n`
  await writeFile(shim, content, { mode: 0o755 })
  const onPath = (process.env.PATH ?? '').split(delimiter).some((dir) => dir === binDir)
  if (onPath) ok(`Command ${style.bold('nod')} is ready`)
  else
    warn(
      `Add ${style.bold(binDir)} to your PATH to use the ${style.bold('nod')} command` +
        (windows ? ' (System settings › Environment variables).' : ' (e.g. in ~/.profile).'),
    )
}

export const uninstall = async (): Promise<void> => {
  const path = claudeSettingsPath()
  const settings = await readSettings(path)
  if (!settings.hooks) {
    warn('No hooks found, nothing to do.')
    return
  }
  await backup(path, settings)
  const hooks = withoutNod(settings.hooks as HooksConfig)
  const { hooks: _removed, ...rest } = settings
  await writeJsonFile(path, Object.keys(hooks).length > 0 ? { ...rest, hooks } : rest, 0o644)
  ok(`Removed Nod hooks from ${style.dim(path)}`)
}

export const isInstalled = async (): Promise<boolean> => {
  const settings = await readSettings(claudeSettingsPath()).catch(
    () => ({}) as Record<string, unknown>,
  )
  const hooks = (settings.hooks as HooksConfig | undefined) ?? {}
  return Object.values(hooks).some((groups) => groups.some(isNodGroup))
}
