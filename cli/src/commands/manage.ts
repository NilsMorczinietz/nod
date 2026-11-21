/** Device management, pause/resume, status and end-to-end test commands. */
import { hostname } from 'node:os'
import { basename } from 'node:path'
import { ROUTING_MODES, type Device, type NewItem, type RoutingMode } from '@nod/protocol'
import { claudeSettingsPath, configPath, loadConfig, type Config } from '../config.ts'
import { fail, ok, style, warn } from '../log.ts'
import { renderQr } from '../qr.ts'
import { RelayClient } from '../relay-client.ts'
import { isInstalled } from './install.ts'

const requireConfig = async (): Promise<{ config: Config; client: RelayClient }> => {
  const config = await loadConfig()
  if (!config) throw new Error('Not configured. Run `nod setup` first.')
  return { config, client: new RelayClient(config.relayUrl, config.adminToken) }
}

const ago = (epochMs: number): string => {
  const s = Math.round((Date.now() - epochMs) / 1000)
  if (s < 90) return 'just now'
  if (s < 90 * 60) return `${Math.round(s / 60)} min ago`
  if (s < 36 * 3600) return `${Math.round(s / 3600)} h ago`
  return `${Math.round(s / 86400)} d ago`
}

const printDevices = (devices: Device[]) => {
  if (devices.length === 0) {
    warn('No devices paired yet. Run `nod pair`.')
    return
  }
  for (const d of devices) {
    const push =
      d.kind === 'android'
        ? d.hasPushToken
          ? style.green(' push ✓')
          : style.yellow(' push ✗')
        : ''
    console.log(
      `  ${style.bold(d.name)} ${style.dim(`(${d.kind})`)}${push}  ${style.dim(`seen ${ago(d.lastSeenAt)} · ${d.id}`)}`,
    )
  }
}

export const pair = async (): Promise<void> => {
  const { client } = await requireConfig()
  const before = new Set((await client.listDevices()).devices.map((d) => d.id))
  const pairing = await client.createPairing()

  console.log(`\n${renderQr(pairing.link)}\n`)
  console.log(`  Scan with the Nod app, or enter manually:`)
  console.log(`  Code   ${style.bold(style.cyan(pairing.code))}`)
  console.log(`  Link   ${style.dim(pairing.link)}`)
  console.log(style.dim(`\n  Waiting for a device… (expires in 10 min, Ctrl+C to stop)`))

  while (Date.now() < pairing.expiresAt) {
    await new Promise((r) => setTimeout(r, 2000))
    const added = (await client.listDevices()).devices.find((d) => !before.has(d.id))
    if (added) {
      ok(`Paired ${style.bold(added.name)} (${added.kind})`)
      return
    }
  }
  fail('Pairing code expired.')
  process.exitCode = 1
}

export const devices = async (): Promise<void> => {
  const { client } = await requireConfig()
  printDevices((await client.listDevices()).devices)
}

export const revoke = async (id: string | undefined): Promise<void> => {
  if (!id) throw new Error('Usage: nod revoke <device-id>')
  const { client } = await requireConfig()
  await client.revokeDevice(id)
  ok('Device revoked.')
}

const MODE_TEXT: Record<RoutingMode, string> = {
  away: 'When I am away: desktop always, phone only while you are away from the PC.',
  always: 'Every time: desktop and phone.',
  updates: 'Updates only: done/failed notifications; requests stay in the terminal.',
  terminal: 'Terminal: nothing is sent; Claude Code asks in the terminal.',
}

export const setPaused = async (paused: boolean): Promise<void> => {
  const { client } = await requireConfig()
  const settings = await client.updateSettings({ paused })
  ok(
    paused
      ? 'Paused: Claude Code asks in the terminal again.'
      : `Resumed. ${MODE_TEXT[settings.mode]}`,
  )
}

export const setMode = async (mode: string | undefined): Promise<void> => {
  const { client } = await requireConfig()
  if (!mode) {
    const settings = await client.settings()
    console.log(MODE_TEXT[settings.mode])
    return
  }
  if (!(ROUTING_MODES as readonly string[]).includes(mode)) {
    throw new Error(`Usage: nod mode <${ROUTING_MODES.join('|')}>`)
  }
  const settings = await client.updateSettings({ mode: mode as RoutingMode })
  ok(MODE_TEXT[settings.mode])
}

