#!/usr/bin/env node
// Sets one version across all components. Usage: npm run version:set -- 0.2.0
import { spawnSync } from 'node:child_process'
import { readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const version = process.argv[2]?.replace(/^v/, '')
if (!version || !/^\d+\.\d+\.\d+$/.test(version)) {
  console.error('Usage: npm run version:set -- <major.minor.patch>')
  process.exit(1)
}
const [major, minor, patch] = version.split('.').map(Number)
// Android needs a monotonically increasing integer.
const versionCode = major * 10_000 + minor * 100 + patch

const edit = (path, transform) => {
  const file = join(ROOT, path)
  const before = readFileSync(file, 'utf8')
  const after = transform(before)
  if (after === before) {
    console.log(`· ${path} (unchanged)`)
    return
  }
  writeFileSync(file, after)
  console.log(`✓ ${path}`)
}

const setJsonVersion = (text) => text.replace(/("version":\s*")[^"]+(")/, `$1${version}$2`)

for (const path of [
  'package.json',
  'cli/package.json',
  'relay/package.json',
  'desktop/package.json',
]) {
  edit(path, setJsonVersion)
}
edit('desktop/src-tauri/Cargo.toml', (t) =>
  t.replace(/^version = "[^"]+"/m, `version = "${version}"`),
)
edit('desktop/src-tauri/Cargo.lock', (t) =>
  t.replace(/(name = "nod-desktop"\nversion = ")[^"]+(")/, `$1${version}$2`),
)
edit('desktop/src-tauri/tauri.conf.json', (t) =>
  // Only touch a literal version; "../package.json" is already in sync.
  t.replace(/("version":\s*")(\d+\.\d+\.\d+)(")/, `$1${version}$3`),
)
edit('android/gradle.properties', (t) =>
  t
    .replace(/^nod\.versionName=.*$/m, `nod.versionName=${version}`)
    .replace(/^nod\.versionCode=.*$/m, `nod.versionCode=${versionCode}`),
)

// Keep the workspace versions in package-lock.json in sync (npm ci fails otherwise).
const lock = spawnSync('npm', ['install', '--package-lock-only', '--ignore-scripts'], {
  cwd: ROOT,
  stdio: 'ignore',
  shell: process.platform === 'win32',
})
console.log(
  lock.status === 0 ? '✓ package-lock.json' : '! run `npm install` to update package-lock.json',
)

console.log(`
Version ${version} (Android versionCode ${versionCode}). Next:
  1. Add a "## [${version}]" section to CHANGELOG.md
  2. git commit -am "Release v${version}" && git tag v${version} && git push --follow-tags
`)
