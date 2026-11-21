declare const __NOD_VERSION__: string

/** Injected at build time from cli/package.json; "dev" when run from source. */
export const VERSION = typeof __NOD_VERSION__ === 'string' ? __NOD_VERSION__ : 'dev'

/** GitHub repository that publishes releases (override for forks with NOD_REPO=owner/name). */
export const REPO = process.env.NOD_REPO ?? 'NilsMorczinietz/nod'

/** Compares two "x.y.z" versions; returns >0 if a is newer than b. */
export const compareVersions = (a: string, b: string): number => {
  const pa = a
    .replace(/^v/, '')
    .split(/[.-]/)
    .map((n) => Number.parseInt(n, 10) || 0)
  const pb = b
    .replace(/^v/, '')
    .split(/[.-]/)
    .map((n) => Number.parseInt(n, 10) || 0)
  for (let i = 0; i < 3; i++) {
    const diff = (pa[i] ?? 0) - (pb[i] ?? 0)
    if (diff !== 0) return diff
  }
  return 0
}