export const status = async (): Promise<void> => {
  const config = await loadConfig()
  console.log(`${style.bold('Config')}   ${style.dim(configPath())}`)
  if (!config) {
    warn('Not configured. Run `nod setup`.')
    return
  }
  console.log(`${style.bold('Relay')}    ${config.relayUrl}`)
  console.log(
    `${style.bold('Hooks')}    ${(await isInstalled()) ? style.green('installed') : style.yellow('not installed')} ${style.dim(claudeSettingsPath())}`,
  )
  try {
    const client = new RelayClient(config.relayUrl, config.adminToken)
    const [settings, { devices: list }] = await Promise.all([
      client.settings(),
      client.listDevices(),
    ])
    console.log(
      `${style.bold('State')}    ${settings.paused ? style.yellow('paused') : style.green(settings.mode)}`,
    )
    console.log(style.bold('Devices'))
    printDevices(list)
  } catch (error) {
    fail(`Relay unreachable: ${(error as Error).message}`)
    process.exitCode = 1
  }
}

const SAMPLES: Record<string, (session: NewItem['session']) => NewItem> = {
  permission: (session) => ({
    kind: 'permission',
    session,
    title: 'Run the test suite',
    body: 'npm test -- --coverage',
    ttlSeconds: 120,
    permission: {
      tool: 'Bash',
      summary: 'npm test -- --coverage',
      detail: 'npm test -- --coverage',
      canAlwaysAllow: true,
    },
  }),
  question: (session) => ({
    kind: 'question',
    session,
    title: 'Database?',
    body: 'Which database should the new service use?',
    ttlSeconds: 120,
    questions: [
      {
        question: 'Which database should the new service use?',
        header: 'Database',
        multiSelect: false,
        options: [
          { label: 'SQLite', description: 'Embedded, zero ops' },
          { label: 'Postgres', description: 'Battle-tested server' },
          { label: 'DuckDB', description: 'Analytics-first' },
        ],
      },
    ],
  }),
  plan: (session) => ({
    kind: 'plan',
    session,
    title: 'Plan ready for review',
    body: 'Add a settings screen with a pause toggle and device list.',
    ttlSeconds: 120,
    plan: {
      markdown:
        '## Plan\n\n1. Add `SettingsScreen`\n2. Wire pause toggle to the relay\n3. Show paired devices',
    },
  }),
  completed: (session) => ({
    kind: 'completed',
    session,
    title: 'Task complete',
    body: 'Refactored the relay client and added retry with exponential backoff. All 42 tests pass.',
  }),
}

export const test = async (kind = 'permission'): Promise<void> => {
  const sample = SAMPLES[kind]
  if (!sample) throw new Error(`Unknown sample "${kind}". Use: ${Object.keys(SAMPLES).join(', ')}`)
  const { client } = await requireConfig()
  const session = {
    id: 'nod-test',
    project: basename(process.cwd()),
    cwd: process.cwd(),
    host: hostname(),
  }
  const { item, delivered } = await client.createItem(sample(session))
  if (item.status === 'info') {
    ok(delivered ? 'Notification sent.' : 'Stored, but not delivered (mode or no devices).')
    return
  }
  if (!delivered) {
    warn('Not delivered (mode terminal/updates, or no devices paired).')
    return
  }
  console.log(style.dim('Sent. Answer it on a device… (Ctrl+C to cancel)'))
  process.once('SIGINT', () => void client.cancelItem(item.id).finally(() => process.exit(130)))
  for (;;) {
    const current = await client.waitForItem(item.id, 25)
    if (current.status === 'pending') continue
    if (current.status === 'resolved' && current.resolution) {
      const r = current.resolution
      const answers = r.answers ? ` ${JSON.stringify(r.answers)}` : ''
      ok(
        `${style.bold(r.decision)}${r.alwaysAllow ? ' (always)' : ''}${answers} from ${r.deviceName}`,
      )
    } else {
      warn(`Finished with status "${current.status}".`)
    }
    return
  }
}
