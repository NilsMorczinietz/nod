#!/usr/bin/env node
// Stores the Firebase configuration on the relay so the Android app gets pushes.
// Usage: npm run setup:push -- <google-services.json> <service-account.json>
import { spawnSync } from 'node:child_process'
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const PACKAGE = 'io.github.nilsmorczinietz.nod'
const RELAY = join(dirname(fileURLToPath(import.meta.url)), '..', 'relay')

const die = (message) => {
  console.error(`✗ ${message}`)
  process.exit(1)
}

const [servicesPath, accountPath] = process.argv.slice(2)
if (!servicesPath || !accountPath) {
  die('Usage: npm run setup:push -- <google-services.json> <service-account.json>')
}

const readJson = (path) => {
  try {
    return JSON.parse(readFileSync(path, 'utf8'))
  } catch (error) {
    return die(`Cannot read ${path}: ${error.message}`)
  }
}

const services = readJson(servicesPath)
const account = readJson(accountPath)

const client = services.client?.find(
  (c) => c.client_info?.android_client_info?.package_name === PACKAGE,
)
if (!client) die(`google-services.json has no Android app with package name ${PACKAGE}.`)
if (account.type !== 'service_account' || !account.private_key) {
  die(
    'The second file is not a Firebase service-account key (Project settings › Service accounts).',
  )
}

const secrets = {
  FIREBASE_APP_ID: client.client_info.mobilesdk_app_id,
  FIREBASE_API_KEY: client.api_key?.[0]?.current_key,
  FIREBASE_SENDER_ID: services.project_info?.project_number,
  FIREBASE_SERVICE_ACCOUNT: JSON.stringify(account),
}
for (const [key, value] of Object.entries(secrets)) if (!value) die(`Missing value for ${key}.`)

// `wrangler secret bulk` uploads all four in one deployment; the temp file is owner-only.
const dir = mkdtempSync(join(tmpdir(), 'nod-push-'))
const file = join(dir, 'secrets.json')
try {
  writeFileSync(file, JSON.stringify(secrets), { mode: 0o600 })
  const result = spawnSync('npx', ['wrangler', 'secret', 'bulk', file], {
    cwd: RELAY,
    stdio: 'inherit',
    shell: process.platform === 'win32',
  })
  if (result.status !== 0) die('wrangler secret bulk failed.')
} finally {
  rmSync(dir, { recursive: true, force: true })
}

console.log(`
✓ Push is configured for Firebase project ${account.project_id}.
  Pair the phone again (nod pair) so it receives the Firebase settings.
  You can now delete both downloaded JSON files.`)
