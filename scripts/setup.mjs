#!/usr/bin/env node
// Interactive one-command setup: deploys (or joins) a relay, configures the CLI
// and registers the Claude Code hooks.  Usage: npm run setup
import { spawn, spawnSync } from 'node:child_process'
import { randomBytes } from 'node:crypto'
import { dirname, join } from 'node:path'
import { createInterface } from 'node:readline/promises'
import { fileURLToPath } from 'node:url'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const RELAY = join(ROOT, 'relay')
const CLI = join(ROOT, 'cli', 'dist', 'nod.mjs')
const isWindows = process.platform === 'win32'

const bold = (s) => (process.stdout.isTTY ? `\x1b[1m${s}\x1b[0m` : s)
const dim = (s) => (process.stdout.isTTY ? `\x1b[2m${s}\x1b[0m` : s)
const step = (n, text) => console.log(`\n${bold(`${n}.`)} ${bold(text)}`)
const die = (message) => {
  console.error(`\n✗ ${message}`)
  process.exit(1)
}

const rl = createInterface({ input: process.stdin, output: process.stdout })
const ask = async (q) => (await rl.question(q)).trim()

/** Runs a command, streaming its output; returns stdout when capture is set. */
const run = (cmd, args, { cwd = ROOT, capture = false, input } = {}) =>
  new Promise((resolve, reject) => {
    const child = spawn(cmd, args, {
      cwd,
      shell: isWindows,
      stdio: [input === undefined ? 'inherit' : 'pipe', capture ? 'pipe' : 'inherit', 'inherit'],
    })
    let out = ''
    if (capture) {
      child.stdout.on('data', (chunk) => {
        out += chunk
        process.stdout.write(chunk)
      })
    }
    if (input !== undefined) child.stdin.end(input)
    child.on('error', reject)
    child.on('close', (code) =>
      code === 0 ? resolve(out) : reject(new Error(`${cmd} ${args.join(' ')} failed (${code})`)),
    )
  })

const wrangler = (args, opts = {}) => run('npx', ['wrangler', ...args], { cwd: RELAY, ...opts })

const waitForHealth = async (url) => {
  for (let i = 0; i < 20; i++) {
    try {
      const res = await fetch(`${url}/v1/health`, { signal: AbortSignal.timeout(5000) })
      if (res.ok) return
    } catch {
      // not reachable yet (DNS for new workers.dev subdomains can take a moment)
    }
    await new Promise((r) => setTimeout(r, 3000))
  }
  die(`The relay at ${url} does not respond. Check \`npx wrangler tail\` in relay/.`)
}

const deployRelay = async () => {
  step(2, 'Cloudflare login')
  const whoami = spawnSync('npx', ['wrangler', 'whoami'], {
    cwd: RELAY,
    shell: isWindows,
    encoding: 'utf8',
  })
  if (!/associated with the email|You are logged in/i.test(`${whoami.stdout}${whoami.stderr}`)) {
    await wrangler(['login'])
  } else {
    console.log(dim('  Already logged in.'))
  }

  step(3, 'Deploy the relay')
  const output = await wrangler(['deploy'], { capture: true })
  const url = output.match(/https:\/\/[\w.-]+\.workers\.dev/)?.[0]
  if (!url) die('Could not find the relay URL in the deploy output.')

  step(4, 'Create the admin token')
  console.log(
    dim(
      '  Note: this replaces any previous token; other machines then re-run setup with option 2.',
    ),
  )
  const token = `nod_adm_${randomBytes(32).toString('base64url')}`
  await wrangler(['secret', 'put', 'ADMIN_TOKEN'], { input: `${token}\n` })
  await waitForHealth(url)
  return { url, token, created: true }
}

const joinRelay = async () => {
  step(2, 'Connect to your relay')
  const url = (await ask('  Relay URL (https://…workers.dev): ')).replace(/\/+$/, '')
  const token = await ask('  Admin token (nod_adm_…): ')
  if (!url.startsWith('https://') || !token) die('Relay URL and admin token are required.')
  return { url, token, created: false }
}

const main = async () => {
  console.log(bold('\nNod setup'))
  console.log(dim('Connects Claude Code on this machine to your phone and desktop.'))

  step(1, 'Relay')
  console.log('  [1] First machine: deploy a new relay to my Cloudflare account (free)')
  console.log('  [2] Another machine: connect to the relay I already deployed')
  const choice = await ask('  Choose 1 or 2: ')
  const relay = choice === '2' ? await joinRelay() : await deployRelay()

  step(relay.created ? 5 : 3, 'Install the CLI and Claude Code hooks')
  await run('npm', ['run', 'build', '-w', '@nod/cli'])
  await run(process.execPath, [CLI, 'setup', '--relay', relay.url, '--token', relay.token])
  await run(process.execPath, [CLI, 'install'])
  rl.close()

  console.log(`\n${bold('Done.')} Your relay: ${relay.url}`)
  if (relay.created) {
    console.log(
      `\n${bold('Admin token')} ${dim('(store it in your password manager; needed for other machines):')}`,
    )
    console.log(`  ${relay.token}`)
  }
  console.log(`
${bold('Next')}
  1. Install the apps from https://github.com/NilsMorczinietz/nod/releases/latest
     (Nod-<version>.apk for Android, Nod_<version>_x64-setup.exe for Windows)
  2. Run ${bold('nod pair')} and scan the QR code (Android) or paste the link (Windows)
  3. Try it: ${bold('nod test permission')}
  Optional: phone push while the app is closed → ${bold('npm run setup:push')} (see docs/setup.md)
`)
}

main().catch((error) => die(error.message))
