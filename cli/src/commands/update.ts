/**
 * `nod update`: replaces the installed CLI with the latest GitHub release.
 * The download is verified against the release's SHA256SUMS before it is used.
 */
import { spawnSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { mkdir, rename, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { dataDir } from '../config.ts'
import { ok, style, warn } from '../log.ts'
import { REPO, VERSION, compareVersions } from '../version.ts'

interface Release {
  tag_name: string
  html_url: string
  assets: { name: string; browser_download_url: string }[]
}

const github = async (url: string): Promise<Response> => {
  const response = await fetch(url, {
    headers: { 'user-agent': 'nod-cli', accept: 'application/vnd.github+json' },
    signal: AbortSignal.timeout(20_000),
  })
  if (!response.ok) throw new Error(`GitHub request failed (${response.status}): ${url}`)
  return response
}

const asset = (release: Release, name: string): string => {
  const found = release.assets.find((a) => a.name === name)
  if (!found) throw new Error(`Release ${release.tag_name} has no ${name}`)
  return found.browser_download_url
}

export const latestRelease = async (): Promise<Release> => {
  const url = `https://api.github.com/repos/${REPO}/releases/latest`
  const response = await fetch(url, {
    headers: { 'user-agent': 'nod-cli', accept: 'application/vnd.github+json' },
    signal: AbortSignal.timeout(20_000),
  })
  if (response.status === 404) throw new Error(`No published release found for ${REPO}.`)
  if (!response.ok) throw new Error(`GitHub request failed (${response.status}): ${url}`)
  return (await response.json()) as Release
}

export const update = async (opts: { check?: boolean }): Promise<void> => {
  const release = await latestRelease()
  const latest = release.tag_name.replace(/^v/, '')
  if (VERSION !== 'dev' && compareVersions(latest, VERSION) <= 0) {
    ok(`Nod ${VERSION} is up to date.`)
    return
  }
  console.log(
    `Update available: ${style.bold(VERSION)} → ${style.bold(latest)}  ${style.dim(release.html_url)}`,
  )
  if (opts.check) return

  const [script, sums] = await Promise.all([
    github(asset(release, 'nod.mjs')).then((r) => r.arrayBuffer()),
    github(asset(release, 'SHA256SUMS')).then((r) => r.text()),
  ])
  const expected = sums
    .split('\n')
    .map((line) => line.trim().split(/\s+\*?/))
    .find(([, name]) => name === 'nod.mjs')?.[0]
  const actual = createHash('sha256').update(Buffer.from(script)).digest('hex')
  if (!expected || expected !== actual) throw new Error('Checksum mismatch, update aborted.')

  const target = join(dataDir(), 'nod.mjs')
  await mkdir(dataDir(), { recursive: true })
  await writeFile(`${target}.new`, Buffer.from(script), { mode: 0o755 })
  await rename(`${target}.new`, target)
  ok(`Installed Nod ${latest} (checksum verified)`)

  // Let the new version refresh hooks and the PATH shim.
  const result = spawnSync(process.execPath, [target, 'install'], { stdio: 'inherit' })
  if (result.status !== 0) warn('Re-run `nod install` to refresh the hooks.')
}
