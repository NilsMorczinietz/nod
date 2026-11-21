/** `nod setup`: stores relay URL + admin token after verifying them. */
import { createInterface } from 'node:readline/promises'
import { DEFAULT_CONFIG, configPath, loadConfig, saveConfig } from '../config.ts'
import { ok, style } from '../log.ts'
import { RelayClient } from '../relay-client.ts'

const ask = async (question: string): Promise<string> => {
  const rl = createInterface({ input: process.stdin, output: process.stdout })
  try {
    return (await rl.question(question)).trim()
  } finally {
    rl.close()
  }
}

export const setup = async (opts: { relay?: string; token?: string }): Promise<void> => {
  const existing = await loadConfig()
  const answer =
    opts.relay ?? (await ask(`Relay URL${existing ? ` [${existing.relayUrl}]` : ''}: `))
  const relayUrl = answer.trim().replace(/\/+$/, '') || existing?.relayUrl
  if (!relayUrl) throw new Error('A relay URL is required.')
  if (!/^https:\/\/|^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(relayUrl)) {
    throw new Error('The relay URL must use https:// (http is only allowed for localhost).')
  }
  const adminToken =
    opts.token ||
    process.env.NOD_ADMIN_TOKEN ||
    (await ask(`Admin token${existing ? ' [keep current]' : ''}: `)) ||
    existing?.adminToken
  if (!adminToken) throw new Error('An admin token is required.')

  const client = new RelayClient(relayUrl, adminToken)
  const health = await client.health()
  await client.settings() // verifies the token
  await saveConfig({ ...DEFAULT_CONFIG, ...existing, relayUrl, adminToken })

  ok(`Connected to relay (protocol v${health.protocol})`)
  ok(`Config saved to ${style.dim(configPath())}`)
  console.log(
    `\nNext: ${style.bold('nod install')} to register the Claude Code hooks, then ${style.bold('nod pair')}.`,
  )
}
