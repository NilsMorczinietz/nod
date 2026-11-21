/** Resolves the app / island theme settings against the system preference. */
import type { AppTheme, IslandTheme } from './types'

const query = matchMedia('(prefers-color-scheme: light)')

class SystemScheme {
  light = $state(query.matches)
  constructor() {
    query.addEventListener('change', (e) => (this.light = e.matches))
  }
}

export const systemScheme = new SystemScheme()

export const resolveApp = (theme: AppTheme): 'light' | 'dark' =>
  theme === 'system' ? (systemScheme.light ? 'light' : 'dark') : theme

export const resolveIsland = (island: IslandTheme, app: AppTheme): 'light' | 'dark' =>
  island === 'app' ? resolveApp(app) : island

/** Puts exactly one of theme-light / theme-dark on <html> (or none for "system"). */
export const applyThemeClass = (theme: 'light' | 'dark' | null): void => {
  const root = document.documentElement.classList
  root.toggle('theme-light', theme === 'light')
  root.toggle('theme-dark', theme === 'dark')
}
